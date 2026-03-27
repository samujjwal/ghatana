/*
 * Copyright (c) 2025 Ghatana
 */
package com.ghatana.services.auth;

import com.ghatana.platform.http.server.response.ErrorResponse;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.security.port.JwtTokenProviders;
import com.ghatana.platform.security.model.User;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.ghatana.platform.security.ratelimit.DefaultRateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiterConfig;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpServer;
import io.activej.http.RoutingServlet;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.Module;
import io.activej.launcher.Launcher;
import io.activej.promise.Promise;
import io.activej.service.ServiceGraphModule;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

/**
 * Authentication and security gateway service launcher.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides centralized authentication and security services using platform auth:
 * <ul>
 * <li>JWT token validation and issuance (via JwtTokenProvider)</li>
 * <li>Tenant context extraction and propagation (via TenantExtractor)</li>
 * <li>Rate limiting per tenant (via RateLimiter)</li>
 * </ul>
 *
 * <p><b>Migration TODO (SHA-007)</b><br>
 * This launcher extends {@link io.activej.launcher.Launcher} directly and
 * manually provides its own {@code Eventloop}, {@code MeterRegistry}, and
 * {@code MetricsCollector} bindings, which duplicates the same boilerplate
 * in every shared service.
 *
 * <p>Migrate to the canonical pattern:
 * <ol>
 *   <li>Extend {@code com.ghatana.core.activej.launcher.ServiceLauncher}
 *       (from {@code platform:java:runtime}) instead of
 *       {@link io.activej.launcher.Launcher}.</li>
 *   <li>Return bindings from {@code createModule()} using
 *       {@code com.ghatana.core.activej.launcher.ServiceCommonModule} for the
 *       {@code Eventloop} and
 *       {@code com.ghatana.platform.observability.ObservabilityModule} for
 *       {@code MeterRegistry} / {@code MetricsCollector}.</li>
 *   <li>Replace the inline {@code /health} and {@code /readiness} route
 *       snippets with
 *       {@code com.ghatana.platform.http.server.servlet.HealthCheckServlet
 *       .addHealthEndpoints(builder, "auth-gateway", VERSION)}.</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Authentication gateway HTTP service
 * @doc.layer product
 * @doc.pattern Service Launcher
 */
