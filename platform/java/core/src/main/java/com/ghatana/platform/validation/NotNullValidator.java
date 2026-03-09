/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.validation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Validator that checks for non-null values.
 *
 * @doc.type class
 * @doc.purpose Validates that values are not null
 * @doc.layer platform
 * @doc.pattern Validator
 */
public final class NotNullValidator implements Validator<Object> {

    private static final NotNullValidator INSTANCE = new NotNullValidator();

    private NotNullValidator() {}

    @NotNull
    public static NotNullValidator instance() {
        return INSTANCE;
    }

    @Override
    @NotNull
    public ValidationResult validate(@Nullable Object value, @NotNull String fieldName) {
        if (value == null) {
            return ValidationResult.failure(
                new ValidationError("NOT_NULL", fieldName + " must not be null", fieldName, null)
            );
        }
        return ValidationResult.success();
    }

    @Override
    @NotNull
    public String getType() {
        return "NOT_NULL";
    }
}
