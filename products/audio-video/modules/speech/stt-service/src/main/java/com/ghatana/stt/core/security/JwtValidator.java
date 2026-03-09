package com.ghatana.stt.core.security;

/**
 * Interface for JWT token validation.
 *
 * @doc.type interface
 * @doc.purpose JWT validation strategy
 * @doc.layer security
 * @doc.pattern Strategy
 */
public interface JwtValidator {
    /**
     * Validates a JWT token.
     *
     * @param token the token to validate
     * @return JWT claims if valid
     * @throws JwtValidationException if validation fails
     */
    JwtClaims validate(String token) throws JwtValidationException;
}
