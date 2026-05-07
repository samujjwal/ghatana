/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.integration.registry;

import com.ghatana.aep.registry.AgentRegistryContracts;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import com.ghatana.agent.catalog.CatalogRegistry;
import io.activej.promise.Promise;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapts a {@link CatalogRegistry} to the {@link AgentRegistryContracts} interface.
 *
 * <p>This adapter bridges the orchestrator's in-process agent catalog (via ServiceLoader
 * discovery) with the {@link AgentRegistryContracts} contract expected by
 * {@link com.ghatana.aep.engine.registry.AepCentralRegistryService}. All three contract
 * methods — list, get, and findByCapability — delegate synchronously to the
 * catalog registry and are lifted to {@link Promise} with {@link Promise#of}.
 *
 * <p>The catalog registry is populated at startup via {@link CatalogRegistry#discover()}.
 * This adapter is intentionally read-only: registration and deregistration operations
 * are handled by the in-memory map in {@code AepCentralRegistryService} itself.
 *
 * @doc.type class
 * @doc.purpose Adapts CatalogRegistry to AgentRegistryContracts for DI wiring
 * @doc.layer product
 * @doc.pattern Adapter
 * @see CatalogRegistry
 * @see AgentRegistryContracts
 */
public final class CatalogRegistryContractAdapter implements AgentRegistryContracts {

    private static final Logger log = LoggerFactory.getLogger(CatalogRegistryContractAdapter.class);

    private final CatalogRegistry catalogRegistry;

    /**
     * Creates an adapter wrapping the given catalog registry.
     *
     * @param catalogRegistry catalog registry populated via ServiceLoader discovery; non-null
     */
    public CatalogRegistryContractAdapter(CatalogRegistry catalogRegistry) {
        this.catalogRegistry = Objects.requireNonNull(catalogRegistry, "catalogRegistry");
    }

    /**
     * Lists all agent definitions from the merged catalog.
     *
     * @return promise of all catalog-registered agent entries
     */
    @Override
    public Promise<List<CatalogAgentEntry>> listAgents() {
        List<CatalogAgentEntry> all = new ArrayList<>(catalogRegistry.allDefinitions());
        log.debug("CatalogRegistryContractAdapter.listAgents: {} entries", all.size());
        return Promise.of(all);
    }

    /**
     * Retrieves a single agent definition by ID.
     *
     * @param agentId agent identifier
     * @return promise of optional catalog entry; empty when not found
     */
    @Override
    public Promise<Optional<CatalogAgentEntry>> getAgent(String agentId) {
        Objects.requireNonNull(agentId, "agentId");
        Optional<CatalogAgentEntry> result = catalogRegistry.findById(agentId);
        log.debug(
                "CatalogRegistryContractAdapter.getAgent({}): {}", agentId, result.isPresent() ? "found" : "not found");
        return Promise.of(result);
    }

    /**
     * Finds all agent definitions declaring a given capability.
     *
     * @param capability capability name to match
     * @return promise of list of matching catalog entries; empty list when none match
     */
    @Override
    public Promise<List<CatalogAgentEntry>> findByCapability(String capability) {
        Objects.requireNonNull(capability, "capability");
        List<CatalogAgentEntry> result = catalogRegistry.findByCapability(capability);
        log.debug("CatalogRegistryContractAdapter.findByCapability({}): {} entries", capability, result.size());
        return Promise.of(result);
    }
}
