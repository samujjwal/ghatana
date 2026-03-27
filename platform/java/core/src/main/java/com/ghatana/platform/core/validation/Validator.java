package com.ghatana.platform.core.validation;

import com.ghatana.platform.core.exception.ValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Core validator abstraction for the platform validation framework.
 *
 * <p>Validators are composable: use {@link #and(Validator)} to chain validators
 * or {@link ValidatorBuilder} to construct multi-rule validators fluently.
 *
 * <p>Usage:
 * <pre>{@code
 * Validator<String> notBlank = Validator.of("name", s -> s != null && !s.isBlank(), "must not be blank");
 * ValidationResult result = notBlank.validate(input);
 * result.throwIfInvalid();
 * }</pre>
 *
 * @param <T> the type this validator validates
 *
 * @doc.type interface
 * @doc.purpose Composable functional validator interface for platform validation
 * @doc.layer core
 * @doc.pattern Strategy, Validation
 *
 * @since 2026-03-27
 */
@FunctionalInterface
public interface Validator<T> {

    /**
     * Validates the given value.
     *
     * @param value the value to validate (may be null — must handle null safety)
     * @return a {@link ValidationResult} — never null
     */
    ValidationResult validate(T value);

    /**
     * Validates the value and throws {@link ValidationException} on failure.
     * Convenience method for callers that want fail-fast behaviour.
     *
     * @param value the value to validate
     * @throws ValidationException if any constraints are violated
     */
    default void validateAndThrow(T value) {
        validate(value).throwIfInvalid();
    }

    /**
     * Composes this validator with {@code other}, running both and combining results.
     *
     * @param other the other validator
     * @return a new composite validator
     */
    default Validator<T> and(Validator<T> other) {
        Validator<T> self = this;
        return value -> self.validate(value).and(other.validate(value));
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    /**
     * Creates a simple validator from a predicate.
     *
     * @param field   the field name for violation reporting
     * @param test    the predicate — must return {@code true} if valid
     * @param message the violation message if the predicate fails
     * @param <T>     the type to validate
     * @return a new validator
     */
    static <T> Validator<T> of(String field, Predicate<T> test, String message) {
        return value -> test.test(value)
            ? ValidationResult.valid()
            : ValidationResult.invalid(field, message);
    }

    /**
     * Creates a validator that always passes.
     */
    static <T> Validator<T> alwaysValid() {
        return value -> ValidationResult.valid();
    }

    /**
     * Creates a required (non-null) validator.
     *
     * @param field the field name
     * @param <T>   the type to validate
     */
    static <T> Validator<T> required(String field) {
        return of(field, value -> value != null, field + " is required");
    }

    /**
     * Creates a builder for multi-rule validators.
     *
     * @param <T> the type to validate
     * @return a new builder
     */
    static <T> ValidatorBuilder<T> builder() {
        return new ValidatorBuilder<>();
    }

    // ── No-op marker ──────────────────────────────────────────────────────────

    /**
     * Marker class for the {@link Validate @Validate} annotation's {@code using()} default.
     * Not meant to be instantiated.
     */
    final class NoOp implements Validator<Object> {
        private NoOp() {}

        @Override
        public ValidationResult validate(Object value) {
            return ValidationResult.valid();
        }
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    /**
     * Fluent builder for constructing multi-rule validators.
     *
     * @param <T> the type to validate
     */
    final class ValidatorBuilder<T> {
        private final List<Rule<T>> rules = new ArrayList<>();

        /**
         * Adds a named validation rule with a predicate and message.
         *
         * @param field   the field name for violation reporting
         * @param test    the predicate (true = valid)
         * @param message the violation message if the predicate fails
         * @return this builder
         */
        public ValidatorBuilder<T> rule(String field, Predicate<T> test, String message) {
            rules.add(new Rule<>(field, test, message));
            return this;
        }

        /**
         * Adds a required-field check.
         *
         * @param field     the field name
         * @param extractor function to extract the field value
         * @return this builder
         */
        public <R> ValidatorBuilder<T> required(String field, Function<T, R> extractor) {
            return rule(field, obj -> {
                R val = extractor.apply(obj);
                return val != null && (!(val instanceof String s) || !s.isBlank());
            }, field + " is required");
        }

        /**
         * Adds a maximum-length check for String fields.
         *
         * @param field     the field name
         * @param extractor function to extract the String field
         * @param maxLength maximum allowed length (inclusive)
         * @return this builder
         */
        public ValidatorBuilder<T> maxLength(String field, Function<T, String> extractor, int maxLength) {
            return rule(field,
                obj -> {
                    String val = extractor.apply(obj);
                    return val == null || val.length() <= maxLength;
                },
                field + " must be at most " + maxLength + " characters");
        }

        /**
         * Adds an integer range check.
         *
         * @param field     the field name
         * @param extractor function to extract the integer field
         * @param min       minimum value (inclusive)
         * @param max       maximum value (inclusive)
         * @return this builder
         */
        public ValidatorBuilder<T> range(String field, Function<T, Integer> extractor, int min, int max) {
            return rule(field,
                obj -> {
                    Integer val = extractor.apply(obj);
                    return val != null && val >= min && val <= max;
                },
                field + " must be between " + min + " and " + max);
        }

        /**
         * Builds the validator.
         *
         * @return a new {@link Validator} based on the accumulated rules
         */
        public Validator<T> build() {
            List<Rule<T>> snapshot = List.copyOf(rules);
            return value -> {
                List<ValidationResult.Violation> violations = new ArrayList<>();
                for (Rule<T> rule : snapshot) {
                    if (!rule.test.test(value)) {
                        violations.add(new ValidationResult.Violation(rule.field, rule.message));
                    }
                }
                return ValidationResult.of(violations);
            };
        }

        private record Rule<T>(String field, Predicate<T> test, String message) {}
    }
}
