package com.ghatana.platform.plugin;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern NUMERIC_VERSION_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)*)");

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
        String trimmed = version.trim();
        Matcher matcher = NUMERIC_VERSION_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "0";
    }
}
