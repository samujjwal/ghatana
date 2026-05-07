package com.ghatana.pattern.api.exception;

/**
 * Raised when serialization to/from EventCloud representations fails.
  * @doc.type class
 * @doc.purpose Provides event cloud serialization exception functionality.
 * @doc.layer product
 * @doc.pattern Event
*/
public class EventCloudSerializationException extends Exception {
    public EventCloudSerializationException(String message) {
        super(message);
    }

    public EventCloudSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
