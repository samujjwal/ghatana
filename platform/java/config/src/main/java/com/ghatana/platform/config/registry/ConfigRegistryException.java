/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.config.registry;

/**
 * Exception thrown when a configuration registry operation fails.
 *
 * @doc.type class
 * @doc.purpose Exception for config registry failures
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public class ConfigRegistryException extends RuntimeException {

    /**
     * Creates a new config registry exception.
     *
     * @param message the error message
     */
    public ConfigRegistryException(String message) {
        super(message);
    }

    /**
     * Creates a new config registry exception with cause.
     *
     * @param message the error message
     * @param cause   the cause
     */
    public ConfigRegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
