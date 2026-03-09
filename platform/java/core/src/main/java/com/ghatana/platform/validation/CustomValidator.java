/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.validation;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Custom validator using a predicate.
 *
 * @param <T> the type to validate
 *
 * @doc.type class
 * @doc.purpose Custom validator using a user-supplied predicate function
 * @doc.layer platform
 * @doc.pattern Validator
 */
public final class CustomValidator<T> implements Validator<T> {
    private final Predicate<T> predicate;
    private final String errorCode;
    private final String errorMessage;
    private final String validatorType;

    public CustomValidator(Predicate<T> predicate, String errorCode, String errorMessage) {
        this(predicate, errorCode, errorMessage, "CUSTOM");
    }

    public CustomValidator(Predicate<T> predicate, String errorCode, String errorMessage, String validatorType) {
        this.predicate = Objects.requireNonNull(predicate);
        this.errorCode = Objects.requireNonNull(errorCode);
        this.errorMessage = Objects.requireNonNull(errorMessage);
        this.validatorType = Objects.requireNonNull(validatorType);
    }

    @Override
    public ValidationResult validate(T value, String fieldName) {
        try {
            if (!predicate.test(value)) {
                return ValidationResult.failure(
                    new ValidationError(errorCode, errorMessage, fieldName, value)
                );
            }
        } catch (Exception e) {
            return ValidationResult.failure(
                new ValidationError("VALIDATION_ERROR", "Validation failed: " + e.getMessage(), fieldName, value)
            );
        }
        return ValidationResult.success();
    }

    @Override
    public String getType() { return validatorType; }
}
