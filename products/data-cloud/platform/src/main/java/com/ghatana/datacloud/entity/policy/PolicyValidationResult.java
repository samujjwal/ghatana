package com.ghatana.datacloud.entity.policy;

import java.util.List;
import java.util.Objects;

/**
 * Result of policy validation.
 *
 * <p><b>Purpose</b><br>
 * Represents the outcome of validating a policy definition (syntax, structure).
 * Used for pre-deployment validation of policies.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PolicyValidationResult valid = PolicyValidationResult.success();
 * PolicyValidationResult invalid = PolicyValidationResult.invalid(
 *     List.of("Line 5: Syntax error in condition", "Missing required field 'allow'")
 * );
 *
 * if (!result.isValid()) {
 *     result.getErrors().forEach(error -> logger.error("Policy error: {}", error));
 * }
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable value object - thread-safe.
 *
 * @doc.type record
 * @doc.purpose Policy validation result
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record PolicyValidationResult(
    boolean valid,
    List<String> errors
) {
    /**
     * Creates a policy validation result.
     *
     * @param valid whether the policy is valid
     * @param errors list of error messages (required, can be empty)
     * @throws NullPointerException if errors is null
     */
    public PolicyValidationResult {
        Objects.requireNonNull(errors, "Errors must not be null");
        errors = List.copyOf(errors);
    }

    /**
     * Checks if policy is valid.
     *
     * @return true if valid, false if errors present
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Gets validation errors.
     *
     * @return immutable error list (never null, may be empty)
     */
    public List<String> getErrors() {
        return errors;
    }

    /**
     * Creates a valid result.
     *
     * @return PolicyValidationResult with valid=true
     */
    public static PolicyValidationResult success() {
        return new PolicyValidationResult(true, List.of());
    }

    /**
     * Creates an invalid result with errors.
     *
     * @param errors the validation errors (required, must not be empty)
     * @return PolicyValidationResult with valid=false
     * @throws IllegalArgumentException if errors is empty
     */
    public static PolicyValidationResult invalid(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("Errors must not be empty for invalid result");
        }
        return new PolicyValidationResult(false, errors);
    }

    @Override
    public String toString() {
        return String.format("PolicyValidationResult{valid=%s, errors=%s}", valid, errors);
    }
}
