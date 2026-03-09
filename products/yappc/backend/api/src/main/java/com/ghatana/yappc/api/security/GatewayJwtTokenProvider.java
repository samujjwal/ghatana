/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API - Auth Gateway JWT Token Provider
 *
 * Delegates JWT validation to the shared auth-gateway service instead of
 * performing local JJWT validation. This ensures the YAPPC backend uses
 * centralized authentication.
 */

package com.ghatana.yappc.api.security;

import io.activej.http.HttpClient;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Auth-gateway-backed JWT token provider.
 *
 * <p>Calls the shared {@code auth-gateway} service's {@code /auth/validate} and
 * {@code /auth/login} endpoints over HTTP instead of performing local JJWT
 * parsing. This is the recommended production configuration for centralized
 * authentication.
 *
 * <p>For development and testing, the local {@link JwtTokenProvider} can still be
 * used. Switch implementations via configuration.
 *
 * <p>All remote calls use ActiveJ {@link HttpClient} and return non-blocking
 * {@link Promise} results (bridged to {@link Promise} where the
 * existing API requires it).
 *
 * @doc.type class
 * @doc.purpose Auth-gateway-backed JWT validation and user extraction
 * @doc.layer product
 * @doc.pattern Adapter, Anti-Corruption Layer
 */
public class GatewayJwtTokenProvider {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayJwtTokenProvider.class);

    private final HttpClient httpClient;
    private final String gatewayBaseUrl;

    /**
     * Creates a gateway JWT token provider.
     *
     * @param httpClient     ActiveJ HTTP client (must share the same Eventloop)
     * @param gatewayBaseUrl base URL of the auth-gateway, e.g. {@code http://auth-gateway:8081}
     */
    public GatewayJwtTokenProvider(@NotNull HttpClient httpClient,
                                    @NotNull String gatewayBaseUrl) {
        this.httpClient = httpClient;
        this.gatewayBaseUrl = gatewayBaseUrl.endsWith("/")
                ? gatewayBaseUrl.substring(0, gatewayBaseUrl.length() - 1)
                : gatewayBaseUrl;
    }

    /**
     * Validates a JWT token by calling the auth-gateway's {@code /auth/validate} endpoint.
     *
     * @param token the raw JWT token
     * @return a {@code Promise<Boolean>} that completes with {@code true} if valid
     */
    public CompletableFuture<Boolean> validateToken(@NotNull String token) {
        return validateTokenPromise(token).toCompletableFuture();
    }

    /**
     * Promise-native validation — preferred for ActiveJ code paths.
     */
    public Promise<Boolean> validateTokenPromise(@NotNull String token) {
        HttpRequest request = HttpRequest.get(gatewayBaseUrl + "/auth/validate")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();

        return httpClient.request(request)
                .map(response -> {
                    int status = response.getCode();
                    if (status == 200) {
                        LOG.debug("Token validated successfully via auth-gateway");
                        return true;
                    }
                    LOG.warn("Token validation failed via auth-gateway: HTTP {}", status);
                    return false;
                })
                .mapException(e -> {
                    LOG.error("Auth-gateway unreachable for token validation", e);
                    return e;
                });
    }

    /**
     * Extracts user context from a valid JWT token via the auth-gateway.
     *
     * <p>Returns a Promise of UserContext for non-blocking usage. Legacy synchronous
     * callers should migrate to this method with proper async chaining.
     *
     * @param token the raw JWT token
     * @return promise of the UserContext, or null if validation fails
     */
    @NotNull
    public Promise<@Nullable UserContext> getUserFromToken(@NotNull String token) {
        return getUserFromTokenPromise(token)
            .whenException(e ->
                LOG.error("Failed to get user from token via auth-gateway", e));
    }

    /**
     * Promise-native user extraction — preferred for ActiveJ code paths.
     */
    public Promise<UserContext> getUserFromTokenPromise(@NotNull String token) {
        HttpRequest request = HttpRequest.get(gatewayBaseUrl + "/auth/validate")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();

        return httpClient.request(request)
                .then(response -> {
                    if (response.getCode() != 200) {
                        return Promise.of(null);
                    }
                    return response.loadBody()
                            .map(body -> {
                                String json = body.getString(StandardCharsets.UTF_8);
                                return parseUserContextFromJson(json);
                            });
                });
    }

    /**
     * Parses the auth-gateway /auth/validate response into a UserContext.
     *
     * <p>Expected response format:
     * <pre>{@code
     * {
     *   "valid": true,
     *   "userId": "user-123",
     *   "email": "user@example.com",
     *   "roles": ["admin", "user"],
     *   "tenantId": "tenant-1"
     * }
     * }</pre>
     */
    @Nullable
    private UserContext parseUserContextFromJson(@NotNull String json) {
        try {
            // Lightweight JSON parsing — avoids Jackson dependency
            String userId = extractJsonStringField(json, "userId");
            String email = extractJsonStringField(json, "email");
            String userName = extractJsonStringField(json, "userName");
            String tenantId = extractJsonStringField(json, "tenantId");
            List<String> roles = extractJsonStringArray(json, "roles");

            if (userId == null) {
                LOG.warn("Auth-gateway response missing userId");
                return null;
            }

            return UserContext.builder()
                    .userId(userId)
                    .email(email)
                    .userName(userName != null ? userName : userId)
                    .tenantId(tenantId != null ? tenantId : "default")
                    .roles(roles)
                    .permissions(List.of()) // Permissions resolved locally
                    .build();

        } catch (Exception e) {
            LOG.error("Failed to parse auth-gateway response", e);
            return null;
        }
    }

    /**
     * Extracts a string field from a JSON object (lightweight, no Jackson).
     */
    @Nullable
    private static String extractJsonStringField(@NotNull String json, @NotNull String field) {
        String pattern = "\"" + field + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx < 0) return null;
        int quoteStart = json.indexOf('"', colonIdx + 1);
        if (quoteStart < 0) return null;
        int quoteEnd = json.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) return null;
        return json.substring(quoteStart + 1, quoteEnd);
    }

    /**
     * Extracts a string array field from a JSON object (lightweight).
     */
    @NotNull
    private static List<String> extractJsonStringArray(@NotNull String json, @NotNull String field) {
        String pattern = "\"" + field + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return List.of();
        int bracketStart = json.indexOf('[', idx + pattern.length());
        if (bracketStart < 0) return List.of();
        int bracketEnd = json.indexOf(']', bracketStart + 1);
        if (bracketEnd < 0) return List.of();

        String inner = json.substring(bracketStart + 1, bracketEnd).trim();
        if (inner.isEmpty()) return List.of();

        List<String> result = new ArrayList<>();
        int pos = 0;
        while (pos < inner.length()) {
            int qs = inner.indexOf('"', pos);
            if (qs < 0) break;
            int qe = inner.indexOf('"', qs + 1);
            if (qe < 0) break;
            result.add(inner.substring(qs + 1, qe));
            pos = qe + 1;
        }
        return List.copyOf(result);
    }
}
