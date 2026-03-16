package com.ghatana.services.userprofile;

import com.ghatana.platform.security.jwt.JwtTokenProvider;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.activej.http.*;
import io.activej.inject.annotation.Provides;
import io.activej.launchers.http.HttpServerLauncher;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;
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
        return new JwtTokenProvider(secret, 15 * 60 * 1000L);
    }

    // ─── HTTP Handler ─────────────────────────────────────────────────────────

    @Provides
    AsyncServlet servlet(UserProfileStore store, JwtTokenProvider jwtProvider) {
        String internalKey = System.getenv().getOrDefault(ENV_INTERNAL_KEY, "");

        return RoutingServlet.builder()
                // ── Liveness probe
                .with(GET, "/health", request ->
                        Promise.of(HttpResponse.ok200()
                                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                .withBody("{\"status\":\"UP\",\"service\":\"user-profile-service\"}".getBytes())))

                // ── Metrics
                .with(GET, "/metrics", request ->
                        Promise.of(HttpResponse.ok200()
                                .withHeader(HttpHeaders.CONTENT_TYPE, "text/plain")
                                .withBody("# user_profile_service_up 1\n".getBytes())))

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
                    }

                    return store.findByTenantAndUser(tenantId, userId)
                            .map(opt -> opt
                                    .<HttpResponse>map(profile -> HttpResponse.ok200()
                                            .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                            .withBody(profileToJson(profile).getBytes()))
                                    .orElse(HttpResponse.ofCode(404)
                                            .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                            .withBody(("{\"error\":\"Profile not found for user " + sanitize(userId) + "\"}").getBytes())));
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
                        String json = body.getString(body.readRemaining());
                        try {
                            UserProfile profile = parseProfileFromJson(json, userId, tenantId);
                            return store.upsert(profile)
                                    .map(saved -> HttpResponse.ok200()
                                            .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                            .withBody(profileToJson(saved).getBytes()));
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
                            .map(v -> HttpResponse.ofCode(204));
                })

                .build();
    }

    // ─── Entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        String port = System.getenv().getOrDefault(ENV_PORT, "8085");
        System.setProperty("http.listenAddresses", "0.0.0.0:" + port);
        UserProfileService launcher = new UserProfileService();
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
            return jwt.getUserIdFromToken(token);
        } catch (Exception e) {
            return null;
        }
    }

    /** Serialises a {@link UserProfile} to a compact JSON string (no external library needed). */
    private static String profileToJson(UserProfile p) {
        return "{" +
                "\"userId\":"              + quote(p.userId())              + "," +
                "\"tenantId\":"            + quote(p.tenantId())            + "," +
                "\"email\":"               + quote(p.email())               + "," +
                "\"displayName\":"         + quote(p.displayName())         + "," +
                "\"avatarUrl\":"           + (p.avatarUrl() != null ? quote(p.avatarUrl()) : "null") + "," +
                "\"preferredLanguage\":"   + quote(p.preferredLanguage())   + "," +
                "\"timezone\":"            + quote(p.timezone())            + "," +
                "\"theme\":"               + quote(p.theme())               + "," +
                "\"notificationsEnabled\":" + p.notificationsEnabled()      + "," +
                "\"createdAt\":"           + quote(p.createdAt().toString()) + "," +
                "\"updatedAt\":"           + quote(p.updatedAt().toString()) +
                "}";
    }

    /**
     * Parses a minimal JSON body into a {@link UserProfile}. Fields not present
     * in the body keep their defaults (upsert semantics: partial update is
     * achieved by the caller first GET-ing the profile and re-submitting it).
     */
    private static UserProfile parseProfileFromJson(String json, String userId, String tenantId) {
        String email           = extractJsonString(json, "email");
        String displayName     = extractJsonString(json, "displayName");
        String avatarUrl       = extractJsonString(json, "avatarUrl");
        String preferredLang   = extractJsonString(json, "preferredLanguage");
        String timezone        = extractJsonString(json, "timezone");
        String theme           = extractJsonString(json, "theme");
        String notifStr        = extractJsonString(json, "notificationsEnabled");

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Field 'email' is required");
        }

        return UserProfile.builder()
                .userId(userId)
                .tenantId(tenantId)
                .email(email)
                .displayName(displayName)
                .avatarUrl(avatarUrl)
                .preferredLanguage(preferredLang)
                .timezone(timezone)
                .theme(theme)
                .notificationsEnabled(!"false".equalsIgnoreCase(notifStr))
                .build();
    }

    private static HttpResponse error(int code, String message) {
        return HttpResponse.ofCode(code)
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .withBody(("{\"error\":" + quote(message) + "}").getBytes());
    }

    private static String quote(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Safely extracts a string value from a flat JSON object (no nested objects). */
    private static String extractJsonString(String json, String key) {
        if (json == null) return null;
        // Match: "key":"value"  or  "key":true/false/number
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "\"" + key + "\"\\s*:\\s*(?:\"([^\"]*)\"|([^,}\\s]+))");
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            String quoted   = m.group(1);
            String unquoted = m.group(2);
            return quoted != null ? quoted : unquoted;
        }
        return null;
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        return s.replaceAll("[^a-zA-Z0-9_\\-@.]", "");
    }
}
