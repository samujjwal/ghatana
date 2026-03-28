/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.core.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Utility class for common input validation at service boundaries.
 *
 * <p>Provides static guard methods that throw {@link IllegalArgumentException} with
 * clear field-scoped messages when inputs violate constraints. Use this class at HTTP
 * handler or service entry-points to validate untrusted input before passing it to
 * domain logic.</p>
 *
 * <p>For async/schema-driven validation, see
 * {@link com.ghatana.platform.validation.ValidationService}.</p>
 *
 * @doc.type class
 * @doc.purpose Static input validation helpers for use at service boundaries
 * @doc.layer core
 * @doc.pattern Utility
 */
public final class ValidationUtils {

    private ValidationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Validates that a string is not null and not blank.
     *
     * @param value     the value to validate
     * @param fieldName the field name used in the error message
     * @throws IllegalArgumentException if the value is null or blank
     */
    public static void validateNotBlank(@Nullable String value, @NotNull String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    /**
     * Validates that an object reference is not null.
     *
     * @param value     the value to validate
     * @param fieldName the field name used in the error message
     * @throws IllegalArgumentException if the value is null
     */
    public static void validateNotNull(@Nullable Object value, @NotNull String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
    }

    /**
     * Validates that a string does not exceed a maximum length.
     *
     * @param value     the value to validate (null is accepted — use {@link #validateNotBlank} if required)
     * @param max       the maximum allowed length (inclusive)
     * @param fieldName the field name used in the error message
     * @throws IllegalArgumentException if the value exceeds the maximum length
     */
    public static void validateMaxLength(@Nullable String value, int max, @NotNull String fieldName) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(
                    fieldName + " must not exceed " + max + " characters (was " + value.length() + ")");
        }
    }

    /**
     * Validates that a string meets a minimum length.
     *
     * @param value     the value to validate (null is accepted — use {@link #validateNotBlank} if required)
     * @param min       the minimum required length (inclusive)
     * @param fieldName the field name used in the error message
     * @throws IllegalArgumentException if the value is shorter than the minimum length
     */
    public static void validateMinLength(@Nullable String value, int min, @NotNull String fieldName) {
        if (value != null && value.length() < min) {
            throw new IllegalArgumentException(
                    fieldName + " must be at least " + min + " characters (was " + value.length() + ")");
        }
    }

    /**
     * Validates that an integer value falls within [min, max] (inclusive).
     *
     * @param value     the value to validate
     * @param min       the minimum allowed value (inclusive)
     * @param max       the maximum allowed value (inclusive)
     * @param fieldName the field name used in the error message
     * @throws IllegalArgumentException if the value is outside the allowed range
     */
    public static void validateRange(int value, int min, int max, @NotNull String fieldName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    fieldName + " must be between " + min + " and " + max + " (was " + value + ")");
        }
    }

    /**
     * Validates that a long value falls within [min, max] (inclusive).
     *
     * @param value     the value to validate
     * @param min       the minimum allowed value (inclusive)
     * @param max       the maximum allowed value (inclusive)
     * @param fieldName the field name used in the error message
     * @throws IllegalArgumentException if the value is outside the allowed range
     */
    public static void validateRange(long value, long min, long max, @NotNull String fieldName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    fieldName + " must be between " + min + " and " + max + " (was " + value + ")");
        }
    }

    /**
     * Validates that a string matches a given pattern.
     *
     * @param value     the value to validate (null is accepted — use {@link #validateNotNull} if required)
     * @param pattern   the regex pattern the value must match
     * @param fieldName the field name used in the error message
     * @throws IllegalArgumentException if the value does not match the pattern
     */
    public static void validatePattern(@Nullable String value, @NotNull Pattern pattern, @NotNull String fieldName) {
        if (value != null && !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    fieldName + " does not match the required format");
        }
    }

    /**
     * Validates that a collection is not null and not empty.
     *
     * @param value     the collection to validate
     * @param fieldName the field name used in the error message
     * @throws IllegalArgumentException if the collection is null or empty
     */
    public static void validateNotEmpty(@Nullable Collection<?> value, @NotNull String fieldName) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
    }

    /**
     * Validates that a positive integer is positive (greater than zero).
     *
     * @param value     the value to validate
     * @param fieldName the field name used in the error message
     * @throws IllegalArgumentException if the value is not positive
     */
    public static void validatePositive(int value, @NotNull String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive (was " + value + ")");
        }
    }

    /**
     * Validates that a long value is positive (greater than zero).
     *
     * @param value     the value to validate
     * @param fieldName the field name used in the error message
     * @throws IllegalArgumentException if the value is not positive
     */
    public static void validatePositive(long value, @NotNull String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive (was " + value + ")");
        }
    }
}
