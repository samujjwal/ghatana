/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry;

import com.ghatana.agent.framework.memory.MemoryNamespace;
import com.ghatana.agent.framework.memory.MemoryNamespaceRepository;
import com.ghatana.agent.framework.memory.MemoryScope;
import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.datacloud.entity.storage.QuerySpecInterface;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Data-Cloud-backed implementation of {@link MemoryNamespaceRepository}.
 *
 * <p>Each {@link MemoryNamespace} record is persisted as an entity in the
 * {@value #COLLECTION} Data-Cloud collection.
 *
 * <p>All public methods are ActiveJ async and must not block the event loop.
 *
 * @doc.type class
 * @doc.purpose Data-Cloud-backed persistence for agent memory namespaces
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class DataCloudMemoryNamespaceRepository implements MemoryNamespaceRepository {

    private static final Logger log = LoggerFactory.getLogger(DataCloudMemoryNamespaceRepository.class);

    /** Data-Cloud collection name for memory namespaces. */
    public static final String COLLECTION = "memory-namespaces";

    // ─── field name constants ──────────────────────────────────────────────────
    private static final String F_NAMESPACE_ID       = "namespaceId";
    private static final String F_TENANT_ID          = "tenantId";
    private static final String F_AGENT_ID           = "agentId";
    private static final String F_SCOPE              = "scope";
    private static final String F_LABEL              = "label";
    private static final String F_DESCRIPTION        = "description";
    private static final String F_RETENTION_DAYS     = "retentionDays";
    private static final String F_PROMOTION_ENABLED  = "promotionEnabled";
    private static final String F_MAX_ENTRIES        = "maxEntries";
    private static final String F_CREATED_AT         = "createdAt";
    private static final String F_UPDATED_AT         = "updatedAt";

    private final DataCloudClient dataCloud;
    private final String tenantId;

    /**
     * Creates a new repository instance.
     *
     * @param dataCloud the Data-Cloud client
     * @param tenantId  the tenant scope for all operations
     */
    public DataCloudMemoryNamespaceRepository(DataCloudClient dataCloud, String tenantId) {
        this.dataCloud = Objects.requireNonNull(dataCloud, "dataCloud");
        this.tenantId  = Objects.requireNonNull(tenantId, "tenantId");
    }

    // ─── MemoryNamespaceRepository ─────────────────────────────────────────────

    @Override
    public Promise<MemoryNamespace> save(MemoryNamespace namespace) {
        Objects.requireNonNull(namespace, "namespace");
        Map<String, Object> data = toDataMap(namespace);
        return dataCloud.createEntity(tenantId, COLLECTION, data)
                .map(entity -> {
                    log.debug("Saved MemoryNamespace [{}] agent={} scope={} entity={}",
                            namespace.namespaceId(), namespace.agentId(), namespace.scope(), entity.getId());
                    return namespace;
                });
    }

    @Override
    public Promise<Optional<MemoryNamespace>> findById(String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId");
        QuerySpecInterface query = buildFieldEqQuery(F_NAMESPACE_ID, namespaceId);
        return dataCloud.queryEntities(tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .findFirst()
                        .map(e -> fromDataMap(e.getData())));
    }

    @Override
    public Promise<List<MemoryNamespace>> findByAgent(String agentId, String tenantId) {
        Objects.requireNonNull(agentId, "agentId");
        QuerySpecInterface query = buildFieldEqQuery(F_AGENT_ID, agentId);
        return dataCloud.queryEntities(this.tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(e -> fromDataMap(e.getData()))
                        .collect(Collectors.toCollection(ArrayList::new)));
    }

    @Override
    public Promise<List<MemoryNamespace>> findPromotionEnabledByAgent(String agentId, String tenantId) {
        Objects.requireNonNull(agentId, "agentId");
        QuerySpecInterface query = buildTwoFieldEqQuery(
                F_AGENT_ID, agentId,
                F_PROMOTION_ENABLED, "true");
        return dataCloud.queryEntities(this.tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(e -> fromDataMap(e.getData()))
                        .filter(MemoryNamespace::promotionEnabled)
                        .collect(Collectors.toCollection(ArrayList::new)));
    }

    @Override
    public Promise<Optional<MemoryNamespace>> findByAgentAndScope(
            String agentId, MemoryScope scope, String tenantId) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(scope, "scope");
        QuerySpecInterface query = buildTwoFieldEqQuery(
                F_AGENT_ID, agentId,
                F_SCOPE, scope.name());
        return dataCloud.queryEntities(this.tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(e -> fromDataMap(e.getData()))
                        .findFirst());
    }

    @Override
    public Promise<Boolean> delete(String namespaceId, String tenantId) {
        Objects.requireNonNull(namespaceId, "namespaceId");
        QuerySpecInterface query = buildFieldEqQuery(F_NAMESPACE_ID, namespaceId);
        return dataCloud.queryEntities(this.tenantId, COLLECTION, query)
                .then(entities -> {
                    if (entities.isEmpty()) {
                        return Promise.of(false);
                    }
                    java.util.UUID entityId = entities.getFirst().getId();
                    return dataCloud.deleteEntity(this.tenantId, COLLECTION, entityId)
                            .map(ignored -> true);
                });
    }

    // ─── Serialization helpers ─────────────────────────────────────────────────

    private static Map<String, Object> toDataMap(MemoryNamespace ns) {
        Map<String, Object> m = new HashMap<>();
        m.put(F_NAMESPACE_ID,      ns.namespaceId());
        m.put(F_TENANT_ID,         ns.tenantId());
        m.put(F_AGENT_ID,          ns.agentId());
        m.put(F_SCOPE,             ns.scope().name());
        m.put(F_LABEL,             ns.label());
        m.put(F_PROMOTION_ENABLED, String.valueOf(ns.promotionEnabled()));
        m.put(F_CREATED_AT,        ns.createdAt().toString());
        m.put(F_UPDATED_AT,        ns.updatedAt().toString());
        if (ns.description() != null)   m.put(F_DESCRIPTION, ns.description());
        if (ns.retentionDays() != null) m.put(F_RETENTION_DAYS, String.valueOf(ns.retentionDays()));
        if (ns.maxEntries() != null)    m.put(F_MAX_ENTRIES, String.valueOf(ns.maxEntries()));
        if (!ns.data().isEmpty())       m.putAll(ns.data());
        return m;
    }

    private static MemoryNamespace fromDataMap(Map<String, Object> m) {
        Object rawRetention = m.get(F_RETENTION_DAYS);
        Integer retentionDays = rawRetention != null
                ? Integer.parseInt(String.valueOf(rawRetention))
                : null;
        Object rawMax = m.get(F_MAX_ENTRIES);
        Integer maxEntries = rawMax != null
                ? Integer.parseInt(String.valueOf(rawMax))
                : null;
        Object rawPromotion = m.get(F_PROMOTION_ENABLED);
        boolean promotionEnabled = rawPromotion instanceof Boolean b
                ? b : Boolean.parseBoolean(String.valueOf(rawPromotion));

        return new MemoryNamespace(
                str(m, F_NAMESPACE_ID),
                str(m, F_TENANT_ID),
                str(m, F_AGENT_ID),
                MemoryScope.valueOf(str(m, F_SCOPE)),
                str(m, F_LABEL),
                (String) m.get(F_DESCRIPTION),
                retentionDays,
                promotionEnabled,
                maxEntries,
                Instant.parse(str(m, F_CREATED_AT)),
                Instant.parse(str(m, F_UPDATED_AT)),
                Map.of()
        );
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? String.valueOf(v) : null;
    }

    private static QuerySpecInterface buildFieldEqQuery(String field, String value) {
        return filterSpec(field + " = '" + value + "'");
    }

    private static QuerySpecInterface buildTwoFieldEqQuery(
            String field1, String value1,
            String field2, String value2) {
        return filterSpec(field1 + " = '" + value1 + "' AND " + field2 + " = '" + value2 + "'");
    }

    private static QuerySpecInterface filterSpec(String filter) {
        return new QuerySpecInterface() {
            private String f = filter;
            private Integer limit = 1000;
            private Integer offset = 0;

            @Override public String getFilter() { return f; }
            @Override public void setFilter(String v) { this.f = v; }
            @Override public Integer getLimit() { return limit; }
            @Override public void setLimit(Integer v) { this.limit = v; }
            @Override public Integer getOffset() { return offset; }
            @Override public void setOffset(Integer v) { this.offset = v; }
            @Override public String getQueryType() { return "filter"; }
            @Override public void setQueryType(String v) { /* no-op */ }
        };
    }
}
