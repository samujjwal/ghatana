/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.security.auth;

import com.ghatana.platform.security.port.JwtTokenProvider;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Utility class for common HTTP-layer authentication operations.
 *
 * <p>Centralises the repeated pattern of extracting a Bearer token from an HTTP
 * request and validating it with a {@link JwtTokenProvider}. Services should use
 * these helpers at their request entry-points instead of duplicating Bearer-token
 * extraction logic.</p>
 *
 * <p>For credential-based authentication use {@link AuthenticationProvider} and its
 * implementations. This utility is scoped to token-bearer HTTP request flows only.</p>
 *
 * @doc.type class
 * @doc.purpose Static HTTP authentication helpers for Bearer-token validation at service boundaries
 * @doc.layer platform
 * @doc.pattern Utility
 */
public final class AuthenticationUtils {

    private static final String BEARER_PREFIX = "Bearer ";

    private AuthenticationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Extracts the Bearer token from the {@code Authorization} header of an HTTP request.
     *
     * @param request the incoming HTTP request
     * @return the raw token string, or {@code null} if the header is absent or not a Bearer token
     */
    @Nullable
    public static String extractBearerToken(@NotNull HttpRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length());
    }

    /**
     * Validates a raw JWT token string using the provided {@link JwtTokenProvider}.
     *
     * @param token    the raw JWT token string (may be {@code null})
     * @param provider the token provider to use for validation
     * @return {@code true} if the token is non-null and passes validation; {@code false} otherwise
     */
    public static boolean validateToken(@Nullable String token, @NotNull JwtTokenProvider provider) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            return provider.validateToken(token);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extracts and validates the Bearer token from an HTTP request in a single call.
     *
     * @param request  the incoming HTTP request
     * @param provider the token provider to use for validation
     * @return {@code true} if a valid Bearer token is present in the request
     */
    public static boolean isAuthenticated(@NotNull HttpRequest request, @NotNull JwtTokenProvider provider) {
        String token = extractBearerToken(request);
        return validateToken(token, provider);
    }

    /**
     * Returns the user ID extracted from the Bearer token in the request, if valid.
     *
     * @param request  the incoming HTTP request
     * @param provider the token provider to use for parsing
     * @return an {@link Optional} containing the user ID, or empty if the token is absent or invalid
     */
    @NotNull
    public static Optional<String> extractUserId(@NotNull HttpRequest request, @NotNull JwtTokenProvider provider) {
        String token = extractBearerToken(request);
        if (token == null || !validateToken(token, provider)) {
            return Optional.empty();
        }
        return provider.getUserIdFromToken(token);
    }
}
