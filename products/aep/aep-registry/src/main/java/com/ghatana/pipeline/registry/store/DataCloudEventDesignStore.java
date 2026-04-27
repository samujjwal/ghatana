/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.pipeline.registry.store;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Filter;
import com.ghatana.datacloud.DataCloudClient.Query;
import com.ghatana.datacloud.DataCloudClient.Sort;
import com.ghatana.pipeline.registry.model.ConnectorBinding;
import com.ghatana.pipeline.registry.model.SchemaDefinition;
import com.ghatana.platform.domain.auth.TenantId;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/**
 * Data-Cloud backed implementation of {@link EventDesignStore}.
 *
 * <p>T-18: Provides durable persistence for event schemas and connector bindings
 * using Data-Cloud entity storage. Replaces the in-memory EventDesignService
 * with tenant-isolated durable storage.</p>
 *
 * @doc.type class
 * @doc.purpose Data-Cloud backed storage for event design artifacts
 * @doc.layer product
 * @doc.pattern Repository, Adapter
 * @since 2.0.0
 */
public class DataCloudEventDesignStore implements EventDesignStore {

    private static final Logger log = LoggerFactory.getLogger(DataCloudEventDesignStore.class);

    /** Data-Cloud collection for schema definitions. */
    public static final String SCHEMA_COLLECTION = "aep_event_schemas";

    /** Data-Cloud collection for connector bindings. */
    public static final String BINDING_COLLECTION = "aep_connector_bindings";

    private final DataCloudClient client;

    public DataCloudEventDesignStore(DataCloudClient client) {
        this.client = Objects.requireNonNull(client, "DataCloudClient required");
    }

    // =========================================================================
    // Schema Operations
    // =========================================================================

    @Override
    public Promise<SchemaDefinition> saveSchema(SchemaDefinition schema) {
        String tenant = tenantStr(schema.getTenantId());
        Map<String, Object> data = toSchemaEntityData(schema);

        return client.save(tenant, SCHEMA_COLLECTION, data)
            .map(this::fromSchemaEntity)
            .whenException(e ->
                log.error("[event-design] saveSchema failed id={} tenant={}: {}",
                    schema.getId(), tenant, e.getMessage(), e));
    }

    @Override
    public Promise<Optional<SchemaDefinition>> findSchemaById(String id, TenantId tenantId) {
        String tenant = tenantStr(tenantId);
        return client.findById(tenant, SCHEMA_COLLECTION, id)
            .map(optEntity -> optEntity
                .filter(e -> tenant.equals(e.data().get("tenantId")))
                .map(this::fromSchemaEntity))
            .whenException(e ->
                log.error("[event-design] findSchemaById failed id={} tenant={}: {}",
                    id, tenant, e.getMessage(), e));
    }

    @Override
    public Promise<Collection<SchemaDefinition>> listSchemasByTenant(TenantId tenantId) {
        String tenant = tenantStr(tenantId);
        Query query = Query.builder()
            .filter(Filter.eq("tenantId", tenant))
            .limit(10_000)
            .build();

        return client.query(tenant, SCHEMA_COLLECTION, query)
            .map(entities -> {
                Collection<SchemaDefinition> schemas = entities.stream()
                    .map(this::fromSchemaEntity)
                    .toList();
                return schemas;
            })
            .whenException(e ->
                log.error("[event-design] listSchemasByTenant failed tenant={}: {}",
                    tenant, e.getMessage(), e));
    }

