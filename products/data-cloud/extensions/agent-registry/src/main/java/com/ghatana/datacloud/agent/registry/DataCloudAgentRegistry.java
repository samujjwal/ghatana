/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.datacloud.agent.registry;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.spi.AgentRegistry;
import com.ghatana.datacloud.agent.registry.event.RegistryEventPublisher;
import com.ghatana.datacloud.client.DataCloudClient;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Data-Cloud-backed implementation of {@link AgentRegistry}.
 *
 * <p><strong>Role (v2.5+):</strong> This class is the <em>durable persistence backend</em>
 * for agent metadata and lifecycle events. It is injected into {@code AepCentralRegistryService}
 * as an optional provider to handle persistence operations.
 *
 * <p>All agent descriptors and configurations are persisted to Data-Cloud for
 * durability and cross-instance discovery. An in-memory write-through cache
 * supports O(1) {@link #resolve(String)} and {@link #listAgentIds()} without
 * incurring round-trip I/O on the hot path.
 *
 * <h2>Storage Layout</h2>
 * <table border="1">
 *   <caption>Data-Cloud collections used by this registry</caption>
 *   <tr><th>Role</th><th>Collection</th><th>Data-Cloud Record</th></tr>
 *   <tr><td>Agent descriptors + configs</td><td>{@code agent-registry}</td><td>EntityRecord</td></tr>
 *   <tr><td>Registry lifecycle events</td><td>{@code agent-registry-events}</td><td>EventRecord (append-only)</td></tr>
 * </table>
 *
 * <h2>Consistency Model</h2>
 * <p>The in-memory cache is the source of truth for instance lifecycle (which
 * {@link TypedAgent} objects are currently running). Data-Cloud is the source
 * of truth for discovery queries ({@link #findByCapability(String)}) and
 * statistics ({@link #getStats()}). On process restart, agents must
 * re-register — the Data-Cloud data is used for audit and capability search
 * only, not for reconstructing live agent instances.
 *
 * <h2>Thread Safety</h2>
 * <p>All public methods are safe for concurrent access. The in-memory maps are
 * backed by {@link ConcurrentHashMap}. Data-Cloud writes are async and
 * non-blocking with respect to the local maps.
 *
 * <h2>Architecture (v2.5+)</h2>
 * <p>As of v2.5, this class is a <strong>persistence provider</strong> injected into
 * {@code AepCentralRegistryService}. The unified AEP runtime now:
 * <ul>
 *   <li>Handles all HTTP API requests via {@code AepAgentRegistryController}</li>
 *   <li>Manages catalog-based discovery via {@code AepCentralRegistryService}</li>
 *   <li>Delegates persistence to this provider with optional in-process calls</li>
 *   <li>Records all lifecycle events (agent.registered, agent.deregistered) to immutable audit trail</li>
 * </ul>
 *
 * <p>Products no longer expose their own registry HTTP endpoints. Agentic discovery and
 * runtime-facing operations route through AEP-owned APIs, while this provider remains the
 * Data-Cloud-backed persistence layer.
 *
 * @doc.type class
 * @doc.purpose Data-Cloud-backed agent registry with in-memory write-through cache and TTL eviction
 * @doc.layer registry
 * @doc.pattern Repository, Cache-Aside, Provider
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 * @see com.ghatana.aep.runtime.AepCentralRegistryService (v2.5+ primary consumer)
 */
public final class DataCloudAgentRegistry implements AgentRegistry, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DataCloudAgentRegistry.class);

    // ── Data-Cloud collection names ────────────────────────────────────────────
    static final String REGISTRY_COLLECTION = "agent-registry";

    /** Default in-memory cache TTL: 24 hours. Entries not accessed within this window are evicted. */
    public static final long DEFAULT_CACHE_TTL_MS = TimeUnit.HOURS.toMillis(24);
    /** Default maximum number of agents kept in the in-memory cache (LRU eviction above this). */
    public static final int DEFAULT_MAX_CACHE_SIZE = 10_000;
    /** Background eviction interval: 5 minutes. */
    private static final long EVICTION_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5);

    // ── In-memory write-through cache ─────────────────────────────────────────
    private final ConcurrentHashMap<String, TypedAgent<?, ?>> agents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> entityIds = new ConcurrentHashMap<>();   // agentId → Data-Cloud UUID
    private final ConcurrentHashMap<String, AgentConfig> configs = new ConcurrentHashMap<>();
    /** Tracks the last-access time (millis) for each cached entry to support TTL eviction. */
    private final ConcurrentHashMap<String, Long> lastAccessMs = new ConcurrentHashMap<>();

    // ── Infrastructure ────────────────────────────────────────────────────────
    private final DataCloudClient dataCloud;
    private final String registryTenantId;
    private final RegistryEventPublisher eventPublisher;
    private final long cacheTtlMs;
    private final int maxCacheSize;
    private final ScheduledExecutorService evictionScheduler;

    /**
     * Constructs a registry backed by the given Data-Cloud client.
     *
     * @param dataCloud        Data-Cloud client for entity persistence
     * @param registryTenantId tenant ID used for all registry data
     *                         (typically {@code "platform"})
     */
    public DataCloudAgentRegistry(@NotNull DataCloudClient dataCloud,
                                   @NotNull String registryTenantId) {
        this(dataCloud, registryTenantId, DEFAULT_CACHE_TTL_MS, DEFAULT_MAX_CACHE_SIZE);
    }

    /**
     * Constructs a registry with a custom in-memory cache TTL.
     *
     * @param dataCloud        Data-Cloud client for entity persistence
     * @param registryTenantId tenant ID used for all registry data
     * @param cacheTtlMs       TTL in milliseconds for in-memory cache entries; use 0 to disable TTL eviction
     */
    public DataCloudAgentRegistry(@NotNull DataCloudClient dataCloud,
                                   @NotNull String registryTenantId,
                                   long cacheTtlMs) {
        this(dataCloud, registryTenantId, cacheTtlMs, DEFAULT_MAX_CACHE_SIZE);
    }

    /**
     * Constructs a registry with custom TTL and max cache size (LRU + TTL hybrid eviction).
     *
     * <p>When {@code maxCacheSize} is exceeded, the least-recently-used entries are evicted
     * first (based on last-access timestamp). TTL eviction then removes entries that have
     * not been accessed within {@code cacheTtlMs}, regardless of cache size.
     *
     * @param dataCloud        Data-Cloud client for entity persistence
     * @param registryTenantId tenant ID used for all registry data
     * @param cacheTtlMs       TTL in milliseconds; use 0 to disable TTL eviction
     * @param maxCacheSize     maximum number of in-memory entries; LRU eviction triggers above this
     */
    public DataCloudAgentRegistry(@NotNull DataCloudClient dataCloud,
                                   @NotNull String registryTenantId,
                                   long cacheTtlMs,
                                   int maxCacheSize) {
        this.dataCloud = Objects.requireNonNull(dataCloud, "dataCloud");
        this.registryTenantId = Objects.requireNonNull(registryTenantId, "registryTenantId");
        this.eventPublisher = new RegistryEventPublisher(dataCloud, registryTenantId);
        this.cacheTtlMs = cacheTtlMs;
        this.maxCacheSize = maxCacheSize > 0 ? maxCacheSize : DEFAULT_MAX_CACHE_SIZE;
        if (cacheTtlMs > 0 || maxCacheSize > 0) {
            this.evictionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "agent-registry-eviction");
                t.setDaemon(true);
                return t;
            });
            this.evictionScheduler.scheduleAtFixedRate(
                this::evictExpiredEntries,
                EVICTION_INTERVAL_MS,
                EVICTION_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );
        } else {
            this.evictionScheduler = null;
        }
    }

    // ── AgentRegistry ─────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>The agent descriptor is serialised to a {@link Map} and saved as an
     * {@code EntityRecord} in the {@code agent-registry} collection. The
     * returned Data-Cloud UUID is cached locally so that {@link #deregister}
     * can locate and delete the record without a round-trip query. A
     * {@code agent.registered} event is fired to the registry event stream
     * as a fire-and-forget side effect.
     */
    @NotNull
    @Override
    public Promise<Void> register(@NotNull TypedAgent<?, ?> agent,
                                  @NotNull AgentConfig config) {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(config, "config");

        AgentDescriptor descriptor = agent.descriptor();
        String agentId = descriptor.getAgentId();
        Map<String, Object> data = toDataMap(descriptor, config);

        return dataCloud.createEntity(registryTenantId, REGISTRY_COLLECTION, data)
                .then(entity -> {
                    // Populate cache after successful persist
                    long now = System.currentTimeMillis();
                    entityIds.put(agentId, entity.getId());
                    agents.put(agentId, agent);
                    configs.put(agentId, config);
                    lastAccessMs.put(agentId, now);
                    log.info("Registered agent [{}] v{} in DataCloudAgentRegistry (entity={})",
                            agentId, descriptor.getVersion(), entity.getId());

                    // Fire-and-forget lifecycle event (never block user response)
                    eventPublisher.publishAgentRegistered(agentId, descriptor.getName(), descriptor.getVersion())
                            .whenException(e -> log.warn("Failed to publish agent.registered event for [{}]: {}",
                                    agentId, e.getMessage()));
                    return Promise.<Void>complete();
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Removes the descriptor from Data-Cloud and the local cache. Silently
     * succeeds if the agent is not registered.
     */
    @NotNull
    @Override
    public Promise<Void> deregister(@NotNull String agentId) {
        Objects.requireNonNull(agentId, "agentId");

        UUID entityId = entityIds.remove(agentId);
        agents.remove(agentId);
        configs.remove(agentId);
        lastAccessMs.remove(agentId);

        if (entityId == null) {
            log.debug("deregister [{}] — not found in registry, nothing to do", agentId);
            return Promise.complete();
        }

        return dataCloud.deleteEntity(registryTenantId, REGISTRY_COLLECTION, entityId)
                .then(v -> {
                    log.info("Deregistered agent [{}] from DataCloudAgentRegistry", agentId);

                    // Fire-and-forget lifecycle event
                    eventPublisher.publishAgentDeregistered(agentId)
                            .whenException(e -> log.warn("Failed to publish agent.deregistered event for [{}]: {}",
                                    agentId, e.getMessage()));
                    return Promise.<Void>complete();
                })
                .whenException(e ->
                        log.warn("Failed to delete agent [{}] entity {} from Data-Cloud (already removed?): {}",
                                agentId, entityId, e.getMessage()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the live agent instance from the in-memory cache. Agents that
     * appear in Data-Cloud but were not registered in this JVM instance cannot
     * be resolved — their descriptor exists only for discovery purposes.
     */
    @NotNull
    @Override
    @SuppressWarnings("unchecked")
    public <I, O> Promise<Optional<TypedAgent<I, O>>> resolve(@NotNull String agentId) {
        Objects.requireNonNull(agentId, "agentId");
        TypedAgent<I, O> agent = (TypedAgent<I, O>) agents.get(agentId);
        if (agent != null) {
            lastAccessMs.put(agentId, System.currentTimeMillis()); // refresh TTL on access
        }
        return Promise.of(Optional.ofNullable(agent));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns a snapshot of registered agent IDs from the in-memory cache.
     */
    @NotNull
    @Override
    public Promise<Set<String>> listAgentIds() {
        return Promise.of(Set.copyOf(agents.keySet()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Filters the in-memory cache by calling
     * {@link AgentDescriptor#hasCapability(String)} on each registered agent's
     * descriptor. This is O(n) in the number of registered agents and is
     * therefore suitable for registries with up to a few thousand agents.
     */
    @NotNull
    @Override
    public Promise<List<String>> findByCapability(@NotNull String capability) {
        Objects.requireNonNull(capability, "capability");
        List<String> matched = agents.entrySet().stream()
                .filter(e -> e.getValue().descriptor().hasCapability(capability))
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
        return Promise.of(matched);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Combines the in-memory cache size (live agents) with the persisted
     * count from Data-Cloud (total agents ever registered in this tenant).
     */
    @NotNull
    @Override
    public Promise<Map<String, Object>> getStats() {
        return dataCloud.countEntities(registryTenantId, REGISTRY_COLLECTION, null)
                .map(persistedCount -> {
                    Map<String, Object> stats = new LinkedHashMap<>();
                    stats.put("registeredAgents", agents.size());
                    stats.put("persistedAgents", persistedCount);
                    stats.put("registryType", "DataCloud");
                    stats.put("registryTenantId", registryTenantId);
                    return Collections.unmodifiableMap(stats);
                });
    }

    // ── Cache TTL eviction ─────────────────────────────────────────────────────

    /**
     * Evicts cache entries based on a hybrid LRU + TTL strategy:
     * <ol>
     *   <li><b>TTL pass</b>: removes entries whose last-access time exceeds {@link #cacheTtlMs}
     *       (when TTL eviction is enabled).</li>
     *   <li><b>LRU pass</b>: if the cache remains over {@link #maxCacheSize} after the TTL
     *       pass, the least-recently-used entries (oldest {@link #lastAccessMs}) are removed
     *       until the cache fits within the limit.</li>
     * </ol>
     * Called periodically by the background eviction scheduler.
     */
    private void evictExpiredEntries() {
        int evicted = 0;

        // ── TTL pass ────────────────────────────────────────────────────────────
        if (cacheTtlMs > 0) {
            long cutoff = System.currentTimeMillis() - cacheTtlMs;
            for (Map.Entry<String, Long> entry : lastAccessMs.entrySet()) {
                if (entry.getValue() < cutoff) {
                    String agentId = entry.getKey();
                    agents.remove(agentId);
                    configs.remove(agentId);
                    // entityIds intentionally kept for deregister() to avoid a round-trip
                    lastAccessMs.remove(agentId);
                    evicted++;
                    log.debug("TTL-evicted agent [{}] from in-memory cache", agentId);
                }
            }
        }

        // ── LRU pass — trim to maxCacheSize ────────────────────────────────────
        int overBy = agents.size() - maxCacheSize;
        if (overBy > 0) {
            // Sort all cached agents by last-access ascending (oldest first = LRU)
            List<Map.Entry<String, Long>> sortedByAccess = new ArrayList<>(lastAccessMs.entrySet());
            sortedByAccess.sort(Map.Entry.comparingByValue());

            for (int i = 0; i < overBy && i < sortedByAccess.size(); i++) {
                String agentId = sortedByAccess.get(i).getKey();
                agents.remove(agentId);
                configs.remove(agentId);
                lastAccessMs.remove(agentId);
                evicted++;
                log.debug("LRU-evicted agent [{}] from in-memory cache (cache over maxSize={})", agentId, maxCacheSize);
            }
        }

        if (evicted > 0) {
            log.info("Cache eviction cycle completed: {} entry(-ies) evicted (cacheSize={}/{})",
                evicted, agents.size(), maxCacheSize);
        }
    }

    /**
     * Shuts down the background eviction scheduler.
     * Should be called when the registry is no longer needed to prevent resource leaks.
     */
    @Override
    public void close() {
        if (evictionScheduler != null) {
            evictionScheduler.shutdown();
            try {
                if (!evictionScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    evictionScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                evictionScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Package-private — test use only.
     *
     * <p>Runs one eviction cycle immediately without waiting for the background
     * scheduler.  Allows tests to verify LRU and TTL eviction behaviour without
     * sleeping for {@value #EVICTION_INTERVAL_MS} ms.
     */
    void triggerEvictionForTesting() {
        evictExpiredEntries();
    }

    /**
     * Returns the number of agents currently held in the in-memory cache.
     * Intended for observability and tests.
     */
    public int getCacheSize() {
        return agents.size();
    }

    // ── Serialisation helper ───────────────────────────────────────────────────

    /**
     * Converts an {@link AgentDescriptor} and {@link AgentConfig} to a flat
     * {@link Map} suitable for storage as a Data-Cloud entity payload.
     */
    private static Map<String, Object> toDataMap(AgentDescriptor descriptor,
                                                  AgentConfig config) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agentId", descriptor.getAgentId());
        data.put("name", descriptor.getName());
        data.put("version", descriptor.getVersion());
        data.put("description", descriptor.getDescription());
        data.put("namespace", descriptor.getNamespace());
        data.put("type", descriptor.getType() != null ? descriptor.getType().name() : null);
        data.put("subtype", descriptor.getSubtype());
        data.put("determinism", descriptor.getDeterminism() != null ? descriptor.getDeterminism().name() : null);
        data.put("latencySlaMs", descriptor.getLatencySla() != null ? descriptor.getLatencySla().toMillis() : null);
        data.put("throughputTarget", descriptor.getThroughputTarget());
        data.put("stateMutability", descriptor.getStateMutability() != null ? descriptor.getStateMutability().name() : null);
        data.put("failureMode", descriptor.getFailureMode() != null ? descriptor.getFailureMode().name() : null);
        data.put("capabilities", descriptor.getCapabilities() != null
                ? new ArrayList<>(descriptor.getCapabilities()) : List.of());
        data.put("inputEventTypes", descriptor.getInputEventTypes() != null
                ? new ArrayList<>(descriptor.getInputEventTypes()) : List.of());
        data.put("outputEventTypes", descriptor.getOutputEventTypes() != null
                ? new ArrayList<>(descriptor.getOutputEventTypes()) : List.of());
        data.put("metadata", descriptor.getMetadata() != null ? descriptor.getMetadata() : Map.of());
        data.put("labels", descriptor.getLabels() != null ? descriptor.getLabels() : Map.of());
        data.put("annotations", descriptor.getAnnotations() != null ? descriptor.getAnnotations() : Map.of());

        // Config summary (not the full config — avoid storing sensitive properties)
        data.put("configVersion", config.getVersion());
        data.put("configType", config.getType() != null ? config.getType().name() : null);
        data.put("configFailureMode", config.getFailureMode() != null ? config.getFailureMode().name() : null);

        data.put("registeredAt", Instant.now().toString());
        return data;
    }
}
