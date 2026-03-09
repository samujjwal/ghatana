/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.registry.datacloud;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.TypedAgent;
import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.datacloud.entity.storage.QuerySpecInterface;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Agent registry implementation backed by Data-Cloud for persistence.
 *
 * <p>Stores agent descriptors, capability indices, and execution metadata
 * exclusively in Data-Cloud collections. An in-memory cache provides fast
 * lookups; the cache is populated at startup from Data-Cloud and kept
 * consistent via write-through semantics.
 *
 * <h2>Data-Cloud Collections</h2>
 * <table>
 *   <tr><th>Collection</th><th>Content</th></tr>
 *   <tr><td>{@code agent-registry}</td><td>Agent descriptors (EntityRecord)</td></tr>
 *   <tr><td>{@code agent-capabilities}</td><td>Capability index (EntityRecord)</td></tr>
 *   <tr><td>{@code agent-executions}</td><td>Execution history (EventRecord)</td></tr>
 * </table>
 *
 * @doc.type class
 * @doc.purpose Data-Cloud-backed agent registry
 * @doc.layer registry
 * @doc.pattern Repository, Write-Through Cache
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public class DataCloudAgentRegistry implements com.ghatana.agent.registry.AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(DataCloudAgentRegistry.class);

    private static final String REGISTRY_COLLECTION = "agent-registry";
    private static final String CAPABILITY_COLLECTION = "agent-capabilities";
    private static final String EXECUTION_COLLECTION = "agent-executions";

    private final DataCloudClient dataCloud;
    private final String tenantId;

    // Write-through in-memory cache
    private final ConcurrentHashMap<String, RegistryEntry> cache = new ConcurrentHashMap<>();

    public DataCloudAgentRegistry(@NotNull DataCloudClient dataCloud,
                                   @NotNull String tenantId) {
        this.dataCloud = Objects.requireNonNull(dataCloud, "dataCloud");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Registration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Registers an agent with its descriptor and config.
     *
     * @param agent  the typed agent to register
     * @param config the agent configuration
     * @return a Promise completing when registration is persisted
     */
    @Override
    @NotNull
    public Promise<Void> register(@NotNull TypedAgent<?, ?> agent,
                                   @NotNull AgentConfig config) {
        AgentDescriptor descriptor = agent.descriptor();
        String agentId = descriptor.getAgentId();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agentId", agentId);
        data.put("name", descriptor.getName());
        data.put("version", descriptor.getVersion());
        data.put("capabilities", new ArrayList<>(descriptor.getCapabilities()));
        data.put("agentType", descriptor.getType() != null
                ? descriptor.getType().name() : "UNKNOWN");
        data.put("registeredAt", Instant.now().toString());
        data.put("status", "ACTIVE");

        return dataCloud.createEntity(tenantId, REGISTRY_COLLECTION, data)
                .then(entity -> indexCapabilities(agentId, descriptor.getCapabilities()))
                .map($ -> {
                    cache.put(agentId, new RegistryEntry(agent, config, Instant.now()));
                    log.info("Registered agent '{}' in Data-Cloud registry", agentId);
                    return (Void) null;
                });
    }

    /**
     * Deregisters an agent by ID.
     */
    @Override
    @NotNull
    public Promise<Void> deregister(@NotNull String agentId) {
        cache.remove(agentId);

        RegistryQuery query = new RegistryQuery();
        query.setFilters(Map.of("agentId", agentId));
        query.setLimit(1);

        return dataCloud.queryEntities(tenantId, REGISTRY_COLLECTION, query)
                .then(entities -> {
                    if (entities.isEmpty()) {
                        return Promise.complete();
                    }
                    return dataCloud.deleteEntity(tenantId, REGISTRY_COLLECTION, entities.get(0).getId());
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Discovery
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Resolves a registered agent by ID.
     */
    @Override
    @NotNull
    @SuppressWarnings("unchecked")
    public <I, O> Promise<Optional<TypedAgent<I, O>>> resolve(@NotNull String agentId) {
        RegistryEntry entry = cache.get(agentId);
        if (entry != null) {
            return Promise.of(Optional.of((TypedAgent<I, O>) entry.agent()));
        }
        // Fallback: not in cache means not locally registered
        return Promise.of(Optional.empty());
    }

    /**
     * Returns all registered agent IDs.
     */
    @Override
    @NotNull
    public Promise<Set<String>> listAgentIds() {
        return Promise.of(Collections.unmodifiableSet(cache.keySet()));
    }

    /**
     * Searches for agents by capability using Data-Cloud capability index.
     */
    @Override
    @NotNull
    public Promise<List<String>> findByCapability(@NotNull String capability) {
        RegistryQuery query = new RegistryQuery();
        query.setFilters(Map.of("capability", capability));
        query.setLimit(100);

        return dataCloud.queryEntities(tenantId, CAPABILITY_COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(e -> (String) e.getData().get("agentId"))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
    }

    /**
     * Returns registry statistics.
     */
    @Override
    @NotNull
    public Promise<Map<String, Object>> getStats() {
        return dataCloud.countEntities(tenantId, REGISTRY_COLLECTION, null)
                .map(count -> Map.of(
                        "totalRegistered", count,
                        "cachedLocally", (long) cache.size(),
                        "tenantId", tenantId
                ));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Capability Indexing
    // ═══════════════════════════════════════════════════════════════════════════

    private Promise<Void> indexCapabilities(String agentId, Set<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return Promise.complete();
        }

        List<Promise<Void>> indexOps = capabilities.stream()
                .map(cap -> {
                    Map<String, Object> capData = Map.of(
                            "agentId", agentId,
                            "capability", cap,
                            "indexedAt", Instant.now().toString()
                    );
                    return dataCloud.createEntity(tenantId, CAPABILITY_COLLECTION, capData)
                            .map(e -> (Void) null);
                })
                .collect(Collectors.toList());

        return io.activej.promise.Promises.toList(indexOps).map($ -> null);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Inner types
    // ═══════════════════════════════════════════════════════════════════════════

    private record RegistryEntry(TypedAgent<?, ?> agent, AgentConfig config, Instant registeredAt) {}

    private static class RegistryQuery implements QuerySpecInterface {
        private Map<String, Object> filters = Map.of();
        private List<String> sortFields = List.of();
        private Integer limit = 100;
        private Integer offset = 0;
        private String queryType;
        private String filter;

        public Map<String, Object> getFilters() { return filters; }
        public void setFilters(Map<String, Object> filters) { this.filters = filters; }
        public List<String> getSortFields() { return sortFields; }
        public void setSortFields(List<String> sortFields) { this.sortFields = sortFields; }
        @Override public Integer getLimit() { return limit; }
        @Override public void setLimit(Integer limit) { this.limit = limit; }
        @Override public Integer getOffset() { return offset; }
        @Override public void setOffset(Integer offset) { this.offset = offset; }
        @Override public String getQueryType() { return queryType; }
        @Override public void setQueryType(String queryType) { this.queryType = queryType; }
        @Override public String getFilter() { return filter; }
        @Override public void setFilter(String filter) { this.filter = filter; }
    }
}
