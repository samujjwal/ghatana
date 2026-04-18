package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.entity.validation.EntitySchemaValidator;
import com.ghatana.datacloud.entity.validation.ValidationResult;
import com.ghatana.datacloud.entity.storage.QuerySpec;
import com.ghatana.datacloud.infrastructure.storage.OpenSearchConnector;
import com.ghatana.datacloud.launcher.http.ApiInputValidator;
import com.ghatana.datacloud.launcher.http.TraceSpanSupport;
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
 * @doc.type class
 * @doc.purpose Entity CRUD, batch, export, and anomaly HTTP handlers
 * @doc.layer product
 * @doc.pattern Handler
 */
public class EntityCrudHandler {

    private static final Logger log = LoggerFactory.getLogger(EntityCrudHandler.class);

    private final DataCloudClient client;
    private final HttpHandlerSupport http;
    private final BiConsumer<String, Map<String, Object>> wsBroadcaster;
    private TraceSpanSupport traceSupport = TraceSpanSupport.disabled();
    private SemanticSearchHandler semanticSearchHandler;

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

    public EntityCrudHandler withSemanticSearchHandler(SemanticSearchHandler semanticSearchHandler) {
        this.semanticSearchHandler = semanticSearchHandler;
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
                        DataCloudClient.Event cdcEvent = DataCloudClient.Event.of("entity.saved", Map.of(
                            "collection", entity.collection(),
                            "id", entity.id(),
                            "version", entity.version(),
                            "operation", "upsert"
                        ));
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
                            .then(savedEntity -> semanticSearchHandler == null
                                ? Promise.of(savedEntity)
                                : semanticSearchHandler.indexEntity(resolvedTenantId, collection, savedEntity)
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

        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
                request,
                tenantId,
                "datacloud.http.entity.query",
                traceSupport.requestSpanId(request),
                Map.of("collection", collection, "limit", limit));

        DataCloudClient.Query query = DataCloudClient.Query.limit(limit);

        return traceSupport.trace(
            request,
            tenantId,
            "datacloud.entity.store.query",
            handlerSpan.spanId(),
            Map.of("collection", collection, "limit", limit),
            () -> client.query(tenantId, collection, query))
            .map(entities -> http.jsonResponse(Map.of(
                "entities", entities.stream().map(e -> Map.of(
                    "id", e.id(),
                    "collection", e.collection(),
                    "data", e.data(),
                    "version", e.version(),
                    "createdAt", e.createdAt() != null ? e.createdAt().toString() : null,
                    "updatedAt", e.updatedAt() != null ? e.updatedAt().toString() : null
                )).toList(),
                "count", entities.size(),
                "timestamp", Instant.now().toString()
            ))).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
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
                return traceSupport.trace(
                    request,
                    resolvedTenantId,
                    "datacloud.entity.store.delete",
                    handlerSpan.spanId(),
                    Map.of("collection", collection, "entity.id", id),
                    () -> client.delete(resolvedTenantId, collection, id))
                    .then(v -> {
                        DataCloudClient.Event cdcEvent = DataCloudClient.Event.of("entity.deleted", Map.of(
                            "collection", collection,
                            "id", id,
                            "operation", "delete"
                        ));
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
                            .then(deleted -> semanticSearchHandler == null
                                ? Promise.of(deleted)
                                : semanticSearchHandler.deleteEntity(resolvedTenantId, id)
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
                    DataCloudClient.Event cdcEvent = DataCloudClient.Event.of("entity.batch-saved", Map.of(
                        "collection", collection,
                        "count", savedEntities.size(),
                        "ids", ids,
                        "operation", "batch-upsert"
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

            List<Promise<Void>> deletePromises = ids.stream()
                .map(id -> client.delete(resolvedTenant, collection, id))
                .toList();

            return Promises.all(deletePromises)
                .then(v -> {
                    DataCloudClient.Event cdcEvent = DataCloudClient.Event.of("entity.batch-deleted", Map.of(
                        "collection", collection,
                        "count", ids.size(),
                        "ids", ids,
                        "operation", "batch-delete"
                    ));
                    return client.appendEvent(resolvedTenant, cdcEvent)
                        .map(ignored -> {
                            wsBroadcaster.accept("collection.batch-deleted", Map.of(
                                "collection", collection,
                                "count",      ids.size(),
                                "tenantId",   resolvedTenant
                            ));
                            return http.jsonResponse(Map.of(
                                "deleted", ids.size(),
                                "collection", collection,
                                "ids", ids,
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

        // First fetch the current entity to confirm it exists
        return client.findById(tenantId, collection, id).then(optEntity -> {
            if (optEntity.isEmpty()) {
                return Promise.of(http.errorResponse(404, "Entity not found: " + id));
            }
            DataCloudClient.Entity current = optEntity.get();

            // Fetch all events up to asOf and reconstruct entity state at that point in time.
            // The EventQuery endTime is inclusive of asOf — we replay all CDC mutations up to
            // and including that instant.
            DataCloudClient.EventQuery timeQuery = new DataCloudClient.EventQuery(
                    List.of(),        // all types — let collection + entity-id filtering happen in stream
                    null,             // no lower bound
                    asOf,             // upper bound inclusive
                    1_000             // cap at 1 000 events per request
            );

            return client.queryEvents(tenantId, timeQuery).map(events -> {
                // Filter only events that reference this entity and collection
                List<DataCloudClient.Event> entityEvents = events.stream()
                        .filter(ev -> id.equals(ev.payload().get("entityId"))
                                && collection.equals(ev.payload().get("collection")))
                        .toList();

                // Reconstruct data: start from current state, fold in each event's "data" patch
                // in ascending timestamp order so earliest mutations come first.
                Map<String, Object> reconstructed = new LinkedHashMap<>(current.data());
                List<DataCloudClient.Event> sorted = entityEvents.stream()
                        .sorted((a, b) -> {
                            Instant ta = a.timestamp() != null ? a.timestamp() : Instant.EPOCH;
                            Instant tb = b.timestamp() != null ? b.timestamp() : Instant.EPOCH;
                            return ta.compareTo(tb);
                        })
                        .toList();

                long version = current.version();
                int appliedEvents = 0;
                for (DataCloudClient.Event ev : sorted) {
                    Object dataPatch = ev.payload().get("data");
                    if (dataPatch instanceof Map<?, ?> patch) {
                        for (Map.Entry<?, ?> entry : patch.entrySet()) {
                            reconstructed.put(String.valueOf(entry.getKey()), entry.getValue());
                        }
                        appliedEvents++;
                    }
                    Object evVersion = ev.payload().get("version");
                    if (evVersion instanceof Number n) {
                        version = n.longValue();
                    }
                }

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("id", id);
                body.put("collection", collection);
                body.put("data", reconstructed);
                body.put("version", version);
                body.put("asOf", asOf.toString());
                body.put("appliedEvents", appliedEvents);
                body.put("currentVersionAt", current.updatedAt() != null ? current.updatedAt().toString() : null);
                return http.jsonResponse(body);
            }).mapException(e -> {
                log.error("[asOf] tenant={} collection={} id={} asOf={}: {}",
                        tenantId, collection, id, asOf, e.getMessage(), e);
                return new HttpException("Point-in-time query failed: " + e.getMessage(), e);
            });
        });
    }
}
