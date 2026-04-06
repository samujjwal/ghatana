/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts;

import java.util.List;

/**
 * Thrown when a contract fails validation during registration.
 *
 * @doc.type class
 * @doc.purpose Exception for contract validation failures
 * @doc.layer core
 * @doc.pattern Exception
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public final class ContractValidationException extends RuntimeException {

    private final String contractId;
    private final List<String> errors;

    /**
     * Creates a new validation exception.
     *
     * @param contractId the contract that failed validation
     * @param errors the validation errors
     */
    public ContractValidationException(String contractId, List<String> errors) {
        super("Contract validation failed for '" + contractId + "': " + errors);
        this.contractId = contractId;
        this.errors = List.copyOf(errors);
    }

    public String getContractId() { return contractId; }
    public List<String> getErrors() { return errors; }
}
