/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Framework Module
 */
package com.ghatana.yappc.framework.core.plugin.sandbox;

/**
 * Thrown when a plugin invocation exceeds its wall-clock time budget.
 *
 * @doc.type class
 * @doc.purpose Signals plugin wall-time budget exhaustion
 * @doc.layer product
 * @doc.pattern Exception
 */
public class PluginTimeoutException extends RuntimeException {

    /**
     * @param pluginId  plugin that timed out
     * @param maxWallMs configured wall-clock budget in milliseconds
     */
    public PluginTimeoutException(String pluginId, long maxWallMs) {
        super("Plugin '" + pluginId + "' exceeded its wall-time budget of " + maxWallMs + " ms.");
    }
}
