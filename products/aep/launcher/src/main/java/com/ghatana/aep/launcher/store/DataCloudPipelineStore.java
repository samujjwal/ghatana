/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.launcher.store;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Filter;
import com.ghatana.datacloud.DataCloudClient.Query;
import com.ghatana.pipeline.registry.model.Pipeline;
import com.ghatana.pipeline.registry.repository.PipelineRepository;
import com.ghatana.platform.core.common.pagination.Page;
import com.ghatana.platform.domain.auth.TenantId;
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
import java.util.UUID;

/**
 * Data-Cloud EntityStore-backed implementation of {@link PipelineRepository}.
 *
 * <p>Replaces the in-memory {@code InMemoryPipelineRepository} with durable, distributed
 * storage backed by Data-Cloud. Each pipeline is stored as an entity in the
 * {@value #COLLECTION} collection, tenant-isolated by the Data-Cloud client's tenant context.
 *
 * <h3>Collection Schema</h3>
 * <pre>
 * Collection : {@value #COLLECTION}
 * Entity ID  : pipeline UUID — stored in data map under key "id"
 * Fields:
 *   id          : String   — pipeline unique identifier, used as entity key
 *   tenantId    : String   — tenant isolation key
 *   name        : String   — human-readable pipeline name
 *   description : String   — optional description
 *   version     : int      — monotonically increasing version
 *   active      : boolean  — whether the pipeline is enabled
 *   config      : String   — JSON configuration (legacy format)
 *   createdAt   : String   — ISO-8601 creation timestamp
 *   updatedAt   : String   — ISO-8601 update timestamp
 *   createdBy   : String   — identity of creator
 *   updatedBy   : String   — identity of last updater
 * </pre>
 *
 * <h3>Design Notes</h3>
 * <ul>
 *   <li>All Data-Cloud I/O is already async; no {@code Promise.ofBlocking} wrapping is needed.</li>
 *   <li>Pagination is implemented in-memory after fetching from Data-Cloud (acceptable at
 *       current scale; a native cursor API can be introduced later).</li>
 *   <li>Soft-delete is supported by marking {@code active=false}; hard-delete uses
 *       {@link DataCloudClient#delete}.</li>
 *   <li>The entity ID is written into the data map under key {@code "id"} so that
 *       {@code DataCloudClient.save()} uses it as the entity key (upsert semantics).</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Data-Cloud EntityStore-backed PipelineRepository for durable, multi-tenant pipeline storage
 * @doc.layer product
 * @doc.pattern Repository, Adapter
 */
public final class DataCloudPipelineStore implements PipelineRepository {

    private static final Logger log = LoggerFactory.getLogger(DataCloudPipelineStore.class);

    /** Data-Cloud collection name for AEP pipeline storage. */
    public static final String COLLECTION = "aep_pipelines";

    private final DataCloudClient client;

    /**
     * Constructs a new store backed by the given Data-Cloud client.
     *
     * @param client Data-Cloud client; must not be {@code null}
     */
    public DataCloudPipelineStore(DataCloudClient client) {
        this.client = Objects.requireNonNull(client, "DataCloudClient must not be null");
    }

