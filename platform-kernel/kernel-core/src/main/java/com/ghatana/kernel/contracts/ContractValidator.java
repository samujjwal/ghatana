/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts;

import com.ghatana.platform.validation.ValidationError;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Validation hook for kernel contracts.
 *
 * <p>Implementations perform family-specific or cross-cutting validation
 * on contracts before they are registered. Multiple validators can be
 * composed in a chain.</p>
 *
 * @doc.type interface
 * @doc.purpose Pluggable validation hook for contract registration
 * @doc.layer core
 * @doc.pattern Strategy
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public interface ContractValidator {

    /**
     * Validation result: either valid or carrying error messages.
     */
    record ValidationResult(com.ghatana.platform.validation.ValidationResult coreResult) {

        public ValidationResult {
            Objects.requireNonNull(coreResult, "coreResult");
        }

        /** Successful validation. */
        public static final ValidationResult OK = new ValidationResult(
            com.ghatana.platform.validation.ValidationResult.success());

        /**
         * Compatibility accessor preserving the historical record shape.
         */
        public boolean valid() {
            return coreResult.isValid();
        }

        /**
         * Compatibility accessor preserving the historical string-only error view.
         */
        public List<String> errors() {
            return getErrors().stream()
                .map(ValidationError::getMessage)
                .collect(Collectors.toUnmodifiableList());
        }

        /**
         * Returns the canonical typed validation errors backing this result.
         */
        public List<ValidationError> getErrors() {
            return coreResult.getErrors();
        }

        /**
         * Returns the canonical core validation result.
         */
        public com.ghatana.platform.validation.ValidationResult toCoreValidationResult() {
            return coreResult;
        }

        /**
         * Returns a failed result with the given errors.
         */
        public static ValidationResult failed(List<String> errors) {
            return fromValidationErrors(errors.stream()
                .map(ValidationResult::toValidationError)
                .collect(Collectors.toUnmodifiableList()));
        }

        /**
         * Returns a failed result with a single error message.
         */
        public static ValidationResult failed(String error) {
            return failed(toValidationError(error));
        }

        /**
         * Returns a failed result with typed validation errors.
         */
        public static ValidationResult failed(ValidationError... errors) {
            return new ValidationResult(com.ghatana.platform.validation.ValidationResult.failure(errors));
        }

        /**
         * Returns a failed result with typed validation errors.
         */
        public static ValidationResult fromValidationErrors(List<ValidationError> errors) {
            return new ValidationResult(com.ghatana.platform.validation.ValidationResult.failure(errors));
        }

        /**
         * Creates a kernel validation result from the canonical core validation result.
         */
        public static ValidationResult fromCoreValidationResult(
                com.ghatana.platform.validation.ValidationResult coreResult) {
            return new ValidationResult(coreResult);
        }

        private static ValidationError toValidationError(String error) {
            return new ValidationError("CONTRACT_VALIDATION_FAILED", error);
        }
    }

    /**
     * Validates the given contract.
     *
     * @param contract the contract to validate
     * @return validation result (never null)
     */
    ValidationResult validate(KernelContract contract);

    /**
     * Returns the contract families this validator applies to, or empty for all families.
     */
    default List<KernelContract.ContractFamily> applicableFamilies() {
        return List.of();
    }
}
