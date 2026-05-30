/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.security;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpMethod;
import io.activej.http.MediaTypes;
import io.activej.http.ContentType;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import io.activej.bytebuf.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * JWT Authentication filter for AEP HTTP server.
 *
 * <p>Enforces authentication on all non-public endpoints by validating
 * Bearer tokens in the Authorization header. Public endpoints (health, ready,
 * metrics) bypass authentication.
 *
 * <p>The filter expects a JWT secret to be configured via the {@code AEP_JWT_SECRET}
 * environment variable. If not set, authentication is disabled (development mode).
 *
 * @doc.type class
 * @doc.purpose Enforce JWT authentication on launcher endpoints
 * @doc.layer product
  * @doc.pattern Filter
*/
public final class AepAuthFilter implements AsyncServlet {

    private static final Logger log = LoggerFactory.getLogger(AepAuthFilter.class);
    private static final HttpHeader CORRELATION_ID_HEADER = HttpHeaders.of("X-Correlation-ID");
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";
    public static final String JWT_PAYLOAD_ATTACHMENT = "aep.jwt.payload";

    // Reusable ObjectMapper for JWT payload parsing (thread-safe)
    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    // Public endpoints that bypass authentication
    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/health",
        "/ready",
        "/live",
        "/info",
        "/metrics"
    );

    private final AsyncServlet next;
    private final String jwtSecret;
    private final boolean authEnabled;

    public AepAuthFilter(AsyncServlet next) {
        this(next,
            resolveSetting("AEP_JWT_SECRET"),
            !"true".equalsIgnoreCase(resolveSetting("AEP_AUTH_DISABLED")),
            resolveEnvironment());
    }

    AepAuthFilter(AsyncServlet next, String jwtSecret, boolean authEnabled) {
        this(next, jwtSecret, authEnabled, resolveEnvironment());
    }

    AepAuthFilter(AsyncServlet next, String jwtSecret, boolean authEnabled, String environment) {
        this.next = next;
        this.jwtSecret = jwtSecret;
        this.authEnabled = authEnabled;

        boolean isProduction = !"development".equalsIgnoreCase(environment)
            && !"test".equalsIgnoreCase(environment);

        if (!authEnabled && isProduction) {
            throw new IllegalStateException(
                "AEP_AUTH_DISABLED=true is not permitted when AEP_ENV='" + environment
                + "'. Authentication must be enabled in non-development environments. "
                + "Set AEP_ENV=development to allow disabling auth locally.");
        }

        if (isProduction && (jwtSecret == null || jwtSecret.isBlank())) {
            throw new IllegalStateException(
                "AEP_JWT_SECRET must be set in non-development environments (AEP_ENV='"
                + environment + "'). "
                + "Set AEP_JWT_SECRET to a secure random secret (>= 32 bytes).");
        }

        if (!authEnabled) {
            log.warn("JWT authentication DISABLED via AEP_AUTH_DISABLED=true — development mode only");
        } else if (jwtSecret == null || jwtSecret.isBlank()) {
            log.error("AEP_JWT_SECRET is not set — all authenticated requests will be rejected. "
                + "Set AEP_JWT_SECRET or AEP_AUTH_DISABLED=true for development.");
        } else {
            log.info("JWT authentication enabled (env={})", environment);
        }
    }

    /**
     * Resolves the runtime environment from the {@code AEP_ENV} env-var.
     * Defaults to {@code "production"} when unset to fail-safe.
     */
    private static String resolveEnvironment() {
        String env = resolveSetting("AEP_ENV");
        return (env == null || env.isBlank()) ? "production" : env.trim().toLowerCase();
    }

    private static String resolveSetting(String key) {
        String propertyValue = System.getProperty(key);
        if (propertyValue != null) {
            return propertyValue;
        }
        return System.getenv(key);
    }

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) throws Exception {
        String correlationId = resolveCorrelationId(request);

        // Skip auth for public endpoints and preflight
        String path = request.getPath();
        if (request.getMethod() == HttpMethod.OPTIONS || isPublicPath(path)) {
            return serveWithCorrelation(request, correlationId);
        }

        // If auth explicitly disabled (AEP_AUTH_DISABLED=true), allow through (dev mode only)
        if (!authEnabled) {
            log.debug("Request to {} allowed without auth (dev mode)", path);
            return serveWithCorrelation(request, correlationId);
        }

        // Fail closed: if auth is enabled but JWT secret is not configured, reject
        if (jwtSecret == null || jwtSecret.isBlank()) {
            log.error("Rejecting request to {} — AEP_JWT_SECRET not configured", path);
            return Promise.of(unauthorizedResponse("Server authentication not configured", correlationId));
        }

        // Validate Authorization header
        String authHeader = request.getHeader(HttpHeaders.of("Authorization"));
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return Promise.of(unauthorizedResponse("Missing or invalid Authorization header", correlationId));
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            return Promise.of(unauthorizedResponse("Empty bearer token", correlationId));
        }

        // Validate JWT
        try {
            JwtPayload payload = validateJwt(token);
            request.attach(JWT_PAYLOAD_ATTACHMENT, payload);
            return serveWithCorrelation(request, correlationId);
        } catch (JwtValidationException e) {
            log.warn("JWT validation failed for path {}: {}", path, e.getMessage());
            return Promise.of(unauthorizedResponse("Authentication failed", correlationId));
        }
    }

    private Promise<HttpResponse> serveWithCorrelation(HttpRequest request, String correlationId) throws Exception {
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        return next.serve(request)
            .map(response -> withCorrelationHeader(response, correlationId))
            .whenComplete(($, e) -> MDC.remove(CORRELATION_ID_MDC_KEY));
    }

    private String resolveCorrelationId(HttpRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return correlationId;
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.contains(path) ||
               path.startsWith("/health/") ||
               path.startsWith("/api/v1/status"); // Additional public status paths
    }

    private JwtPayload validateJwt(String token) throws JwtValidationException {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtValidationException("Invalid JWT structure");
        }

        // Verify signature
        String headerB64 = parts[0];
        String payloadB64 = parts[1];
        String signatureB64 = parts[2];

        String expectedSig = hmacSha256(headerB64 + "." + payloadB64, jwtSecret);
        if (!timingSafeEquals(signatureB64, expectedSig)) {
            throw new JwtValidationException("Invalid signature");
        }

        // Parse payload
        String payloadJson = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
        Map<String, Object> claims;
        try {
            claims = OBJECT_MAPPER.readValue(payloadJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new JwtValidationException("Invalid payload encoding");
        }

        // Check expiration
        Object expObj = claims.get("exp");
        if (expObj instanceof Number) {
            long exp = ((Number) expObj).longValue();
            if (exp < Instant.now().getEpochSecond()) {
                throw new JwtValidationException("Token expired");
            }
        }

        return new JwtPayload(
            (String) claims.get("sub"),
            (String) claims.get("iss"),
            expObj instanceof Number ? ((Number) expObj).longValue() : 0,
            claims.get("iat") instanceof Number ? ((Number) claims.get("iat")).longValue() : 0,
            extractStringClaims(claims, "role", "roles"),
            extractStringClaims(claims, "permission", "permissions", "scope", "scopes"),
            (String) claims.get("tenantId")
        );
    }

    private List<String> extractStringClaims(Map<String, Object> claims, String... keys) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String key : keys) {
            Object claimValue = claims.get(key);
            if (claimValue instanceof String stringValue) {
                for (String part : stringValue.split("[ ,]") ) {
                    String normalized = part.trim();
                    if (!normalized.isEmpty()) {
                        values.add(normalized);
                    }
                }
            } else if (claimValue instanceof Collection<?> collectionValue) {
                for (Object item : collectionValue) {
                    if (item instanceof String stringItem && !stringItem.isBlank()) {
                        values.add(stringItem.trim());
                    }
                }
            }
        }
        return List.copyOf(values);
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC", e);
        }
    }

    private boolean timingSafeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }

    private HttpResponse unauthorizedResponse(String message, String correlationId) {
        String body = String.format(
            "{\"error\":\"Unauthorized\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
            message.replace("\"", "\\\""), Instant.now()
        );
        return HttpResponse.ofCode(401)
            .withHeader(HttpHeaders.CONTENT_TYPE,
                HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
            .withHeader(HttpHeaders.of("WWW-Authenticate"), HttpHeaderValue.of("Bearer"))
            .withHeader(CORRELATION_ID_HEADER, HttpHeaderValue.of(correlationId))
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();
    }

    private HttpResponse withCorrelationHeader(HttpResponse response, String correlationId) {
        ByteBuf body = null;
        try {
            body = response.getBody();
        } catch (IllegalStateException ignored) {
            // Response has no body.
        }

        HttpResponse.Builder builder = HttpResponse.ofCode(response.getCode());
        for (Map.Entry<HttpHeader, HttpHeaderValue> entry : response.getHeaders()) {
            builder.withHeader(entry.getKey(), entry.getValue());
        }
        builder.withHeader(CORRELATION_ID_HEADER, HttpHeaderValue.of(correlationId));
        if (body != null && body.readRemaining() > 0) {
            builder.withBody(body);
        }
        return builder.build();
    }

    public record JwtPayload(
        String sub,
        String iss,
        long exp,
        long iat,
        List<String> roles,
        List<String> permissions,
        String tenantId
    ) {
        public boolean hasRole(String requiredRole) {
            return roles != null && roles.stream().anyMatch(requiredRole::equalsIgnoreCase);
        }

        public boolean hasPermission(String requiredPermission) {
            return permissions != null && permissions.stream().anyMatch(permission ->
                permission.equalsIgnoreCase(requiredPermission)
                    || permission.equalsIgnoreCase("*")
                    || permission.equalsIgnoreCase("deployment:*")
                    || permission.equalsIgnoreCase("deployment:write")
            );
        }

        public boolean canManageDeployments() {
            return hasRole("admin")
                || hasRole("deployer")
                || hasRole("operator")
                || hasPermission("deployment:create")
                || hasPermission("deployment:update")
                || hasPermission("deployment:delete");
        }

        public boolean canManagePipelines() {
            return hasRole("admin")
                || hasRole("operator")
                || hasRole("deployer")
                || hasPermission("pipeline:create")
                || hasPermission("pipeline:update")
                || hasPermission("pipeline:delete")
                || hasPermission("pipelines:write")
                || hasPermission("action:pipelines:write");
        }
    }

    public static class JwtValidationException extends Exception {
        public JwtValidationException(String message) {
            super(message);
        }
    }
}
