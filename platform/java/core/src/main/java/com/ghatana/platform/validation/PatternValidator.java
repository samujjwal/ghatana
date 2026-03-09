/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.validation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Validator for regex patterns.
 *
 * @doc.type class
 * @doc.purpose Validates that string values match a given regex pattern
 * @doc.layer platform
 * @doc.pattern Validator
 */
public final class PatternValidator implements Validator<String> {

    private final Pattern pattern;
    private static final String TYPE = "PATTERN";

    public PatternValidator(@NotNull String regex) {
        this.pattern = Pattern.compile(regex);
    }

    @Override
    @NotNull
    public ValidationResult validate(@Nullable String value, @NotNull String fieldName) {
        if (value == null) {
            return ValidationResult.failure(
                new ValidationError("NOT_NULL", fieldName + " must not be null", fieldName, null)
            );
        }
        if (!pattern.matcher(value).matches()) {
            return ValidationResult.failure(
                new ValidationError("PATTERN_MISMATCH", fieldName + " does not match required pattern", fieldName, value)
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
