/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.plugin;

/**
 * Exception thrown when plugin resource quotas are invalid or exceeded.
 *
 * @doc.type exception
 * @doc.purpose Plugin resource exception - quota validation, resource limit exceeded
 * @doc.layer platform
 * @doc.pattern Exception
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public class PluginResourceException extends RuntimeException {

    /**
     * Creates a new plugin resource exception.
     *
     * @param message the error message
     */
    public PluginResourceException(String message) {
        super(message);
    }

    /**
     * Creates a new plugin resource exception with cause.
     *
     * @param message the error message
     * @param cause the cause of the exception
     */
    public PluginResourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
