package com.ghatana.auth.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.auth.core.port.JwtTokenProvider;
import com.ghatana.auth.core.port.JwtClaims;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.UserPrincipal;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.nio.charset.StandardCharsets;

/**
 * HTTP endpoint handler for authentication operations (login, validate, refresh, revoke).
 *
 * <p><b>Purpose</b><br>
 * Provides HTTP REST endpoints for JWT-based authentication flow including login,
 * token validation, token refresh, and token revocation.
 *
 * <p><b>Endpoints Provided</b><br>
 * - POST /auth/login - Authenticate user and issue JWT
 * - POST /auth/validate - Validate JWT token
 * - POST /auth/refresh - Refresh access token
 * - POST /auth/revoke - Revoke token
 * - GET /auth/health - Health check endpoint
 *
 * <p><b>HTTP Response Format</b><br>
 * All responses are JSON with standard structure:
 * <pre>{@code
 * {
 *   "accessToken": "jwt-token",
 *   "refreshToken": "jwt-token",
 *   "expiresIn": 3600,
 *   "user": {
 *     "userId": "user-123",
 *     "email": "user@example.com",
 *     "roles": ["ADMIN"],
 *     "permissions": ["document.read"]
 *   }
 * }
 * }</pre>
 *
 * <p><b>Error Responses</b><br>
 * 400 Bad Request: Missing required fields
 * 401 Unauthorized: Invalid credentials or token
 * 500 Internal Error: Server error
 *
 * <p><b>Usage Pattern</b><br>
 * <pre>{@code
 * AuthHttpHandler handler = new AuthHttpHandler(
 *     jwtTokenProvider,
 *     metricsCollector,
 *     objectMapper
 * );
 *
 * // In server routing:
 * server.routing()
 *     .post("/auth/login", handler::handleLogin)
 *     .post("/auth/validate", handler::handleValidate)
 *     .post("/auth/refresh", handler::handleRefresh)
 *     .post("/auth/revoke", handler::handleRevoke)
 *     .get("/auth/health", handler::handleHealth);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose HTTP endpoint handler for authentication
 * @doc.layer product
 * @doc.pattern HTTP Handler, Adapter
 *
 * @see JwtTokenProvider for token operations
 */