public class AuthGatewayLauncher extends Launcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthGatewayLauncher.class);
    private static final int DEFAULT_PORT = 8081;

    @Provides
    Eventloop eventloop() {
        return Eventloop.builder()
                .withThreadName("auth-gateway")
                .build();
    }

    @Provides
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Provides
    MetricsCollector metricsCollector(MeterRegistry registry) {
        return MetricsCollectorFactory.create(registry);
    }

    @Provides
    JwtTokenProvider jwtTokenProvider() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isBlank() || secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET environment variable must be set to a secure value " +
                    "(minimum 32 characters). Do NOT use default secrets in production.");
        }
        long expiryMs = Long.parseLong(System.getenv().getOrDefault("JWT_EXPIRY_MS", "3600000"));

        return JwtTokenProviders.fromSharedSecret(secret, expiryMs);
    }

    @Provides
    CredentialStore credentialStore() {
        boolean useJdbc = Boolean.parseBoolean(
                System.getenv().getOrDefault("USE_JDBC_CREDENTIALS", "false"));

        if (useJdbc) {
            // Production path: JDBC-backed store requires AUTH_DB_URL
            String jdbcUrl = System.getenv("AUTH_DB_URL");
            if (jdbcUrl == null || jdbcUrl.isBlank()) {
                throw new IllegalStateException(
                        "USE_JDBC_CREDENTIALS=true but AUTH_DB_URL is not set");
            }
            com.zaxxer.hikari.HikariConfig cfg = new com.zaxxer.hikari.HikariConfig();
            cfg.setJdbcUrl(jdbcUrl);
            cfg.setUsername(System.getenv().getOrDefault("AUTH_DB_USER", ""));
            cfg.setPassword(System.getenv().getOrDefault("AUTH_DB_PASSWORD", ""));
            cfg.setPoolName("auth-credential-pool");
            cfg.setMinimumIdle(Integer.parseInt(
                System.getenv().getOrDefault("AUTH_DB_POOL_MIN_IDLE", "2")));
            cfg.setMaximumPoolSize(Integer.parseInt(
                System.getenv().getOrDefault("AUTH_DB_POOL_MAX_SIZE", "10")));
            cfg.setConnectionTimeout(Long.parseLong(
                System.getenv().getOrDefault("AUTH_DB_CONNECT_TIMEOUT_MS", "30000")));
            cfg.setIdleTimeout(Long.parseLong(
                System.getenv().getOrDefault("AUTH_DB_IDLE_TIMEOUT_MS", "600000")));
            cfg.setMaxLifetime(Long.parseLong(
                System.getenv().getOrDefault("AUTH_DB_MAX_LIFETIME_MS", "1800000")));
            JdbcCredentialStore store = new JdbcCredentialStore(new com.zaxxer.hikari.HikariDataSource(cfg));
            store.ensureSchema();
            LOGGER.info("Using JdbcCredentialStore (AUTH_DB_URL configured)");
            return store;
        }

        // Development / bootstrap path: in-memory store seeded from env vars
        LOGGER.warn("USE_JDBC_CREDENTIALS is false — using InMemoryCredentialStore (NOT for production)");
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        String adminUser = System.getenv().getOrDefault("ADMIN_USERNAME", "admin");
        String adminPassword = System.getenv("ADMIN_PASSWORD");
        String adminTenant = System.getenv().getOrDefault("ADMIN_TENANT", "default");
        if (adminPassword != null && !adminPassword.isBlank()) {
            store.seedAdmin(adminUser, PasswordHasher.hash(adminPassword), adminTenant);
            LOGGER.info("Seeded admin user: {}", adminUser);
        }
        return store;
    }

    @Provides
    TenantExtractor tenantExtractor() {
        return new TenantExtractor();
    }

    @Provides
    RateLimiter rateLimiter(MetricsCollector metrics) {
        int requestsPerMinute = Integer.parseInt(
                System.getenv().getOrDefault("RATE_LIMIT_REQUESTS_PER_MINUTE", "100"));
        int burstSize = Integer.parseInt(
            System.getenv().getOrDefault("RATE_LIMIT_BURST_SIZE", String.valueOf(requestsPerMinute)));

        return DefaultRateLimiter.create(
            RateLimiterConfig.builder()
                .maxRequestsPerMinute(requestsPerMinute)
                .burstSize(burstSize)
                .build(),
            metrics,
            "auth.gateway.rate_limit"
        );
    }

    @Provides
    RoutingServlet servlet(
            Eventloop eventloop,
            JwtTokenProvider tokenProvider,
            TenantExtractor tenantExtractor,
            RateLimiter rateLimiter,
            MetricsCollector metrics,
            CredentialStore credentialStore) {

        // Platform JWT for cross-product token exchange.
        // Products forward their own JWT here; we validate it and return a
        // short-lived platform-wide token accepted by all services.
        final String platformSecret = System.getenv().getOrDefault(
                "PLATFORM_JWT_SECRET", "dev-platform-jwt-secret-change-me-in-prod!");
        final long platformTokenTtlMs = Long.parseLong(
                System.getenv().getOrDefault("PLATFORM_TOKEN_TTL_MS", String.valueOf(15 * 60 * 1000L)));
        final JwtTokenProvider platformTokenProvider = JwtTokenProviders.fromSharedSecret(platformSecret, platformTokenTtlMs);

        return RoutingServlet.builder(eventloop)
                // Health check
                .with(GET, "/health", request -> {
                    metrics.incrementCounter("auth.gateway.health.count");
                    return HttpResponse.ok200()
                            .withJson("{\"status\":\"healthy\",\"service\":\"auth-gateway\"}")
                            .build()
                            .toPromise();
                })
                // Login - issue JWT token after credential validation
                .with(POST, "/auth/login", request -> {
                    metrics.incrementCounter("auth.gateway.login.count");
                    HttpResponse rateLimitError = rateLimitResponse(request, rateLimiter, metrics, "auth.gateway.login.rate_limited");
                    if (rateLimitError != null) {
                        return Promise.of(rateLimitError);
                    }
                    // Load body asynchronously — never call .getResult() on the eventloop thread
                    return request.loadBody()
                        .then(byteBuf -> {
                            String body = byteBuf.getString(java.nio.charset.StandardCharsets.UTF_8);
                            String username = extractJsonField(body, "username");
                            String password = extractJsonField(body, "password");

                            if (username == null || username.isEmpty()) {
                                metrics.incrementCounter("auth.gateway.login.rejected");
                                return Promise.of(HttpResponse.ofCode(400)
                                        .withJson("{\"error\":\"Username is required\"}")
                                        .build());
                            }
                            if (password == null || password.isEmpty()) {
                                metrics.incrementCounter("auth.gateway.login.rejected");
                                return Promise.of(HttpResponse.ofCode(400)
                                        .withJson("{\"error\":\"Password is required\"}")
                                        .build());
                            }

                            // Validate credentials against the credential store
                            return credentialStore.findByUsername(username)
                                    .then(userOpt -> {
                                        if (userOpt.isEmpty()) {
                                            LOGGER.warn("Login attempt for unknown user: {}", username);
                                            metrics.incrementCounter("auth.gateway.login.failed");
                                            return Promise.of(HttpResponse.ofCode(401)
                                                    .withJson("{\"error\":\"Invalid username or password\"}")
                                                    .build());
                                        }
                                        CredentialStore.StoredUser user = userOpt.get();
                                        if (!user.enabled()) {
                                            LOGGER.warn("Login attempt for disabled user: {}", username);
                                            metrics.incrementCounter("auth.gateway.login.disabled");
                                            return Promise.of(HttpResponse.ofCode(403)
                                                    .withJson("{\"error\":\"Account is disabled\"}")
                                                    .build());
                                        }
                                        if (!PasswordHasher.verify(password, user.passwordHash())) {
                                            LOGGER.warn("Invalid password for user: {}", username);
                                            metrics.incrementCounter("auth.gateway.login.failed");
                                            return Promise.of(HttpResponse.ofCode(401)
                                                    .withJson("{\"error\":\"Invalid username or password\"}")
                                                    .build());
                                        }

                                        // Credentials valid — issue tokens
                                        String accessToken = tokenProvider.createToken(
                                                user.username(), user.roles(),
                                                java.util.Map.of(
                                                        "email", user.email(),
                                                        "tenantId", user.tenantId(),
                                                        "tokenType", "ACCESS"));
                                        String refreshToken = tokenProvider.createToken(
                                                user.username(), user.roles(),
                                                java.util.Map.of(
                                                        "email", user.email(),
                                                        "tenantId", user.tenantId(),
                                                        "tokenType", "REFRESH"));
                                        String response = String.format(
                                                "{\"accessToken\":\"%s\",\"refreshToken\":\"%s\",\"expiresIn\":%d}",
                                                accessToken, refreshToken, 3600);
                                        LOGGER.info("User '{}' authenticated successfully (tenant: {})",
                                                username, user.tenantId());
                                        metrics.incrementCounter("auth.gateway.login.success");
                                        return Promise.of(HttpResponse.ok200()
                                                .withJson(response)
                                                .build());
                                    });
                        })
                        .then(Promise::of, ex -> {
                            LOGGER.error("Login failed", ex);
                            metrics.incrementCounter("auth.gateway.login.errors");
                            return Promise.of(HttpResponse.ofCode(500)
                                    .withJson("{\"error\":\"Login failed\"}")
                                    .build());
                        });
                })
                // Validate - verify JWT token
                .with(GET, "/auth/validate", request -> {
                    metrics.incrementCounter("auth.gateway.validate.count");
                    HttpResponse rateLimitError = rateLimitResponse(request, rateLimiter, metrics, "auth.gateway.validate.rate_limited");
                    if (rateLimitError != null) {
                        return Promise.of(rateLimitError);
                    }

                    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        return HttpResponse.ofCode(401)
                                .withJson("{\"error\":\"Missing or invalid Authorization header\"}")
                                .build()
                                .toPromise();
                    }

                    try {
                        String token = authHeader.substring(7);

                        if (!tokenProvider.validateToken(token)) {
                            return HttpResponse.ofCode(401)
                                    .withJson("{\"valid\":false,\"error\":\"Invalid or expired token\"}")
                                    .build()
                                    .toPromise();
                        }

                        String userId = tokenProvider.getUserIdFromToken(token).orElse("unknown");
                        var claims = tokenProvider.extractClaims(token);
                        String email = claims.map(c -> String.valueOf(c.getOrDefault("email", ""))).orElse("");

                        String response = String.format(
                                "{\"valid\":true,\"userId\":\"%s\",\"email\":\"%s\"}",
                                userId, email);

                        return HttpResponse.ok200()
                                .withJson(response)
                                .build()
                                .toPromise();

                    } catch (Exception ex) {
                        LOGGER.error("Token validation failed", ex);
                        metrics.incrementCounter("auth.gateway.validate.errors");

                        return HttpResponse.ofCode(401)
                                .withJson("{\"valid\":false,\"error\":\"Invalid token\"}")
                                .build()
                                .toPromise();
                    }
                })
                // Refresh - refresh JWT token
                .with(POST, "/auth/refresh", request -> {
                    metrics.incrementCounter("auth.gateway.refresh.count");
                    HttpResponse rateLimitError = rateLimitResponse(request, rateLimiter, metrics, "auth.gateway.refresh.rate_limited");
                    if (rateLimitError != null) {
                        return Promise.of(rateLimitError);
                    }
                    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        return HttpResponse.ofCode(401)
                                .withJson("{\"error\":\"Missing refresh token in Authorization header\"}")
                                .build()
                                .toPromise();
                    }
                    try {
                        String refreshToken = authHeader.substring(7);

                        if (!tokenProvider.validateToken(refreshToken)) {
                            return HttpResponse.ofCode(401)
                                    .withJson("{\"error\":\"Invalid or expired refresh token\"}")
                                    .build()
                                    .toPromise();
                        }

                        String userId = tokenProvider.getUserIdFromToken(refreshToken).orElse("unknown");
                        java.util.List<String> roles = tokenProvider.getRolesFromToken(refreshToken);
                        String newAccessToken = tokenProvider.createToken(userId, roles,
                                java.util.Map.of("tokenType", "ACCESS"));
                        String response = String.format(
                                "{\"accessToken\":\"%s\",\"expiresIn\":%d}",
                                newAccessToken, 3600);
                        return HttpResponse.ok200()
                                .withJson(response)
                                .build()
                                .toPromise();
                    } catch (Exception ex) {
                        LOGGER.error("Token refresh failed", ex);
                        metrics.incrementCounter("auth.gateway.refresh.errors");
                        return HttpResponse.ofCode(401)
                                .withJson("{\"error\":\"Invalid or expired refresh token\"}")
                                .build()
                                .toPromise();
                    }
                })
                // Tenant - extract tenant context
                .with(GET, "/auth/tenant", request -> {
                    metrics.incrementCounter("auth.gateway.tenant.count");
                    String tenantId = tenantExtractor.extract(request, tokenProvider);
                    return HttpResponse.ok200()
                            .withJson(String.format("{\"tenantId\":\"%s\"}", tenantId))
                            .build()
                            .toPromise();
                })
                // Cross-product token exchange
                //
                // Any product holding a valid product-scoped JWT can POST it here
                // to receive a short-lived (15 min) platform-wide token that all
                // other products and shared services accept.
                //
                // Request:  POST /auth/exchange
                //           Authorization: Bearer <product-jwt>
                //
                // Response: { "platformToken": "...", "expiresIn": 900 }
                //
                // The platform token carries the original subject, roles, email,
                // tenantId, and a "tokenType":"PLATFORM" claim so services can
                // distinguish it from product-scoped tokens.
                .with(POST, "/auth/exchange", request -> {
                    metrics.incrementCounter("auth.gateway.exchange.count");
                    HttpResponse rateLimitError = rateLimitResponse(request, rateLimiter, metrics, "auth.gateway.exchange.rate_limited");
                    if (rateLimitError != null) {
                        return Promise.of(rateLimitError);
                    }
                    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        return Promise.of(HttpResponse.ofCode(401)
                                .withJson("{\"error\":\"Missing or invalid Authorization header\"}")
                                .build());
                    }
                    try {
                        String productToken = authHeader.substring(7);
                        // Validate the incoming product-scoped JWT
                        if (!tokenProvider.validateToken(productToken)) {
                            metrics.incrementCounter("auth.gateway.exchange.rejected");
                            return Promise.of(HttpResponse.ofCode(401)
                                    .withJson("{\"error\":\"Invalid or expired product token\"}")
                                    .build());
                        }
                        String userId = tokenProvider.getUserIdFromToken(productToken).orElse("unknown");
                        java.util.List<String> roles = tokenProvider.getRolesFromToken(productToken);
                        java.util.Map<String, Object> srcClaims =
                                tokenProvider.extractClaims(productToken).orElse(java.util.Map.of());
                        String email    = String.valueOf(srcClaims.getOrDefault("email", ""));
                        String tenantId = String.valueOf(srcClaims.getOrDefault("tenantId", "default"));

                        // Issue platform-wide short-lived token
                        String platformToken = platformTokenProvider.createToken(
                                userId, roles,
                                java.util.Map.of(
                                        "email",      email,
                                        "tenantId",   tenantId,
                                        "tokenType",  "PLATFORM",
                                        "issuer",     "ghatana-auth-gateway"
                                )
                        );
                        long expiresIn = platformTokenTtlMs / 1000L;
                        LOGGER.info("Issued platform token for userId={} tenantId={}", userId, tenantId);
                        metrics.incrementCounter("auth.gateway.exchange.success");
                        return Promise.of(HttpResponse.ok200()
                                .withJson(String.format(
                                    "{\"platformToken\":\"%s\",\"expiresIn\":%d}",
                                    platformToken, expiresIn))
                                .build());
                    } catch (Exception ex) {
                        LOGGER.error("Cross-product token exchange failed", ex);
                        metrics.incrementCounter("auth.gateway.exchange.errors");
                        return Promise.of(HttpResponse.ofCode(500)
                                .withJson("{\"error\":\"Token exchange failed\"}")
                                .build());
                    }
                })
                .build();
    }

    @Provides
    HttpServer httpServer(Eventloop eventloop, RoutingServlet servlet) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", String.valueOf(DEFAULT_PORT)));

        return HttpServer.builder(eventloop, servlet)
                .withListenPort(port)
                .build();
    }

    @Override
    protected Module getModule() {
        return ServiceGraphModule.create();
    }

    @Override
    protected void run() throws Exception {
        LOGGER.info("Starting Auth Gateway Service on port {}...",
                System.getenv().getOrDefault("PORT", String.valueOf(DEFAULT_PORT)));
        awaitShutdown();
    }

    public static void main(String[] args) throws Exception {
        LOGGER.info("Auth Gateway Service starting...");
        AuthGatewayLauncher launcher = new AuthGatewayLauncher();
        launcher.launch(args);
    }

    /**
     * Simple JSON field extractor (avoids adding a JSON library dependency).
     * Handles fields with string values in flat JSON objects.
     */
    private static String extractJsonField(String json, String field) {
        if (json == null) return null;
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) return null;
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return null;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }

    private static HttpResponse rateLimitResponse(
            HttpRequest request,
            RateLimiter rateLimiter,
            MetricsCollector metrics,
            String metricName
    ) {
        String rateLimitKey = resolveRateLimitKey(request);
        RateLimiter.AcquireResult result = rateLimiter.tryAcquire(rateLimitKey);
        if (result.allowed()) {
            return null;
        }

        metrics.incrementCounter(metricName, "key", rateLimitKey);
        return ResponseBuilder.status(429)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(result.retryAfterSeconds()))
            .json(ErrorResponse.of(429, "RATE_LIMIT_EXCEEDED", "Too many authentication requests. Retry later."))
                .build();
    }

    private static String resolveRateLimitKey(HttpRequest request) {
        String forwardedFor = request.getHeader(HttpHeaders.of("X-Forwarded-For"));
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int comma = forwardedFor.indexOf(',');
            return comma >= 0 ? forwardedFor.substring(0, comma).trim() : forwardedFor.trim();
        }

        String realIp = request.getHeader(HttpHeaders.of("X-Real-IP"));
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        String remoteAddress = request.getHeader(HttpHeaders.of("X-Remote-Addr"));
        if (remoteAddress != null && !remoteAddress.isBlank()) {
            return remoteAddress.trim();
        }

        return "unknown-client";
    }
}
