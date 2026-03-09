/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.validation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Validator for numeric ranges.
 *
 * @doc.type class
 * @doc.purpose Validates that comparable values fall within a specified min/max range
 * @doc.layer platform
 * @doc.pattern Validator
 */
public final class RangeValidator<T extends Comparable<T>> implements Validator<T> {

    private final T min;
    private final T max;
    private static final String TYPE = "RANGE";

    public RangeValidator(@NotNull T min, @NotNull T max) {
        this.min = min;
        this.max = max;
    }

    @Override
    @NotNull
    public ValidationResult validate(@Nullable T value, @NotNull String fieldName) {
        if (value == null) {
            return ValidationResult.failure(
                new ValidationError("NOT_NULL", fieldName + " must not be null", fieldName, null)
            );
        }
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            return ValidationResult.failure(
                new ValidationError("OUT_OF_RANGE", 
                    fieldName + " must be between " + min + " and " + max, fieldName, value)
            );
        }
        return ValidationResult.success();
    }

    @Override
    @NotNull
    public String getType() {
        return TYPE;
    }
}
