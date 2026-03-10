/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Framework Module
 */
package com.ghatana.yappc.framework.core.plugin.sandbox;

/**
 * Thrown when a plugin cannot be loaded or instantiated by {@link IsolatingPluginSandbox}.
 *
 * @doc.type class
 * @doc.purpose Signals plugin loading failure
 * @doc.layer product
 * @doc.pattern Exception
 */
public class PluginLoadException extends Exception {

    /**
     * @param message detail message
     */
    public PluginLoadException(String message) {
        super(message);
    }

    /**
     * @param message detail message
     * @param cause   root cause
     */
    public PluginLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
