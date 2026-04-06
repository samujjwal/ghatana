package com.ghatana.platform.plugin;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a dependency on another plugin.
 *
 * @param pluginId The ID of the required plugin
 * @param versionRange Semantic version range (e.g., "^1.0.0")
 * @param optional Whether the dependency is optional
 *
 * @doc.type record
 * @doc.purpose Define plugin dependencies
 * @doc.layer core
 */
public record PluginDependency(
    @NotNull String pluginId,
    @NotNull String versionRange,
    boolean optional
) {}
