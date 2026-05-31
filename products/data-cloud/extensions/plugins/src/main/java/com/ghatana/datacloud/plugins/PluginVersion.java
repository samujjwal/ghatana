/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.plugins;

/**
 * Plugin version information (P8).
 *
 * @doc.type record
 * @doc.purpose Plugin version with semantic versioning
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PluginVersion(
        String version,
        int major,
        int minor,
        int patch,
        String preRelease,
        String buildMetadata
) {
    public PluginVersion {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        if (major < 0) {
            throw new IllegalArgumentException("major must be non-negative");
        }
        if (minor < 0) {
            throw new IllegalArgumentException("minor must be non-negative");
        }
        if (patch < 0) {
            throw new IllegalArgumentException("patch must be non-negative");
        }
        if (preRelease == null) {
            preRelease = "";
        }
        if (buildMetadata == null) {
            buildMetadata = "";
        }
    }

    /**
     * Creates a PluginVersion from a semantic version string.
     */
    public static PluginVersion parse(String versionString) {
        if (versionString == null || versionString.isBlank()) {
            throw new IllegalArgumentException("versionString must not be blank");
        }

        String[] parts = versionString.split("-", 2);
        String mainVersion = parts[0];
        String preRelease = parts.length > 1 ? parts[1] : "";

        String[] versionParts = mainVersion.split("\\+");
        String versionPart = versionParts[0];
        String buildMetadata = versionParts.length > 1 ? versionParts[1] : "";

        String[] numbers = versionPart.split("\\.");
        if (numbers.length < 3) {
            throw new IllegalArgumentException("Invalid semantic version: " + versionString);
        }

        int major = Integer.parseInt(numbers[0]);
        int minor = Integer.parseInt(numbers[1]);
        int patch = Integer.parseInt(numbers[2]);

        return new PluginVersion(versionString, major, minor, patch, preRelease, buildMetadata);
    }

    /**
     * Returns true if this version is greater than the other version.
     */
    public boolean isGreaterThan(PluginVersion other) {
        if (this.major != other.major) {
            return this.major > other.major;
        }
        if (this.minor != other.minor) {
            return this.minor > other.minor;
        }
        if (this.patch != other.patch) {
            return this.patch > other.patch;
        }
        return false;
    }

    /**
     * Returns true if this version is compatible with the other version.
     * Compatible means same major version and equal or greater minor/patch.
     */
    public boolean isCompatibleWith(PluginVersion other) {
        return this.major == other.major
            && (this.minor > other.minor || (this.minor == other.minor && this.patch >= other.patch));
    }
}
