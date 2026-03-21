/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory registry for kernel contracts with pluggable validation.
 *
 * <p>Contracts are registered after passing all applicable validators.
 * The registry is thread-safe and supports lookup by family, ID, or name.</p>
 *
 * @doc.type class
 * @doc.purpose Central registry for validated kernel contracts
 * @doc.layer core
 * @doc.pattern Registry
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public final class ContractRegistry {

    private final Map<String, KernelContract> contracts = new ConcurrentHashMap<>();
    private final List<ContractValidator> validators;

    /**
     * Creates a registry with the given validators.
     *
     * @param validators validation hooks applied on registration (may be empty)
     */
    public ContractRegistry(List<ContractValidator> validators) {
        this.validators = validators != null ? List.copyOf(validators) : List.of();
    }

    /**
     * Creates a registry with no external validators.
     */
    public ContractRegistry() {
        this(List.of());
    }

    /**
     * Registers a contract after validation.
     *
     * @param contract the contract to register
     * @throws ContractValidationException if any validator rejects the contract
     * @throws IllegalArgumentException if a contract with the same ID is already registered
     */
    public void register(KernelContract contract) {
        // Run built-in validation
        List<String> builtInErrors = contract.getValidationErrors();
        if (!builtInErrors.isEmpty()) {
            throw new ContractValidationException(contract.getContractId(), builtInErrors);
        }

        // Run pluggable validators
        for (ContractValidator validator : validators) {
            List<ContractFamily> applicable = validator.applicableFamilies();
            if (!applicable.isEmpty() && !applicable.contains(contract.getFamily())) {
                continue;
            }
            ContractValidator.ValidationResult result = validator.validate(contract);
            if (!result.valid()) {
                throw new ContractValidationException(contract.getContractId(), result.errors());
            }
        }

        KernelContract existing = contracts.putIfAbsent(contract.getContractId(), contract);
        if (existing != null) {
            throw new IllegalArgumentException(
                "Contract already registered: " + contract.getContractId());
        }
    }

    /**
     * Looks up a contract by ID.
     */
    public Optional<KernelContract> getById(String contractId) {
        return Optional.ofNullable(contracts.get(contractId));
    }

    /**
     * Returns all contracts of a given family.
     */
    public List<KernelContract> getByFamily(ContractFamily family) {
        return contracts.values().stream()
            .filter(c -> c.getFamily() == family)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns all registered contracts.
     */
    public List<KernelContract> getAll() {
        return List.copyOf(contracts.values());
    }

    /**
     * Removes a contract by ID. Returns true if it was present.
     */
    public boolean unregister(String contractId) {
        return contracts.remove(contractId) != null;
    }

    /**
     * Returns the number of registered contracts.
     */
    public int size() {
        return contracts.size();
    }
}
