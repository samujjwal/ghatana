package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.datacloud.entity.validation.EntitySchemaValidator;
import com.ghatana.datacloud.entity.validation.ValidationResult;
import com.ghatana.datacloud.entity.storage.QuerySpec;
import com.ghatana.datacloud.infrastructure.storage.OpenSearchConnector;
import com.ghatana.datacloud.launcher.http.ApiInputValidator;
import com.ghatana.datacloud.launcher.http.TraceSpanSupport;
import com.ghatana.platform.security.annotation.RequiresRole;
import com.ghatana.platform.security.annotation.Secured;
import io.activej.http.*;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Handles entity CRUD, batch, export, and anomaly-detection HTTP endpoints.
 *
 * <p>Extracted from {@code DataCloudHttpServer} to reduce the god-class size.
 * Registered in the server via method references:
 * <pre>{@code
 * .with(HttpMethod.POST, "/api/v1/entities/:collection", entityHandler::handleSaveEntity)
 * }</pre>
 *
 * <h2>Security</h2>
 * All entity operations require authentication. Role-based access control is enforced
 * at the HTTP filter level (DataCloudSecurityFilter) based on the endpoint sensitivity
 * and user roles. Write operations require EDITOR or higher role.
 *
 * @doc.type class
 * @doc.purpose Entity CRUD, batch, export, and anomaly HTTP handlers
 * @doc.layer product
 * @doc.pattern Handler
 */
@Secured
public class EntityCrudHandler {

    private static final Logger log = LoggerFactory.getLogger(EntityCrudHandler.class);

    private final DataCloudClient client;
    private final HttpHandlerSupport http;
    private final BiConsumer<String, Map<String, Object>> wsBroadcaster;
    private TraceSpanSupport traceSupport = TraceSpanSupport.disabled();
    private SemanticIndexPort semanticIndexPort;
    private SemanticDeletePort semanticDeletePort;

    private EntitySchemaValidator schemaValidator;
    private OpenSearchConnector openSearchConnector;

    /**
     * Creates an entity handler with required dependencies.
     *
     * @param client        the Data-Cloud client
     * @param http          shared HTTP helper methods
     * @param wsBroadcaster callback to broadcast WebSocket events; may be a no-op
     */
    public EntityCrudHandler(DataCloudClient client,
                             HttpHandlerSupport http,
                             BiConsumer<String, Map<String, Object>> wsBroadcaster) {
        this.client = client;
        this.http = http;
        this.wsBroadcaster = wsBroadcaster;
    }

    public EntityCrudHandler withSchemaValidator(EntitySchemaValidator validator) {
        this.schemaValidator = validator;
        return this;
    }

    public EntityCrudHandler withOpenSearchConnector(OpenSearchConnector connector) {
        this.openSearchConnector = connector;
        return this;
    }

    public EntityCrudHandler withTraceSupport(TraceSpanSupport traceSupport) {
        this.traceSupport = traceSupport != null ? traceSupport : TraceSpanSupport.disabled();
        return this;
    }

    public EntityCrudHandler withSemanticSearchPorts(
            SemanticIndexPort semanticIndexPort,
            SemanticDeletePort semanticDeletePort) {
        this.semanticIndexPort = semanticIndexPort;
        this.semanticDeletePort = semanticDeletePort;
        return this;
    }

