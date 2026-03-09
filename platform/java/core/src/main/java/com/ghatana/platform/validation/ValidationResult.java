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
 * Contains validation status and any errors that occurred.
 *
 * @doc.type class
 * @doc.purpose Aggregated result of one or more validation checks
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
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
     * Alias for {@link #failure(ValidationError...)} for backward compatibility.
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