    @Override
    public Promise<SchemaListPage> listSchemasByTenant(TenantId tenantId, String cursor, int pageSize) {
        String tenant = tenantStr(tenantId);
        int effectiveLimit = Math.min(Math.max(pageSize, 1), 1000);
        int offset = cursor != null ? decodeCursor(cursor) : 0;

        Query query = Query.builder()
            .filter(Filter.eq("tenantId", tenant))
            .sorts(List.of(Sort.asc("createdAt")))
            .offset(offset)
            .limit(effectiveLimit + 1) // Fetch one extra to detect hasMore
            .build();

        return client.query(tenant, SCHEMA_COLLECTION, query)
            .map(entities -> {
                List<SchemaDefinition> all = entities.stream()
                    .map(this::fromSchemaEntity)
                    .toList();

                boolean hasMore = all.size() > effectiveLimit;
                List<SchemaDefinition> items = hasMore
                    ? all.subList(0, effectiveLimit)
                    : all;

                String nextCursor = hasMore
                    ? encodeCursor(offset + effectiveLimit)
                    : null;

                return new SchemaListPage(items, nextCursor, items.size());
            })
            .whenException(e ->
                log.error("[event-design] listSchemasByTenant (paged) failed tenant={}: {}",
                    tenant, e.getMessage(), e));
    }

