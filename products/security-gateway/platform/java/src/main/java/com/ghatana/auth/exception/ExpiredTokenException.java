package com.ghatana.auth.exception;

/**
 * Exception thrown when a token has expired.
 *
 * <p><b>Purpose</b><br>
 * Indicates that an access token, refresh token, or password reset token
 * has passed its expiration time and can no longer be used.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * if (token.isExpired()) {
 *     throw new ExpiredTokenException("Access token expired");
 * }
 * 
 * // In HTTP adapter
 * promise.whenException(ExpiredTokenException.class, ex -> {
 *     return ResponseBuilder.unauthorized()
 *         .header("WWW-Authenticate", "Bearer error=\"invalid_token\", error_description=\"Token expired\"")
 *         .json(Map.of("error", "token_expired", "message", ex.getMessage()))
 *         .build();
 * });
 * }</pre>
 *
 * <p><b>Recovery Strategy</b><br>
 * - Client should obtain new access token using refresh token
 * - If refresh token also expired, redirect to login
 * - Password reset tokens cannot be refreshed - user must request new one
 *
 * @see AuthenticationException
 * @see com.ghatana.auth.service.AuthenticationService#refreshAccessToken
 * @doc.type exception
 * @doc.purpose Expired token error
 * @doc.layer platform
 * @doc.pattern Exception
 */
public class ExpiredTokenException extends AuthenticationException {
    
    /**
     * Create exception with message.
     *
     * @param message Error message
     */
    public ExpiredTokenException(String message) {
        super(message);
    }
}
