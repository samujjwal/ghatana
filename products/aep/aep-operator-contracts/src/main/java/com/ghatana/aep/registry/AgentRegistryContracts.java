package com.ghatana.aep.registry;

import com.ghatana.agent.catalog.CatalogAgentEntry;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Contract for AEP agent registry operations consumed by external products.
 *
 * <p>Decouples products (e.g. YAPPC) from the concrete {@code AepCentralRegistryService}
 * implementation, allowing them to depend only on this stable interface in
 * {@code aep-operator-contracts} rather than on {@code aep-central-runtime}.
 *
 * @doc.type interface
 * @doc.purpose Stable contract for agent registry discovery operations
 * @doc.layer api
 * @doc.pattern Contract, SPI
 */
public interface AgentRegistryContracts {

    /**
     * Lists all registered agent definitions from the merged catalog.
     *
     * @return promise of all agent entries
     */
    Promise<List<CatalogAgentEntry>> listAgents();

    /**
     * Retrieves a single agent definition by ID.
     *
     * @param agentId the agent identifier
     * @return promise of the agent entry, or empty if not found
     */
    Promise<Optional<CatalogAgentEntry>> getAgent(String agentId);

    /**
     * Finds agents matching the given capability.
     *
     * @param capability the capability to search for
     * @return promise of matching agent entries
     */
    Promise<List<CatalogAgentEntry>> findByCapability(String capability);
}
