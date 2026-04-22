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
 * @doc.pattern ValueObject
 */
public record PluginCompatibility(
    @NotNull String minDataCloudVersion,
    String maxDataCloudVersion
) {
    public static PluginCompatibility dataCloudVersion(String min) {
        return new PluginCompatibility(min, null);
    }

    /**
     * Returns true when {@code platformVersion} is within this compatibility window.
     */
    public boolean isCompatibleWith(@NotNull String platformVersion) {
        if (compareVersions(platformVersion, minDataCloudVersion) < 0) {
            return false;
        }
        return maxDataCloudVersion == null || compareVersions(platformVersion, maxDataCloudVersion) <= 0;
    }

    private static int compareVersions(@NotNull String left, @NotNull String right) {
        String[] leftParts = normalize(left).split("\\.");
        String[] rightParts = normalize(right).split("\\.");
        int max = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < max; i++) {
            int leftValue = i < leftParts.length ? Integer.parseInt(leftParts[i]) : 0;
            int rightValue = i < rightParts.length ? Integer.parseInt(rightParts[i]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private static String normalize(@NotNull String version) {
        int suffixIndex = version.indexOf('-');
        return suffixIndex >= 0 ? version.substring(0, suffixIndex) : version;
    }
}
