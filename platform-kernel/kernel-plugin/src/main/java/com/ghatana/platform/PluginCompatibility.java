package com.ghatana.platform.plugin;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Defines version compatibility for a plugin against the platform runtime.
 *
 * <p>The compatibility window is expressed as a semantic version range ({@code min} inclusive,
 * {@code max} inclusive if specified). Products declare compatible plugin versions/ranges in
 * their binding manifests rather than assuming a fixed version.</p>
 *
 * @param minPlatformVersion Minimum supported platform kernel version
 * @param maxPlatformVersion Maximum supported platform kernel version (optional — {@code null} means unbounded)
 *
 * @doc.type record
 * @doc.purpose Generic platform version compatibility descriptor for plugins
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record PluginCompatibility(
    @NotNull String minPlatformVersion,
    String maxPlatformVersion
) {
    private static final Pattern NUMERIC_VERSION_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)*)");

    /**
     * Creates a compatibility descriptor with only a minimum platform version bound.
     *
     * @param min the minimum platform kernel version required
     * @return a new {@link PluginCompatibility} with an unbounded upper version
     */
    public static PluginCompatibility atLeast(@NotNull String min) {
        return new PluginCompatibility(min, null);
    }

    /**
     * Creates a compatibility descriptor with both minimum and maximum platform version bounds.
     *
     * @param min the minimum platform kernel version required (inclusive)
     * @param max the maximum platform kernel version supported (inclusive)
     * @return a new {@link PluginCompatibility}
     */
    public static PluginCompatibility range(@NotNull String min, @NotNull String max) {
        return new PluginCompatibility(min, max);
    }

    /**
     * Returns {@code true} when {@code platformVersion} falls within this compatibility window.
     *
     * @param platformVersion the platform kernel version to check; must be non-null
     * @return {@code true} if compatible
     */
    public boolean isCompatibleWith(@NotNull String platformVersion) {
        if (compareVersions(platformVersion, minPlatformVersion) < 0) {
            return false;
        }
        return maxPlatformVersion == null || compareVersions(platformVersion, maxPlatformVersion) <= 0;
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
