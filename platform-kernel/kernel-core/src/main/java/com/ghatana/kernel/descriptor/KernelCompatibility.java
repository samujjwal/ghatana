package com.ghatana.kernel.descriptor;

import java.util.Objects;

/**
 * Defines compatibility constraints between kernel versions.
 *
 * <p>Compatibility constraints specify which kernel versions a component
 * is compatible with using semantic versioning constraints.</p>
 *
 * @doc.type class
 * @doc.purpose Kernel version compatibility constraints for components
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class KernelCompatibility {

    private final String kernelVersionConstraint;
    private final CompatibilityType type;
    private final String description;

    /**
     * Creates a kernel compatibility constraint.
     *
     * @param kernelVersionConstraint version constraint (e.g., ">=1.0.0", "[1.0,2.0)")
     * @param type the compatibility type
     * @param description human-readable description
     */
    public KernelCompatibility(String kernelVersionConstraint, CompatibilityType type, String description) {
        if (kernelVersionConstraint == null || kernelVersionConstraint.trim().isEmpty()) {
            throw new IllegalArgumentException("kernelVersionConstraint cannot be null or empty");
        }
        this.kernelVersionConstraint = kernelVersionConstraint;
        this.type = type != null ? type : CompatibilityType.COMPATIBLE;
        this.description = description != null ? description : "";
    }

    /**
     * Creates a compatibility constraint with default type.
     */
    public KernelCompatibility(String kernelVersionConstraint) {
        this(kernelVersionConstraint, CompatibilityType.COMPATIBLE, "");
    }

    // Getters
    public String getKernelVersionConstraint() { return kernelVersionConstraint; }
    public CompatibilityType getType() { return type; }
    public String getDescription() { return description; }

    /**
     * Checks if a kernel version satisfies this compatibility constraint.
     *
     * @param kernelVersion the kernel version to check
     * @return true if compatible
     */
    public boolean isCompatible(String kernelVersion) {
        if (kernelVersionConstraint.equals("*")) {
            return true;
        }

        // Simplified semver comparison
        // In production, use a proper semver library
        String constraintBase = kernelVersionConstraint.replaceAll("[^0-9.]", "");
        String versionBase = kernelVersion.replaceAll("[^0-9.]", "");

        if (kernelVersionConstraint.startsWith(">=")) {
            return compareVersions(versionBase, constraintBase) >= 0;
        } else if (kernelVersionConstraint.startsWith(">")) {
            return compareVersions(versionBase, constraintBase) > 0;
        } else if (kernelVersionConstraint.startsWith("<=")) {
            return compareVersions(versionBase, constraintBase) <= 0;
        } else if (kernelVersionConstraint.startsWith("<")) {
            return compareVersions(versionBase, constraintBase) < 0;
        } else if (kernelVersionConstraint.startsWith("=")) {
            return compareVersions(versionBase, constraintBase) == 0;
        } else {
            // Exact version match (without prefix)
            return versionBase.equals(constraintBase);
        }
    }

    /**
     * Compares two version strings.
     *
     * @return negative if v1 < v2, 0 if equal, positive if v1 > v2
     */
    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int p1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int p2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;

            if (p1 != p2) {
                return Integer.compare(p1, p2);
            }
        }
        return 0;
    }

    // Object methods
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KernelCompatibility that = (KernelCompatibility) o;
        return Objects.equals(kernelVersionConstraint, that.kernelVersionConstraint) &&
               type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kernelVersionConstraint, type);
    }

    @Override
    public String toString() {
        return String.format("KernelCompatibility{constraint='%s', type=%s}",
            kernelVersionConstraint, type);
    }

    public enum CompatibilityType {
        COMPATIBLE,
        INCOMPATIBLE,
        DEPRECATED,
        EXPERIMENTAL,
        BREAKING_CHANGE
    }
}
