/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.agent.integration;

import com.ghatana.agent.catalog.CatalogAgentEntry;
import com.ghatana.aep.registry.AgentRegistryContracts;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Bridge between the YAPPC product and the centralised AEP runtime.
 *
 * <p>Provides YAPPC-specific derived views (by SDLC phase, by step name)
 * on top of the AEP central catalog and registry, replacing the
 * product-local registry ownership that existed before v2.4.
 *
 * <p>YAPPC code that previously called product-local agent registries or
 * {@code YappcAgentCatalog} directly should migrate to this bridge, which
 * delegates all catalog/registry operations to AEP while preserving the
 * YAPPC-specific query patterns (phase-based lookup, step-name resolution).
 *
 * @doc.type class
 * @doc.purpose YAPPC→AEP integration bridge — derived views over central registry
 * @doc.layer product
 * @doc.pattern Adapter, Facade
 */
public final class YappcAepIntegration {

    private static final Logger log = LoggerFactory.getLogger(YappcAepIntegration.class);

    /** Only YAPPC-owned agents start with these prefixes. */
    private static final String YAPPC_AGENT_PREFIX = "agent.yappc.";
    private static final String YAPPC_CATALOG_ID = "yappc";

    private final AgentRegistryContracts registryService;

    /**
     * Creates the integration bridge.
     *
     * @param registryService the central agent registry contract
     */
    public YappcAepIntegration(
            @NotNull AgentRegistryContracts registryService) {
        this.registryService = Objects.requireNonNull(registryService, "registryService");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Derived views — replace product-local indexes
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Lists all YAPPC agent definitions by filtering the central catalog.
     *
     * @return Promise of YAPPC-specific agent entries
     */
    public Promise<List<CatalogAgentEntry>> listYappcAgents() {
        return registryService.listAgents()
                .map(all -> all.stream()
                        .filter(e -> isYappcOwned(e))
                        .collect(Collectors.toList()));
    }

    /**
     * Retrieves YAPPC agents grouped by SDLC phase.
     *
     * <p>Phases are extracted from the agent ID convention:
     * {@code agent.yappc.<phase>.<role>}, e.g. {@code agent.yappc.architecture.domain-modeler}.
     *
     * @return Promise of phase → agent entries map
     */
    public Promise<Map<String, List<CatalogAgentEntry>>> getAgentsByPhase() {
        return listYappcAgents()
                .map(agents -> agents.stream()
                        .collect(Collectors.groupingBy(
                                e -> extractPhase(e.getId()))));
    }

    /**
     * Finds YAPPC agents for a specific SDLC phase.
     *
     * @param phase the SDLC phase (e.g. "architecture", "implementation", "testing")
     * @return Promise of matching agent entries
     */
    public Promise<List<CatalogAgentEntry>> getAgentsForPhase(@NotNull String phase) {
        return listYappcAgents()
                .map(agents -> agents.stream()
                        .filter(e -> phase.equals(extractPhase(e.getId())))
                        .collect(Collectors.toList()));
    }

    /**
     * Resolves a single YAPPC agent definition by its step name.
     *
     * <p>Step names follow the convention {@code <phase>.<step>}, e.g.
     * {@code architecture.intake}. This method maps them to the catalog ID
     * pattern {@code agent.yappc.<phase>.<step>}.
     *
     * @param stepName the YAPPC step name (e.g. "architecture.intake")
     * @return Promise of the matching entry, or empty if not found
     */
    public Promise<Optional<CatalogAgentEntry>> resolveByStepName(@NotNull String stepName) {
        String expectedId = YAPPC_AGENT_PREFIX + stepName;
        return registryService.getAgent(expectedId);
    }

    /**
     * Finds YAPPC agents that advertise a given capability.
     *
     * @param capability the capability to search for
     * @return Promise of matching YAPPC agent entries
     */
    public Promise<List<CatalogAgentEntry>> findByCapability(@NotNull String capability) {
        return registryService.findByCapability(capability)
                .map(all -> all.stream()
                        .filter(this::isYappcOwned)
                        .collect(Collectors.toList()));
    }

    /**
     * Returns the total count of YAPPC agent definitions in the central catalog.
     *
     * @return Promise of the agent count
     */
    public Promise<Integer> yappcAgentCount() {
        return listYappcAgents().map(List::size);
    }

    /**
     * Returns all distinct SDLC phases that have YAPPC agents registered.
     *
     * @return Promise of the set of phase names
     */
    public Promise<Set<String>> getAllPhases() {
        return listYappcAgents()
                .map(agents -> agents.stream()
                        .map(e -> extractPhase(e.getId()))
                        .collect(Collectors.toSet()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Internals
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean isYappcOwned(CatalogAgentEntry entry) {
        return entry.getId() != null
                && entry.getId().startsWith(YAPPC_AGENT_PREFIX);
    }

    private static String extractPhase(String agentId) {
        if (agentId == null || !agentId.startsWith(YAPPC_AGENT_PREFIX)) {
            return "unknown";
        }
        String remainder = agentId.substring(YAPPC_AGENT_PREFIX.length());
        int dotIndex = remainder.indexOf('.');
        return dotIndex > 0 ? remainder.substring(0, dotIndex) : remainder;
    }
}
