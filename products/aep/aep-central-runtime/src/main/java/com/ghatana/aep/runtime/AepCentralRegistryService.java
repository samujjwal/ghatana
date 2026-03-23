/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.runtime;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import com.ghatana.agent.spi.AgentLogicProviderRegistry;
import com.ghatana.aep.catalog.AepCentralCatalogService;
import com.ghatana.aep.registry.AgentRegistryContracts;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AEP-owned centralized runtime that unifies catalog-based discovery,
 * {@code implementationRef} resolution, and agent lifecycle management.
 *
 * <p>This replaces product-local registries (YAPPC {@code YAPPCAgentRegistry},
 * Data Cloud {@code DataCloudAgentRegistry}) with a single surface that:
 * <ul>
 *   <li>Loads catalogs via {@link AepCentralCatalogService}</li>
 *   <li>Resolves providers via {@link AgentLogicProviderRegistry}</li>
 *   <li>Materializes agents via {@link AgentMaterializer}</li>
 *   <li>Manages agent lifecycle: register, instantiate, health, shutdown</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Centralized AEP registry and runtime operations
 * @doc.layer product
 * @doc.pattern Service, Registry
 */
public class AepCentralRegistryService implements AgentRegistryContracts {

    private static final Logger log = LoggerFactory.getLogger(AepCentralRegistryService.class);

    private final AepCentralCatalogService catalogService;
    private final AgentMaterializer materializer;
    private final Map<String, TypedAgent<?, ?>> liveAgents = new ConcurrentHashMap<>();

    /**
     * Creates the central registry service.
     *
     * @param catalogService  loaded catalog service (multi-root)
     * @param materializer    agent materializer (YAML → provider → agent)
     */
    public AepCentralRegistryService(
            AepCentralCatalogService catalogService,
            AgentMaterializer materializer) {
        this.catalogService = Objects.requireNonNull(catalogService);
        this.materializer = Objects.requireNonNull(materializer);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Agent Discovery (read-only, catalog-backed)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Lists all registered agent definitions from the merged catalog.
     */
    public Promise<List<CatalogAgentEntry>> listAgents() {
        return Promise.of(new ArrayList<>(catalogService.getRegistry().allDefinitions()));
    }

    /**
     * Retrieves a single agent definition by ID.
     */
    public Promise<Optional<CatalogAgentEntry>> getAgent(String agentId) {
        return Promise.of(catalogService.getRegistry().findById(agentId));
    }

    /**
     * Finds agents matching the given capability.
     */
    public Promise<List<CatalogAgentEntry>> findByCapability(String capability) {
        return Promise.of(catalogService.getRegistry().findByCapability(capability));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Agent Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Materializes an agent from its catalog entry, using the
     * {@code implementationRef} to resolve the provider and create
     * a live instance.
     *
     * @param agentId             catalog agent ID
     * @param implementationRef   ref for provider resolution
     * @param config              runtime configuration
     * @return the materialized agent
     */
    public Promise<TypedAgent<?, ?>> materializeAgent(
            String agentId,
            String implementationRef,
            AgentConfig config) {

        TypedAgent<?, ?> agent = materializer.materialize(implementationRef, config);
        liveAgents.put(agentId, agent);
        log.info("Materialized and registered live agent '{}'", agentId);
        return Promise.of(agent);
    }

    /**
     * Returns a live agent instance by ID, or empty if not materialized.
     */
    public Promise<Optional<TypedAgent<?, ?>>> getLiveAgent(String agentId) {
        return Promise.of(Optional.ofNullable(liveAgents.get(agentId)));
    }

    /**
     * Shuts down a live agent and removes it from the registry.
     */
    public Promise<Boolean> shutdownAgent(String agentId) {
        TypedAgent<?, ?> agent = liveAgents.remove(agentId);
        if (agent == null) {
            return Promise.of(false);
        }
        return agent.shutdown().map(v -> {
            log.info("Shut down agent '{}'", agentId);
            return true;
        });
    }

    /**
     * Returns health status for a live agent.
     */
    public Promise<Boolean> isAgentHealthy(String agentId) {
        TypedAgent<?, ?> agent = liveAgents.get(agentId);
        if (agent == null) {
            return Promise.of(false);
        }
        return agent.healthCheck()
                .map(status -> status == com.ghatana.agent.HealthStatus.HEALTHY
                        || status == com.ghatana.agent.HealthStatus.DEGRADED);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Runtime Stats
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the number of live agent instances.
     */
    public int liveAgentCount() {
        return liveAgents.size();
    }

    /**
     * Returns IDs of all live agents.
     */
    public Set<String> liveAgentIds() {
        return Collections.unmodifiableSet(liveAgents.keySet());
    }

    /**
     * Whether the materializer can resolve the given implementation ref.
     */
    public boolean canMaterialize(String implementationRef) {
        return materializer.canMaterialize(implementationRef);
    }
}
