package com.ghatana.platform.security.oauth2.exception;

/**
 * Exception thrown when token introspection fails.
 
 *
 * @doc.type class
 * @doc.purpose Token introspection exception
 * @doc.layer core
 * @doc.pattern Exception
*/
public class TokenIntrospectionException extends RuntimeException {
    
    /**
     * Constructs a new token introspection exception with the specified detail message.
     *
     * @param message the detail message
     */
    public TokenIntrospectionException(String message) {
        super(message);
    }

    /**
     * Constructs a new token introspection exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public TokenIntrospectionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new token introspection exception with the specified cause.
     *
     * @param cause the cause
     */
    public TokenIntrospectionException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new token introspection exception with the specified detail message,
     * cause, suppression enabled or disabled, and writable stack trace enabled or disabled.
     *
     * @param message the detail message
     * @param cause the cause
     * @param enableSuppression whether or not suppression is enabled or disabled
     * @param writableStackTrace whether or not the stack trace should be writable
     */
    protected TokenIntrospectionException(String message, Throwable cause,
                                       boolean enableSuppression,
                                       boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
