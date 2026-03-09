package com.ghatana.requirements.api.security;

import com.ghatana.platform.security.jwt.JwtTokenProvider;
import com.ghatana.platform.security.model.User;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * JWT service that adapts platform JwtTokenProvider for the authentication filter.
 *
 * <p>Provides request-level JWT validation: extracts Bearer tokens from
 * Authorization headers, validates them, and returns User.
 *
 * @doc.type class
 * @doc.purpose JWT authentication adapter for HTTP requests
 * @doc.layer product
 * @doc.pattern Adapter
 * @since 1.0.0
 */
public final class JwtService {
    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final Set<String> publicPaths;

    /**
     * Create JWT service wrapping the platform token provider.
     *
     * @param tokenProvider platform JWT token provider
     * @param publicPaths   set of path prefixes that do not require authentication
     */
    public JwtService(JwtTokenProvider tokenProvider, Set<String> publicPaths) {
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider is required");
        this.publicPaths = Objects.requireNonNull(publicPaths, "publicPaths is required");
    }

    /**
     * Create JWT service with default public paths.
     *
     * @param tokenProvider platform JWT token provider
     */
    public JwtService(JwtTokenProvider tokenProvider) {
        this(tokenProvider, Set.of("/health", "/ready", "/metrics"));
    }

    /**
     * Check whether the request path requires authentication.
     *
     * @param request the HTTP request
     * @return true if authentication is required
     */
    public boolean requiresAuthentication(HttpRequest request) {
        String path = request.getPath();
        for (String publicPath : publicPaths) {
            if (path.equals(publicPath) || path.startsWith(publicPath + "/")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Extract and validate the JWT from the request, returning the authenticated user.
     *
     * @param request the HTTP request
     * @return authenticated User
     * @throws JwtValidationException if the token is missing or invalid
     */
    public User extractPrincipal(HttpRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new JwtValidationException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new JwtValidationException("Empty Bearer token");
        }

        if (!tokenProvider.validateToken(token)) {
            logger.debug("JWT validation failed");
            throw new JwtValidationException("Invalid or expired token");
        }

        String userId = tokenProvider.getUserIdFromToken(token)
            .orElseThrow(() -> new JwtValidationException("Token has no subject"));
        List<String> roles = tokenProvider.getRolesFromToken(token);

        return User.builder()
            .userId(userId)
            .username(userId)
            .addRoles(roles)
            .authenticated(true)
            .authToken(token)
            .build();
    }

    /**
     * Exception thrown when JWT validation fails.
     */
    public static class JwtValidationException extends RuntimeException {
        public JwtValidationException(String message) {
            super(message);
        }

        public JwtValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
