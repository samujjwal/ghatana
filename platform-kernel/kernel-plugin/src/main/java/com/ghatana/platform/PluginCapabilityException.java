/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.plugin;

/**
 * Exception thrown when plugin capabilities are invalid or not approved.
 *
 * @doc.type exception
 * @doc.purpose Plugin capability exception - invalid capabilities, approval required
 * @doc.layer platform
 * @doc.pattern Exception
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public class PluginCapabilityException extends RuntimeException {

    /**
     * Creates a new plugin capability exception.
     *
     * @param message the error message
     */
    public PluginCapabilityException(String message) {
        super(message);
    }

    /**
     * Creates a new plugin capability exception with cause.
     *
     * @param message the error message
     * @param cause the cause of the exception
     */
    public PluginCapabilityException(String message, Throwable cause) {
        super(message, cause);
    }
}
