/**
 * Pack Validation Service
 * 
 * Validates pack structure and metadata.
 * Ensures packs conform to required structure and validation rules.
 * 
 * @doc.type interface
 * @doc.purpose Pack validation
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.scaffold;

import com.ghatana.yappc.api.PackMetadata;

/**
 * Service interface for validating pack structure and metadata.
 */
public interface PackValidationService {

    /**
     * Validates pack metadata.
     * 
     * @param packMetadata The pack metadata to validate
     * @return PackValidationResult containing validation status and any errors
     */
    PackValidationResult validatePack(PackMetadata packMetadata);

    /**
     * Validates pack structure against required files and constraints.
     * 
     * @param packMetadata The pack metadata to validate
     * @return PackValidationResult containing validation status and any errors
     */
    PackValidationResult validateStructure(PackMetadata packMetadata);

    /**
     * Validates template variables.
     * 
     * @param packMetadata The pack metadata to validate
     * @return PackValidationResult containing validation status and any errors
     */
    PackValidationResult validateTemplateVariables(PackMetadata packMetadata);
}

/**
 * Pack validation result.
 */
record PackValidationResult(
    boolean isValid,
    java.util.List<String> errors,
    java.util.List<String> warnings
) {
    public PackValidationResult {
        if (errors == null) {
            errors = java.util.List.of();
        }
        if (warnings == null) {
            warnings = java.util.List.of();
        }
    }
}
