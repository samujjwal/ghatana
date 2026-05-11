/**
 * Result of import validation.
 *
 * @doc.type record
 * @doc.purpose Import validation result
 * @doc.layer product
 * @doc.pattern DTO
 */
package com.ghatana.yappc.services.import_;

/**
 * Result of import validation.
 */
public record ImportValidationResult(
    boolean isValid,
    java.util.List<String> errors,
    java.util.List<String> warnings
) {
    public ImportValidationResult {
        if (errors == null) {
            errors = java.util.List.of();
        }
        if (warnings == null) {
            warnings = java.util.List.of();
        }
    }
}
