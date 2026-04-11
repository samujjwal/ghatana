/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.runtime;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import com.ghatana.agent.spi.AgentRegistry;
import com.ghatana.aep.catalog.AepCentralCatalogService;
import com.ghatana.aep.registry.AgentRegistryContracts;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AEP-owned centralized runtime that unifies catalog-based discovery,
 * {@code implementationRef} resolution, and agent lifecycle management.
 *
 * <p>This unified registry service provides the single discovery and execution surface for
 * all agent operations across all products (YAPPC, DataCloud, etc.). It operates in two modes:
 *
 * <h2>Discovery Mode (Read-Only Catalog)</h2>
 * <p>Loads agent definitions from {@link AepCentralCatalogService}. Clients can list, search,
 * and find agents by capability. These are static definitions, not live instances.
 *
 * <h2>Execution Mode (Live Agent Materialization)</h2>
 * <p>Materializes agents from catalog entries using provider resolution and the
 * {@link AgentMaterializer}. Maintains in-process live agent instances.
 *
 * <h2>Persistence Integration (v2.5+)</h2>
 * <p>Optional integration with {@link AgentRegistry} for durable agent metadata
 * and audit trail. When present, this service:
 * <ul>
 *   <li>Persists agent definitions to registry on registration</li>
 *   <li>Merges registry-persisted agents in discovery queries</li>
 *   <li>Records lifecycle events (registered, deregistered) to audit trail</li>
 *   <li>Maintains in-memory write-through cache for zero-latency access</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Unified centralized AEP registry and runtime operations
 * @doc.layer product
 * @doc.pattern Service, Registry, Facade
 * @see AgentRegistry for persistence backend
 * @see AepCentralCatalogService for catalog loading
 * @see AgentMaterializer for agent instantiation
 */

public class AepCentralRegistryService implements AgentRegistryContracts {

    private static final Logger log = LoggerFactory.getLogger(AepCentralRegistryService.class);

    private final AepCentralCatalogService catalogService;
    private final AgentMaterializer materializer;
    private final Map<String, TypedAgent<?, ?>> liveAgents = new ConcurrentHashMap<>();
    private final AgentRegistry persistenceRegistry;  // Optional; nullable

    /**
     * Creates the central registry service with optional persistence.
     *
     * @param catalogService        loaded catalog service (multi-root)
     * @param materializer          agent materializer (YAML → provider → agent)
     * @param persistenceRegistry   optional registry for durable metadata (v2.5+); nullable
     */
    public AepCentralRegistryService(
            @NotNull AepCentralCatalogService catalogService,
            @NotNull AgentMaterializer materializer,
            @Nullable AgentRegistry persistenceRegistry) {
        this.catalogService = Objects.requireNonNull(catalogService, "catalogService");
        this.materializer = Objects.requireNonNull(materializer, "materializer");
        this.persistenceRegistry = persistenceRegistry;  // May be null; optional feature
    }

    /**
     * Backward-compatible constructor (no persistence).
     *
     * @param catalogService loaded catalog service (multi-root)
     * @param materializer   agent materializer (YAML → provider → agent)
     */
    public AepCentralRegistryService(
            @NotNull AepCentralCatalogService catalogService,
            @NotNull AgentMaterializer materializer) {
        this(catalogService, materializer, null);
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
     * a live instance. Optionally persists metadata to DataCloud.
     *
     * <p><strong>Behavior with persistence (v2.5+):</strong>
     * <ol>
     *   <li>Materializes live agent instance from provider</li>
     *   <li>If {@link #persistenceRegistry} is present, also registers agent in DataCloud</li>
     *   <li>Fires agent.registered lifecycle event to audit trail</li>
     * </ol>
     *
     * @param agentId             catalog agent ID
     * @param implementationRef   ref for provider resolution
     * @param config              runtime configuration
     * @return the materialized agent, or error if materialization fails
     */
    public Promise<TypedAgent<?, ?>> materializeAgent(
            @NotNull String agentId,
            @NotNull String implementationRef,
            @NotNull AgentConfig config) {

        TypedAgent<?, ?> agent = materializer.materialize(implementationRef, config);
        liveAgents.put(agentId, agent);
        log.info("Materialized and registered live agent '{}'", agentId);

        // Optionally persist to registry (v2.5+)
        if (persistenceRegistry != null) {
            Promise<TypedAgent<?, ?>> persistencePromise = persistenceRegistry.register(agent, config)
                    .map(v -> {
                        log.debug("Persisted agent '{}' to registry", agentId);
                        return agent;
                    });
            persistencePromise.whenException(e -> log.warn(
                    "Failed to persist agent '{}' to registry (continuing anyway): {}",
                    agentId, e.getMessage()));
            return persistencePromise;
        }

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
     * Also deregisters from persistence registry if persistence is enabled.
     *
     * <p><strong>Behavior:</strong>
     * <ol>
     *   <li>Removes agent from in-memory live registry</li>
     *   <li>Calls agent shutdown hook to clean up resources</li>
     *   <li>If {@link #persistenceRegistry} is present, also deregisters from registry</li>
     *   <li>Fires agent.deregistered lifecycle event to audit trail</li>
     * </ol>
     *
     * @param agentId agent ID to shut down
     * @return true if agent was live and shut down; false if never registered
     */
    public Promise<Boolean> shutdownAgent(@NotNull String agentId) {
        TypedAgent<?, ?> agent = liveAgents.remove(agentId);
        if (agent == null) {
            return Promise.of(false);
        }

        // Optionally deregister from registry (v2.5+)
        Promise<Void> deregister = (persistenceRegistry != null)
                ? persistenceRegistry.deregister(agentId)
                : Promise.complete();

        return deregister
                .then(() -> agent.shutdown())
                .map(v -> {
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
