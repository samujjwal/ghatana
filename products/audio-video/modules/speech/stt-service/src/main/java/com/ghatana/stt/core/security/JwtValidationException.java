package com.ghatana.stt.core.security;

/**
 * Exception thrown when JWT validation fails.
 *
 * @doc.type class
 * @doc.purpose JWT validation error
 * @doc.layer security
 */
public class JwtValidationException extends Exception {
    /**
     * Constructs a new JwtValidationException with the specified detail message.
     *
     * @param message the detail message
     */
    public JwtValidationException(String message) {
        super(message);
    }

    /**
     * Constructs a new JwtValidationException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public JwtValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
