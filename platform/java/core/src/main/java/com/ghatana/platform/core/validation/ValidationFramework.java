package com.ghatana.platform.core.validation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Centralized validation framework for consistent validation across the platform.
 * 
 * <p>Provides a fluent API for building validation rules and collecting validation
 * results. Supports field-level validation, custom validators, and detailed error
 * reporting.</p>
 * 
 * <h3>Design Principles:</h3>
 * <ul>
 *   <li><b>Fluent API</b>: Chainable validation methods</li>
 *   <li><b>Fail-fast or collect-all</b>: Configurable validation behavior</li>
 *   <li><b>Detailed errors</b>: Field-level error messages</li>
 *   <li><b>Extensible</b>: Custom validators supported</li>
 * </ul>
 * 
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * ValidationResult result = ValidationFramework.validate()
 *     .field("email", user.getEmail())
 *         .notNull("Email is required")
 *         .matches(EMAIL_PATTERN, "Invalid email format")
 *     .field("age", user.getAge())
 *         .notNull("Age is required")
 *         .min(18, "Must be 18 or older")
 *     .build();
 * 
 * if (!result.isValid()) {
 *     throw new ValidationException(result.getErrors());
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Centralized validation framework with fluent API
 * @doc.layer core
 * @doc.pattern Builder, Fluent Interface
 * 
 * @since 1.0.0
 */
public class ValidationFramework {

    private final List<ValidationError> errors = new ArrayList<>();
    private final boolean failFast;

    private ValidationFramework(boolean failFast) {
        this.failFast = failFast;
    }

    /**
     * Create a new validation framework that collects all errors.
     * 
     * @return new validation framework
     */
    @NotNull
    public static ValidationFramework validate() {
        return new ValidationFramework(false);
    }

    /**
     * Create a new validation framework that fails on first error.
     * 
     * @return new validation framework
     */
    @NotNull
    public static ValidationFramework validateFailFast() {
        return new ValidationFramework(true);
    }

    /**
     * Start validating a field.
     * 
     * @param fieldName the field name
     * @param value the field value
     * @param <T> the value type
     * @return field validator
     */
    @NotNull
    public <T> FieldValidator<T> field(@NotNull String fieldName, @Nullable T value) {
        return new FieldValidator<>(this, fieldName, value);
    }

    /**
     * Add a custom validation error.
     * 
     * @param fieldName the field name
     * @param message the error message
     * @return this framework for chaining
     */
    @NotNull
    public ValidationFramework addError(@NotNull String fieldName, @NotNull String message) {
        errors.add(new ValidationError(fieldName, message));
        return this;
    }

    /**
     * Build the validation result.
     * 
     * @return validation result
     */
    @NotNull
    public ValidationResult build() {
        return new ValidationResult(errors);
    }

    /**
     * Field validator for fluent validation API.
     * 
     * @param <T> the field value type
     */
    public static class FieldValidator<T> {
        private final ValidationFramework framework;
        private final String fieldName;
        private final T value;

        private FieldValidator(ValidationFramework framework, String fieldName, T value) {
            this.framework = framework;
            this.fieldName = fieldName;
            this.value = value;
        }

        /**
         * Validate that the field is not null.
         * 
         * @param message error message if validation fails
         * @return this validator for chaining
         */
        @NotNull
        public FieldValidator<T> notNull(@NotNull String message) {
            if (value == null) {
                framework.addError(fieldName, message);
                if (framework.failFast) {
                    throw new IllegalStateException("Validation failed: " + message);
                }
            }
            return this;
        }

        /**
         * Validate that the field is not blank (for strings).
         * 
         * @param message error message if validation fails
         * @return this validator for chaining
         */
        @NotNull
        public FieldValidator<T> notBlank(@NotNull String message) {
            if (value instanceof String str && (str == null || str.trim().isEmpty())) {
                framework.addError(fieldName, message);
                if (framework.failFast) {
                    throw new IllegalStateException("Validation failed: " + message);
                }
            }
            return this;
        }

        /**
         * Validate that the field matches a predicate.
         * 
         * @param predicate the validation predicate
         * @param message error message if validation fails
         * @return this validator for chaining
         */
        @NotNull
        public FieldValidator<T> matches(@NotNull Predicate<T> predicate, @NotNull String message) {
            if (value != null && !predicate.test(value)) {
                framework.addError(fieldName, message);
                if (framework.failFast) {
                    throw new IllegalStateException("Validation failed: " + message);
                }
            }
            return this;
        }

        /**
         * Validate that a numeric field is at least a minimum value.
         * 
         * @param min minimum value
         * @param message error message if validation fails
         * @return this validator for chaining
         */
        @NotNull
        public FieldValidator<T> min(long min, @NotNull String message) {
            if (value instanceof Number num && num.longValue() < min) {
                framework.addError(fieldName, message);
                if (framework.failFast) {
                    throw new IllegalStateException("Validation failed: " + message);
                }
            }
            return this;
        }

        /**
         * Validate that a numeric field is at most a maximum value.
         * 
         * @param max maximum value
         * @param message error message if validation fails
         * @return this validator for chaining
         */
        @NotNull
        public FieldValidator<T> max(long max, @NotNull String message) {
            if (value instanceof Number num && num.longValue() > max) {
                framework.addError(fieldName, message);
                if (framework.failFast) {
                    throw new IllegalStateException("Validation failed: " + message);
                }
            }
            return this;
        }

        /**
         * Validate that a string field has a minimum length.
         * 
         * @param minLength minimum length
         * @param message error message if validation fails
         * @return this validator for chaining
         */
        @NotNull
        public FieldValidator<T> minLength(int minLength, @NotNull String message) {
            if (value instanceof String str && str.length() < minLength) {
                framework.addError(fieldName, message);
                if (framework.failFast) {
                    throw new IllegalStateException("Validation failed: " + message);
                }
            }
            return this;
        }

        /**
         * Validate that a string field has a maximum length.
         * 
         * @param maxLength maximum length
         * @param message error message if validation fails
         * @return this validator for chaining
         */
        @NotNull
        public FieldValidator<T> maxLength(int maxLength, @NotNull String message) {
            if (value instanceof String str && str.length() > maxLength) {
                framework.addError(fieldName, message);
                if (framework.failFast) {
                    throw new IllegalStateException("Validation failed: " + message);
                }
            }
            return this;
        }

        /**
         * Validate that a string field matches a regex pattern.
         * 
         * @param pattern regex pattern
         * @param message error message if validation fails
         * @return this validator for chaining
         */
        @NotNull
        public FieldValidator<T> matches(@NotNull String pattern, @NotNull String message) {
            if (value instanceof String str && !str.matches(pattern)) {
                framework.addError(fieldName, message);
                if (framework.failFast) {
                    throw new IllegalStateException("Validation failed: " + message);
                }
            }
            return this;
        }

        /**
         * Return to the framework to validate another field.
         * 
         * @return the validation framework
         */
        @NotNull
        public ValidationFramework and() {
            return framework;
        }

        /**
         * Build the validation result.
         * 
         * @return validation result
         */
        @NotNull
        public ValidationResult build() {
            return framework.build();
        }
    }

    /**
     * Validation error representing a field-level validation failure.
     */
    public record ValidationError(
            @NotNull String fieldName,
            @NotNull String message
    ) {}

    /**
     * Validation result containing all validation errors.
     */
    public static class ValidationResult {
        private final List<ValidationError> errors;

        private ValidationResult(List<ValidationError> errors) {
            this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        }

        /**
         * Check if validation passed (no errors).
         * 
         * @return true if valid, false otherwise
         */
        public boolean isValid() {
            return errors.isEmpty();
        }

        /**
         * Get all validation errors.
         * 
         * @return unmodifiable list of errors
         */
        @NotNull
        public List<ValidationError> getErrors() {
            return errors;
        }

        /**
         * Get error messages as a list of strings.
         * 
         * @return list of error messages
         */
        @NotNull
        public List<String> getErrorMessages() {
            return errors.stream()
                    .map(e -> e.fieldName() + ": " + e.message())
                    .toList();
        }

        /**
         * Get the first error message, or null if valid.
         * 
         * @return first error message or null
         */
        @Nullable
        public String getFirstError() {
            return errors.isEmpty() ? null : errors.get(0).message();
        }
    }
}
