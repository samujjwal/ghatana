package com.ghatana.kernel.descriptor;

import java.util.Objects;

/**
 * Represents a dependency that a kernel component requires.
 *
 * <p>Dependencies can be on other kernel modules, capabilities, or external services.
 * Each dependency specifies version constraints and whether it's optional.</p>
 *
 * @doc.type class
 * @doc.purpose Kernel dependency definition with version constraints and type
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class KernelDependency {
    
    private final String dependencyId;
    private final String versionConstraint;
    private final DependencyType type;
    private final boolean optional;
    private final String description;

    /**
     * Creates a new kernel dependency.
     *
     * @param dependencyId the unique identifier of the dependency
     * @param versionConstraint version constraint (e.g., "1.0.0", ">=1.0.0", "[1.0,2.0)")
     * @param type the type of dependency
     * @param optional whether this dependency is optional
     * @throws IllegalArgumentException if dependencyId is null or empty
     */
    public KernelDependency(String dependencyId, String versionConstraint, 
                           DependencyType type, boolean optional) {
        this(dependencyId, versionConstraint, type, optional, "");
    }

    /**
     * Creates a new kernel dependency with description.
     *
     * @param dependencyId the unique identifier of the dependency
     * @param versionConstraint version constraint
     * @param type the type of dependency
     * @param optional whether this dependency is optional
     * @param description human-readable description
     * @throws IllegalArgumentException if dependencyId is null or empty
     */
    public KernelDependency(String dependencyId, String versionConstraint,
                           DependencyType type, boolean optional, String description) {
        if (dependencyId == null || dependencyId.trim().isEmpty()) {
            throw new IllegalArgumentException("dependencyId cannot be null or empty");
        }
        this.dependencyId = dependencyId;
        this.versionConstraint = versionConstraint != null ? versionConstraint : "*";
        this.type = type != null ? type : DependencyType.MODULE;
        this.optional = optional;
        this.description = description != null ? description : "";
    }

    // ==================== Getters ====================

    public String getDependencyId() { return dependencyId; }
    public String getVersionConstraint() { return versionConstraint; }
    public DependencyType getType() { return type; }
    public boolean isOptional() { return optional; }
    public String getDescription() { return description; }

    // ==================== Business Methods ====================

    /**
     * Checks if a given version satisfies this dependency's version constraint.
     *
     * @param version the version to check
     * @return true if the version satisfies the constraint
     */
    public boolean isVersionSatisfied(String version) {
        if (versionConstraint.equals("*")) {
            return true;
        }
        
        // Handle exact version match
        if (versionConstraint.matches("\\d+\\.\\d+\\.\\d+.*")) {
            return version.startsWith(versionConstraint.split("[-+]")[0]);
        }
        
        // For more complex constraints, we'd need a semver parser
        // This is a simplified implementation
        return true;
    }

    /**
     * Checks if this dependency is on a module.
     *
     * @return true if type is MODULE
     */
    public boolean isModuleDependency() {
        return type == DependencyType.MODULE;
    }

    /**
     * Checks if this dependency is on a capability.
     *
     * @return true if type is CAPABILITY
     */
    public boolean isCapabilityDependency() {
        return type == DependencyType.CAPABILITY;
    }

    /**
     * Checks if this dependency is on an external service.
     *
     * @return true if type is EXTERNAL_SERVICE
     */
    public boolean isExternalServiceDependency() {
        return type == DependencyType.EXTERNAL_SERVICE;
    }

    // ==================== Object Methods ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KernelDependency that = (KernelDependency) o;
        return Objects.equals(dependencyId, that.dependencyId) &&
               Objects.equals(versionConstraint, that.versionConstraint) &&
               type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dependencyId, versionConstraint, type);
    }

    @Override
    public String toString() {
        return String.format("KernelDependency{id='%s', version='%s', type=%s, optional=%b}",
            dependencyId, versionConstraint, type, optional);
    }

    // ==================== Dependency Types ====================

    /**
     * Types of kernel dependencies.
     */
    public enum DependencyType {
        /**
         * Dependency on another kernel module.
         */
        MODULE,
        
        /**
         * Dependency on a kernel capability.
         */
        CAPABILITY,
        
        /**
         * Dependency on a kernel plugin.
         */
        PLUGIN,
        
        /**
         * Dependency on a kernel extension.
         */
        EXTENSION,
        
        /**
         * Dependency on an external service.
         */
        EXTERNAL_SERVICE,
        
        /**
         * Dependency on a specific feature being enabled.
         */
        FEATURE,
        
        /**
         * Dependency on a configuration value.
         */
        CONFIGURATION
    }
}
