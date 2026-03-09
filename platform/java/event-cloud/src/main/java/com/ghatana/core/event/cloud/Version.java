package com.ghatana.core.event.cloud;

/**
 * Semantic version for event types.
 * Follows major.minor versioning scheme.
 
 *
 * @doc.type record
 * @doc.purpose Version
 * @doc.layer platform
 * @doc.pattern ValueObject
*/
public record Version(int major, int minor) implements Comparable<Version> {

    public Version {
        if (major < 0) {
            throw new IllegalArgumentException("Major version must be non-negative, got: " + major);
        }
        if (minor < 0) {
            throw new IllegalArgumentException("Minor version must be non-negative, got: " + minor);
        }
    }

    /**
     * Parse version from string (e.g., "1.2").
     */
    public static Version parse(String version) {
        String[] parts = version.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid version format: " + version + " (expected major.minor)");
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return new Version(major, minor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid version format: " + version, e);
        }
    }

    @Override
    public int compareTo(Version other) {
        int majorCompare = Integer.compare(this.major, other.major);
        if (majorCompare != 0) {
            return majorCompare;
        }
        return Integer.compare(this.minor, other.minor);
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
