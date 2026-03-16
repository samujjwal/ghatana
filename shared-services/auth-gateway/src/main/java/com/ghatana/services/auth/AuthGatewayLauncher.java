/*
 * Copyright (c) 2025 Ghatana
 */
package com.ghatana.services.auth;

import com.ghatana.platform.security.jwt.JwtTokenProvider;
import com.ghatana.platform.security.model.User;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpHeaders;
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

        return new JwtTokenProvider(secret, expiryMs);
    }

    @Provides
    CredentialStore credentialStore() {
        // TODO: Replace with JdbcCredentialStore backed by a real database
        //       for production deployments.
        InMemoryCredentialStore store = new InMemoryCredentialStore();

        // Seed admin user from environment variables (for bootstrapping)
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

        return new RateLimiter(requestsPerMinute, metrics);
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
        final JwtTokenProvider platformTokenProvider = new JwtTokenProvider(platformSecret, platformTokenTtlMs);

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
                    try {
                        String body = request.loadBody().getResult().getString(java.nio.charset.StandardCharsets.UTF_8);
                        String username = extractJsonField(body, "username");
                        String password = extractJsonField(body, "password");

                        if (username == null || username.isEmpty()) {
                            metrics.incrementCounter("auth.gateway.login.rejected");
                            return HttpResponse.ofCode(400)
                                    .withJson("{\"error\":\"Username is required\"}")
                                    .build()
                                    .toPromise();
                        }
                        if (password == null || password.isEmpty()) {
                            metrics.incrementCounter("auth.gateway.login.rejected");
                            return HttpResponse.ofCode(400)
                                    .withJson("{\"error\":\"Password is required\"}")
                                    .build()
                                    .toPromise();
                        }

                        // Validate credentials against the credential store
                        return credentialStore.findByUsername(username)
                                .then(userOpt -> {
                                    if (userOpt.isEmpty()) {
                                        LOGGER.warn("Login attempt for unknown user: {}", username);
                                        metrics.incrementCounter("auth.gateway.login.failed");
                                        return HttpResponse.ofCode(401)
                                                .withJson("{\"error\":\"Invalid username or password\"}")
                                                .build()
                                                .toPromise();
                                    }
                                    CredentialStore.StoredUser user = userOpt.get();
                                    if (!user.enabled()) {
                                        LOGGER.warn("Login attempt for disabled user: {}", username);
                                        metrics.incrementCounter("auth.gateway.login.disabled");
                                        return HttpResponse.ofCode(403)
                                                .withJson("{\"error\":\"Account is disabled\"}")
                                                .build()
                                                .toPromise();
                                    }
                                    if (!PasswordHasher.verify(password, user.passwordHash())) {
                                        LOGGER.warn("Invalid password for user: {}", username);
                                        metrics.incrementCounter("auth.gateway.login.failed");
                                        return HttpResponse.ofCode(401)
                                                .withJson("{\"error\":\"Invalid username or password\"}")
                                                .build()
                                                .toPromise();
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
                                    return HttpResponse.ok200()
                                            .withJson(response)
                                            .build()
                                            .toPromise();
                                });
                    } catch (Exception ex) {
                        LOGGER.error("Login failed", ex);
                        metrics.incrementCounter("auth.gateway.login.errors");
                        return HttpResponse.ofCode(500)
                                .withJson("{\"error\":\"Login failed\"}")
                                .build()
                                .toPromise();
                    }
                })
                // Validate - verify JWT token
                .with(GET, "/auth/validate", request -> {
                    metrics.incrementCounter("auth.gateway.validate.count");

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
}
