package com.ghatana.datacloud.event.spi.secrets;

import com.ghatana.platform.core.exception.ConfigurationException;

/**
 * Thrown when a secret reference cannot be resolved during event processing.
 *
 * @doc.type class
 * @doc.purpose Exception for secret resolution failures
 * @doc.layer core
 * @doc.pattern Exception
 */
public class SecretResolutionException extends ConfigurationException {
    public SecretResolutionException(String message) {
        super(message);
    }

    public SecretResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