    // ==================== Entity CRUD ====================

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleSaveEntity(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        final String resolvedTenantId = tenantId;

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(resolvedTenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
            request,
            resolvedTenantId,
            "datacloud.http.entity.save",
            traceSupport.requestSpanId(request),
            Map.of("collection", collection));

        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = http.objectMapper().readValue(body, Map.class);

                Optional<String> payloadErr = ApiInputValidator.validateEntityPayload(data);
                if (payloadErr.isPresent()) return Promise.of(http.errorResponse(400, payloadErr.get()));

                if (schemaValidator != null) {
                    ValidationResult vr = schemaValidator.validate(resolvedTenantId, collection, data);
                    if (!vr.valid()) {
                        return Promise.of(http.errorResponse(422, "Schema validation failed: " + vr.violationSummary()));
                    }
                }

                return traceSupport.trace(
                    request,
                    resolvedTenantId,
                    "datacloud.entity.store.save",
                    handlerSpan.spanId(),
                    Map.of("collection", collection),
                    () -> client.save(resolvedTenantId, collection, data))
                    .then(entity -> {
                        DataCloudClient.Event cdcEvent = DataCloudClient.Event.of("entity.saved",
                            buildCdcEnvelope(entity, "upsert"));
                        return traceSupport.trace(
                            request,
                            resolvedTenantId,
                            "datacloud.event.store.append",
                            handlerSpan.spanId(),
                            Map.of("collection", entity.collection(), "event.type", "entity.saved"),
                            () -> client.appendEvent(resolvedTenantId, cdcEvent))
                            .map(ignored -> {
                                wsBroadcaster.accept("collection.saved", Map.of(
                                    "entityId",  entity.id(),
                                    "collection", entity.collection(),
                                    "tenantId",  resolvedTenantId
                                ));
                                return entity;
                            })
                            .then(savedEntity -> semanticIndexPort == null
                                ? Promise.of(savedEntity)
                                : semanticIndexPort.index(resolvedTenantId, collection, savedEntity)
                                    .map(ignored -> savedEntity));
                    })
                    .map(entity -> http.jsonResponse(Map.of(
                        "id", entity.id(),
                        "collection", entity.collection(),
                        "version", entity.version(),
                        "createdAt", entity.createdAt().toString(),
                        "timestamp", Instant.now().toString()
                    )));
            } catch (Exception e) {
                log.error("Error saving entity", e);
                return Promise.of(http.errorResponse(400, "Invalid entity data: " + e.getMessage()));
            }
        }).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }

    public Promise<HttpResponse> handleGetEntity(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        String id = request.getPathParameter("id");
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));
        Optional<String> idErr = ApiInputValidator.validateId(id);
        if (idErr.isPresent()) return Promise.of(http.errorResponse(400, idErr.get()));

        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
                request,
                tenantId,
                "datacloud.http.entity.get",
                traceSupport.requestSpanId(request),
                Map.of("collection", collection, "entity.id", id));

        return traceSupport.trace(
            request,
            tenantId,
            "datacloud.entity.store.find_by_id",
            handlerSpan.spanId(),
            Map.of("collection", collection, "entity.id", id),
            () -> client.findById(tenantId, collection, id))
            .map(optEntity -> {
                if (optEntity.isPresent()) {
                    DataCloudClient.Entity entity = optEntity.get();
                    return http.jsonResponse(Map.of(
                        "id", entity.id(),
                        "collection", entity.collection(),
                        "data", entity.data(),
                        "version", entity.version(),
                        "createdAt", entity.createdAt().toString(),
                        "updatedAt", entity.updatedAt().toString()
                    ));
                } else {
                    return http.errorResponse(404, "Entity not found: " + id);
                }
            }).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }

    public Promise<HttpResponse> handleQueryEntities(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

        ApiInputValidator.LimitResult limitResult = ApiInputValidator.validateLimit(request.getQueryParameter("limit"), 100);
        if (!limitResult.isValid()) return Promise.of(http.errorResponse(400, limitResult.getError().orElseThrow()));
        int limit = limitResult.getValue();

        int offset = parseOffset(request.getQueryParameter("offset"));
        String search = request.getQueryParameter("search");
        List<DataCloudClient.Sort> sorts = parseSorts(request.getQueryParameter("sort"));
        List<DataCloudClient.Filter> filters = parseFilters(request.getQueryParameter("filter"));

        if (search != null && !search.isBlank()) {
            Optional<String> searchErr = ApiInputValidator.validateSearchQuery(search);
            if (searchErr.isPresent()) {
                return Promise.of(http.errorResponse(400, searchErr.get()));
            }
            filters = new ArrayList<>(filters);
            filters.add(DataCloudClient.Filter.like("id", "%" + search + "%"));
        }

        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filters(filters)
            .sorts(sorts)
            .offset(offset)
            .limit(limit)
            .build();

        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
                request,
                tenantId,
                "datacloud.http.entity.query",
                traceSupport.requestSpanId(request),
                Map.of("collection", collection, "limit", limit, "offset", offset));

        com.ghatana.datacloud.spi.EntityStore store = client.entityStore();
        com.ghatana.datacloud.spi.TenantContext tenantContext = com.ghatana.datacloud.spi.TenantContext.of(tenantId);
        com.ghatana.datacloud.spi.EntityStore.QuerySpec countSpec = toEntityStoreQuerySpec(collection, query);

        Promise<Long> totalPromise = store != null
            ? store.count(tenantContext, countSpec)
            : Promise.of(-1L);

        return traceSupport.trace(
            request,
            tenantId,
            "datacloud.entity.store.query",
            handlerSpan.spanId(),
            Map.of("collection", collection, "limit", limit, "offset", offset),
            () -> client.query(tenantId, collection, query))
            .combine(totalPromise, (entities, total) -> {
                boolean hasMore = total >= 0 && offset + entities.size() < total;
                return http.jsonResponse(Map.of(
                    "entities", entities.stream().map(e -> Map.of(
                        "id", e.id(),
                        "collection", e.collection(),
                        "data", e.data(),
                        "version", e.version(),
                        "createdAt", e.createdAt() != null ? e.createdAt().toString() : null,
                        "updatedAt", e.updatedAt() != null ? e.updatedAt().toString() : null
                    )).toList(),
                    "total", total >= 0 ? total : entities.size(),
                    "count", entities.size(),
                    "offset", offset,
                    "limit", limit,
                    "hasMore", hasMore,
                    "timestamp", Instant.now().toString()
                ));
            }).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }

    private int parseOffset(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.strip()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private List<DataCloudClient.Sort> parseSorts(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<DataCloudClient.Sort> result = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] tokens = trimmed.split(":");
            String field = tokens[0];
            boolean ascending = tokens.length < 2 || "asc".equalsIgnoreCase(tokens[1]);
            result.add(ascending ? DataCloudClient.Sort.asc(field) : DataCloudClient.Sort.desc(field));
        }
        return List.copyOf(result);
    }

    private List<DataCloudClient.Filter> parseFilters(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<DataCloudClient.Filter> result = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] tokens = trimmed.split(":");
            if (tokens.length >= 3) {
                String field = tokens[0];
                String op = tokens[1];
                String value = tokens[2];
                result.add(switch (op) {
                    case "eq" -> DataCloudClient.Filter.eq(field, value);
                    case "ne" -> DataCloudClient.Filter.ne(field, value);
                    case "gt" -> DataCloudClient.Filter.gt(field, value);
                    case "gte" -> DataCloudClient.Filter.gte(field, value);
                    case "lt" -> DataCloudClient.Filter.lt(field, value);
                    case "lte" -> DataCloudClient.Filter.lte(field, value);
                    case "like" -> DataCloudClient.Filter.like(field, value);
                    default -> DataCloudClient.Filter.eq(field, value);
                });
            }
        }
        return List.copyOf(result);
    }

    private com.ghatana.datacloud.spi.EntityStore.QuerySpec toEntityStoreQuerySpec(String collection, DataCloudClient.Query query) {
        com.ghatana.datacloud.spi.EntityStore.QuerySpec.Builder builder =
            com.ghatana.datacloud.spi.EntityStore.QuerySpec.builder()
                .collection(collection)
                .offset(query.offset())
                .limit(query.limit());
        if (!query.filters().isEmpty()) {
            List<com.ghatana.datacloud.spi.EntityStore.Filter> storeFilters = new ArrayList<>();
            for (DataCloudClient.Filter f : query.filters()) {
                storeFilters.add(toStoreFilter(f));
            }
            builder.filters(storeFilters);
        }
        if (!query.sorts().isEmpty()) {
            List<com.ghatana.datacloud.spi.EntityStore.Sort> storeSorts = new ArrayList<>();
            for (DataCloudClient.Sort s : query.sorts()) {
                storeSorts.add(s.ascending()
                    ? com.ghatana.datacloud.spi.EntityStore.Sort.asc(s.field())
                    : com.ghatana.datacloud.spi.EntityStore.Sort.desc(s.field()));
            }
            builder.sorts(storeSorts);
        }
        return builder.build();
    }

    private com.ghatana.datacloud.spi.EntityStore.Filter toStoreFilter(DataCloudClient.Filter filter) {
        return switch (filter.operator()) {
            case "eq" -> com.ghatana.datacloud.spi.EntityStore.Filter.eq(filter.field(), filter.value());
            case "ne" -> com.ghatana.datacloud.spi.EntityStore.Filter.ne(filter.field(), filter.value());
            case "gt" -> com.ghatana.datacloud.spi.EntityStore.Filter.gt(filter.field(), filter.value());
            case "gte" -> com.ghatana.datacloud.spi.EntityStore.Filter.gte(filter.field(), filter.value());
            case "lt" -> com.ghatana.datacloud.spi.EntityStore.Filter.lt(filter.field(), filter.value());
            case "lte" -> com.ghatana.datacloud.spi.EntityStore.Filter.lte(filter.field(), filter.value());
            case "like" -> com.ghatana.datacloud.spi.EntityStore.Filter.like(filter.field(), (String) filter.value());
            default -> com.ghatana.datacloud.spi.EntityStore.Filter.eq(filter.field(), filter.value());
        };
    }

    public Promise<HttpResponse> handleDeleteEntity(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        String id = request.getPathParameter("id");
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        final String resolvedTenantId = tenantId;

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(resolvedTenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));
        Optional<String> idErr = ApiInputValidator.validateId(id);
        if (idErr.isPresent()) return Promise.of(http.errorResponse(400, idErr.get()));

        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
                request,
                resolvedTenantId,
                "datacloud.http.entity.delete",
                traceSupport.requestSpanId(request),
                Map.of("collection", collection, "entity.id", id));

        return traceSupport.trace(
            request,
            resolvedTenantId,
            "datacloud.entity.store.find_by_id",
            handlerSpan.spanId(),
            Map.of("collection", collection, "entity.id", id),
            () -> client.findById(resolvedTenantId, collection, id))
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.of(http.errorResponse(404, "Entity not found: " + id));
                }
                DataCloudClient.Entity existingEntity = opt.get();
            return traceSupport.trace(
                    request,
                    resolvedTenantId,
                    "datacloud.entity.store.delete",
                    handlerSpan.spanId(),
                    Map.of("collection", collection, "entity.id", id),
                    () -> client.delete(resolvedTenantId, collection, id))
                    .then(v -> {
                        DataCloudClient.Event cdcEvent = DataCloudClient.Event.of("entity.deleted",
                            buildDeleteCdcEnvelope(existingEntity, collection, id));
                        return traceSupport.trace(
                            request,
                            resolvedTenantId,
                            "datacloud.event.store.append",
                            handlerSpan.spanId(),
                            Map.of("collection", collection, "event.type", "entity.deleted"),
                            () -> client.appendEvent(resolvedTenantId, cdcEvent))
                            .map(ignored -> {
                                wsBroadcaster.accept("collection.deleted", Map.of(
                                    "entityId",  id,
                                    "collection", collection,
                                    "tenantId",  resolvedTenantId
                                ));
                                return v;
                            })
                            .then(deleted -> semanticDeletePort == null
                                ? Promise.of(deleted)
                                : semanticDeletePort.delete(resolvedTenantId, id)
                                    .map(ignored -> deleted));
                    })
                    .map(v -> http.jsonResponse(Map.of(
                        "deleted", true,
                        "id", id,
                        "collection", collection,
                        "timestamp", Instant.now().toString()
                    )));
                    }).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }

    @FunctionalInterface
    public interface SemanticIndexPort {
        Promise<Void> index(String tenantId, String collection, DataCloudClient.Entity entity);
    }

    @FunctionalInterface
    public interface SemanticDeletePort {
        Promise<Void> delete(String tenantId, String entityId);
    }

    // ==================== Bulk Entity Endpoints ====================

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleBatchSaveEntities(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

        final String resolvedTenant = tenantId;

        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> payload = http.objectMapper().readValue(body, Map.class);

            Object rawEntities = payload.get("entities");
            if (!(rawEntities instanceof List)) {
                return Promise.of(http.errorResponse(400, "Request body must contain an 'entities' array"));
            }

            List<Map<String, Object>> entityList = (List<Map<String, Object>>) rawEntities;
            Optional<String> batchErr = ApiInputValidator.validateBatchSize(entityList);
            if (batchErr.isPresent()) return Promise.of(http.errorResponse(400, batchErr.get()));

            if (schemaValidator != null) {
                List<String> allViolations = new ArrayList<>();
                for (int i = 0; i < entityList.size(); i++) {
                    ValidationResult vr = schemaValidator.validate(resolvedTenant, collection, entityList.get(i));
                    if (!vr.valid()) {
                        allViolations.add("[" + i + "] " + vr.violationSummary());
                    }
                }
                if (!allViolations.isEmpty()) {
                    return Promise.of(http.errorResponse(422, "Batch schema validation failed: " + String.join("; ", allViolations)));
                }
            }

            List<Promise<DataCloudClient.Entity>> savePromises = entityList.stream()
                .map(data -> client.save(resolvedTenant, collection, data))
                .toList();

            return Promises.toList(savePromises)
                .then(savedEntities -> {
                    List<String> ids = savedEntities.stream()
                        .map(DataCloudClient.Entity::id)
                        .toList();
                    List<Map<String, Object>> entitySnapshots = savedEntities.stream()
                        .map(e -> buildCdcEnvelope(e, "upsert"))
                        .toList();
                    DataCloudClient.Event cdcEvent = DataCloudClient.Event.of("entity.batch-saved", Map.of(
                        "collection", collection,
                        "count", savedEntities.size(),
                        "ids", ids,
                        "operation", "batch-upsert",
                        "entities", entitySnapshots
                    ));
                    return client.appendEvent(resolvedTenant, cdcEvent)
                        .map(ignored -> {
                            wsBroadcaster.accept("collection.batch-saved", Map.of(
                                "collection", collection,
                                "count",      savedEntities.size(),
                                "tenantId",   resolvedTenant
                            ));
                            return http.jsonResponse(Map.of(
                                "saved", savedEntities.size(),
                                "collection", collection,
                                "ids", ids,
                                "errors", List.of(),
                                "timestamp", Instant.now().toString()
                            ));
                        });
                })
                .then(Promise::of, e -> {
                    log.error("Batch save failed for collection {}", collection, e);
                    return Promise.of(http.errorResponse(500, "Batch save failed: " + e.getMessage()));
                });
        } catch (Exception e) {
            log.error("Error parsing batch save request", e);
            return Promise.of(http.errorResponse(400, "Invalid batch request body: " + e.getMessage()));
        }
        });
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleBatchDeleteEntities(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

        final String resolvedTenant = tenantId;

        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> payload = http.objectMapper().readValue(body, Map.class);

                Object rawIds = payload.get("ids");
            if (!(rawIds instanceof List)) {
                return Promise.of(http.errorResponse(400, "Request body must contain an 'ids' array"));
            }

            List<String> ids = (List<String>) rawIds;
            Optional<String> batchErr = ApiInputValidator.validateDeleteBatch(ids);
            if (batchErr.isPresent()) return Promise.of(http.errorResponse(400, batchErr.get()));

            List<Promise<Map<String, Object>>> trackedDeletes = ids.stream()
                .map(id -> client.delete(resolvedTenant, collection, id)
                    .map(ignored -> {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("id", id);
                        result.put("success", true);
                        return result;
                    })
                    .then(Promise::of, e -> {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("id", id);
                        result.put("success", false);
                        result.put("error", e.getMessage());
                        return Promise.of(result);
                    }))
                .toList();

            return Promises.toList(trackedDeletes)
                .then(results -> {
                    List<String> deletedIds = results.stream()
                        .filter(r -> Boolean.TRUE.equals(r.get("success")))
                        .map(r -> (String) r.get("id"))
                        .toList();
                    List<Map<String, Object>> errors = results.stream()
                        .filter(r -> !Boolean.TRUE.equals(r.get("success")))
                        .map(r -> Map.of("id", r.get("id"), "error", r.get("error")))
                        .toList();

                    DataCloudClient.Event cdcEvent = DataCloudClient.Event.of("entity.batch-deleted", Map.of(
                        "collection", collection,
                        "count", deletedIds.size(),
                        "ids", deletedIds,
                        "operation", "batch-delete",
                        "totalRequested", ids.size(),
                        "errors", errors
                    ));
                    return client.appendEvent(resolvedTenant, cdcEvent)
                        .map(ignored -> {
                            wsBroadcaster.accept("collection.batch-deleted", Map.of(
                                "collection", collection,
                                "count",      deletedIds.size(),
                                "tenantId",   resolvedTenant
                            ));
                            return http.jsonResponse(Map.of(
                                "deleted", deletedIds.size(),
                                "collection", collection,
                                "ids", deletedIds,
                                "errors", errors,
                                "timestamp", Instant.now().toString()
                            ));
                        });
                })
                .then(Promise::of, e -> {
                    log.error("Batch delete failed for collection {}", collection, e);
                    return Promise.of(http.errorResponse(500, "Batch delete failed: " + e.getMessage()));
                });
        } catch (Exception e) {
            log.error("Error parsing batch delete request", e);
            return Promise.of(http.errorResponse(400, "Invalid batch request body: " + e.getMessage()));
        }
        });
    }

    // ==================== CDC Helpers (DC-AUD-010 / DC-AUD-023) ====================

    /**
     * Builds a full CDC event envelope for entity mutations.
     *
     * <p>The envelope contains enough metadata and data to replay history
     * from genesis via {@code handleGetEntityAsOf} and satisfy audit requirements.
     */
    private static Map<String, Object> buildCdcEnvelope(DataCloudClient.Entity entity, String operation) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("collection", entity.collection());
        envelope.put("id", entity.id());
        envelope.put("version", entity.version());
        envelope.put("operation", operation);
        envelope.put("eventType", "entity.mutated");
        envelope.put("timestamp", Instant.now().toString());
        // Full snapshot required for point-in-time replay
        envelope.put("data", entity.data());
        envelope.put("createdAt", entity.createdAt() != null ? entity.createdAt().toString() : null);
        envelope.put("updatedAt", entity.updatedAt() != null ? entity.updatedAt().toString() : null);
        return envelope;
    }

    private static Map<String, Object> buildDeleteCdcEnvelope(DataCloudClient.Entity entity, String collection, String id) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("collection", collection);
        envelope.put("id", id);
        envelope.put("operation", "delete");
        envelope.put("eventType", "entity.deleted");
        envelope.put("timestamp", Instant.now().toString());
        envelope.put("tombstone", true);
        if (entity != null) {
            envelope.put("data", entity.data());
            envelope.put("version", entity.version());
            envelope.put("createdAt", entity.createdAt() != null ? entity.createdAt().toString() : null);
            envelope.put("updatedAt", entity.updatedAt() != null ? entity.updatedAt().toString() : null);
        }
        return envelope;
    }

    // ==================== Full-Text Search ====================

    /**
     * Handles full-text entity search via OpenSearch.
     *
     * <p>Returns {@code 501 Not Implemented} when no {@link OpenSearchConnector} is
     * configured. Returns {@code 400 Bad Request} when required query params are
     * absent or invalid.
     *
     * @param request the incoming HTTP request
     * @return a Promise resolving to the HTTP response
     *
     * @doc.type     method
     * @doc.purpose  REST handler for OpenSearch full-text entity search
     * @doc.layer    product
     * @doc.pattern  Handler
     */
    public Promise<HttpResponse> handleFullTextSearch(HttpRequest request) {
        if (openSearchConnector == null) {
            return Promise.of(http.errorResponse(501,
                "Full-text search is not enabled; configure an OpenSearchConnector"));
        }

        String collection = request.getPathParameter("collection");
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

        String q = request.getQueryParameter("q");
        Optional<String> qErr = ApiInputValidator.validateSearchQuery(q);
        if (qErr.isPresent()) return Promise.of(http.errorResponse(400, qErr.get()));

        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));

        ApiInputValidator.LimitResult limitResult = ApiInputValidator.validateLimit(request.getQueryParameter("limit"), 20);
        if (!limitResult.isValid()) return Promise.of(http.errorResponse(400, limitResult.getError().orElseThrow()));
        int limit  = limitResult.getValue();
        int offset = Math.max(HttpHandlerSupport.parseIntParam(request.getQueryParameter("offset"), 0), 0);

        QuerySpec spec = QuerySpec.builder()
            .filter(q)
            .limit(limit)
            .offset(offset)
            .build();

        log.debug("[search] tenant={} collection={} q='{}' limit={} offset={}", tenantId, collection, q, limit, offset);

        return openSearchConnector.query((java.util.UUID) null, tenantId, spec)
            .map(qr -> {
                List<Map<String, Object>> results = qr.entities().stream()
                    .map(e -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", e.getId() != null ? e.getId().toString() : null);
                        item.put("collectionName", e.getCollectionName());
                        item.put("data", e.getData());
                        item.put("version", e.getVersion());
                        item.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
                        item.put("updatedAt", e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null);
                        return item;
                    })
                    .toList();

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("results",     results);
                body.put("total",       qr.total());
                body.put("limit",       qr.limit());
                body.put("offset",      qr.offset());
                body.put("hasMore",     qr.hasMore());
                body.put("executionMs", qr.executionTimeMs());
                return http.jsonResponse(body);
            })
            .mapException(e -> {
                log.error("[search] tenant={} collection={} q='{}': {}",
                    tenantId, collection, q, e.getMessage(), e);
                return new HttpException("Search failed: " + e.getMessage(), e);
            });
    }

    /**
     * GET /api/v1/entities/:collection/:id?asOf={ISO-8601} — B14 point-in-time query.
     *
     * <p>Fetches the current entity from storage, then overlays any event-log entries that were
     * created before or exactly at the requested timestamp. The reconstruction is additive:
     * each event whose payload contains a {@code "data"} map is merged in timestamp order so
     * that the last writer wins on a per-field basis. When no events are found before the
     * requested time, the current entity state is returned as-is (best-effort; the store may
     * not have been persisted with full CDC coverage).
     *
     * @param request the incoming HTTP request
     * @return 200 with the reconstructed entity snapshot, 400 on validation error,
     *         404 when entity not found now or has no events before the timestamp
     *
     * @doc.type method
     * @doc.purpose Return entity state at a specific point-in-time
     * @doc.layer product
     * @doc.pattern Handler
     */
    public Promise<HttpResponse> handleGetEntityAsOf(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        String id = request.getPathParameter("id");
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String asOfParam = request.getQueryParameter("asOf");

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));
        Optional<String> idErr = ApiInputValidator.validateId(id);
        if (idErr.isPresent()) return Promise.of(http.errorResponse(400, idErr.get()));
        if (asOfParam == null || asOfParam.isBlank()) {
            return Promise.of(http.errorResponse(400, "'asOf' query parameter is required (ISO-8601 instant)"));
        }

        final Instant asOf;
        try {
            asOf = Instant.parse(asOfParam);
        } catch (DateTimeParseException e) {
            return Promise.of(http.errorResponse(400, "Invalid 'asOf' value — expected ISO-8601 instant, e.g. 2026-01-15T12:00:00Z"));
        }

        // Genesis-forward reconstruction: replay all CDC events for this entity from the
        // earliest event up to asOf, starting with an empty map. This correctly handles
        // entities that were created, mutated, deleted, or recreated before asOf.
        DataCloudClient.EventQuery timeQuery = new DataCloudClient.EventQuery(
                List.of(),        // all types — let collection + entity-id filtering happen in stream
                null,             // no lower bound
                asOf,             // upper bound inclusive
                1_000             // cap at 1 000 events per request
        );

        return client.queryEvents(tenantId, timeQuery).map(events -> {
            // Filter only events that reference this entity and collection
            List<DataCloudClient.Event> entityEvents = events.stream()
                    .filter(ev -> {
                        Map<String, Object> p = ev.payload();
                        if (!collection.equals(p.get("collection"))) return false;
                        // Individual save / delete
                        if (id.equals(p.get("id"))) return true;
                        // Batch-saved: check entities list for matching id
                        if ("entity.batch-saved".equals(ev.type())) {
                            Object ents = p.get("entities");
                            if (ents instanceof List<?> list) {
                                for (Object e : list) {
                                    if (e instanceof Map<?, ?> m && id.equals(m.get("id"))) return true;
                                }
                            }
                        }
                        // Batch-deleted: check ids list
                        if ("entity.batch-deleted".equals(ev.type())) {
                            Object ids = p.get("ids");
                            return ids instanceof List<?> list && list.contains(id);
                        }
                        return false;
                    })
                    .toList();

            // Sort ascending by timestamp so earliest events come first (genesis-forward)
            List<DataCloudClient.Event> sorted = entityEvents.stream()
                    .sorted((a, b) -> {
                        Instant ta = a.timestamp() != null ? a.timestamp() : Instant.EPOCH;
                        Instant tb = b.timestamp() != null ? b.timestamp() : Instant.EPOCH;
                        return ta.compareTo(tb);
                    })
                    .toList();

            Map<String, Object> reconstructed = new LinkedHashMap<>();
            boolean isDeleted = false;
            boolean everExisted = false;
            long version = 0;
            Instant lastMutationAt = null;
            int appliedEvents = 0;

            for (DataCloudClient.Event ev : sorted) {
                Map<String, Object> p = ev.payload();
                String eventType = ev.type();

                if ("entity.saved".equals(eventType) && id.equals(p.get("id"))) {
                    Object dataObj = p.get("data");
                    if (dataObj instanceof Map<?, ?> dataMap) {
                        reconstructed.clear();
                        for (Map.Entry<?, ?> e : dataMap.entrySet()) {
                            reconstructed.put(String.valueOf(e.getKey()), e.getValue());
                        }
                        isDeleted = false;
                        everExisted = true;
                    }
                    Object ver = p.get("version");
                    if (ver instanceof Number n) version = n.longValue();
                    lastMutationAt = ev.timestamp();
                    appliedEvents++;
                } else if ("entity.deleted".equals(eventType) && id.equals(p.get("id"))) {
                    reconstructed.clear();
                    isDeleted = true;
                    everExisted = true;
                    lastMutationAt = ev.timestamp();
                    appliedEvents++;
                } else if ("entity.batch-saved".equals(eventType)) {
                    Object ents = p.get("entities");
                    if (ents instanceof List<?> list) {
                        for (Object e : list) {
                            if (e instanceof Map<?, ?> m && id.equals(m.get("id"))) {
                                Object dataObj = m.get("data");
                                if (dataObj instanceof Map<?, ?> dataMap) {
                                    reconstructed.clear();
                                    for (Map.Entry<?, ?> entry : dataMap.entrySet()) {
                                        reconstructed.put(String.valueOf(entry.getKey()), entry.getValue());
                                    }
                                    isDeleted = false;
                                    everExisted = true;
                                }
                                Object ver = m.get("version");
                                if (ver instanceof Number n) version = n.longValue();
                                lastMutationAt = ev.timestamp();
                                appliedEvents++;
                                break;
                            }
                        }
                    }
                } else if ("entity.batch-deleted".equals(eventType)) {
                    Object ids = p.get("ids");
                    if (ids instanceof List<?> list && list.contains(id)) {
                        reconstructed.clear();
                        isDeleted = true;
                        everExisted = true;
                        lastMutationAt = ev.timestamp();
                        appliedEvents++;
                    }
                }
            }

            if (!everExisted) {
                return http.errorResponse(404, "Entity not found at " + asOf + ": " + id);
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", id);
            body.put("collection", collection);
            body.put("data", reconstructed);
            body.put("version", version);
            body.put("asOf", asOf.toString());
            body.put("appliedEvents", appliedEvents);
            body.put("reconstructionMethod", "genesis-forward");
            body.put("lastMutationAt", lastMutationAt != null ? lastMutationAt.toString() : null);
            if (isDeleted) {
                body.put("deletedAt", lastMutationAt != null ? lastMutationAt.toString() : null);
                body.put("tombstone", true);
            }
            return http.jsonResponse(body);
        }).mapException(e -> {
            log.error("[asOf] tenant={} collection={} id={} asOf={}: {}",
                    tenantId, collection, id, asOf, e.getMessage(), e);
            return new HttpException("Point-in-time query failed: " + e.getMessage(), e);
        });
    }

    /**
     * Lists all collections registered for the tenant.
     *
     * <p>Implements the first-class collection registry endpoint (dc-s4) that
     * exposes what entity collections exist for a tenant so the Data Explorer
     * can drive navigation without assuming hard-coded names.</p>
     */
    public Promise<HttpResponse> handleListCollections(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }

        // Prefer the backing EntityStore if it supports listCollections
        try {
            EntityStore store = client.entityStore();
            return store.listCollections(TenantContext.of(tenantId))
                .map(collections -> {
                    List<Map<String, Object>> entries = collections.stream()
                        .map(name -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("name", name);
                            m.put("systemCollection", name.startsWith("dc_"));
                            return m;
                        })
                        .toList();
                    return http.jsonResponse(Map.of(
                        "tenantId", tenantId,
                        "collections", entries,
                        "total", entries.size()
                    ));
                });
        } catch (Exception e) {
            log.error("[listCollections] tenant={} failed: {}", tenantId, e.getMessage(), e);
            return Promise.of(http.errorResponse(500, "Collection registry query failed: " + e.getMessage()));
        }
    }
}
