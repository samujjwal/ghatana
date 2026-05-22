package com.ghatana.kernel.interaction;

import java.util.Map;
import java.util.Set;

/**
 * Registry contract for discovering product interaction handlers.
 *
 * <p>The registry enables automatic handler discovery instead of manual builder registration.
 * Products implement this contract to expose their interaction handlers to the Kernel broker.
 * The registry can be backed by service discovery, configuration, or static registration.</p>
 *
 * @doc.type interface
 * @doc.purpose Registry contract for discovering product interaction handlers
 * @doc.layer kernel
 * @doc.pattern Registry
 */
public interface ProductInteractionHandlerRegistry {

    /**
     * Returns all registered product interaction handlers.
     *
     * <p>The returned map is keyed by contract ID. Each handler implements the
     * ProductInteractionHandler SPI for a specific contract.</p>
     *
     * @return map of contract ID → handler
     */
    Map<String, ProductInteractionHandler<?, ?>> allHandlers();

    /**
     * Returns the set of contract IDs for which handlers are registered.
     *
     * @return set of contract IDs
     */
    default Set<String> registeredContractIds() {
        return allHandlers().keySet();
    }

    /**
     * Checks if a handler is registered for the given contract ID.
     *
     * @param contractId the contract ID to check
     * @return true if a handler is registered, false otherwise
     */
    default boolean hasHandler(String contractId) {
        return allHandlers().containsKey(contractId);
    }

    /**
     * Returns the handler for the given contract ID, if registered.
     *
     * @param contractId the contract ID
     * @return the handler, or null if not registered
     */
    default ProductInteractionHandler<?, ?> getHandler(String contractId) {
        return allHandlers().get(contractId);
    }

    /**
     * Checks if the registry has no registered handlers.
     *
     * @return true if no handlers are registered, false otherwise
     */
    default boolean isEmpty() {
        return allHandlers().isEmpty();
    }

    /**
     * Returns the number of registered handlers.
     *
     * @return count of registered handlers
     */
    default int size() {
        return allHandlers().size();
    }
}
