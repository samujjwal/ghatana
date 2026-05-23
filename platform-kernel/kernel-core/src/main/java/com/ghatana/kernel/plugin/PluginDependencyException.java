package com.ghatana.kernel.plugin;

/**
 * Exception thrown when kernel plugin dependencies cannot be resolved.
 *
 * @doc.type exception
 * @doc.purpose Signals circular or invalid kernel plugin dependencies
 * @doc.layer core
 * @doc.pattern Exception
 */
public final class PluginDependencyException extends RuntimeException {

    public PluginDependencyException(String message) {
        super(message);
    }

    public PluginDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
