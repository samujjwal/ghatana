package com.ghatana.platform.schema;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Result of a schema validation operation.
 *
 * <p>Construct instances via the {@link #valid()} or {@link #failure} factory methods.
 *
 * @doc.type class
 * @doc.purpose Carries the outcome of JSON payload validation against a schema
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class ValidationResult {

    private final boolean valid;
    private final List<ValidationError> errors;

    private ValidationResult(boolean valid, List<ValidationError> errors) {
        this.valid = valid;
        this.errors = List.copyOf(errors);
    }

    // ─── Factories ────────────────────────────────────────────────────────────

    /** Returns a successful (valid) result with no errors. */
    @NotNull
    public static ValidationResult valid() {
        return new ValidationResult(true, List.of());
    }

    /**
     * Returns a failed result with a single error at the given field path.
     *
     * @param fieldPath    JSON Pointer path to the offending field (e.g. {@code "/address/zip"})
     * @param errorMessage human-readable description of the error
     */
    @NotNull
    public static ValidationResult failure(@NotNull String fieldPath, @NotNull String errorMessage) {
        return new ValidationResult(false, List.of(new ValidationError(fieldPath, errorMessage)));
    }

    /**
     * Returns a failed result with multiple errors.
     *
     * @param errors list of validation errors
     */
    @NotNull
    public static ValidationResult failure(@NotNull List<ValidationError> errors) {
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("errors must not be empty for a failure result");
        }
        return new ValidationResult(false, errors);
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    /** Returns {@code true} if the payload conformed to the schema. */
    public boolean isValid() {
        return valid;
    }

    /** Returns the list of validation errors (empty for valid results). */
    @NotNull
    public List<ValidationError> errors() {
        return errors;
    }

    /**
     * Returns the first error field path, or {@code null} if this is a valid result.
     * Convenience accessor for single-error cases.
     */
    @Nullable
    public String firstErrorPath() {
        return errors.isEmpty() ? null : errors.get(0).fieldPath();
    }

    /**
     * Returns the first error message, or {@code null} if this is a valid result.
     * Convenience accessor for single-error cases.
     */
    @Nullable
    public String firstErrorMessage() {
        return errors.isEmpty() ? null : errors.get(0).errorMessage();
    }

    @Override
    public String toString() {
        return valid ? "ValidationResult{valid}" :
                "ValidationResult{invalid, errors=" + errors.size() + "}";
    }

    // ─── Inner Types ──────────────────────────────────────────────────────────

    /**
     * A single field-level validation error.
     *
     * @param fieldPath    JSON Pointer path to the failing field
     * @param errorMessage human-readable error description
     */
    public record ValidationError(
            @NotNull String fieldPath,
            @NotNull String errorMessage
    ) {
        public ValidationError {
            Objects.requireNonNull(fieldPath, "fieldPath must not be null");
            Objects.requireNonNull(errorMessage, "errorMessage must not be null");
        }
    }
}