    // =========================================================================
    // PipelineRepository — CRUD
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Generates a new UUID if the pipeline has none. Persists the pipeline as a
     * Data-Cloud entity and returns the saved domain object.
     */
    @Override
    public Promise<Pipeline> save(Pipeline pipeline) {
        if (pipeline.getId() == null || pipeline.getId().isBlank()) {
            pipeline.setId(UUID.randomUUID().toString());
        }
        String tenantId = tenantStr(pipeline.getTenantId());
        Map<String, Object> data = toEntityData(pipeline);
        // data already contains "id" → DataCloudClient uses it as the entity key
        return client.save(tenantId, COLLECTION, data)
                .map(this::fromEntity)
                .whenException(e ->
                    log.error("[pipeline-store] save failed id={} tenant={}: {}",
                            pipeline.getId(), tenantId, e.getMessage(), e));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Optional<Pipeline>> findById(String id, TenantId tenantId) {
        String tenant = tenantStr(tenantId);
        return client.findById(tenant, COLLECTION, id)
                .map(optEntity -> optEntity
                    .filter(e -> tenant.equals(e.data().get("tenantId")))
                    .map(this::fromEntity))
                .whenException(e ->
                    log.error("[pipeline-store] findById failed id={} tenant={}: {}",
                            id, tenant, e.getMessage(), e));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Optional<Pipeline>> findLatestVersion(String name, TenantId tenantId) {
        String tenant = tenantStr(tenantId);
        Query query = Query.builder()
                .filter(Filter.eq("name", name))
                .limit(1000)
                .build();
        return client.query(tenant, COLLECTION, query)
                .map(entities -> entities.stream()
                    .map(this::fromEntity)
                    .max(java.util.Comparator.comparingInt(Pipeline::getVersion)))
                .whenException(e ->
                    log.error("[pipeline-store] findLatestVersion failed name={} tenant={}: {}",
                            name, tenant, e.getMessage(), e));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Optional<Pipeline>> findByNameAndVersion(String name, int version, TenantId tenantId) {
        String tenant = tenantStr(tenantId);
        Query query = Query.builder()
                .filters(List.of(
                    Filter.eq("name", name),
                    Filter.eq("version", String.valueOf(version))
                ))
                .limit(10)
                .build();
        return client.query(tenant, COLLECTION, query)
                .map(entities -> entities.stream().map(this::fromEntity).findFirst())
                .whenException(e ->
                    log.error("[pipeline-store] findByNameAndVersion failed name={} v={} tenant={}: {}",
                            name, version, tenant, e.getMessage(), e));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Boolean> exists(String id, TenantId tenantId) {
        return findById(id, tenantId).map(Optional::isPresent);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Soft-delete sets {@code active=false} and updates the audit fields via a
     * read-modify-write cycle. Hard-delete removes the entity permanently.
     */
    @Override
    public Promise<Void> delete(String id, TenantId tenantId, boolean hardDelete, String deletedBy) {
        String tenant = tenantStr(tenantId);
        if (hardDelete) {
            return client.delete(tenant, COLLECTION, id)
                    .map(ignored -> (Void) null)
                    .whenException(e ->
                        log.error("[pipeline-store] hard-delete failed id={} tenant={}: {}",
                                id, tenant, e.getMessage(), e));
        }
        // Soft delete — mark active=false via read-modify-write
        return client.findById(tenant, COLLECTION, id)
                .then(optEntity -> {
                    if (optEntity.isEmpty()) {
                        return Promise.ofException(
                                new IllegalArgumentException("Pipeline not found: " + id));
                    }
                    Map<String, Object> updated = new HashMap<>(optEntity.get().data());
                    updated.put("id", id); // ensure id is present for upsert
                    updated.put("active", false);
                    updated.put("updatedAt", Instant.now().toString());
                    updated.put("updatedBy", deletedBy != null ? deletedBy : "system");
                    return client.save(tenant, COLLECTION, updated).map(ignored -> (Void) null);
                })
                .whenException(e ->
                    log.error("[pipeline-store] soft-delete failed id={} tenant={}: {}",
                            id, tenant, e.getMessage(), e));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Page<Pipeline>> findAll(TenantId tenantId, String nameFilter,
                                           Boolean activeOnly, int page, int size) {
        String tenant = tenantStr(tenantId);
        List<Filter> filters = new ArrayList<>();
        if (activeOnly != null && activeOnly) {
            filters.add(Filter.eq("active", "true"));
        }

        Query query = filters.isEmpty()
                ? Query.limit(10_000)
                : Query.builder().filters(filters).limit(10_000).build();

        return client.query(tenant, COLLECTION, query)
                .map(entities -> {
                    List<Pipeline> all = entities.stream()
                            .map(this::fromEntity)
                            .filter(p -> nameFilter == null || nameFilter.isBlank()
                                    || (p.getName() != null && p.getName().contains(nameFilter)))
                            .toList();
                    int from = Math.min((page - 1) * size, all.size());
                    int to   = Math.min(from + size, all.size());
                    List<Pipeline> pageContent = all.subList(from, to);
                    return Page.of(pageContent, size, page - 1, all.size());
                })
                .whenException(e ->
                    log.error("[pipeline-store] findAll failed tenant={}: {}", tenant, e.getMessage(), e));
    }

    /** {@inheritDoc} */
    @Override
    public Promise<Integer> nextVersion(String name, TenantId tenantId) {
        return findLatestVersion(name, tenantId)
                .map(opt -> opt.map(p -> p.getVersion() + 1).orElse(1));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns all stored versions of a pipeline with the given name, ordered by version ascending.
     */
    @Override
    public Promise<List<Pipeline>> findAllVersions(String name, TenantId tenantId) {
        String tenant = tenantStr(tenantId);
        Query query = Query.builder()
                .filter(Filter.eq("name", name))
                .limit(1_000)
                .build();
        return client.query(tenant, COLLECTION, query)
                .map(entities -> entities.stream()
                    .map(this::fromEntity)
                    .sorted(java.util.Comparator.comparingInt(Pipeline::getVersion))
                    .toList())
                .then(Promise::of, e -> {
                    log.warn("[pipeline-store] findAllVersions failed name={} tenant={}: {}",
                            name, tenant, e.getMessage());
                    return Promise.of(List.of());
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Counts pipelines that have been migrated to the structured config format.
     * Since {@code DataCloudPipelineStore} does not yet serialize {@code PipelineConfig},
     * this method fetches all tenant pipelines and counts those where the in-memory
     * {@code structuredConfig} field is non-null (always 0 for DataCloud-loaded entities
     * until full structured-config serialization is added).
     */
    @Override
    public Promise<Long> countStructuredConfigPipelines(TenantId tenantId) {
        String tenant = tenantStr(tenantId);
        return client.query(tenant, COLLECTION, Query.limit(10_000))
                .map(entities -> entities.stream()
                    .map(this::fromEntity)
                    .filter(Pipeline::hasStructuredConfig)
                    .count())
                .then(Promise::of, e -> {
                    log.warn("[pipeline-store] countStructuredConfigPipelines failed tenant={}: {}",
                            tenant, e.getMessage());
                    return Promise.of(0L);
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Counts pipelines that still use the legacy string config format (i.e., have
     * a non-null {@code config} field and no structured config). Useful for monitoring
     * migration progress.
     */
    @Override
    public Promise<Long> countLegacyConfigPipelines(TenantId tenantId) {
        String tenant = tenantStr(tenantId);
        return client.query(tenant, COLLECTION, Query.limit(10_000))
                .map(entities -> entities.stream()
                    .map(this::fromEntity)
                    .filter(p -> !p.hasStructuredConfig() && p.getConfig() != null)
                    .count())
                .then(Promise::of, e -> {
                    log.warn("[pipeline-store] countLegacyConfigPipelines failed tenant={}: {}",
                            tenant, e.getMessage());
                    return Promise.of(0L);
                });
    }

    // =========================================================================
    // Mapping helpers
    // =========================================================================

    /**
     * Converts a {@link Pipeline} domain object to a Data-Cloud entity data map.
     *
     * <p>The pipeline's ID is stored under {@code "id"} so that {@code DataCloudClient.save()}
     * uses it as the entity key, providing upsert semantics with a stable identifier.
     */
    private Map<String, Object> toEntityData(Pipeline pipeline) {
        Map<String, Object> data = new HashMap<>();
        data.put("id",          pipeline.getId());
        data.put("tenantId",    tenantStr(pipeline.getTenantId()));
        data.put("name",        safeStr(pipeline.getName(), ""));
        data.put("description", safeStr(pipeline.getDescription(), ""));
        data.put("version",     pipeline.getVersion());
        data.put("active",      pipeline.isActive());
        data.put("config",      safeStr(pipeline.getConfig(), "{}"));
        data.put("createdAt",   pipeline.getCreatedAt() != null
                                    ? pipeline.getCreatedAt().toString()
                                    : Instant.now().toString());
        data.put("updatedAt",   Instant.now().toString());
        data.put("createdBy",   safeStr(pipeline.getCreatedBy(), "system"));
        data.put("updatedBy",   safeStr(pipeline.getUpdatedBy(), "system"));
        return data;
    }

    /** Converts a Data-Cloud {@link Entity} back to the {@link Pipeline} domain object. */
    private Pipeline fromEntity(Entity entity) {
        Map<String, Object> d = entity.data();
        Pipeline p = new Pipeline();
        p.setId(safeStr(d.get("id"), entity.id()));
        p.setTenantId(TenantId.of(safeStr(d.get("tenantId"), "system")));
        p.setName(safeStr(d.get("name"), ""));
        p.setDescription(safeStr(d.get("description"), ""));
        p.setVersion(parseInt(d.get("version"), 1));
        p.setActive(parseBool(d.get("active"), true));
        p.setConfig(safeStr(d.get("config"), "{}"));
        p.setCreatedAt(parseInstant(d.get("createdAt")));
        p.setUpdatedAt(parseInstant(d.get("updatedAt")));
        p.setCreatedBy(safeStr(d.get("createdBy"), "system"));
        p.setUpdatedBy(safeStr(d.get("updatedBy"), "system"));
        return p;
    }

    // ── Type-safe field extractors ────────────────────────────────────────────

    private static String tenantStr(TenantId tenantId) {
        return tenantId != null ? tenantId.value() : "system";
    }

    private static String safeStr(Object value, String fallback) {
        return value instanceof String s ? s : fallback;
    }

    private static int parseInt(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try { return value != null ? Integer.parseInt(value.toString()) : fallback; }
        catch (NumberFormatException e) { return fallback; }
    }

    private static boolean parseBool(Object value, boolean fallback) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return fallback;
    }

    private static Instant parseInstant(Object value) {
        if (value == null) return Instant.now();
        try { return Instant.parse(value.toString()); }
        catch (Exception e) { return Instant.now(); }
    }
}
