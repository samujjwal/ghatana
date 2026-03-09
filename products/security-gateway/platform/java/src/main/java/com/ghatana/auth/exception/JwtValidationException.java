package com.ghatana.auth.exception;

/**
 * Thrown when JWT token validation fails.
 *
 * <p><b>Purpose</b><br>
 * Indicates token is invalid, expired, tampered with, or revoked.
 * Includes detailed failure reason for logging and metrics.
 *
 * <p><b>Failure Reasons</b><br>
 * - Invalid signature: Token was signed with different key
 * - Expired: Token's exp claim is in the past
 * - Issuer mismatch: Token's iss claim doesn't match expected issuer
 * - Audience mismatch: Token's aud claim doesn't include expected audience
 * - Tenant mismatch: Token's tenantId claim doesn't match request tenant
 * - Revoked: Token was explicitly revoked
 * - Parse error: Token couldn't be parsed (malformed)
 *
 * <p><b>Recovery</b><br>
 * Client should re-authenticate to obtain new token. For refresh tokens,
 * use refresh endpoint if available.
 *
 * <p><b>Thread Safety</b><br>
 * Immutable exception class, thread-safe.
 *
 * @doc.type exception
 * @doc.purpose JWT token validation failure
 * @doc.layer product
 * @doc.pattern Exception
 */
public class JwtValidationException extends RuntimeException {

    /**
     * Creates JWT validation exception with message.
     *
     * @param message the error message describing validation failure
     */
    public JwtValidationException(String message) {
        super(message);
    }

    /**
     * Creates JWT validation exception with message and cause.
     *
     * @param message the error message describing validation failure
     * @param cause the underlying exception that caused validation to fail
     */
    public JwtValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
