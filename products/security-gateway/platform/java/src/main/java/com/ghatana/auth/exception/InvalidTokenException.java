package com.ghatana.auth.exception;

/**
 * Exception thrown when a token is invalid or malformed.
 *
 * <p><b>Purpose</b><br>
 * Indicates that a token cannot be validated due to:
 * - Invalid signature
 * - Malformed structure
 * - Tampered payload
 * - Wrong issuer/audience
 * - Revoked or blacklisted
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * try {
 *     JwtClaims claims = jwtConsumer.processToClaims(token);
 * } catch (InvalidJwtException e) {
 *     throw new InvalidTokenException("Token validation failed", e);
 * }
 * 
 * if (isTokenRevoked(token)) {
 *     throw new InvalidTokenException("Token has been revoked");
 * }
 * }</pre>
 *
 * <p><b>Security Note</b><br>
 * Log detailed validation failures internally but return generic error
 * to client to prevent information disclosure about token structure.
 *
 * <p><b>Recovery Strategy</b><br>
 * - Client must obtain fresh token (cannot fix invalid token)
 * - Use refresh token flow if available
 * - Otherwise redirect to login
 * - Rate limit clients sending invalid tokens (possible attack)
 *
 * @see AuthenticationException
 * @see ExpiredTokenException
 * @see com.ghatana.auth.service.AuthenticationService#validateSession
 * @doc.type exception
 * @doc.purpose Invalid token error
 * @doc.layer platform
 * @doc.pattern Exception
 */
public class InvalidTokenException extends AuthenticationException {
    
    /**
     * Create exception with message.
     *
     * @param message Error message
     */
    public InvalidTokenException(String message) {
        super(message);
    }
    
    /**
     * Create exception with message and cause.
     *
     * @param message Error message
     * @param cause Root cause (e.g., InvalidJwtException)
     */
    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