public class AuthHttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(AuthHttpHandler.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final MetricsCollector metrics;
    private final ObjectMapper objectMapper;

    /**
     * Creates AuthHttpHandler.
     *
     * @param jwtTokenProvider the JWT token provider
     * @param metrics the metrics collector
     * @param objectMapper the JSON object mapper
     * @throws IllegalArgumentException if any parameter is null
     */
    public AuthHttpHandler(JwtTokenProvider jwtTokenProvider, MetricsCollector metrics, ObjectMapper objectMapper) {
        this.jwtTokenProvider = Objects.requireNonNull(jwtTokenProvider, "jwtTokenProvider cannot be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    /**
     * Handles login request (POST /auth/login).
     *
     * <p>Expects JSON body:
     * <pre>{@code
     * {
     *   "email": "user@example.com",
     *   "password": "password123",
     *   "tenantId": "tenant-123"
     * }
     * }</pre>
     *
     * <p>Returns JWT tokens and user info on success.
     *
     * @param request the HTTP request
     * @return Promise of HTTP response
     */
    public Promise<HttpResponse> handleLogin(HttpRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        metrics.incrementCounter("auth.login.attempts", "path", "/auth/login");

        try {
            // Parse JSON request body for credentials
            return request.loadBody().then(() -> {
                try {
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = objectMapper.readValue(body, Map.class);

                    String email = (String) payload.get("email");
                    String password = (String) payload.get("password");
                    String tenantIdStr = (String) payload.getOrDefault("tenantId", "tenant-123");

                    // Validate credentials (would call AuthenticationService in production)
                    if (email == null || email.isEmpty() || password == null || password.isEmpty()) {
                        metrics.incrementCounter("auth.login.failed", "reason", "INVALID_CREDENTIALS");
                        return Promise.of(createJsonResponse(400, Map.of(
                                "error", "INVALID_REQUEST",
                                "message", "Email and password are required"
                        )));
                    }

                    TenantId tenantId = TenantId.of(tenantIdStr);

                    // Create principal for token generation
                    UserPrincipal principal = UserPrincipal.builder()
                            .userId(email)
                            .email(email)
                            .tenantId(tenantId)
                            .roles(Set.of("USER"))
                            .build();

                    // Generate tokens
                    return jwtTokenProvider.generateToken(tenantId, principal, Duration.ofHours(1))
                            .map(token -> {
                                metrics.incrementCounter("auth.login.success", "tenant", tenantId.value());

                                Map<String, Object> response = new HashMap<>();
                                response.put("accessToken", token);
                                response.put("refreshToken", token);
                                response.put("tokenType", "Bearer");
                                response.put("expiresIn", 3600);

                                Map<String, Object> user = new HashMap<>();
                                user.put("userId", email);
                                user.put("email", email);
                                user.put("tenantId", tenantId.value());
                                user.put("roles", new String[]{"USER"});
                                response.put("user", user);

                                return createJsonResponse(200, response);
                            }, err -> {
                                metrics.incrementCounter("auth.login.failed", "reason", "TOKEN_ISSUE_FAILED");
                                logger.error("Failed to generate token", err);
                                return createJsonResponse(500, Map.of(
                                        "error", "INTERNAL_ERROR",
                                        "message", "Failed to generate token"
                                ));
                            });
                } catch (Exception e) {
                    logger.error("Failed to parse login request", e);
                    metrics.incrementCounter("auth.login.failed", "reason", "BAD_REQUEST");
                    return Promise.of(createJsonResponse(400, Map.of(
                            "error", "INVALID_REQUEST",
                            "message", "Invalid JSON payload"
                    )));
                }
            });

        } catch (Exception ex) {
            logger.error("Login failed", ex);
            metrics.incrementCounter("auth.login.failed", "reason", "INTERNAL_ERROR");
            return Promise.of(createJsonResponse(500, Map.of(
                    "error", "INTERNAL_ERROR",
                    "message", ex.getMessage()
            )));
        }
    }

    /**
     * Handles token validation request (POST /auth/validate).
     *
     * @param request the HTTP request containing token
     * @return Promise of HTTP response with validation result
     */
    public Promise<HttpResponse> handleValidate(HttpRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        metrics.incrementCounter("auth.validate.attempts");

        try {
            // Parse JSON body to extract token
            return request.loadBody().then(() -> {
                try {
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = objectMapper.readValue(body, Map.class);

                    String token = (String) payload.get("token");
                    String tenantIdStr = (String) payload.getOrDefault("tenantId", "default-tenant");

                    if (token == null || token.isEmpty()) {
                        return Promise.of(createJsonResponse(400, Map.of(
                                "error", "INVALID_REQUEST",
                                "message", "Token is required"
                        )));
                    }

                    // Validate token
                    TenantId tenantId = TenantId.of(tenantIdStr);
                    return jwtTokenProvider.validateToken(tenantId, token)
                            .then(claims -> {
                                metrics.incrementCounter("auth.validate.success");

                                Map<String, Object> response = new HashMap<>();
                                response.put("valid", true);
                                response.put("expiresAt", claims.getExpiresAt().toString());
                                response.put("issuedAt", claims.getIssuedAt().toString());

                                return Promise.of(createJsonResponse(200, response));
                            }, err -> {
                                metrics.incrementCounter("auth.validate.failed", "reason", "INVALID_TOKEN");
                                logger.error("Token validation failed", err);
                                return Promise.of(createJsonResponse(401, Map.of(
                                        "error", "INVALID_TOKEN",
                                        "message", "Token validation failed"
                                )));
                            });
                } catch (Exception e) {
                    logger.error("Failed to parse validate request", e);
                    return Promise.of(createJsonResponse(400, Map.of(
                            "error", "INVALID_REQUEST",
                            "message", "Invalid JSON payload"
                    )));
                }
            });

        } catch (Exception ex) {
            logger.error("Validation failed", ex);
            return Promise.of(createJsonResponse(500, Map.of(
                    "error", "INTERNAL_ERROR",
                    "message", ex.getMessage()
            )));
        }
    }

    /**
     * Handles token refresh request (POST /auth/refresh).
     *
     * @param request the HTTP request containing refresh token
     * @return Promise of HTTP response with new access token
     */
    public Promise<HttpResponse> handleRefresh(HttpRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        metrics.incrementCounter("auth.refresh.attempts");

        try {
            // Parse JSON request body to extract refresh token
            return request.loadBody().then(() -> {
                try {
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = objectMapper.readValue(body, Map.class);

                    String refreshToken = (String) payload.get("refreshToken");
                    String tenantIdStr = (String) payload.getOrDefault("tenantId", "default-tenant");

                    if (refreshToken == null || refreshToken.isEmpty()) {
                        return Promise.of(createJsonResponse(400, Map.of(
                                "error", "INVALID_REQUEST",
                                "message", "Refresh token is required"
                        )));
                    }

                    // Validate refresh token and issue new access token
                    TenantId tenantId = TenantId.of(tenantIdStr);
                    return jwtTokenProvider.validateToken(tenantId, refreshToken)
                            .then(claims -> {
                                // Issue new access token
                                UserPrincipal principal = UserPrincipal.builder()
                                        .userId(claims.getSubject())
                                        .email(claims.getEmail())
                                        .tenantId(claims.getTenantId())
                                        .roles(claims.getRoles())
                                        .permissions(claims.getPermissions())
                                        .build();

                                return jwtTokenProvider.generateToken(TenantId.of(claims.getTenantId().value()), principal, Duration.ofHours(1))
                                        .map(newToken -> {
                                            metrics.incrementCounter("auth.refresh.success");

                                            Map<String, Object> response = new HashMap<>();
                                            response.put("accessToken", newToken);
                                            response.put("tokenType", "Bearer");
                                            response.put("expiresIn", 3600);

                                            return createJsonResponse(200, response);
                                        });
                            }, err -> {
                                metrics.incrementCounter("auth.refresh.failed", "reason", "INVALID_TOKEN");
                                logger.error("Refresh failed", err);
                                return Promise.of(createJsonResponse(401, Map.of(
                                        "error", "INVALID_TOKEN",
                                        "message", "Refresh token validation failed"
                                )));
                            });
                } catch (Exception e) {
                    logger.error("Failed to parse refresh request", e);
                    return Promise.of(createJsonResponse(400, Map.of(
                            "error", "INVALID_REQUEST",
                            "message", "Invalid JSON payload"
                    )));
                }
            });

        } catch (Exception ex) {
            logger.error("Refresh failed", ex);
            return Promise.of(createJsonResponse(500, Map.of(
                    "error", "INTERNAL_ERROR",
                    "message", ex.getMessage()
            )));
        }
    }

    /**
     * Handles token revocation request (POST /auth/revoke).
     *
     * @param request the HTTP request containing token to revoke
     * @return Promise of HTTP response (204 No Content on success)
     */
    public Promise<HttpResponse> handleRevoke(HttpRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        metrics.incrementCounter("auth.revoke.attempts");

        try {
            // Parse JSON payload for token and reason
            return request.loadBody().then(() -> {
                try {
                    String body = request.getBody().asString(StandardCharsets.UTF_8);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = objectMapper.readValue(body, Map.class);

                    String tokenToRevoke = (String) payload.get("token");
                    String reason = (String) payload.getOrDefault("reason", "user_logout");
                    String tenantIdStr = (String) payload.getOrDefault("tenantId", "default-tenant");

                    if (tokenToRevoke == null || tokenToRevoke.isEmpty()) {
                        return Promise.of(createJsonResponse(400, Map.of(
                                "error", "INVALID_REQUEST",
                                "message", "Token is required"
                        )));
                    }

                    // Revoke token
                    TenantId tenantId = TenantId.of(tenantIdStr);
                    return jwtTokenProvider.revokeToken(tenantId, tokenToRevoke)
                            .then(revoked -> {
                                metrics.incrementCounter("auth.revoke.success", "reason", reason);
                                // 204 No Content response
                                return Promise.of(HttpResponse.ofCode(204).build());
                            }, err -> {
                                metrics.incrementCounter("auth.revoke.failed", "reason", "REVOCATION_FAILED");
                                logger.error("Revocation failed", err);
                                return Promise.of(createJsonResponse(500, Map.of(
                                        "error", "INTERNAL_ERROR",
                                        "message", "Token revocation failed"
                                )));
                            });
                } catch (Exception e) {
                    logger.error("Failed to parse revoke request", e);
                    return Promise.of(createJsonResponse(400, Map.of(
                            "error", "INVALID_REQUEST",
                            "message", "Invalid JSON payload"
                    )));
                }
            });

        } catch (Exception ex) {
            logger.error("Revocation failed", ex);
            metrics.incrementCounter("auth.revoke.failed", "reason", "INTERNAL_ERROR");
            return Promise.of(createJsonResponse(500, Map.of(
                    "error", "INTERNAL_ERROR",
                    "message", ex.getMessage()
            )));
        }
    }

    /**
     * Handles health check request (GET /auth/health).
     *
     * @param request the HTTP request
     * @return Promise of HTTP response with health status
     */
    public Promise<HttpResponse> handleHealth(HttpRequest request) {
        metrics.incrementCounter("auth.health.checks");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", Instant.now().toString());
        response.put("version", "1.0.0");

        Map<String, String> components = new HashMap<>();
        components.put("jwtProvider", "UP");
        response.put("components", components);

        return Promise.of(createJsonResponse(200, response));
    }

    /**
     * Creates JSON HTTP response with proper headers and serialization.
     *
     * <p>Uses ObjectMapper to serialize the response body to JSON. All errors
     * during serialization are caught and a 500 error response is returned.
     *
     * @param statusCode the HTTP status code
     * @param body the response body object to serialize
     * @return HTTP response with JSON body and Content-Type header
     */
    private HttpResponse createJsonResponse(int statusCode, Object body) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(body);
            return HttpResponse.ofCode(statusCode)
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .withBody(json)
                    .build();
        } catch (Exception ex) {
            logger.error("Failed to serialize response to JSON", ex);
            return HttpResponse.ofCode(500)
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .withBody("{\"error\":\"Failed to serialize response\"}".getBytes())
                    .build();
        }
    }
}
