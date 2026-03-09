/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.validation;

import java.util.function.Predicate;

/**
 * Factory for creating common validators.
 *
 * @doc.type class
 * @doc.purpose Factory for creating common validator instances (notNull, notEmpty, range, pattern, email, custom)
 * @doc.layer platform
 * @doc.pattern Factory
 */
public final class ValidationFactory {
    private ValidationFactory() {}

    public static Validator<Object> notNull() { return NotNullValidator.instance(); }

    public static Validator<String> notEmpty() { return NotEmptyValidator.instance(); }

    public static <T extends Comparable<T>> Validator<T> range(T min, T max) {
        return new RangeValidator<>(min, max);
    }

    public static Validator<String> pattern(String regex) {
        return new PatternValidator(regex);
    }

    public static Validator<String> email() { return ValidEmailValidator.instance(); }

    public static <T> Validator<T> custom(Predicate<T> predicate, String errorCode, String errorMessage) {
        return new CustomValidator<>(predicate, errorCode, errorMessage);
    }

    public static <T> Validator<T> custom(Predicate<T> predicate, String errorCode, String errorMessage, String validatorType) {
        return new CustomValidator<>(predicate, errorCode, errorMessage, validatorType);
    }
}
