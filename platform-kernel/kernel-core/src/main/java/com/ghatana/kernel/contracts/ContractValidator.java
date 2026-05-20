/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts;

import com.ghatana.platform.core.validation.ValidationResult;
import com.ghatana.platform.core.validation.ValidationResult.Violation;

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

    List<KernelContract.ContractFamily> ALL_CONTRACT_FAMILIES = List.of();

    /**
     * Validation result: either valid or carrying error messages.
     */
    record ValidationResult(com.ghatana.platform.core.validation.ValidationResult coreResult) {

        public ValidationResult {
            Objects.requireNonNull(coreResult, "coreResult");
        }

        /** Successful validation. */
        public static final ValidationResult OK = new ValidationResult(com.ghatana.platform.core.validation.ValidationResult.valid());

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
            return coreResult.violations().stream()
                .map(Violation::message)
                .collect(Collectors.toUnmodifiableList());
        }

        /**
         * Returns the canonical typed validation errors backing this result.
         */
        public List<Violation> getErrors() {
            return coreResult.violations();
        }

        /**
         * Returns the canonical core validation result.
         */
        public com.ghatana.platform.core.validation.ValidationResult toCoreValidationResult() {
            return coreResult;
        }

        /**
         * Returns a failed result with the given errors.
         */
        public static ValidationResult failed(List<String> errors) {
            List<Violation> violations = errors.stream()
                .map(error -> new Violation("CONTRACT_VALIDATION_FAILED", error))
                .collect(Collectors.toList());
            return new ValidationResult(com.ghatana.platform.core.validation.ValidationResult.of(violations));
        }

        /**
         * Returns a failed result with a single error message.
         */
        public static ValidationResult failed(String error) {
            return new ValidationResult(com.ghatana.platform.core.validation.ValidationResult.invalid("CONTRACT_VALIDATION_FAILED", error));
        }

        /**
         * Returns a failed result with typed validation errors.
         */
        public static ValidationResult failed(Violation... errors) {
            return new ValidationResult(com.ghatana.platform.core.validation.ValidationResult.of(List.of(errors)));
        }

        /**
         * Returns a failed result with typed validation errors.
         */
        public static ValidationResult fromValidationErrors(List<Violation> errors) {
            return new ValidationResult(com.ghatana.platform.core.validation.ValidationResult.of(errors));
        }

        /**
         * Creates a kernel validation result from the canonical core validation result.
         */
        public static ValidationResult fromCoreValidationResult(
                com.ghatana.platform.core.validation.ValidationResult coreResult) {
            return new ValidationResult(coreResult);
        }

        private static Violation toValidationError(String error) {
            return new Violation("CONTRACT_VALIDATION_FAILED", error);
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
        return ALL_CONTRACT_FAMILIES;
    }
}
