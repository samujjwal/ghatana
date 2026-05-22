package com.ghatana.kernel.plugin;

import java.util.Map;
import java.util.Set;

/**
 * Registry for discovering plugin interaction handlers.
 *
 * <p>This interface mirrors {@link com.ghatana.kernel.interaction.ProductInteractionHandlerRegistry}
 * but for plugin interactions. It provides methods for discovering and retrieving handlers
 * by contract ID.</p>
 *
 * @doc.type interface
 * @doc.purpose Registry for plugin interaction handler discovery
 * @doc.layer kernel
 * @doc.pattern Registry
 */
public interface PluginInteractionHandlerRegistry {

    /**
     * Gets all registered handlers.
     *
     * @return map of contract ID to handler
     */
    Map<String, PluginInteractionHandler<?, ?>> allHandlers();

    /**
     * Gets all registered contract IDs.
     *
     * @return set of contract IDs
     */
    Set<String> registeredContractIds();

    /**
     * Checks if a handler is registered for a contract.
     *
     * @param contractId the contract ID
     * @return true if registered
     */
    boolean hasHandler(String contractId);

    /**
     * Gets a handler by contract ID.
     *
     * @param contractId the contract ID
     * @return the handler, or null if not found
     */
    PluginInteractionHandler<?, ?> getHandler(String contractId);
}
