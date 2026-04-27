/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.store;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Filter;
import com.ghatana.datacloud.DataCloudClient.Query;
import com.ghatana.datacloud.DataCloudClient.Sort;
import com.ghatana.pipeline.registry.model.Pipeline;
import com.ghatana.pipeline.registry.model.PipelineRegistration;
import com.ghatana.pipeline.registry.model.PipelineVersionStatus;
import com.ghatana.pipeline.registry.repository.PipelineRepository;
import com.ghatana.platform.core.common.pagination.Page;
import com.ghatana.platform.domain.auth.TenantId;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
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
 *   <li>T-25: Pagination uses native Data-Cloud cursor-based queries (not in-memory).</li>
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

    /** Data-Cloud collection name for immutable AEP pipeline version snapshots. */
    public static final String VERSION_COLLECTION = "aep_pipeline_versions";

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
    // PipelineRepository — abstract method implementations (String tenantId)
    // =========================================================================

    @Override
    public Promise<PipelineRegistration> save(PipelineRegistration pipeline) {
        Pipeline p;
        if (pipeline instanceof Pipeline pp) {
            p = pp;
        } else {
            p = new Pipeline();
            p.setId(pipeline.getId());
            p.setTenantId(pipeline.getTenantId());
            p.setName(pipeline.getName());
            p.setDescription(pipeline.getDescription());
            p.setVersion(pipeline.getVersion());
            p.setActive(pipeline.isActive());
            p.setConfig(pipeline.getConfig());
            p.setCreatedAt(pipeline.getCreatedAt());
            p.setUpdatedAt(pipeline.getUpdatedAt());
            p.setCreatedBy(pipeline.getCreatedBy());
            p.setUpdatedBy(pipeline.getUpdatedBy());
        }
        return save(p).map(saved -> saved);
    }

    @Override
    public Promise<Optional<PipelineRegistration>> findById(String id, String tenantId) {
        return findById(id, TenantId.of(tenantId)).map(opt -> opt.map(p -> p));
    }

    @Override
    public Promise<List<PipelineRegistration>> findByTenantId(String tenantId) {
        return findAll(TenantId.of(tenantId), null, null, 1, 10_000)
                .map(page -> page.content().stream()
                        .map(p -> (PipelineRegistration) p)
                        .toList());
    }

    @Override
    public Promise<Void> delete(String id, String tenantId) {
        return delete(id, TenantId.of(tenantId), true, "system");
    }

    @Override
    public Promise<Long> countByTenantId(String tenantId) {
        String tenant = tenantId != null ? tenantId : "system";
        return client.query(tenant, COLLECTION, Query.limit(10_000))
                .map(entities -> (long) entities.size())
                .then(Promise::of, e -> {
                    log.warn("[pipeline-store] countByTenantId failed tenant={}: {}",
                            tenant, e.getMessage());
                    return Promise.of(0L);
                });
    }

            @Override
            public Promise<Void> saveVersionSnapshot(String pipelineId, PipelineRegistration snapshot) {
            Pipeline pipeline = toPipeline(snapshot);
            String tenant = tenantStr(pipeline.getTenantId());
            String snapshotEntityId = versionEntityId(pipelineId, pipeline.getVersion());
            Map<String, Object> data = toVersionEntityData(pipelineId, pipeline, snapshotEntityId);
            return client.save(tenant, VERSION_COLLECTION, data)
                .map(ignored -> (Void) null)
                .whenException(e ->
                    log.error("[pipeline-store] saveVersionSnapshot failed pipelineId={} version={} tenant={}: {}",
                        pipelineId, pipeline.getVersion(), tenant, e.getMessage(), e));
            }

            @Override
            public Promise<List<PipelineRegistration>> findVersionHistory(String pipelineId, String tenantId) {
            Query query = Query.builder()
                .filter(Filter.eq("pipelineId", pipelineId))
                .limit(1_000)
                .build();
            return client.query(tenantId, VERSION_COLLECTION, query)
                .map(entities -> entities.stream()
                    .filter(entity -> tenantId.equals(entity.data().get("tenantId")))
                    .map(this::fromVersionEntity)
                    .sorted(java.util.Comparator.comparingInt(PipelineRegistration::getVersion))
                    .map(pipeline -> (PipelineRegistration) pipeline)
                    .toList())
                .whenException(e ->
                    log.error("[pipeline-store] findVersionHistory failed pipelineId={} tenant={}: {}",
                        pipelineId, tenantId, e.getMessage(), e));
            }

            @Override
            public Promise<Optional<PipelineRegistration>> findVersionSnapshot(String pipelineId, int version, String tenantId) {
            return client.findById(tenantId, VERSION_COLLECTION, versionEntityId(pipelineId, version))
                .map(optEntity -> optEntity
                    .filter(entity -> tenantId.equals(entity.data().get("tenantId")))
                    .map(this::fromVersionEntity)
                    .filter(pipeline -> pipelineId.equals(pipeline.getId()))
                    .map(pipeline -> (PipelineRegistration) pipeline))
                .whenException(e ->
                    log.error("[pipeline-store] findVersionSnapshot failed pipelineId={} version={} tenant={}: {}",
                        pipelineId, version, tenantId, e.getMessage(), e));
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
        int effectiveSize = Math.min(Math.max(size, 1), 1000);
        int offset = Math.max((page - 1) * effectiveSize, 0);

        List<Filter> filters = new ArrayList<>();
        filters.add(Filter.eq("tenantId", tenant));
        if (activeOnly != null && activeOnly) {
            filters.add(Filter.eq("active", "true"));
        }

        // T-25: Use native cursor-based pagination with Data-Cloud offset/limit
        Query query = Query.builder()
                .filters(filters)
            .sorts(List.of(Sort.asc("createdAt")))
                .offset(offset)
                .limit(effectiveSize + 1) // Fetch one extra to detect hasMore
                .build();

        return client.query(tenant, COLLECTION, query)
                .map(entities -> {
                    List<Pipeline> all = entities.stream()
                            .map(this::fromEntity)
                            .filter(p -> nameFilter == null || nameFilter.isBlank()
                                    || (p.getName() != null && p.getName().contains(nameFilter)))
                            .toList();

                    // Determine if there are more results
                    boolean hasMore = all.size() > effectiveSize;
                    List<Pipeline> pageContent = hasMore
                            ? all.subList(0, effectiveSize)
                            : all;

                    // Keep a concrete count estimate for callers that rely on total values.
                    long totalEstimate = offset + all.size();

                    return Page.of(pageContent, effectiveSize, page - 1, totalEstimate);
                })
                .whenException(e ->
                    log.error("[pipeline-store] findAll failed tenant={}: {}", tenant, e.getMessage(), e));
    }

    /**
     * T-25: Cursor-based pagination for efficient large dataset traversal.
     *
     * @param tenantId the tenant scope
     * @param nameFilter optional name filter (applied client-side)
     * @param activeOnly filter to active pipelines only
     * @param cursor pagination cursor (null for first page)
     * @param pageSize maximum items to return
     * @return Promise completing with cursor page result
     */
    public Promise<CursorPage<Pipeline>> findAllWithCursor(TenantId tenantId, String nameFilter,
                                                           Boolean activeOnly, String cursor, int pageSize) {
        String tenant = tenantStr(tenantId);
        int effectiveSize = Math.min(Math.max(pageSize, 1), 1000);
        int offset = cursor != null ? decodeCursor(cursor) : 0;

        List<Filter> filters = new ArrayList<>();
        filters.add(Filter.eq("tenantId", tenant));
        if (activeOnly != null && activeOnly) {
            filters.add(Filter.eq("active", "true"));
        }

        Query query = Query.builder()
                .filters(filters)
            .sorts(List.of(Sort.asc("createdAt")))
                .offset(offset)
                .limit(effectiveSize + 1) // Fetch one extra to detect hasMore
                .build();

        return client.query(tenant, COLLECTION, query)
                .map(entities -> {
                    List<Pipeline> all = entities.stream()
                            .map(this::fromEntity)
                            .filter(p -> nameFilter == null || nameFilter.isBlank()
                                    || (p.getName() != null && p.getName().contains(nameFilter)))
                            .toList();

                    boolean hasMore = all.size() > effectiveSize;
                    List<Pipeline> items = hasMore
                            ? all.subList(0, effectiveSize)
                            : all;

                    String nextCursor = hasMore ? encodeCursor(offset + effectiveSize) : null;

                    return new CursorPage<>(items, nextCursor, items.size(), hasMore);
                })
                .whenException(e ->
                    log.error("[pipeline-store] findAllWithCursor failed tenant={}: {}", tenant, e.getMessage(), e));
    }

    /**
     * Cursor-based pagination result.
     *
     * @param <T> the item type
     * @param items the page items
     * @param nextCursor cursor for next page (null if no more items)
     * @param count number of items in this page
     * @param hasMore whether more items are available
     */
    public record CursorPage<T>(
            List<T> items,
            String nextCursor,
            int count,
            boolean hasMore
    ) {}

    private static String encodeCursor(int offset) {
        return Base64.getEncoder().encodeToString(String.valueOf(offset).getBytes());
    }

    private static int decodeCursor(String cursor) {
        try {
            String decoded = new String(Base64.getDecoder().decode(cursor));
            return Integer.parseInt(decoded);
        } catch (IllegalArgumentException e) {
            return 0;
        }
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
        data.put("versionLabel", safeStr(pipeline.getVersionLabel(), ""));
        data.put("versionStatus", pipeline.getVersionStatus() != null
            ? pipeline.getVersionStatus().name()
            : PipelineVersionStatus.DRAFT.name());
        data.put("versionControl", pipeline.getVersionControl());
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
        p.setVersionLabel(safeStr(d.get("versionLabel"), ""));
        p.setVersionStatus(parseVersionStatus(d.get("versionStatus")));
        p.setVersionControl(parseLong(d.get("versionControl"), 0L));
        return p;
    }

    private Map<String, Object> toVersionEntityData(String pipelineId, Pipeline pipeline, String snapshotEntityId) {
        Map<String, Object> data = toEntityData(pipeline);
        data.put("id", snapshotEntityId);
        data.put("pipelineId", pipelineId);
        data.put("snapshotVersion", pipeline.getVersion());
        return data;
    }

    private Pipeline fromVersionEntity(Entity entity) {
        Pipeline pipeline = fromEntity(entity);
        pipeline.setId(safeStr(entity.data().get("pipelineId"), pipeline.getId()));
        return pipeline;
    }

    private static Pipeline toPipeline(PipelineRegistration registration) {
        if (registration instanceof Pipeline pipeline) {
            return pipeline;
        }
        Pipeline pipeline = new Pipeline();
        pipeline.setId(registration.getId());
        pipeline.setTenantId(registration.getTenantId());
        pipeline.setName(registration.getName());
        pipeline.setDescription(registration.getDescription());
        pipeline.setVersion(registration.getVersion());
        pipeline.setActive(registration.isActive());
        pipeline.setConfig(registration.getConfig());
        pipeline.setCreatedAt(registration.getCreatedAt());
        pipeline.setUpdatedAt(registration.getUpdatedAt());
        pipeline.setCreatedBy(registration.getCreatedBy());
        pipeline.setUpdatedBy(registration.getUpdatedBy());
        pipeline.setVersionLabel(registration.getVersionLabel());
        pipeline.setVersionStatus(registration.getVersionStatus());
        pipeline.setVersionControl(registration.getVersionControl());
        return pipeline;
    }

    private static String versionEntityId(String pipelineId, int version) {
        return pipelineId + ":v" + version;
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

    private static long parseLong(Object value, long fallback) {
        if (value instanceof Number n) return n.longValue();
        try { return value != null ? Long.parseLong(value.toString()) : fallback; }
        catch (NumberFormatException e) { return fallback; }
    }

    private static boolean parseBool(Object value, boolean fallback) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return fallback;
    }

    private static PipelineVersionStatus parseVersionStatus(Object value) {
        if (value == null) {
            return PipelineVersionStatus.DRAFT;
        }
        try {
            return PipelineVersionStatus.valueOf(value.toString());
        } catch (IllegalArgumentException ignored) {
            return PipelineVersionStatus.DRAFT;
        }
    }

    private static Instant parseInstant(Object value) {
        if (value == null) return Instant.now();
        try { return Instant.parse(value.toString()); }
        catch (DateTimeParseException e) { return Instant.now(); }
    }
}
