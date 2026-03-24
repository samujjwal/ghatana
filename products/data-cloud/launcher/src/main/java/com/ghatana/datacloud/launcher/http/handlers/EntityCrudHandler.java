package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.entity.validation.EntitySchemaValidator;
import com.ghatana.datacloud.entity.validation.ValidationResult;
import com.ghatana.datacloud.analytics.export.EntityExportService;
import com.ghatana.datacloud.analytics.anomaly.StatisticalAnomalyDetector;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability.AnomalyContext;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability.DetectionType;
import com.ghatana.datacloud.entity.storage.QuerySpec;
import com.ghatana.datacloud.infrastructure.storage.OpenSearchConnector;
import com.ghatana.datacloud.launcher.http.ApiInputValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.*;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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

    private EntitySchemaValidator schemaValidator;
    private EntityExportService exportService;
    private StatisticalAnomalyDetector anomalyDetector;
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

    public EntityCrudHandler withExportService(EntityExportService service) {
        this.exportService = service;
        return this;
    }

    public EntityCrudHandler withAnomalyDetector(StatisticalAnomalyDetector detector) {
        this.anomalyDetector = detector;
        return this;
    }

    public EntityCrudHandler withOpenSearchConnector(OpenSearchConnector connector) {
        this.openSearchConnector = connector;
        return this;
    }

    // ==================== Entity CRUD ====================

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleSaveEntity(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        String tenantId = http.resolveTenantId(request);
        final String resolvedTenantId = tenantId;

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(resolvedTenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

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

                return client.save(resolvedTenantId, collection, data)
                    .then(entity -> {
                        DataCloudClient.Event cdcEvent = DataCloudClient.Event.of("entity.saved", Map.of(
                            "collection", entity.collection(),
                            "id", entity.id(),
                            "version", entity.version(),
                            "operation", "upsert"
                        ));
                        return client.appendEvent(resolvedTenantId, cdcEvent)
                            .map(ignored -> {
                                wsBroadcaster.accept("collection.saved", Map.of(
                                    "entityId",  entity.id(),
                                    "collection", entity.collection(),
                                    "tenantId",  resolvedTenantId
                                ));
                                return entity;
                            });
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
        });
    }

    public Promise<HttpResponse> handleGetEntity(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        String id = request.getPathParameter("id");
        String tenantId = http.resolveTenantId(request);

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));
        Optional<String> idErr = ApiInputValidator.validateId(id);
        if (idErr.isPresent()) return Promise.of(http.errorResponse(400, idErr.get()));

        return client.findById(tenantId, collection, id)
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
            });
    }

    public Promise<HttpResponse> handleQueryEntities(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        String tenantId = http.resolveTenantId(request);

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

        ApiInputValidator.LimitResult limitResult = ApiInputValidator.validateLimit(request.getQueryParameter("limit"), 100);
        if (!limitResult.isValid()) return Promise.of(http.errorResponse(400, limitResult.getError().orElseThrow()));
        int limit = limitResult.getValue();

        DataCloudClient.Query query = DataCloudClient.Query.limit(limit);

        return client.query(tenantId, collection, query)
            .map(entities -> http.jsonResponse(Map.of(
                "entities", entities.stream().map(e -> Map.of(
                    "id", e.id(),
                    "collection", e.collection(),
                    "data", e.data(),
                    "version", e.version()
                )).toList(),
                "count", entities.size(),
                "timestamp", Instant.now().toString()
            )));
    }

    public Promise<HttpResponse> handleDeleteEntity(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        String id = request.getPathParameter("id");
        String tenantId = http.resolveTenantId(request);
        final String resolvedTenantId = tenantId;

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(resolvedTenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));
        Optional<String> idErr = ApiInputValidator.validateId(id);
        if (idErr.isPresent()) return Promise.of(http.errorResponse(400, idErr.get()));

        return client.findById(resolvedTenantId, collection, id)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.of(http.errorResponse(404, "Entity not found: " + id));
                }
                return client.delete(resolvedTenantId, collection, id)
                    .then(v -> {
                        DataCloudClient.Event cdcEvent = DataCloudClient.Event.of("entity.deleted", Map.of(
                            "collection", collection,
                            "id", id,
                            "operation", "delete"
                        ));
                        return client.appendEvent(resolvedTenantId, cdcEvent)
                            .map(ignored -> {
                                wsBroadcaster.accept("collection.deleted", Map.of(
                                    "entityId",  id,
                                    "collection", collection,
                                    "tenantId",  resolvedTenantId
                                ));
                                return v;
                            });
                    })
                    .map(v -> http.jsonResponse(Map.of(
                        "deleted", true,
                        "id", id,
                        "collection", collection,
                        "timestamp", Instant.now().toString()
                    )));
            });
    }

    // ==================== Bulk Entity Endpoints ====================

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleBatchSaveEntities(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        String tenantId = http.resolveTenantId(request);

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
        String tenantId = http.resolveTenantId(request);

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

    // ==================== Export ====================

    public Promise<HttpResponse> handleExportEntities(HttpRequest request) {
        if (exportService == null) {
            return Promise.of(http.errorResponse(501, "Export service not configured on this server"));
        }

        String collection = request.getPathParameter("collection");
        String tenantId   = http.resolveTenantId(request);

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

        String format = request.getQueryParameter("format");
        if (format == null) format = "csv";

        int limit = 10_000;
        String limitStr = request.getQueryParameter("limit");
        if (limitStr != null) {
            try {
                limit = Integer.parseInt(limitStr);
            } catch (NumberFormatException e) {
                return Promise.of(http.errorResponse(400, "Invalid 'limit' query parameter: " + limitStr));
            }
        }

        final String finalTenant     = tenantId;
        final String finalCollection = collection;
        final int    finalLimit      = limit;

        if ("ndjson".equalsIgnoreCase(format)) {
            return exportService.exportNdjson(finalTenant, finalCollection, Map.of(), finalLimit)
                    .map(data -> HttpResponse.ok200()
                            .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("application/x-ndjson; charset=utf-8"))
                            .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(http.corsAllowOrigin()))
                            .withBody(data.getBytes(StandardCharsets.UTF_8))
                            .build())
                    .then(Promise::of, e -> {
                        log.error("NDJSON export failed tenant={} collection={}", finalTenant, finalCollection, e);
                        return Promise.of(http.errorResponse(500, "Export failed: " + e.getMessage()));
                    });
        } else {
            return exportService.exportCsv(finalTenant, finalCollection, Map.of(), finalLimit)
                    .map(data -> HttpResponse.ok200()
                            .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("text/csv; charset=utf-8"))
                            .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(http.corsAllowOrigin()))
                            .withBody(data.getBytes(StandardCharsets.UTF_8))
                            .build())
                    .then(Promise::of, e -> {
                        log.error("CSV export failed tenant={} collection={}", finalTenant, finalCollection, e);
                        return Promise.of(http.errorResponse(500, "Export failed: " + e.getMessage()));
                    });
        }
    }

    // ==================== Anomaly Detection ====================

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleDetectAnomalies(HttpRequest request) {
        if (anomalyDetector == null) {
            return Promise.of(http.errorResponse(501, "Anomaly detection not configured on this server"));
        }

        String collection = request.getPathParameter("collection");
        String tenantId   = http.resolveTenantId(request);

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

        final String finalTenant     = tenantId;
        final String finalCollection = collection;

        final Map<String, Object> responseEnvelope = Map.of(
                "collection", finalCollection,
                "tenant", finalTenant,
                "timestamp", Instant.now().toString());

        return request.loadBody().then(buf -> {
            try {
                String rawBody = buf.getString(StandardCharsets.UTF_8);

                // Mutable holders so lambda-captured variables remain effectively final.
                double[] threshold   = {StatisticalAnomalyDetector.DEFAULT_Z_THRESHOLD};
                DetectionType[] type = {DetectionType.DATA_QUALITY};

                if (rawBody != null && !rawBody.isBlank()) {
                    Map<String, Object> bodyMap = http.objectMapper().readValue(rawBody, Map.class);
                    if (bodyMap.containsKey("threshold")) {
                        Object t = bodyMap.get("threshold");
                        threshold[0] = t instanceof Number n ? n.doubleValue() : Double.parseDouble(t.toString());
                    }
                    if (bodyMap.containsKey("detectionType")) {
                        try {
                            type[0] = DetectionType.valueOf(bodyMap.get("detectionType").toString());
                        } catch (IllegalArgumentException e) {
                            return Promise.of(http.errorResponse(400, "Unknown detectionType: " + bodyMap.get("detectionType")));
                        }
                    }
                }

                AnomalyContext ctx = AnomalyContext.builder()
                        .tenantId(finalTenant)
                        .collectionName(finalCollection)
                        .detectionType(type[0])
                        .threshold(threshold[0])
                        .build();

                return anomalyDetector.detect(ctx)
                        .map(anomalies -> {
                            Map<String, Object> body2 = new LinkedHashMap<>(responseEnvelope);
                            body2.put("count", anomalies.size());
                            body2.put("anomalies", anomalies);
                            return http.jsonResponse(body2);
                        })
                        .then(Promise::of, e -> {
                            log.error("Anomaly detection failed tenant={} collection={}", finalTenant, finalCollection, e);
                            return Promise.of(http.errorResponse(500, "Anomaly detection failed: " + e.getMessage()));
                        });
            } catch (Exception e) {
                log.error("Error processing anomaly detection request", e);
                return Promise.of(http.errorResponse(400, "Invalid request body: " + e.getMessage()));
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

        String tenantId = http.resolveTenantId(request);
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
}
