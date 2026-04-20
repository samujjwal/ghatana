/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.validation;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Immutable result of a validation operation.
 *
 * <p><b>DEPRECATED</b> - Use {@link com.ghatana.platform.core.validation.ValidationResult} instead.
 * This class is deprecated and will be removed in a future release.
 *
 * <p>Encapsulates the outcome of validation with support for multiple errors.
 *
 * @doc.type class
 * @doc.purpose Aggregated result of one or more validation checks (DEPRECATED)
 * @doc.layer platform
 * @doc.pattern ValueObject
 * @deprecated Use {@link com.ghatana.platform.core.validation.ValidationResult} instead
 */
@Deprecated(since = "1.0", forRemoval = true)
public class ValidationResult {
    private final boolean valid;
    private final List<ValidationError> errors;

    private ValidationResult(boolean valid, List<ValidationError> errors) {
        this.valid = valid;
        this.errors = errors != null ? List.copyOf(errors) : Collections.emptyList();
    }

    @NotNull
    public static ValidationResult success() {
        return new ValidationResult(true, Collections.emptyList());
    }

    /**
     * Alias for {@link #success()} for backward compatibility.
     */
    @NotNull
    public static ValidationResult valid() {
        return success();
    }

    @NotNull
    public static ValidationResult failure(@NotNull ValidationError... errors) {
        return new ValidationResult(false, List.of(errors));
    }

    @NotNull
    public static ValidationResult failure(@NotNull List<ValidationError> errors) {
        return new ValidationResult(false, errors);
    }

    /**
     * Creates a failed validation result with a single error message.
     *
     * @param message human-readable error description
     */
    @NotNull
    public static ValidationResult failure(@NotNull String message) {
        return failure(new ValidationError("VALIDATION_FAILED", message));
    }

    /**     * Alias for {@link #failure(ValidationError...)} for backward compatibility.
     */
    @NotNull
    public static ValidationResult invalid(@NotNull ValidationError... errors) {
        return failure(errors);
    }

    /**
     * Alias for {@link #failure(List)} for backward compatibility.
     */
    @NotNull
    public static ValidationResult invalid(@NotNull List<ValidationError> errors) {
        return failure(errors);
    }

    public boolean isValid() {
        return valid;
    }

    @NotNull
    public List<ValidationError> getErrors() {
        return errors;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("ValidationResult[valid=%s, errors=%d]", valid, errors.size());
    }
}
