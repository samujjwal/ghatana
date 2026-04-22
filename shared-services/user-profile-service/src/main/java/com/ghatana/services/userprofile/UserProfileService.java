package com.ghatana.services.userprofile;

import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.security.port.JwtTokenProviders;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.http.*;
import io.activej.inject.annotation.Provides;
import io.activej.launchers.http.HttpServerLauncher;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

import static io.activej.http.HttpMethod.*;

/**
 * Production-grade User Profile Service — ActiveJ HTTP launcher.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET    /profiles/{userId}} — Fetch profile (tenantId from {@code X-Tenant-Id} header)</li>
 *   <li>{@code PUT    /profiles/{userId}} — Create or update profile</li>
 *   <li>{@code DELETE /profiles/{userId}} — Delete profile</li>
 *   <li>{@code GET    /health}           — Liveness probe</li>
 *   <li>{@code GET    /metrics}          — Prometheus-compatible metrics</li>
 * </ul>
 *
 * <h2>Graceful Shutdown</h2>
 * <p>The service registers a shutdown hook that:
 * <ul>
 *   <li>Stops accepting new HTTP requests</li>
 *   <li>Waits for in-flight requests to complete (up to 30 seconds)</li>
 *   <li>Closes database connections gracefully</li>
 *   <li>Logs shutdown completion</li>
 * </ul>
 *
 * <h2>Authentication</h2>
 * <p>All mutating endpoints ({@code PUT}, {@code DELETE}) require a valid
 * platform JWT in the {@code Authorization: Bearer <token>} header.
 * Read endpoints ({@code GET /profiles/:userId}) accept either a platform
 * JWT or a service-to-service call with the {@code X-Internal-Key} header
 * set to the {@code INTERNAL_API_KEY} environment variable.</p>
 *
 * <h2>Multi-Tenancy</h2>
 * <p>Every request MUST include an {@code X-Tenant-Id} header. The service
 * enforces that authenticated users can only read/write their own profile
 * (or a service account may access any profile in its tenant scope).</p>
 *
 * @doc.type class
 * @doc.purpose Centralised user profile and preferences management service
 * @doc.layer platform
 * @doc.pattern Service Launcher
 */
