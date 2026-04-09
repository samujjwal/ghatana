package com.ghatana.platform.core.validation;

import com.ghatana.platform.core.exception.ValidationException;

import java.util.List;
import java.util.Map;

/**
 * Immutable result of a validation operation.
 *
 * <p>Holds a list of {@link Violation violations}. An empty violations list
 * means the input is valid.
 *
 * <p>Usage:
 * <pre>{@code
 * ValidationResult result = Validators.validate(value, validator);
 * if (!result.isValid()) {
 *     throw result.toException();
 * }
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Immutable validation result holding all constraint violations
 * @doc.layer core
 * @doc.pattern ValueObject, Validation
 *
 * @since 2026-03-27
 */
public record ValidationResult(List<Violation> violations) {

    private static final ValidationResult VALID = new ValidationResult(List.of());

    public ValidationResult {
        violations = violations != null ? List.copyOf(violations) : List.of();
    }

    /**
     * Returns a singleton valid result with no violations.
     */
    public static ValidationResult valid() {
        return VALID;
    }

    /**
     * Creates a result with a single violation.
     *
     * @param field   the field that failed validation
     * @param message the human-readable violation message
     */
    public static ValidationResult invalid(String field, String message) {
        return new ValidationResult(List.of(new Violation(field, message)));
    }

    /**
     * Creates a result from multiple violations.
     *
     * @param violations the list of violations (must be non-null)
     */
    public static ValidationResult of(List<Violation> violations) {
        if (violations == null || violations.isEmpty()) return VALID;
        return new ValidationResult(violations);
    }

    /**
     * Returns {@code true} if there are no violations.
     */
    public boolean isValid() {
        return violations.isEmpty();
    }

    /**
     * Throws a {@link ValidationException} if this result is invalid.
     *
     * @throws ValidationException with field-level errors if not valid
     */
    public void throwIfInvalid() {
        if (!isValid()) {
            throw toException();
        }
    }

    /**
     * Converts this result to a {@link ValidationException}.
     * Caller is responsible for throwing it if needed.
     */
    public ValidationException toException() {
        Map<String, Object> errors = new java.util.LinkedHashMap<>();
        for (Violation v : violations) {
            errors.put(v.field(), v.message());
        }
        String summary = violations.size() == 1
            ? violations.get(0).message()
            : violations.size() + " validation error(s)";
        return new ValidationException(summary, errors);
    }

    /**
     * Merges this result with another, combining all violations.
     *
     * @param other the other result to merge
     * @return a new result containing violations from both
     */
    public ValidationResult and(ValidationResult other) {
        if (other == null || other.isValid()) return this;
        if (this.isValid()) return other;
        java.util.List<Violation> merged = new java.util.ArrayList<>(violations);
        merged.addAll(other.violations);
        return new ValidationResult(merged);
    }

    /**
     * Bridge factory to ease migration from the deprecated
     * {@code com.ghatana.platform.validation.ValidationError} hierarchy.
     *
     * <p>Maps each {@code ValidationError.getCode() + ": " + getPath()}
     * to a {@link Violation} field, and {@code getMessage()} to the violation
     * message. Callers should migrate to {@link #invalid(String, String)} or
     * {@link #of(List)} directly once the old {@code ValidationError} type
     * is removed.
     *
     * @param errors legacy validation errors (may be null / empty)
     * @return valid result if errors is empty, otherwise invalid
     */
    public static ValidationResult fromLegacyErrors(
            java.util.List<com.ghatana.platform.validation.ValidationError> errors) {
        if (errors == null || errors.isEmpty()) return VALID;
        java.util.List<Violation> violations = new java.util.ArrayList<>(errors.size());
        for (com.ghatana.platform.validation.ValidationError e : errors) {
            String field = e.getPath() != null ? e.getPath() : e.getCode();
            violations.add(new Violation(field, e.getMessage()));
        }
        return new ValidationResult(violations);
    }

    // ── Violation ─────────────────────────────────────────────────────────────

    /**
     * A single constraint violation — a field name and a human-readable message.
     */
    public record Violation(String field, String message) {

        public Violation {
            field = field != null ? field : "value";
            message = message != null ? message : "Validation failed";
        }

        /**
         * Creates a global violation not tied to a specific field.
         *
         * @param message the violation message
         */
        public static Violation global(String message) {
            return new Violation("*", message);
        }
    }
}
