/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.validation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Functional interface for validating values.
 *
 * @param <T> the type of value to validate
 *
 * @doc.type interface
 * @doc.purpose Single-responsibility validator for a specific constraint
 * @doc.layer platform
 * @doc.pattern Service
 */
@FunctionalInterface
public interface Validator<T> {

    /**
     * Validates the given value.
     *
     * @param value the value to validate
     * @param fieldName the name of the field being validated (for error reporting)
     * @return the validation result
     */
    @NotNull
    ValidationResult validate(@Nullable T value, @NotNull String fieldName);

    /**
     * Returns the validator type identifier.
     *
     * @return the type name
     */
    @NotNull
    default String getType() {
        return getClass().getSimpleName();
    }
}
