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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

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
 */
public final class AepAuthFilter implements AsyncServlet {

    private static final Logger log = LoggerFactory.getLogger(AepAuthFilter.class);

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
        this.next = next;
        this.jwtSecret = System.getenv("AEP_JWT_SECRET");
        boolean devModeExplicit = "true".equalsIgnoreCase(System.getenv("AEP_AUTH_DISABLED"));
        this.authEnabled = !devModeExplicit;
        
        if (!authEnabled) {
            log.warn("JWT authentication DISABLED via AEP_AUTH_DISABLED=true — do NOT use in production");
        } else if (jwtSecret == null || jwtSecret.isBlank()) {
            log.error("AEP_JWT_SECRET is not set — all authenticated requests will be rejected. "
                + "Set AEP_JWT_SECRET or AEP_AUTH_DISABLED=true for development.");
        } else {
            log.info("JWT authentication enabled");
        }
    }

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) throws Exception {
        // Skip auth for public endpoints and preflight
        String path = request.getPath();
        if (request.getMethod() == HttpMethod.OPTIONS || isPublicPath(path)) {
            return next.serve(request);
        }

        // If auth explicitly disabled (AEP_AUTH_DISABLED=true), allow through (dev mode only)
        if (!authEnabled) {
            log.debug("Request to {} allowed without auth (dev mode)", path);
            return next.serve(request);
        }

        // Fail closed: if auth is enabled but JWT secret is not configured, reject
        if (jwtSecret == null || jwtSecret.isBlank()) {
            log.error("Rejecting request to {} — AEP_JWT_SECRET not configured", path);
            return Promise.of(unauthorizedResponse("Server authentication not configured"));
        }

        // Validate Authorization header
        String authHeader = request.getHeader(HttpHeaders.of("Authorization"));
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return Promise.of(unauthorizedResponse("Missing or invalid Authorization header"));
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            return Promise.of(unauthorizedResponse("Empty bearer token"));
        }

        // Validate JWT
        try {
            JwtPayload payload = validateJwt(token);
            // Note: User context from JWT is available via payload
            // In a production system, this would be attached to a request context
            // For now, we proceed with the validated request
            return next.serve(request);
        } catch (JwtValidationException e) {
            log.warn("JWT validation failed for path {}: {}", path, e.getMessage());
            return Promise.of(unauthorizedResponse("Authentication failed"));
        }
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
            claims = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(payloadJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
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
            claims.get("iat") instanceof Number ? ((Number) claims.get("iat")).longValue() : 0
        );
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

    private HttpResponse unauthorizedResponse(String message) {
        String body = String.format(
            "{\"error\":\"Unauthorized\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
            message.replace("\"", "\\\""), Instant.now()
        );
        return HttpResponse.ofCode(401)
            .withHeader(HttpHeaders.CONTENT_TYPE,
                HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
            .withHeader(HttpHeaders.of("WWW-Authenticate"), HttpHeaderValue.of("Bearer"))
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();
    }

    public record JwtPayload(String sub, String iss, long exp, long iat) {}
    
    public static class JwtValidationException extends Exception {
        public JwtValidationException(String message) {
            super(message);
        }
    }
}
