package com.ghatana.auth.exception;

/**
 * Base exception for authentication failures.
 *
 * <p><b>Purpose</b><br>
 * Thrown when authentication operations fail due to business logic or security
 * violations (invalid credentials, expired tokens, etc.). This is the parent
 * exception for all authentication-related errors in the platform.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Throw directly
 * throw new AuthenticationException("Invalid credentials");
 * 
 * // Throw with cause
 * try {
 *     verifyToken(token);
 * } catch (JwtException e) {
 *     throw new AuthenticationException("Token verification failed", e);
 * }
 * 
 * // Catch in HTTP adapter
 * promise.whenException(AuthenticationException.class, ex -> {
 *     return ResponseBuilder.unauthorized()
 *         .json(Map.of("error", ex.getMessage()))
 *         .build();
 * });
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Domain exception for authentication layer
 * - Used by AuthenticationService implementations
 * - Caught by HTTP adapters and converted to 401 responses
 * - Logged with security context for audit
 *
 * <p><b>Recovery Strategy</b><br>
 * - Log the failure with tenant/user context for security audit
 * - Return 401 Unauthorized with sanitized error message
 * - DO NOT expose internal system details in message
 * - Consider rate limiting after repeated failures
 *
 * @see com.ghatana.auth.service.AuthenticationService
 * @see InvalidCredentialsException
 * @see ExpiredTokenException
 * @see InvalidTokenException
 * @doc.type exception
 * @doc.purpose Authentication failure error
 * @doc.layer platform
 * @doc.pattern Exception
 */
public class AuthenticationException extends RuntimeException {
    
    /**
     * Create exception with message.
     *
     * @param message Error message
     */
    public AuthenticationException(String message) {
        super(message);
    }
    
    /**
     * Create exception with message and cause.
     *
     * @param message Error message
     * @param cause Root cause
     */
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
