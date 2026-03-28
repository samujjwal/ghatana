package com.ghatana.products.yappc.domain.task;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Validation result for task input.
 *
 * <p><b>Migration Notice</b>: Prefer {@code com.ghatana.platform.validation.ValidationResult}
 * for new code. Migrate this class when refactoring {@code TaskValidator} to use
 * {@link com.ghatana.platform.validation.ValidationError} objects instead of raw strings.
 *
 * @param valid  Whether validation passed
 * @param errors List of validation errors (empty if valid)
 * @doc.type record
 * @doc.purpose Task input validation result
 * @doc.layer product
 * @doc.pattern ValueObject
 * @deprecated Use {@code com.ghatana.platform.validation.ValidationResult} — schedule migration
 */
@Deprecated(since = "2026-03-27", forRemoval = false)
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
