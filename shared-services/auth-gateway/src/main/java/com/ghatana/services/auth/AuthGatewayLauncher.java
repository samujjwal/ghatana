/*
 * Copyright (c) 2025 Ghatana
 */
package com.ghatana.services.auth;

import com.ghatana.core.activej.launcher.ServiceLauncher;
import com.ghatana.core.activej.launcher.ServiceCommonModule;
import com.ghatana.platform.config.ConfigManager;
import com.ghatana.platform.http.server.response.ErrorResponse;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.http.server.servlet.HealthCheckServlet;
import com.ghatana.platform.observability.ObservabilityModule;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.security.port.JwtTokenProviders;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.TracingConfiguration;
import com.ghatana.platform.security.ratelimit.DefaultRateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiterConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpServer;
import io.activej.http.RoutingServlet;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.Module;
import io.activej.inject.module.ModuleBuilder;
import io.activej.promise.Promise;
import io.activej.service.ServiceGraphModule;
import io.micrometer.core.instrument.MeterRegistry;
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
public class AuthGatewayLauncher extends ServiceLauncher {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthGatewayLauncher.class);
    private static final int DEFAULT_PORT = 8081;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_PLATFORM_JWT_SECRET = "dev-platform-jwt-secret-change-me-in-prod!";

    // Constants for duplicate literals
    private static final String AUTH_GATEWAY = "auth-gateway";
    private static final String EMAIL = "email";
    private static final String TENANT_ID = "tenantId";
    private static final String TOKEN_TYPE = "tokenType";
    private static final String JTI = "jti";
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
        // Default to true for production safety
        boolean useJdbc = config.getBoolean("USE_JDBC_CREDENTIALS").orElse(true);
        
        // Production environment check: fail if JDBC not configured in production
        String deploymentEnv = config.getString("DEPLOYMENT_ENV").orElse("local");
        boolean isProduction = !deploymentEnv.matches("local|dev|test|development");
        
        if (isProduction && !useJdbc) {
            throw new IllegalStateException(
                "SECURITY ERROR: USE_JDBC_CREDENTIALS must be true in production environment '" + deploymentEnv + "'. " +
                "In-memory credential store is not safe for production deployments. " +
                "Set USE_JDBC_CREDENTIALS=true and configure AUTH_DB_URL.");
        }

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
    TokenBlocklist tokenBlocklist(ConfigManager config, CredentialStore credentialStore) {
        // Use JDBC blocklist if credential store is JDBC-backed
        boolean useJdbc = config.getBoolean("USE_JDBC_CREDENTIALS").orElse(true);
        
        if (useJdbc && credentialStore instanceof JdbcCredentialStore) {
            // Reuse the same DataSource from JdbcCredentialStore
            // Note: This requires JdbcCredentialStore to expose its DataSource
            // For now, we'll create a new connection pool for the blocklist
            String jdbcUrl = config.getString("AUTH_DB_URL").orElse(null);
            if (jdbcUrl != null && !jdbcUrl.isBlank()) {
                com.zaxxer.hikari.HikariConfig cfg = new com.zaxxer.hikari.HikariConfig();
                cfg.setJdbcUrl(jdbcUrl);
                cfg.setUsername(config.getString("AUTH_DB_USER").orElse(""));
                cfg.setPassword(config.getString("AUTH_DB_PASSWORD").orElse(""));
                cfg.setPoolName("auth-blocklist-pool");
                cfg.setMinimumIdle(1);
                cfg.setMaximumPoolSize(5);
                JdbcTokenBlocklist blocklist = new JdbcTokenBlocklist(new com.zaxxer.hikari.HikariDataSource(cfg));
                blocklist.ensureSchema();
                LOGGER.info("Using JdbcTokenBlocklist (AUTH_DB_URL configured)");
                return blocklist;
            }
        }

        // Development path: in-memory blocklist
        LOGGER.warn("Using InMemoryTokenBlocklist (NOT for production)");
        return new InMemoryTokenBlocklist();
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
            CredentialStore credentialStore,
            TokenBlocklist tokenBlocklist) {

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
                                        String jti = java.util.UUID.randomUUID().toString();
                                        long refreshTokenExpiryMs = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000); // 7 days
                                        
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
                                                        TOKEN_TYPE, "REFRESH",
                                                        JTI, jti));
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
                // Logout - revoke refresh token
                .with(POST, "/auth/logout", request -> {
                    metrics.incrementCounter("auth.gateway.logout.count");
                    HttpResponse rateLimitError = rateLimitResponse(request, rateLimiter, metrics, "auth.gateway.logout.rate_limited");
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
                    return request.loadBody()
                        .then(byteBuf -> {
                            String body = byteBuf.getString(java.nio.charset.StandardCharsets.UTF_8);
                            String refreshToken = authHeader.substring(7);

                            // Validate the refresh token
                            if (!tokenProvider.validateToken(refreshToken)) {
                                metrics.incrementCounter("auth.gateway.logout.invalid");
                                return Promise.of(ResponseBuilder.status(401)
                                        .json(ErrorResponse.of(401, UNAUTHORIZED, "Invalid or expired refresh token"))
                                        .build());
                            }

                            // Extract jti claim and add to blocklist
                            java.util.Map<String, Object> claims = tokenProvider.extractClaims(refreshToken).orElse(java.util.Map.of());
                            String jti = String.valueOf(claims.getOrDefault(JTI, ""));
                            
                            if (jti == null || jti.isEmpty() || "null".equals(jti)) {
                                LOGGER.warn("Logout attempted but refresh token has no jti claim");
                                metrics.incrementCounter("auth.gateway.logout.no_jti");
                                return Promise.of(ResponseBuilder.status(400)
                                        .json(ErrorResponse.of(400, "INVALID_TOKEN", "Refresh token missing jti claim"))
                                        .build());
                            }

                            long tokenExpiry = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000); // Default to 7 days from now
                            return tokenBlocklist.block(jti, tokenExpiry)
                                .then(() -> {
                                    LOGGER.info("Refresh token revoked (jti={})", jti);
                                    metrics.incrementCounter("auth.gateway.logout.success");
                                    return Promise.of(HttpResponse.ok200()
                                            .withJson("{\"message\":\"Refresh token revoked\"}")
                                            .build());
                                });
                        })
                        .then(Promise::of, ex -> {
                            LOGGER.error("Logout failed", ex);
                            metrics.incrementCounter("auth.gateway.logout.errors");
                            return Promise.of(ResponseBuilder.status(500)
                                    .json(ErrorResponse.of(500, "INTERNAL_SERVER_ERROR", "Logout failed"))
                                    .build());
                        });
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

                        // Check if refresh token is blocked
                        java.util.Map<String, Object> claims = tokenProvider.extractClaims(refreshToken).orElse(java.util.Map.of());
                        String jti = String.valueOf(claims.getOrDefault(JTI, ""));
                        
                        if (jti != null && !jti.isEmpty() && !"null".equals(jti)) {
                            return tokenBlocklist.isBlocked(jti)
                                .then(blocked -> {
                                    if (blocked) {
                                        LOGGER.warn("Refresh token blocked (jti={})", jti);
                                        metrics.incrementCounter("auth.gateway.refresh.blocked");
                                        return Promise.of(ResponseBuilder.status(401)
                                                .json(ErrorResponse.of(401, UNAUTHORIZED, "Refresh token has been revoked"))
                                                .build());
                                    }
                                    
                                    // Token not blocked, proceed with refresh
                                    String userId = tokenProvider.getUserIdFromToken(refreshToken).orElse("unknown");
                                    java.util.List<String> roles = tokenProvider.getRolesFromToken(refreshToken);
                                    String newAccessToken = tokenProvider.createToken(userId, roles,
                                            java.util.Map.of(TOKEN_TYPE, "ACCESS"));
                                    String response = String.format(
                                            "{\"accessToken\":\"%s\",\"expiresIn\":%d}",
                                            newAccessToken, 3600);
                                    return Promise.of(HttpResponse.ok200()
                                            .withJson(response)
                                            .build());
                                });
                        }
                        
                        // No jti claim, proceed with refresh (backward compatibility)
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
    protected Module createModule() {
        return ModuleBuilder.create()
                .install(new ServiceCommonModule(AUTH_GATEWAY))
                .install(new ObservabilityModule())
                .install(ServiceGraphModule.create())
                .build();
    }

    @Override
    protected void onServiceStarted() {
        int port = ConfigManager.createDefault("auth-gateway").getInt("PORT").orElse(DEFAULT_PORT);
        LOGGER.info("Auth Gateway Service started on port {}", port);
    }

    public static void main(String[] args) throws Exception {
        LOGGER.info("Auth Gateway Service starting...");
        AuthGatewayLauncher launcher = new AuthGatewayLauncher();
        launcher.launch(args);
    }

    /**
     * Extract JSON field using Jackson ObjectMapper.
     * Handles fields with string values, escaped quotes, and nested objects.
     */
    private static String extractJsonField(String json, String field) {
        if (json == null) return null;
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode fieldNode = root.get(field);
            if (fieldNode == null) return null;
            return fieldNode.isTextual() ? fieldNode.asText() : fieldNode.toString();
        } catch (Exception e) {
            LOGGER.warn("Failed to extract JSON field '{}': {}", field, e.getMessage());
            return null;
        }
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
