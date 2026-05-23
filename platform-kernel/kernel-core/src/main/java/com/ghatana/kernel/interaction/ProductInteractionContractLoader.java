package com.ghatana.kernel.interaction;

import java.util.List;
import java.util.Map;

/**
 * Contract loader for ProductInteractionBroker registry.
 *
 * <p>Loads product interaction contracts from a source (file system, Data Cloud, etc.)
 * and provides them to the registry for broker initialization.</p>
 *
 * @doc.type interface
 * @doc.purpose Load product interaction contracts from external sources
 * @doc.layer kernel
 * @doc.pattern Loader
 */
public interface ProductInteractionContractLoader {

    /**
     * Loads all available contracts.
     *
     * @return map of contract ID to contract
     * @throws ContractLoadException if loading fails
     */
    Map<String, ProductInteractionContract> loadAll() throws ContractLoadException;

    /**
     * Loads contracts for a specific provider product.
     *
     * @param providerProductId the provider product ID
     * @return list of contracts for the provider
     * @throws ContractLoadException if loading fails
     */
    List<ProductInteractionContract> loadByProvider(String providerProductId) throws ContractLoadException;

    /**
     * Loads a specific contract by ID.
     *
     * @param contractId the contract ID
     * @return the contract, or null if not found
     * @throws ContractLoadException if loading fails
     */
    ProductInteractionContract loadById(String contractId) throws ContractLoadException;

    /**
     * Checks if contracts are available for loading.
     *
     * @return true if contracts are available, false otherwise
     */
    boolean isAvailable();

    /**
     * Exception thrown when contract loading fails.
     */
    class ContractLoadException extends Exception {
        public ContractLoadException(String message) {
            super(message);
        }

        public ContractLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
