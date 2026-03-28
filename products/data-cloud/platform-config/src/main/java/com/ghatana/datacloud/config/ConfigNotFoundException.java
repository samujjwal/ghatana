package com.ghatana.datacloud.config;

import com.ghatana.platform.core.exception.ResourceNotFoundException;

/**
 * Exception thrown when a configuration cannot be found.
 *
 * @doc.type class
 * @doc.purpose Exception for missing configuration
 * @doc.layer core
 * @doc.pattern Exception
 */
public class ConfigNotFoundException extends ResourceNotFoundException {

    public ConfigNotFoundException(String message) {
        super(message);
    }

    public ConfigNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
