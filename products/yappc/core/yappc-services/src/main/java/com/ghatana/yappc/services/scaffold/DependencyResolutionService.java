/**
 * Dependency Resolution Service
 * 
 * Resolves and validates pack dependencies.
 * Ensures dependencies are compatible and available.
 * 
 * @doc.type interface
 * @doc.purpose Dependency resolution
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.scaffold;

import com.ghatana.yappc.api.PackMetadata;

import java.util.List;

/**
 * Service interface for resolving pack dependencies.
 */
public interface DependencyResolutionService {

    /**
     * Resolves dependencies for a pack.
     * 
     * @param packMetadata The pack metadata to resolve dependencies for
     * @return DependencyResolutionResult containing resolved dependencies and any errors
     */
    DependencyResolutionResult resolveDependencies(PackMetadata packMetadata);

    /**
     * Validates dependency compatibility constraints.
     * 
     * @param packMetadata The pack metadata to validate
     * @return DependencyValidationResult containing validation status and any errors
     */
    DependencyValidationResult validateCompatibility(PackMetadata packMetadata);

    /**
     * Checks for circular dependencies.
     * 
     * @param packMetadata The pack metadata to check
     * @return true if circular dependencies exist, false otherwise
     */
    boolean hasCircularDependencies(PackMetadata packMetadata);
}

/**
 * Dependency resolution result.
 */
record DependencyResolutionResult(
    boolean success,
    List<ResolvedDependency> resolvedDependencies,
    List<String> errors,
    List<String> warnings
) {
    public DependencyResolutionResult {
        if (resolvedDependencies == null) {
            resolvedDependencies = List.of();
        }
        if (errors == null) {
            errors = List.of();
        }
        if (warnings == null) {
            warnings = List.of();
        }
    }
}

/**
 * Resolved dependency.
 */
record ResolvedDependency(
    String dependencyId,
    String resolvedVersion,
    String resolvedLocation,
    boolean isCompatible
) {}

/**
 * Dependency validation result.
 */
record DependencyValidationResult(
    boolean isValid,
    List<String> incompatibilities,
    List<String> warnings
) {
    public DependencyValidationResult {
        if (incompatibilities == null) {
            incompatibilities = List.of();
        }
        if (warnings == null) {
            warnings = List.of();
        }
    }
}
