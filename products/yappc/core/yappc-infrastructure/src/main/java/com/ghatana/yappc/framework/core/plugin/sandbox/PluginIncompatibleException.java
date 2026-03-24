/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Framework Module
 */
package com.ghatana.yappc.framework.core.plugin.sandbox;

/**
 * Thrown when a plugin requires a platform version greater than what is currently running.
 *
 * @doc.type class
 * @doc.purpose Signals plugin/platform version incompatibility
 * @doc.layer product
 * @doc.pattern Exception
 */
public class PluginIncompatibleException extends PluginLoadException {

    /**
     * @param pluginId           plugin that failed the version check
     * @param minPlatformVersion version the plugin requires
     */
    public PluginIncompatibleException(String pluginId, String minPlatformVersion) {
        super("Plugin '" + pluginId + "' requires platform version >= " + minPlatformVersion
                + " but the current platform version is older.");
    }
}
