package com.ghatana.aep.pattern.spec;

import java.util.Objects;

/**
 * Canonical version information for PatternSpec.
 *
 * <p>Provides structured version metadata for pattern specifications,
 * enabling version comparison, compatibility checks, and migration tracking.
 *
 * @doc.type record
 * @doc.purpose Canonical version metadata for PatternSpec
 * @doc.layer product
 * @doc.pattern Model
 */
public record PatternSpecVersion(
        String major,
        String minor,
        String patch,
        String preRelease,
        String buildMetadata) {

    public PatternSpecVersion {
        Objects.requireNonNull(major, "major");
        Objects.requireNonNull(minor, "minor");
        Objects.requireNonNull(patch, "patch");
    }

    /**
     * Parse a version string into PatternSpecVersion.
     * Expected format: major.minor.patch[-preRelease][+buildMetadata]
     *
     * @param versionString version string to parse
     * @return PatternSpecVersion instance
     * @throws IllegalArgumentException if version string is invalid
     */
    public static PatternSpecVersion parse(String versionString) {
        Objects.requireNonNull(versionString, "versionString");
        
        String[] parts = versionString.split("\\+", 2);
        String baseVersion = parts[0];
        String buildMetadata = parts.length > 1 ? parts[1] : null;
        
        String[] preReleaseParts = baseVersion.split("-", 2);
        String versionWithoutPre = preReleaseParts[0];
        String preRelease = preReleaseParts.length > 1 ? preReleaseParts[1] : null;
        
        String[] versionParts = versionWithoutPre.split("\\.", 3);
        if (versionParts.length < 3) {
            throw new IllegalArgumentException("Invalid version format: " + versionString);
        }
        
        return new PatternSpecVersion(
            versionParts[0],
            versionParts[1],
            versionParts[2],
            preRelease,
            buildMetadata
        );
    }

    /**
     * Convert this version to a string representation.
     *
     * @return version string
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(major).append('.').append(minor).append('.').append(patch);
        if (preRelease != null && !preRelease.isEmpty()) {
            sb.append('-').append(preRelease);
        }
        if (buildMetadata != null && !buildMetadata.isEmpty()) {
            sb.append('+').append(buildMetadata);
        }
        return sb.toString();
    }

    /**
     * Compare this version with another for ordering.
     *
     * @param other version to compare with
     * @return negative if this < other, zero if equal, positive if this > other
     */
    public int compareTo(PatternSpecVersion other) {
        int majorCompare = Integer.compare(Integer.parseInt(this.major), Integer.parseInt(other.major));
        if (majorCompare != 0) return majorCompare;
        
        int minorCompare = Integer.compare(Integer.parseInt(this.minor), Integer.parseInt(other.minor));
        if (minorCompare != 0) return minorCompare;
        
        int patchCompare = Integer.compare(Integer.parseInt(this.patch), Integer.parseInt(other.patch));
        if (patchCompare != 0) return patchCompare;
        
        // Pre-release versions come before normal versions
        if (this.preRelease == null && other.preRelease != null) return 1;
        if (this.preRelease != null && other.preRelease == null) return -1;
        if (this.preRelease != null && other.preRelease != null) {
            return this.preRelease.compareTo(other.preRelease);
        }
        
        return 0;
    }
}