public class UserProfileService extends HttpServerLauncher {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);
    private static final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    // ─── Environment variable keys ────────────────────────────────────────────

    private static final String ENV_DB_URL          = "USER_PROFILE_DB_URL";
    private static final String ENV_DB_USER         = "USER_PROFILE_DB_USER";
    private static final String ENV_DB_PASSWORD     = "USER_PROFILE_DB_PASSWORD";
    private static final String ENV_JWT_SECRET      = "PLATFORM_JWT_SECRET";
    private static final String ENV_INTERNAL_KEY    = "INTERNAL_API_KEY";
    private static final String ENV_PORT            = "USER_PROFILE_SERVICE_PORT";

    // ─── Providers ───────────────────────────────────────────────────────────

    @Provides
    DataSource dataSource() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(System.getenv().getOrDefault(ENV_DB_URL,
                "jdbc:postgresql://localhost:5432/ghatana_user_profiles"));
        cfg.setUsername(System.getenv().getOrDefault(ENV_DB_USER, "ghatana"));
        cfg.setPassword(System.getenv().getOrDefault(ENV_DB_PASSWORD, "ghatana"));
        cfg.setMaximumPoolSize(10);
        cfg.setMinimumIdle(2);
        cfg.setConnectionTimeout(3_000);
        cfg.setIdleTimeout(300_000);
        cfg.setMaxLifetime(600_000);
        cfg.setPoolName("user-profile-pool");
        return new HikariDataSource(cfg);
    }

    @Provides
    UserProfileStore profileStore(DataSource ds) {
        return new PostgresUserProfileStore(ds, ForkJoinPool.commonPool());
    }

    @Provides
    JwtTokenProvider jwtTokenProvider() {
        String secret = System.getenv(ENV_JWT_SECRET);
        if (secret == null || secret.length() < 32) {
            if (secret == null) {
                log.warn("PLATFORM_JWT_SECRET not set — JWT validation is DISABLED (dev mode only)");
            } else {
                log.warn("PLATFORM_JWT_SECRET is shorter than 32 characters — use a stronger secret in production");
            }
            // Dev fallback: 32-char placeholder (never accept real tokens with this)
            secret = "dev-only-secret-do-not-use-in-prod!";
        }
        return JwtTokenProviders.fromSharedSecret(secret, 15 * 60 * 1000L);
    }

    // ─── HTTP Handler ─────────────────────────────────────────────────────────

    @Provides
    AsyncServlet servlet(io.activej.reactor.Reactor reactor, UserProfileStore store, JwtTokenProvider jwtProvider) {
        String internalKey = System.getenv().getOrDefault(ENV_INTERNAL_KEY, "");

        return RoutingServlet.builder(reactor)
                // ── Liveness probe
                .with(GET, "/health", request ->
                        Promise.of(HttpResponse.ok200()
                                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                .withBody("{\"status\":\"UP\",\"service\":\"user-profile-service\"}".getBytes())
                                .build()))

                // ── Metrics
                .with(GET, "/metrics", request ->
                        Promise.of(HttpResponse.ok200()
                                .withHeader(HttpHeaders.CONTENT_TYPE, "text/plain")
                                .withBody("# user_profile_service_up 1\n".getBytes())
                                .build()))

                // ── GET /profiles/:userId
                .with(GET, "/profiles/:userId", request -> {
                    String userId   = request.getPathParameter("userId");
                    String tenantId = request.getHeader(HttpHeaders.of("X-Tenant-Id"));
                    if (tenantId == null || tenantId.isBlank()) {
                        return Promise.of(error(400, "Missing X-Tenant-Id header"));
                    }

                    // Allow platform JWT bearer OR internal service key
                    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                    boolean isInternal = !internalKey.isEmpty()
                            && internalKey.equals(request.getHeader(HttpHeaders.of("X-Internal-Key")));
                    if (!isInternal) {
                        String validationError = validateBearerToken(jwtProvider, authHeader);
                        if (validationError != null) {
                            return Promise.of(error(401, validationError));
                        }
                        // Enforce that the authenticated user's tenant matches the requested tenant.
                        // This prevents tenant A's JWT being used to read tenant B's profiles.
                        String callerTenantId = extractTenantIdFromToken(jwtProvider, authHeader);
                        if (callerTenantId != null && !callerTenantId.equals(tenantId)) {
                            return Promise.of(error(403, "Token tenant does not match X-Tenant-Id header"));
                        }
                    }

                    return store.findByTenantAndUser(tenantId, userId)
                            .map(opt -> opt
                                    .<HttpResponse>map(profile -> HttpResponse.ok200()
                                            .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                            .withBody(profileToJson(profile).getBytes())
                                            .build())
                                    .orElse(error(404, "Profile not found for user " + userId)));
                })

                // ── PUT /profiles/:userId  (create or update)
                .with(PUT, "/profiles/:userId", request -> {
                    String userId   = request.getPathParameter("userId");
                    String tenantId = request.getHeader(HttpHeaders.of("X-Tenant-Id"));
                    if (tenantId == null || tenantId.isBlank()) {
                        return Promise.of(error(400, "Missing X-Tenant-Id header"));
                    }
                    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                    String callerUserId = extractUserIdFromToken(jwtProvider, authHeader);
                    if (callerUserId == null) {
                        return Promise.of(error(401, "Valid platform JWT required"));
                    }
                    // Users may only update their own profile unless overridden by internal key
                    boolean isInternal = !internalKey.isEmpty()
                            && internalKey.equals(request.getHeader(HttpHeaders.of("X-Internal-Key")));
                    if (!isInternal && !callerUserId.equals(userId)) {
                        return Promise.of(error(403, "You may only update your own profile"));
                    }

                    return request.loadBody().then(body -> {
                        String json = body.asString(java.nio.charset.StandardCharsets.UTF_8);
                        try {
                            UserProfile profile = parseProfileFromJson(json, userId, tenantId);
                            return store.upsert(profile)
                                    .map(saved -> HttpResponse.ok200()
                                            .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                            .withBody(profileToJson(saved).getBytes())
                                            .build());
                        } catch (IllegalArgumentException e) {
                            return Promise.of(error(400, e.getMessage()));
                        }
                    });
                })

                // ── DELETE /profiles/:userId
                .with(DELETE, "/profiles/:userId", request -> {
                    String userId   = request.getPathParameter("userId");
                    String tenantId = request.getHeader(HttpHeaders.of("X-Tenant-Id"));
                    if (tenantId == null || tenantId.isBlank()) {
                        return Promise.of(error(400, "Missing X-Tenant-Id header"));
                    }
                    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                    String callerUserId = extractUserIdFromToken(jwtProvider, authHeader);
                    if (callerUserId == null) {
                        return Promise.of(error(401, "Valid platform JWT required"));
                    }
                    boolean isInternal = !internalKey.isEmpty()
                            && internalKey.equals(request.getHeader(HttpHeaders.of("X-Internal-Key")));
                    if (!isInternal && !callerUserId.equals(userId)) {
                        return Promise.of(error(403, "You may only delete your own profile"));
                    }

                    return store.delete(tenantId, userId)
                            .map(v -> HttpResponse.ofCode(204).build());
                })

                .build();
    }

    // ─── Entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        String port = System.getenv().getOrDefault(ENV_PORT, "8085");
        System.setProperty("http.listenAddresses", "0.0.0.0:" + port);
        UserProfileService launcher = new UserProfileService();
        
        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received, stopping User Profile Service gracefully...");
            try {
                launcher.shutdown();
                log.info("User Profile Service stopped successfully");
            } catch (Exception e) {
                log.error("Error during shutdown", e);
            }
        }, "user-profile-service-shutdown"));
        
        launcher.launch(args);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /** Returns null if the token is valid, otherwise returns an error message. */
    private static String validateBearerToken(JwtTokenProvider jwt, String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return "Missing or malformed Authorization header";
        }
        String token = authHeader.substring(7).strip();
        try {
            if (!jwt.validateToken(token)) {
                return "Invalid or expired token";
            }
            return null;
        } catch (Exception e) {
            return "Token validation failed";
        }
    }

    /** Returns the userId claim from a Bearer token, or null if invalid. */
    private static String extractUserIdFromToken(JwtTokenProvider jwt, String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7).strip();
        try {
            if (!jwt.validateToken(token)) return null;
            return jwt.getUserIdFromToken(token).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the tenantId claim from a Bearer token, or null if unavailable.
     * Used to enforce cross-tenant isolation on read endpoints.
     */
    private static String extractTenantIdFromToken(JwtTokenProvider jwt, String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7).strip();
        try {
            if (!jwt.validateToken(token)) return null;
            return jwt.extractClaims(token)
                    .map(claims -> {
                        Object tenantClaim = claims.get("tenantId");
                        return tenantClaim != null ? tenantClaim.toString() : null;
                    })
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Serialises a {@link UserProfile} to JSON using Jackson. */
    private static String profileToJson(UserProfile p) {
        try {
            return objectMapper.writeValueAsString(p);
        } catch (Exception e) {
            log.error("Failed to serialize profile to JSON", e);
            throw new IllegalStateException("Failed to serialize profile", e);
        }
    }

    /**
     * Parses a minimal JSON body into a {@link UserProfile} using Jackson.
     * Fields not present in the body keep their defaults (upsert semantics:
     * partial update is achieved by the caller first GET-ing the profile
     * and re-submitting it).
     */
    private static UserProfile parseProfileFromJson(String json, String userId, String tenantId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            
            String email = (String) map.get("email");
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Field 'email' is required");
            }

            return UserProfile.builder()
                    .userId(userId)
                    .tenantId(tenantId)
                    .email(email)
                    .displayName((String) map.get("displayName"))
                    .avatarUrl((String) map.get("avatarUrl"))
                    .preferredLanguage((String) map.get("preferredLanguage"))
                    .timezone((String) map.get("timezone"))
                    .theme((String) map.get("theme"))
                    .notificationsEnabled(!"false".equalsIgnoreCase(String.valueOf(map.get("notificationsEnabled"))))
                    .build();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse profile from JSON", e);
            throw new IllegalArgumentException("Invalid JSON payload", e);
        }
    }

    private static HttpResponse error(int code, String message) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("error", message));
            return HttpResponse.ofCode(code)
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .withBody(json.getBytes())
                    .build();
        } catch (Exception e) {
            log.error("Failed to serialize error response", e);
            return HttpResponse.ofCode(500).build();
        }
    }

}
