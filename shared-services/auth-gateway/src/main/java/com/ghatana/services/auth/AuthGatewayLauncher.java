/*
 * Copyright (c) 2025 Ghatana
 */
package com.ghatana.services.auth;

import com.ghatana.platform.config.ConfigManager;
import com.ghatana.platform.http.server.response.ErrorResponse;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.http.server.servlet.HealthCheckServlet;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.security.port.JwtTokenProviders;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.ghatana.platform.observability.TracingConfiguration;
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
    private static final String DEFAULT_PLATFORM_JWT_SECRET = "dev-platform-jwt-secret-change-me-in-prod!";

    // Constants for duplicate literals
    private static final String AUTH_GATEWAY = "auth-gateway";
    private static final String EMAIL = "email";
    private static final String TENANT_ID = "tenantId";
    private static final String TOKEN_TYPE = "tokenType";
    private static final String UNAUTHORIZED = "UNAUTHORIZED";

    static String resolvePlatformJwtSecret(String deploymentEnv, String configuredSecret) {
        String normalizedEnv = deploymentEnv == null ? "development" : deploymentEnv.trim().toLowerCase();
        boolean localProfile = normalizedEnv.isBlank()
                || "local".equals(normalizedEnv)
                || "development".equals(normalizedEnv)
                || "dev".equals(normalizedEnv)
                || "test".equals(normalizedEnv);

        if (localProfile) {
            if (configuredSecret == null || configuredSecret.isBlank()) {
                LOGGER.warn("PLATFORM_JWT_SECRET not set for local profile '{}'; using development-only fallback", normalizedEnv);
                return DEFAULT_PLATFORM_JWT_SECRET;
            }
            return configuredSecret;
        }

        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw new IllegalStateException(
                    "PLATFORM_JWT_SECRET environment variable must be set for non-local deployment profiles.");
        }

        if (configuredSecret.length() < 32 || DEFAULT_PLATFORM_JWT_SECRET.equals(configuredSecret)) {
            throw new IllegalStateException(
                    "PLATFORM_JWT_SECRET environment variable must be set to a secure value " +
                    "(minimum 32 characters and not the development fallback)."
            );
        }

        return configuredSecret;
    }

    @Provides
    ConfigManager configManager() {
        return ConfigManager.createDefault(AUTH_GATEWAY);
    }

    @Provides
    Eventloop eventloop() {
        return Eventloop.builder()
                .withThreadName(AUTH_GATEWAY)
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
    io.opentelemetry.api.OpenTelemetry openTelemetry(ConfigManager config) {
        String env = config.getString("DEPLOYMENT_ENV").orElse("development");
        boolean enabled = config.getBoolean("TRACING_ENABLED").orElse(true);
        String otlpEndpoint = config.getString("OTEL_EXPORTER_OTLP_ENDPOINT").orElse("http://localhost:4317");

        return TracingConfiguration.initialize(
                TracingConfiguration.TracingConfig.builder()
                        .enabled(enabled)
                        .serviceName(AUTH_GATEWAY)
                        .serviceVersion("1.0.0")
                        .environment(env)
                        .otlpEndpoint(otlpEndpoint)
                        .build());
    }

    @Provides
    JwtTokenProvider jwtTokenProvider(ConfigManager config) {
        String secret = config.getString("JWT_SECRET").orElse(null);
        if (secret == null || secret.isBlank() || secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET environment variable must be set to a secure value " +
                    "(minimum 32 characters). Do NOT use default secrets in production.");
        }
        long expiryMs = config.getLong("JWT_EXPIRY_MS").orElse(3_600_000L);

        return JwtTokenProviders.fromSharedSecret(secret, expiryMs);
    }

    @Provides
    CredentialStore credentialStore(ConfigManager config) {
        boolean useJdbc = config.getBoolean("USE_JDBC_CREDENTIALS").orElse(false);

        if (useJdbc) {
            // Production path: JDBC-backed store requires AUTH_DB_URL
            String jdbcUrl = config.getString("AUTH_DB_URL").orElse(null);
            if (jdbcUrl == null || jdbcUrl.isBlank()) {
                throw new IllegalStateException(
                        "USE_JDBC_CREDENTIALS=true but AUTH_DB_URL is not set");
            }
            com.zaxxer.hikari.HikariConfig cfg = new com.zaxxer.hikari.HikariConfig();
            cfg.setJdbcUrl(jdbcUrl);
            cfg.setUsername(config.getString("AUTH_DB_USER").orElse(""));
            cfg.setPassword(config.getString("AUTH_DB_PASSWORD").orElse(""));
            cfg.setPoolName("auth-credential-pool");
            cfg.setMinimumIdle(config.getInt("AUTH_DB_POOL_MIN_IDLE").orElse(2));
            cfg.setMaximumPoolSize(config.getInt("AUTH_DB_POOL_MAX_SIZE").orElse(10));
            cfg.setConnectionTimeout(config.getLong("AUTH_DB_CONNECT_TIMEOUT_MS").orElse(30_000L));
            cfg.setIdleTimeout(config.getLong("AUTH_DB_IDLE_TIMEOUT_MS").orElse(600_000L));
            cfg.setMaxLifetime(config.getLong("AUTH_DB_MAX_LIFETIME_MS").orElse(1_800_000L));
            JdbcCredentialStore store = new JdbcCredentialStore(new com.zaxxer.hikari.HikariDataSource(cfg));
            store.ensureSchema();
            LOGGER.info("Using JdbcCredentialStore (AUTH_DB_URL configured)");
            return store;
        }

        // Development / bootstrap path: in-memory store seeded from config
        LOGGER.warn("USE_JDBC_CREDENTIALS is false — using InMemoryCredentialStore (NOT for production)");
        InMemoryCredentialStore store = new InMemoryCredentialStore();
        String adminUser = config.getString("ADMIN_USERNAME").orElse("admin");
        String adminPassword = config.getString("ADMIN_PASSWORD").orElse(null);
        String adminTenant = config.getString("ADMIN_TENANT").orElse("default");
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
    RateLimiter rateLimiter(ConfigManager config, MetricsCollector metrics) {
        int requestsPerMinute = config.getInt("RATE_LIMIT_REQUESTS_PER_MINUTE").orElse(100);
        int burstSize = config.getInt("RATE_LIMIT_BURST_SIZE").orElse(requestsPerMinute);

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
            ConfigManager config,
            Eventloop eventloop,
            JwtTokenProvider tokenProvider,
            TenantExtractor tenantExtractor,
            RateLimiter rateLimiter,
            MetricsCollector metrics,
            CredentialStore credentialStore) {

        // Platform JWT for cross-product token exchange.
        // Products forward their own JWT here; we validate it and return a
        // short-lived platform-wide token accepted by all services.
        final String platformSecret = resolvePlatformJwtSecret(
            config.getString("DEPLOYMENT_ENV").orElse("development"),
            config.getString("PLATFORM_JWT_SECRET").orElse(null)
        );
        final long platformTokenTtlMs = config.getLong("PLATFORM_TOKEN_TTL_MS")
                .orElse(15L * 60 * 1000);
        final JwtTokenProvider platformTokenProvider = JwtTokenProviders.fromSharedSecret(platformSecret, platformTokenTtlMs);

        return HealthCheckServlet.addHealthEndpoints(
                RoutingServlet.builder(eventloop), AUTH_GATEWAY, "1.0.0")
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
                                return Promise.of(ResponseBuilder.status(400)
                                        .json(ErrorResponse.of(400, "VALIDATION_ERROR", "Username is required"))
                                        .build());
                            }
                            if (password == null || password.isEmpty()) {
                                metrics.incrementCounter("auth.gateway.login.rejected");
                                return Promise.of(ResponseBuilder.status(400)
                                        .json(ErrorResponse.of(400, "VALIDATION_ERROR", "Password is required"))
                                        .build());
                            }

                            // Validate credentials against the credential store
                            return credentialStore.findByUsername(username)
                                    .then(userOpt -> {
                                        if (userOpt.isEmpty()) {
                                            LOGGER.warn("Login attempt for unknown user: {}", username);
                                            metrics.incrementCounter("auth.gateway.login.failed");
                                            return Promise.of(ResponseBuilder.status(401)
                                                    .json(ErrorResponse.of(401, "AUTHENTICATION_ERROR", "Invalid username or password"))
                                                    .build());
                                        }
                                        CredentialStore.StoredUser user = userOpt.get();
                                        if (!user.enabled()) {
                                            LOGGER.warn("Login attempt for disabled user: {}", username);
                                            metrics.incrementCounter("auth.gateway.login.disabled");
                                            return Promise.of(ResponseBuilder.status(403)
                                                    .json(ErrorResponse.of(403, "FORBIDDEN", "Account is disabled"))
                                                    .build());
                                        }
                                        if (!PasswordHasher.verify(password, user.passwordHash())) {
                                            LOGGER.warn("Invalid password for user: {}", username);
                                            metrics.incrementCounter("auth.gateway.login.failed");
                                            return Promise.of(ResponseBuilder.status(401)
                                                    .json(ErrorResponse.of(401, "AUTHENTICATION_ERROR", "Invalid username or password"))
                                                    .build());
                                        }

                                        // Credentials valid — issue tokens
                                        String accessToken = tokenProvider.createToken(
                                                user.username(), user.roles(),
                                                java.util.Map.of(
                                                        EMAIL, user.email(),
                                                        TENANT_ID, user.tenantId(),
                                                        TOKEN_TYPE, "ACCESS"));
                                        String refreshToken = tokenProvider.createToken(
                                                user.username(), user.roles(),
                                                java.util.Map.of(
                                                        EMAIL, user.email(),
                                                        TENANT_ID, user.tenantId(),
                                                        TOKEN_TYPE, "REFRESH"));
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
                            return Promise.of(ResponseBuilder.status(500)
                                    .json(ErrorResponse.of(500, "INTERNAL_SERVER_ERROR", "Login failed"))
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
                        return ResponseBuilder.status(401)
                                .json(ErrorResponse.of(401, UNAUTHORIZED, "Missing or invalid Authorization header"))
                                .build()
                                .toPromise();
                    }

                    try {
                        String token = authHeader.substring(7);

                        if (!tokenProvider.validateToken(token)) {
                            return ResponseBuilder.status(401)
                                    .json(ErrorResponse.of(401, UNAUTHORIZED, "Invalid or expired token"))
                                    .build()
                                    .toPromise();
                        }

                        String userId = tokenProvider.getUserIdFromToken(token).orElse("unknown");
                        var claims = tokenProvider.extractClaims(token);
                        String email = claims.map(c -> String.valueOf(c.getOrDefault(EMAIL, ""))).orElse("");

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

                        return ResponseBuilder.status(401)
                                .json(ErrorResponse.of(401, UNAUTHORIZED, "Invalid token"))
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
                        return ResponseBuilder.status(401)
                                .json(ErrorResponse.of(401, UNAUTHORIZED, "Missing refresh token in Authorization header"))
                                .build()
                                .toPromise();
                    }
                    try {
                        String refreshToken = authHeader.substring(7);

                        if (!tokenProvider.validateToken(refreshToken)) {
                            return ResponseBuilder.status(401)
                                    .json(ErrorResponse.of(401, UNAUTHORIZED, "Invalid or expired refresh token"))
                                    .build()
                                    .toPromise();
                        }

                        String userId = tokenProvider.getUserIdFromToken(refreshToken).orElse("unknown");
                        java.util.List<String> roles = tokenProvider.getRolesFromToken(refreshToken);
                        String newAccessToken = tokenProvider.createToken(userId, roles,
                                java.util.Map.of(TOKEN_TYPE, "ACCESS"));
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
                        return ResponseBuilder.status(401)
                                .json(ErrorResponse.of(401, UNAUTHORIZED, "Invalid or expired refresh token"))
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
                        return Promise.of(ResponseBuilder.status(401)
                                .json(ErrorResponse.of(401, UNAUTHORIZED, "Missing or invalid Authorization header"))
                                .build());
                    }
                    try {
                        String productToken = authHeader.substring(7);
                        // Validate the incoming product-scoped JWT
                        if (!tokenProvider.validateToken(productToken)) {
                            metrics.incrementCounter("auth.gateway.exchange.rejected");
                            return Promise.of(ResponseBuilder.status(401)
                                    .json(ErrorResponse.of(401, "UNAUTHORIZED", "Invalid or expired product token"))
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
                        return Promise.of(ResponseBuilder.status(500)
                                .json(ErrorResponse.of(500, "INTERNAL_SERVER_ERROR", "Token exchange failed"))
                                .build());
                    }
                })
                .build();
    }

    @Provides
    HttpServer httpServer(ConfigManager config, Eventloop eventloop, RoutingServlet servlet) {
        int port = config.getInt("PORT").orElse(DEFAULT_PORT);

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
        int port = ConfigManager.createDefault("auth-gateway").getInt("PORT").orElse(DEFAULT_PORT);
        LOGGER.info("Starting Auth Gateway Service on port {}...", port);
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
