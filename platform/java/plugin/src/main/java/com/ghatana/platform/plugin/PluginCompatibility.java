package com.ghatana.platform.plugin;

import org.jetbrains.annotations.NotNull;

/**
 * Defines version compatibility for a plugin.
 *
 * @param minDataCloudVersion Minimum supported Data-Cloud version
 * @param maxDataCloudVersion Maximum supported Data-Cloud version (optional)
 *
 * @doc.type record
 * @doc.purpose Version compatibility matrix
 * @doc.layer core
 */
public record PluginCompatibility(
    @NotNull String minDataCloudVersion,
    String maxDataCloudVersion
) {
    public static PluginCompatibility dataCloudVersion(String min) {
        return new PluginCompatibility(min, null);
    }
}
