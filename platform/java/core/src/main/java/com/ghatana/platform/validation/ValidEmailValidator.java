/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.validation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Validator for email format.
 *
 * @doc.type class
 * @doc.purpose Singleton validator for email address format using regex pattern matching
 * @doc.layer platform
 * @doc.pattern Validator
 */
public final class ValidEmailValidator implements Validator<String> {

    private static final ValidEmailValidator INSTANCE = new ValidEmailValidator();
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    private static final String TYPE = "EMAIL";

    private ValidEmailValidator() {}

    @NotNull
    public static ValidEmailValidator instance() {
        return INSTANCE;
    }

    @Override
    @NotNull
    public ValidationResult validate(@Nullable String value, @NotNull String fieldName) {
        if (value == null) {
            return ValidationResult.failure(
                new ValidationError("NOT_NULL", fieldName + " must not be null", fieldName, null)
            );
        }
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            return ValidationResult.failure(
                new ValidationError("INVALID_EMAIL", fieldName + " must be a valid email address", fieldName, value)
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
