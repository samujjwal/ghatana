/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.validation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Validator that checks for non-empty strings.
 *
 * @doc.type class
 * @doc.purpose Validates that string values are not null or empty
 * @doc.layer platform
 * @doc.pattern Validator
 */
public final class NotEmptyValidator implements Validator<String> {

    private static final NotEmptyValidator INSTANCE = new NotEmptyValidator();

    private NotEmptyValidator() {}

    @NotNull
    public static NotEmptyValidator instance() {
        return INSTANCE;
    }

    @Override
    @NotNull
    public ValidationResult validate(@Nullable String value, @NotNull String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return ValidationResult.failure(
                new ValidationError("NOT_EMPTY", fieldName + " must not be empty", fieldName, value)
            );
        }
        return ValidationResult.success();
    }

    @Override
    @NotNull
    public String getType() {
        return "NOT_EMPTY";
    }
}
