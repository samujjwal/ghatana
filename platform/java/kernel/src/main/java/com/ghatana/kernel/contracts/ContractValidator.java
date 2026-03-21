/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts;

import java.util.List;

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
    record ValidationResult(boolean valid, List<String> errors) {

        /** Successful validation. */
        public static final ValidationResult OK = new ValidationResult(true, List.of());

        /**
         * Returns a failed result with the given errors.
         */
        public static ValidationResult failed(List<String> errors) {
            return new ValidationResult(false, List.copyOf(errors));
        }

        /**
         * Returns a failed result with a single error message.
         */
        public static ValidationResult failed(String error) {
            return new ValidationResult(false, List.of(error));
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
    default List<ContractFamily> applicableFamilies() {
        return List.of();
    }
}