    @Override
    public Promise<Optional<SchemaDefinition>> updateSchema(String id, TenantId tenantId, Consumer<SchemaDefinition> updater) {
        return findSchemaById(id, tenantId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.of(Optional.<SchemaDefinition>empty());
                }
                SchemaDefinition updated = opt.get();
                updater.accept(updated);
                return saveSchema(updated).map(Optional::of);
            });
    }

    @Override
    public Promise<Boolean> deleteSchema(String id, TenantId tenantId) {
        String tenant = tenantStr(tenantId);
        return client.delete(tenant, SCHEMA_COLLECTION, id)
            .map(ignored -> Boolean.TRUE)
            .then(Promise::of, e -> {
                log.warn("[event-design] deleteSchema failed id={} tenant={}: {}",
                    id, tenant, e.getMessage());
                return Promise.of(Boolean.FALSE);
            });
    }

    // =========================================================================
    // Binding Operations
    // =========================================================================

    @Override
    public Promise<ConnectorBinding> saveBinding(ConnectorBinding binding) {
        String tenant = tenantStr(binding.getTenantId());
        Map<String, Object> data = toBindingEntityData(binding);

        return client.save(tenant, BINDING_COLLECTION, data)
            .map(this::fromBindingEntity)
            .whenException(e ->
                log.error("[event-design] saveBinding failed id={} tenant={}: {}",
                    binding.getId(), tenant, e.getMessage(), e));
    }

    @Override
    public Promise<Optional<ConnectorBinding>> findBindingById(String id, TenantId tenantId) {
        String tenant = tenantStr(tenantId);
        return client.findById(tenant, BINDING_COLLECTION, id)
            .map(optEntity -> optEntity
                .filter(e -> tenant.equals(e.data().get("tenantId")))
                .map(this::fromBindingEntity))
            .whenException(e ->
                log.error("[event-design] findBindingById failed id={} tenant={}: {}",
                    id, tenant, e.getMessage(), e));
    }

    @Override
    public Promise<Collection<ConnectorBinding>> listBindingsByTenant(TenantId tenantId) {
        String tenant = tenantStr(tenantId);
        Query query = Query.builder()
            .filter(Filter.eq("tenantId", tenant))
            .limit(10_000)
            .build();

        return client.query(tenant, BINDING_COLLECTION, query)
            .map(entities -> {
                Collection<ConnectorBinding> bindings = entities.stream()
                    .map(this::fromBindingEntity)
                    .toList();
                return bindings;
            })
            .whenException(e ->
                log.error("[event-design] listBindingsByTenant failed tenant={}: {}",
                    tenant, e.getMessage(), e));
    }

    @Override
    public Promise<BindingListPage> listBindingsByTenant(TenantId tenantId, String cursor, int pageSize) {
        String tenant = tenantStr(tenantId);
        int effectiveLimit = Math.min(Math.max(pageSize, 1), 1000);
        int offset = cursor != null ? decodeCursor(cursor) : 0;

        Query query = Query.builder()
            .filter(Filter.eq("tenantId", tenant))
            .sorts(List.of(Sort.asc("createdAt")))
            .offset(offset)
            .limit(effectiveLimit + 1)
            .build();

        return client.query(tenant, BINDING_COLLECTION, query)
            .map(entities -> {
                List<ConnectorBinding> all = entities.stream()
                    .map(this::fromBindingEntity)
                    .toList();

                boolean hasMore = all.size() > effectiveLimit;
                List<ConnectorBinding> items = hasMore
                    ? all.subList(0, effectiveLimit)
                    : all;

                String nextCursor = hasMore
                    ? encodeCursor(offset + effectiveLimit)
                    : null;

                return new BindingListPage(items, nextCursor, items.size());
            })
            .whenException(e ->
                log.error("[event-design] listBindingsByTenant (paged) failed tenant={}: {}",
                    tenant, e.getMessage(), e));
    }

    @Override
    public Promise<Optional<ConnectorBinding>> updateBinding(String id, TenantId tenantId, Consumer<ConnectorBinding> updater) {
        return findBindingById(id, tenantId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.of(Optional.<ConnectorBinding>empty());
                }
                ConnectorBinding updated = opt.get();
                updater.accept(updated);
                return saveBinding(updated).map(Optional::of);
            });
    }

    @Override
    public Promise<Boolean> deleteBinding(String id, TenantId tenantId) {
        String tenant = tenantStr(tenantId);
        return client.delete(tenant, BINDING_COLLECTION, id)
            .map(ignored -> Boolean.TRUE)
            .then(Promise::of, e -> {
                log.warn("[event-design] deleteBinding failed id={} tenant={}: {}",
                    id, tenant, e.getMessage());
                return Promise.of(Boolean.FALSE);
            });
    }

    @Override
    public Promise<Collection<ConnectorBinding>> findBindingsBySchema(String schemaId, TenantId tenantId) {
        String tenant = tenantStr(tenantId);
        Query query = Query.builder()
            .filter(Filter.eq("schemaId", schemaId))
            .limit(1_000)
            .build();

        return client.query(tenant, BINDING_COLLECTION, query)
            .map(entities -> {
                Collection<ConnectorBinding> bindings = entities.stream()
                    .map(this::fromBindingEntity)
                    .filter(b -> tenant.equals(b.getTenantId().value()))
                    .toList();
                return bindings;
            })
            .whenException(e ->
                log.error("[event-design] findBindingsBySchema failed schemaId={} tenant={}: {}",
                    schemaId, tenant, e.getMessage(), e));
    }

    // =========================================================================
    // Entity Mapping
    // =========================================================================

    private Map<String, Object> toSchemaEntityData(SchemaDefinition schema) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", schema.getId() != null ? schema.getId() : UUID.randomUUID().toString());
        data.put("tenantId", tenantStr(schema.getTenantId()));
        data.put("eventTypeId", schema.getEventTypeId());
        data.put("version", schema.getVersion());
        data.put("format", schema.getFormat());
        data.put("document", schema.getDocument());
        data.put("direction", schema.getDirection());
        data.put("description", schema.getDescription());
        data.put("createdAt", schema.getCreatedAt() != null
            ? schema.getCreatedAt().toString()
            : Instant.now().toString());
        return data;
    }

    private SchemaDefinition fromSchemaEntity(Entity entity) {
        Map<String, Object> d = entity.data();
        return SchemaDefinition.create(
            TenantId.of(safeStr(d.get("tenantId"), "system")),
            safeStr(d.get("eventTypeId"), ""),
            safeStr(d.get("format"), "JSON_SCHEMA"),
            safeStr(d.get("document"), ""),
            safeStr(d.get("direction"), "both"),
            safeStr(d.get("description"), ""),
            parseInt(d.get("version"), 1)
        );
    }

    private Map<String, Object> toBindingEntityData(ConnectorBinding binding) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", binding.getId() != null ? binding.getId() : UUID.randomUUID().toString());
        data.put("tenantId", tenantStr(binding.getTenantId()));
        data.put("schemaId", binding.getSchemaId());
        data.put("connectorId", binding.getConnectorId());
        data.put("direction", binding.getDirection());
        data.put("encoding", binding.getEncoding());
        data.put("enabled", binding.isEnabled());
        data.put("createdAt", binding.getCreatedAt() != null
            ? binding.getCreatedAt().toString()
            : Instant.now().toString());
        data.put("updatedAt", binding.getUpdatedAt() != null
            ? binding.getUpdatedAt().toString()
            : Instant.now().toString());
        data.put("createdBy", binding.getCreatedBy());
        data.put("updatedBy", binding.getUpdatedBy());

        // Serialize header mappings
        if (binding.getHeaderMappings() != null) {
            List<Map<String, String>> headers = binding.getHeaderMappings().stream()
                .map(hm -> {
                    Map<String, String> m = new HashMap<>();
                    m.put("source", hm.getSource());
                    m.put("target", hm.getTarget());
                    if (hm.getTransform() != null) m.put("transform", hm.getTransform());
                    return m;
                })
                .toList();
            data.put("headerMappings", headers);
        }

        // Serialize payload mapping
        if (binding.getPayloadMapping() != null) {
            Map<String, Object> pm = new HashMap<>();
            pm.put("rootPath", binding.getPayloadMapping().getRootPath());
            pm.put("excludeFields", binding.getPayloadMapping().getExcludeFields());
            pm.put("fieldTransforms", binding.getPayloadMapping().getFieldTransforms());
            data.put("payloadMapping", pm);
        }

        return data;
    }

    private ConnectorBinding fromBindingEntity(Entity entity) {
        Map<String, Object> d = entity.data();

        List<ConnectorBinding.HeaderMapping> headerMappings = new ArrayList<>();
        Object headersRaw = d.get("headerMappings");
        if (headersRaw instanceof List<?> headers) {
            for (Object h : headers) {
                if (h instanceof Map<?, ?> hm) {
                    headerMappings.add(ConnectorBinding.HeaderMapping.builder()
                        .source(safeStr(hm.get("source"), ""))
                        .target(safeStr(hm.get("target"), ""))
                        .transform(safeStr(hm.get("transform"), null))
                        .build());
                }
            }
        }

        ConnectorBinding.PayloadMapping payloadMapping = null;
        Object pmRaw = d.get("payloadMapping");
        if (pmRaw instanceof Map<?, ?> pm) {
            Object excludes = pm.get("excludeFields");
            List<String> excludeFields = new ArrayList<>();
            if (excludes instanceof List<?> exList) {
                for (Object e : exList) {
                    if (e instanceof String s) excludeFields.add(s);
                }
            }

            Object transforms = pm.get("fieldTransforms");
            Map<String, String> fieldTransforms = new HashMap<>();
            if (transforms instanceof Map<?, ?> tf) {
                tf.forEach((k, v) -> {
                    if (k instanceof String key && v instanceof String val) {
                        fieldTransforms.put(key, val);
                    }
                });
            }

            payloadMapping = ConnectorBinding.PayloadMapping.builder()
                .rootPath(safeStr(pm.get("rootPath"), ""))
                .excludeFields(excludeFields)
                .fieldTransforms(fieldTransforms)
                .build();
        }

        return ConnectorBinding.create(
            TenantId.of(safeStr(d.get("tenantId"), "system")),
            safeStr(d.get("schemaId"), ""),
            safeStr(d.get("connectorId"), ""),
            safeStr(d.get("direction"), "egress"),
            safeStr(d.get("encoding"), "json"),
            headerMappings.isEmpty() ? null : headerMappings,
            payloadMapping,
            safeStr(d.get("createdBy"), "system")
        );
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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
}
