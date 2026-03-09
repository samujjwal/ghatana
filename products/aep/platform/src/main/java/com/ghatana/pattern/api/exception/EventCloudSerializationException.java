package com.ghatana.pattern.api.exception;

/**
 * Raised when serialization to/from EventCloud representations fails.
 */
public class EventCloudSerializationException extends Exception {
    public EventCloudSerializationException(String message) {
        super(message);
    }

    public EventCloudSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
