/*
 * Copyright (c) 2026 Ghatana Technologies
 */
package com.ghatana.yappc.services.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.security.port.JwtTokenProvider;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JWT-backed auth endpoints for lifecycle frontend consumers.
 *
 * @doc.type class
 * @doc.purpose Exposes /api/auth/me and /api/auth/validate for bearer-token identity checks
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class JwtAuthController {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JwtTokenProvider tokenProvider;

    public JwtAuthController(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    /** Returns current authenticated user details for the supplied bearer token. */
    public Promise<HttpResponse> currentUser(HttpRequest request) {
        Optional<String> tokenOpt = extractBearerToken(request);
        if (tokenOpt.isEmpty()) {
            return Promise.of(unauthorized("Missing or malformed Authorization header"));
        }

        String token = tokenOpt.get();
        if (!tokenProvider.validateToken(token)) {
            return Promise.of(unauthorized("Invalid or expired token"));
        }

        Optional<String> userIdOpt = tokenProvider.getUserIdFromToken(token);
        Optional<Map<String, Object>> claimsOpt = tokenProvider.extractClaims(token);

        if (userIdOpt.isEmpty() || claimsOpt.isEmpty()) {
            return Promise.of(unauthorized("Unable to resolve token claims"));
        }

        Map<String, Object> claims = claimsOpt.get();
        String userId = userIdOpt.get();
        List<String> roles = tokenProvider.getRolesFromToken(token);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", userId);
        response.put("name", stringClaim(claims, "name").orElse(userId));
        response.put("email", stringClaim(claims, "email").orElse(""));
        response.put("roles", roles);
        response.put("tenantId", stringClaim(claims, "tenantId").orElse("default-tenant"));

        return Promise.of(json(200, response));
    }

    /** Returns token validity information for lightweight auth checks. */
    public Promise<HttpResponse> validate(HttpRequest request) {
        Optional<String> tokenOpt = extractBearerToken(request);
        if (tokenOpt.isEmpty()) {
            return Promise.of(unauthorized("Missing or malformed Authorization header"));
        }

        String token = tokenOpt.get();
        if (!tokenProvider.validateToken(token)) {
            return Promise.of(unauthorized("Invalid or expired token"));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("valid", true);
        payload.put("userId", tokenProvider.getUserIdFromToken(token).orElse("unknown"));
        payload.put("tenantId", tokenProvider.extractClaims(token)
                .flatMap(c -> stringClaim(c, "tenantId"))
                .orElse("default-tenant"));

        return Promise.of(json(200, payload));
    }

    private static Optional<String> extractBearerToken(HttpRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        return Optional.of(authHeader.substring(7).strip());
    }

    private static Optional<String> stringClaim(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        return value instanceof String ? Optional.of((String) value) : Optional.empty();
    }

    private static HttpResponse unauthorized(String message) {
        return error(401, "UNAUTHORIZED", message);
    }

    private static HttpResponse error(int code, String errorCode, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", errorCode);
        payload.put("message", message);
        return json(code, payload);
    }

    private static HttpResponse json(int code, Map<String, Object> payload) {
        try {
            return HttpResponse.ofCode(code)
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .withBody(MAPPER.writeValueAsBytes(payload))
                    .build();
        } catch (Exception e) {
            logger.error("Failed to serialize auth response", e);
            return HttpResponse.ofCode(500)
                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .withJson("{\"error\":\"INTERNAL_SERVER_ERROR\",\"message\":\"Serialization failure\"}")
                    .build();
        }
    }
}
