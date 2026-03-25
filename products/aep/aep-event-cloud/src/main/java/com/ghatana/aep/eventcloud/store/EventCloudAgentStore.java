/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud.store;

import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.EntityStore.Entity;
import com.ghatana.datacloud.spi.EntityStore.EntityId;
import com.ghatana.datacloud.spi.EntityStore.QuerySpec;
import com.ghatana.datacloud.spi.TenantContext;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Agent registry store backed by Data-Cloud's {@link EntityStore}.
 *
 * <p>Stores agent definitions, configurations, and runtime state as entities
 * in Data-Cloud. Each agent is stored in the {@value #COLLECTION} collection
 * with tenant isolation enforced by the store.
 *
 * <h3>Collection Schema</h3>
 * <pre>
 * Collection : {@value #COLLECTION}
 * Entity ID  : agent UUID
 * Fields:
 *   id          : String   - agent unique identifier
 *   name        : String   - human-readable agent name
 *   type        : String   - agent taxonomy type (e.g., "REACTIVE", "DELIBERATIVE")
 *   status      : String   - lifecycle status (ACTIVE, PAUSED, DISABLED)
 *   version     : String   - agent version
 *   config      : Map      - agent configuration
 *   tenantId    : String   - tenant isolation key
 *   createdAt   : String   - ISO-8601 creation timestamp
 *   updatedAt   : String   - ISO-8601 update timestamp
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Agent registry store backed by Data-Cloud EntityStore
 * @doc.layer product
 * @doc.pattern Repository, Adapter
 * @doc.gaa.lifecycle perceive
 */
public final class EventCloudAgentStore {

    private static final Logger log = LoggerFactory.getLogger(EventCloudAgentStore.class);

    /** Data-Cloud collection name for agent storage. */
    public static final String COLLECTION = "aep_agents";

    private final EntityStore entityStore;

    public EventCloudAgentStore(EntityStore entityStore) {
        this.entityStore = Objects.requireNonNull(entityStore, "entityStore required");
    }

    /**
     * Registers or updates an agent.
     *
     * @param tenantId tenant identifier
     * @param agentId  agent unique identifier
     * @param data     agent data (name, type, config, etc.)
     * @return promise of the saved entity
     */
    public Promise<Entity> save(String tenantId, String agentId, Map<String, Object> data) {
        Map<String, Object> entityData = new HashMap<>(data);
        entityData.put("id", agentId);
        entityData.put("tenantId", tenantId);
        entityData.putIfAbsent("status", "ACTIVE");
        entityData.put("updatedAt", Instant.now().toString());
        entityData.putIfAbsent("createdAt", Instant.now().toString());

        Entity entity = new Entity(
            EntityId.of(agentId),
            COLLECTION,
            entityData,
            null);

        TenantContext tenant = TenantContext.of(tenantId);

        return entityStore.save(tenant, entity)
            .whenResult(saved ->
                log.debug("[agent-store] Saved agent={} tenant={}", agentId, tenantId))
            .whenException(e ->
                log.error("[agent-store] Save failed agent={} tenant={}: {}",
                    agentId, tenantId, e.getMessage(), e));
    }

    /**
     * Retrieves an agent by ID.
     *
     * @param tenantId tenant identifier
     * @param agentId  agent unique identifier
     * @return promise of the agent entity, or empty if not found
     */
    public Promise<Optional<Entity>> findById(String tenantId, String agentId) {
        TenantContext tenant = TenantContext.of(tenantId);
        return entityStore.findById(tenant, EntityId.of(agentId))
            .map(opt -> opt.filter(e -> COLLECTION.equals(e.collection())));
    }

    /**
     * Lists all agents for a tenant.
     *
     * @param tenantId tenant identifier
     * @param limit    maximum number of agents to return
     * @return promise of agent entities
     */
    public Promise<List<Entity>> listAgents(String tenantId, int limit) {
        TenantContext tenant = TenantContext.of(tenantId);
        QuerySpec query = QuerySpec.builder()
            .collection(COLLECTION)
            .limit(limit)
            .build();
        return entityStore.query(tenant, query)
            .map(result -> result.entities());
    }

    /**
     * Lists agents of a specific type.
     *
     * @param tenantId  tenant identifier
     * @param agentType agent type filter
     * @param limit     maximum number of agents to return
     * @return promise of matching agent entities
     */
    public Promise<List<Entity>> listByType(String tenantId, String agentType, int limit) {
        TenantContext tenant = TenantContext.of(tenantId);
        QuerySpec query = QuerySpec.builder()
            .collection(COLLECTION)
            .limit(limit)
            .build();
        return entityStore.query(tenant, query)
            .map(result -> result.entities().stream()
                .filter(e -> agentType.equals(e.data().get("type")))
                .toList());
    }

    /**
     * Deletes an agent.
     *
     * @param tenantId tenant identifier
     * @param agentId  agent unique identifier
     * @return promise completing when deleted
     */
    public Promise<Void> delete(String tenantId, String agentId) {
        TenantContext tenant = TenantContext.of(tenantId);
        return entityStore.delete(tenant, EntityId.of(agentId))
            .whenResult(v ->
                log.debug("[agent-store] Deleted agent={} tenant={}", agentId, tenantId))
            .whenException(e ->
                log.error("[agent-store] Delete failed agent={} tenant={}: {}",
                    agentId, tenantId, e.getMessage(), e));
    }

    /**
     * Counts agents for a tenant.
     *
     * @param tenantId tenant identifier
     * @return promise of agent count
     */
    public Promise<Long> count(String tenantId) {
        TenantContext tenant = TenantContext.of(tenantId);
        QuerySpec query = QuerySpec.builder()
            .collection(COLLECTION)
            .limit(Integer.MAX_VALUE)
            .build();
        return entityStore.count(tenant, query);
    }

    /**
     * Checks if an agent exists.
     *
     * @param tenantId tenant identifier
     * @param agentId  agent unique identifier
     * @return promise of existence check
     */
    public Promise<Boolean> exists(String tenantId, String agentId) {
        TenantContext tenant = TenantContext.of(tenantId);
        return entityStore.exists(tenant, EntityId.of(agentId));
    }
}
