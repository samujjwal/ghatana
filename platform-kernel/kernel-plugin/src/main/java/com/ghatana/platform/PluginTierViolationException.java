/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.plugin;

/**
 * Exception thrown when a plugin violates tier restrictions.
 *
 * @doc.type exception
 * @doc.purpose Plugin tier violation exception - escalation prevention, capability access denied
 * @doc.layer platform
 * @doc.pattern Exception
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public class PluginTierViolationException extends RuntimeException {

    /**
     * Creates a new plugin tier violation exception.
     *
     * @param message the error message
     */
    public PluginTierViolationException(String message) {
        super(message);
    }

    /**
     * Creates a new plugin tier violation exception with cause.
     *
     * @param message the error message
     * @param cause the cause of the exception
     */
    public PluginTierViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
