/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.security.port;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Canonical port interface for JWT token operations.
 *
 * <p>This is the canonical replacement for the deprecated JWT interfaces:
 * <ul>
 *   <li>{@code com.ghatana.platform.auth.JwtTokenProvider} (concrete, JJWT)</li>
 *   <li>{@code com.ghatana.platform.auth.port.JwtTokenProvider} (async port)</li>
 * </ul>
 *
 * <p>The canonical implementation is
 * {@link com.ghatana.platform.security.jwt.JwtTokenProvider}.
 *
 * <p>Operations are synchronous as JWT signing/validation is CPU-bound,
 * not IO-bound, and should not block the ActiveJ event loop for
 * typical-sized tokens.
 *
 * @doc.type interface
 * @doc.purpose JWT token creation, validation and parsing port
 * @doc.layer platform
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface JwtTokenProvider {

    /**
     * Create a JWT token for the given user with roles.
     *
     * @param userId the user identifier to embed as subject
     * @param roles  the roles to embed as claims
     * @param additionalClaims optional additional claims to include
     * @return the signed JWT string
     */
    String createToken(String userId, List<String> roles, Map<String, Object> additionalClaims);

    /**
     * Validate a JWT token.
     *
     * @param token the raw JWT token string
     * @return true if the token is valid and not expired
     */
    boolean validateToken(String token);

    /**
     * Extract the user ID (subject) from a token.
     *
     * @param token the raw JWT token string
     * @return the user ID, or empty if extraction fails
     */
    Optional<String> getUserIdFromToken(String token);

    /**
     * Extract the roles from a token.
     *
     * @param token the raw JWT token string
     * @return the list of role names, or empty list if extraction fails
     */
    List<String> getRolesFromToken(String token);

    /**
     * Extract all claims from a token.
     *
     * @param token the raw JWT token string
     * @return the claims map, or empty if extraction fails
     */
    Optional<Map<String, Object>> extractClaims(String token);
}
