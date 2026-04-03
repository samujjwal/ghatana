package com.ghatana.platform.core.error;

/**
 * API exception wrapper for HTTP errors.
 *
 * @doc.type class
 * @doc.purpose Wraps HTTP error status codes as runtime exceptions for propagation across layers.
 * @doc.layer platform
 * @doc.pattern Exception
 */
public class ApiException extends RuntimeException {
    private final int statusCode;

    public ApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
