/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.plugin;

/**
 * Exception thrown when plugin dependencies cannot be resolved.
 *
 * @doc.type exception
 * @doc.purpose Plugin dependency exception - circular dependencies, missing dependencies
 * @doc.layer platform
 * @doc.pattern Exception
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public class PluginDependencyException extends RuntimeException {

    /**
     * Creates a new plugin dependency exception.
     *
     * @param message the error message
     */
    public PluginDependencyException(String message) {
        super(message);
    }

    /**
     * Creates a new plugin dependency exception with cause.
     *
     * @param message the error message
     * @param cause the cause of the exception
     */
    public PluginDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
