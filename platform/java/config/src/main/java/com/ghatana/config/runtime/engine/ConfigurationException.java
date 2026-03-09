/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.config.runtime.engine;

/**
 * Thrown when a configuration operation fails.
 *
 * @doc.type class
 * @doc.purpose Exception for configuration engine errors
 * @doc.layer platform
 * @doc.pattern Exception
 */
public class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
