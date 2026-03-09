package com.ghatana.core.pipeline;

import java.util.List;
import java.util.Objects;

/**
 * Result of pipeline structural validation.
 *
 * @param isValid whether pipeline structure is valid
 * @param errors list of validation error messages (empty if valid)
 * @param warnings list of validation warning messages
 *
 * @doc.type record
 * @doc.purpose Pipeline validation result with detailed error/warning information
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record PipelineValidationResult(
    boolean isValid,
    List<String> errors,
    List<String> warnings
) {
    public PipelineValidationResult {
        Objects.requireNonNull(errors, "errors cannot be null");
        Objects.requireNonNull(warnings, "warnings cannot be null");
    }

    /**
     * Creates a successful validation result.
     *
     * @return valid result with no errors
     */
    public static PipelineValidationResult valid() {
        return new PipelineValidationResult(true, List.of(), List.of());
    }

    /**
     * Creates a validation result with errors.
     *
     * @param errors validation error messages
     * @return invalid result
     */
    public static PipelineValidationResult invalid(List<String> errors) {
        return new PipelineValidationResult(false, errors, List.of());
    }

    /**
     * Creates a validation result with errors and warnings.
     *
     * @param errors validation error messages
     * @param warnings validation warning messages
     * @return result (valid only if errors is empty)
     */
    public static PipelineValidationResult of(List<String> errors, List<String> warnings) {
        return new PipelineValidationResult(errors.isEmpty(), errors, warnings);
    }
}
