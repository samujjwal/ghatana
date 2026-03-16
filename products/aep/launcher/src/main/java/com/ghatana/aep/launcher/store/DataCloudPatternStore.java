/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.launcher.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Filter;
import com.ghatana.datacloud.DataCloudClient.Query;
import com.ghatana.pattern.api.model.PatternMetadata;
import com.ghatana.pattern.api.model.PatternSpecification;
import com.ghatana.pattern.api.model.PatternStatus;
import com.ghatana.pattern.storage.PatternRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-Cloud EntityStore-backed implementation of {@link PatternRepository}.
 *
 * <p>All pattern data is persisted in the Data-Cloud {@value #COLLECTION} collection
 * under the pattern's tenant, enabling multi-tenant isolation by design. Each entity
 * in Data-Cloud represents one pattern revision; the entity ID is the pattern UUID.
 *
 * <h3>Collection Schema</h3>
 * <pre>
 * Collection : {@value #COLLECTION}
 * Entity ID  : UUID (pattern ID) — stored in data map under key "id"
 * Fields:
 *   id          : String   — UUID string, used as entity key
 *   tenantId    : String   — tenant isolation key
 *   name        : String   — human-readable pattern name
 *   description : String   — optional description
 *   version     : int      — monotonically increasing version
 *   status      : String   — PatternStatus enum name
 *   priority    : int      — execution priority
 *   labels      : String[] — searchable tags
 *   eventTypes  : String[] — event-type identifiers triggering this pattern
 *   spec        : Object   — serialized PatternSpecification (full definition)
 *   createdAt   : String   — ISO-8601 timestamp
 *   updatedAt   : String   — ISO-8601 timestamp
 *   activatedAt : String   — ISO-8601 timestamp (nullable)
 *   compiledAt  : String   — ISO-8601 timestamp (nullable)
 * </pre>
 *
 * <h3>ID Strategy</h3>
 * <p>The Data-Cloud {@code save()} API assigns the entity ID from {@code data.get("id")}
 * when present. This store always populates that field, giving us stable UUIDs.
 *
 * <h3>Data-Cloud Usage</h3>
 * <p>All I/O is wrapped in {@link Promise} using the Data-Cloud async client so the
 * ActiveJ event loop is never blocked.
 *
 * @doc.type class
 * @doc.purpose Data-Cloud EntityStore-backed PatternRepository for durable, multi-tenant pattern storage
 * @doc.layer product
 * @doc.pattern Repository, Adapter
 * @doc.gaa.memory procedural
 */
public final class DataCloudPatternStore implements PatternRepository {

    private static final Logger log = LoggerFactory.getLogger(DataCloudPatternStore.class);

    /** Data-Cloud collection name for AEP pattern storage. */
    public static final String COLLECTION = "aep_patterns";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final DataCloudClient client;

    /**
     * Constructs a new store backed by the given Data-Cloud client.
     *
     * @param client Data-Cloud client wired to the correct tenant/server; must not be {@code null}
     */
    public DataCloudPatternStore(DataCloudClient client) {
        this.client = java.util.Objects.requireNonNull(client, "DataCloudClient must not be null");
    }

    // =========================================================================
    // PatternRepository — CRUD
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Generates a new {@link UUID} if the spec has no ID. Persists the full spec
     * as a nested {@code spec} field and indexes key queryable fields at the top level.
     */
    @Override
    public Promise<PatternMetadata> save(PatternSpecification spec) {
        UUID id = spec.getId() != null ? spec.getId() : UUID.randomUUID();
        String tenantId = spec.getTenantId();
        Map<String, Object> data = toEntityData(id, spec);
        // DataCloudClient.save(tenantId, collection, data) uses data.get("id") as the entity key.
        return client.save(tenantId, COLLECTION, data)
                .map(this::toMetadata)
                .whenException(e ->
                    log.error("[pattern-store] save failed id={} tenant={}: {}", id, tenantId, e.getMessage(), e));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses the "system" tenant scope for cross-tenant reads; callers that need
     * strict tenant isolation should use {@link #findByTenantAndId}.
     */
    @Override
    public Promise<Optional<PatternMetadata>> findById(UUID id) {
        return client.findById("system", COLLECTION, id.toString())
                .map(optEntity -> optEntity.map(this::toMetadata))
                .whenException(e ->
                    log.error("[pattern-store] findById failed id={}: {}", id, e.getMessage(), e));
    }

    /**
     * Finds a pattern by ID within a specific tenant, enforcing tenant isolation.
     *
     * @param tenantId tenant scope
     * @param id       pattern UUID
     * @return the pattern metadata if found within that tenant
     */
    public Promise<Optional<PatternMetadata>> findByTenantAndId(String tenantId, UUID id) {
        return client.findById(tenantId, COLLECTION, id.toString())
                .map(optEntity -> optEntity
                    .filter(e -> tenantId.equals(e.data().get("tenantId")))
                    .map(this::toMetadata))
                .whenException(e ->
                    log.error("[pattern-store] findByTenantAndId failed id={} tenant={}: {}",
                            id, tenantId, e.getMessage(), e));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Queries Data-Cloud for all entities in the tenant's pattern collection,
     * optionally filtered by {@code status}.
     */
    @Override
    public Promise<List<PatternMetadata>> findByTenant(String tenantId, PatternStatus status) {
        List<Filter> filters = new ArrayList<>();
        if (status != null) {
            filters.add(Filter.eq("status", status.name()));
        }
        Query query = filters.isEmpty()
                ? Query.limit(10_000)
                : Query.builder().filters(filters).limit(10_000).build();

        return client.query(tenantId, COLLECTION, query)
                .map(entities -> entities.stream().map(this::toMetadata).toList())
                .whenException(e ->
                    log.error("[pattern-store] findByTenant failed tenant={}: {}", tenantId, e.getMessage(), e));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<List<PatternMetadata>> findByTenantAndName(String tenantId, String name) {
        Query query = Query.builder()
                .filter(Filter.eq("name", name))
                .limit(100)
                .build();
        return client.query(tenantId, COLLECTION, query)
                .map(entities -> entities.stream().map(this::toMetadata).toList())
                .whenException(e ->
                    log.error("[pattern-store] findByTenantAndName failed tenant={} name={}: {}",
                            tenantId, name, e.getMessage(), e));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<PatternMetadata> updatePattern(UUID id, PatternSpecification newSpec) {
        String tenantId = newSpec.getTenantId();
        Map<String, Object> data = toEntityData(id, newSpec);
        return client.save(tenantId, COLLECTION, data)
                .map(this::toMetadata)
                .whenException(e ->
                    log.error("[pattern-store] updatePattern failed id={}: {}", id, e.getMessage(), e));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implements read-modify-write: reads the existing entity, updates the
     * {@code status} (and optionally {@code activatedAt}) field, then re-saves.
     * Data-Cloud does not support partial field updates.
     */
    @Override
    public Promise<Void> updateStatus(UUID id, PatternStatus status) {
        return client.findById("system", COLLECTION, id.toString())
                .then(optEntity -> {
                    if (optEntity.isEmpty()) {
                        return Promise.ofException(
                                new IllegalArgumentException("Pattern not found: " + id));
                    }
                    Entity existing = optEntity.get();
                    String tenantId = (String) existing.data().getOrDefault("tenantId", "system");
                    Map<String, Object> updated = new HashMap<>(existing.data());
                    updated.put("status", status.name());
                    updated.put("updatedAt", Instant.now().toString());
                    if (status == PatternStatus.ACTIVE) {
                        updated.put("activatedAt", Instant.now().toString());
                    }
                    // id is already inside updated (from the original data map)
                    return client.save(tenantId, COLLECTION, updated)
                            .map(ignored -> (Void) null);
                })
                .whenException(e ->
                    log.error("[pattern-store] updateStatus failed id={}: {}", id, e.getMessage(), e));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Void> delete(UUID id) {
        return client.delete("system", COLLECTION, id.toString())
                .map(ignored -> (Void) null)
                .whenException(e ->
                    log.error("[pattern-store] delete failed id={}: {}", id, e.getMessage(), e));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Boolean> exists(UUID id) {
        return client.findById("system", COLLECTION, id.toString())
                .map(Optional::isPresent)
                .whenException(e ->
                    log.error("[pattern-store] exists failed id={}: {}", id, e.getMessage(), e));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Long> countByTenant(String tenantId, PatternStatus status) {
        return findByTenant(tenantId, status)
                .map(list -> (long) list.size());
    }

    /** {@inheritDoc} */
    @Override
    public Promise<List<PatternMetadata>> findByEventType(
            String tenantId, String eventType, PatternStatus status) {
        List<Filter> filters = new ArrayList<>();
        if (status != null) {
            filters.add(Filter.eq("status", status.name()));
        }
        Query query = filters.isEmpty()
                ? Query.limit(10_000)
                : Query.builder().filters(filters).limit(10_000).build();

        return client.query(tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                    .map(this::toMetadata)
                    .filter(m -> m.getEventTypes() != null && m.getEventTypes().contains(eventType))
                    .toList())
                .whenException(e ->
                    log.error("[pattern-store] findByEventType failed tenant={} type={}: {}",
                            tenantId, eventType, e.getMessage(), e));
    }

    // =========================================================================
    // Mapping helpers
    // =========================================================================

    /**
     * Converts a {@link PatternSpecification} to a Data-Cloud entity data map.
     *
     * <p>The entity's UUID is stored under the {@code "id"} key so that
     * {@code DataCloudClient.save()} uses it as the entity key (upsert semantics).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toEntityData(UUID id, PatternSpecification spec) {
        Map<String, Object> data = new HashMap<>();
        data.put("id",          id.toString());
        data.put("tenantId",    safeStr(spec.getTenantId(), "system"));
        data.put("name",        safeStr(spec.getName(), ""));
        data.put("description", safeStr(spec.getDescription(), ""));
        data.put("version",     spec.getVersion());
        data.put("status",      spec.getStatus() != null ? spec.getStatus().name() : PatternStatus.DRAFT.name());
        data.put("priority",    spec.getPriority());
        data.put("labels",      spec.getLabels() != null ? spec.getLabels() : List.of());
        data.put("eventTypes",  spec.getEventTypes() != null ? spec.getEventTypes() : List.of());
        data.put("createdAt",   Instant.now().toString());
        data.put("updatedAt",   Instant.now().toString());

        // Persist full spec as nested document for reconstructability
        try {
            Map<String, Object> specMap = MAPPER.convertValue(spec, MAP_TYPE);
            data.put("spec", specMap);
        } catch (Exception e) {
            log.warn("[pattern-store] Could not serialize spec for id={}: {}", id, e.getMessage());
        }
        return data;
    }

    /** Converts a Data-Cloud {@link Entity} to {@link PatternMetadata}. */
    private PatternMetadata toMetadata(Entity entity) {
        Map<String, Object> d = entity.data();
        PatternMetadata.Builder builder = PatternMetadata.builder()
                .id(parseUuid(d.get("id"), entity.id()))
                .tenantId(safeStr(d.get("tenantId"), "system"))
                .name(safeStr(d.get("name"), ""))
                .description(safeStr(d.get("description"), ""))
                .version(parseInt(d.get("version"), 1))
                .status(parseStatus(d.get("status")))
                .priority(parseInt(d.get("priority"), 0))
                .labels(parseStringList(d.get("labels")))
                .eventTypes(parseStringList(d.get("eventTypes")))
                .createdAt(parseInstant(d.get("createdAt")))
                .updatedAt(parseInstant(d.get("updatedAt")));

        if (d.containsKey("activatedAt")) {
            builder.activatedAt(parseInstant(d.get("activatedAt")));
        }
        if (d.containsKey("compiledAt")) {
            builder.compiledAt(parseInstant(d.get("compiledAt")));
        }
        return builder.build();
    }

    // ── Type-safe field extractors ────────────────────────────────────────────

    private static String safeStr(Object value, String fallback) {
        return value instanceof String s ? s : fallback;
    }

    private static int parseInt(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try { return value != null ? Integer.parseInt(value.toString()) : fallback; }
        catch (NumberFormatException e) { return fallback; }
    }

    private static UUID parseUuid(Object value, String fallback) {
        try {
            String s = value instanceof String str ? str : fallback;
            return UUID.fromString(s);
        } catch (Exception e) {
            return UUID.randomUUID();
        }
    }

    private static Instant parseInstant(Object value) {
        if (value == null) return Instant.now();
        try { return Instant.parse(value.toString()); }
        catch (Exception e) { return Instant.now(); }
    }

    private static PatternStatus parseStatus(Object value) {
        if (value == null) return PatternStatus.DRAFT;
        try { return PatternStatus.valueOf(value.toString()); }
        catch (Exception e) { return PatternStatus.DRAFT; }
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item instanceof String)
                    .map(item -> (String) item)
                    .toList();
        }
        return List.of();
    }
}
