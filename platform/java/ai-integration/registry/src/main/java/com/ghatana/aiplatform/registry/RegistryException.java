package com.ghatana.aiplatform.registry;

/**
 * Exception thrown when model registry operations fail.
 *
 * @doc.type exception
 * @doc.purpose Model registry operation failure
 * @doc.layer platform
 * @doc.pattern Exception
 */
public class RegistryException extends RuntimeException {

    public RegistryException(String message) {
        super(message);
    }

    public RegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
