package com.ghatana.products.yappc.domain.task;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Validation result for task input.
 *
 * @param valid  Whether validation passed
 * @param errors List of validation errors (empty if valid)
 * @doc.type record
 * @doc.purpose Task input validation result
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ValidationResult(
        boolean valid,
        @NotNull List<String> errors
) {
    public ValidationResult {
        errors = List.copyOf(errors);
    }

    public static ValidationResult success() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult failure(@NotNull List<String> errors) {
        return new ValidationResult(false, errors);
    }

    public static ValidationResult failure(@NotNull String error) {
        return new ValidationResult(false, List.of(error));
    }
}
