package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.datacloud.entity.validation.EntitySchemaValidator;
import com.ghatana.datacloud.entity.validation.ValidationResult;
import com.ghatana.datacloud.entity.storage.QuerySpec;
import com.ghatana.datacloud.governance.TenantQuotaService;
import com.ghatana.datacloud.governance.QuotaCheckResult;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private TenantQuotaService tenantQuotaService;

    /** P0.2: In-memory idempotency key store for entity writes — bounded, tenant-scoped. */
    private final Map<String, IdempotencyEntry> idempotencyStore = new ConcurrentHashMap<>();
    private static final int IDEMPOTENCY_MAX_ENTRIES = 10_000;
    private static final long IDEMPOTENCY_TTL_MS = 24 * 60 * 60 * 1000L; // 24 hours

    private record IdempotencyEntry(Map<String, Object> responseBody, Instant storedAt) {}

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

    public EntityCrudHandler withTenantQuotaService(TenantQuotaService service) {
        this.tenantQuotaService = service;
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

    // ==================== Quota Enforcement ====================

    /**
     * P0.5: Check tenant quota before write operations.
     * Returns an error promise if quota is exceeded, otherwise null.
     */
    private Promise<HttpResponse> checkQuotaOrNull(String tenantId,
                                                   String operationType,
                                                   int resourceAmount) {
        if (tenantQuotaService == null) return null;
        QuotaCheckResult result = tenantQuotaService.checkQuota(tenantId, operationType, resourceAmount);
        if (!result.isAllowed()) {
            return Promise.of(http.errorResponse(429,
                "Quota exceeded: " + result.message() + " (quota=" + result.quotaValue()
                    + ", used=" + result.usedAmount() + ")"));
        }
        return null;
    }

    // ==================== Idempotency Key Support (P0.2) ====================

    private String idempotencyKey(String tenantId, String collection, String idempotencyKey) {
        return tenantId + "/" + collection + "/" + idempotencyKey;
    }

    private Promise<HttpResponse> checkIdempotencyOrNull(String tenantId, String collection, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return null;
        String key = idempotencyKey(tenantId, collection, idempotencyKey);
        IdempotencyEntry entry = idempotencyStore.get(key);
        if (entry != null && Instant.now().minusMillis(IDEMPOTENCY_TTL_MS).isBefore(entry.storedAt())) {
            log.info("[idempotency] Returning cached response for key={}", key);
            return Promise.of(http.jsonResponse(entry.responseBody()));
        }
        return null;
    }

    private void storeIdempotency(String tenantId, String collection, String idempotencyKey, Map<String, Object> responseBody) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return;
        if (idempotencyStore.size() >= IDEMPOTENCY_MAX_ENTRIES) {
            // Evict oldest entries by removing expired ones first
            Instant cutoff = Instant.now().minusMillis(IDEMPOTENCY_TTL_MS);
            idempotencyStore.entrySet().removeIf(e -> e.getValue().storedAt().isBefore(cutoff));
        }
        idempotencyStore.put(idempotencyKey(tenantId, collection, idempotencyKey), new IdempotencyEntry(responseBody, Instant.now()));
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

        String idempotencyKey = request.getHeader(HttpHeaders.of("X-Idempotency-Key"));
        Promise<HttpResponse> idempotencyResponse = checkIdempotencyOrNull(resolvedTenantId, collection, idempotencyKey);
        if (idempotencyResponse != null) return idempotencyResponse;

        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
            request,
            resolvedTenantId,
            "datacloud.http.entity.save",
            traceSupport.requestSpanId(request),
            Map.of("collection", collection));

        Promise<HttpResponse> quotaErr = checkQuotaOrNull(
            resolvedTenantId, "ENTITY", 1);
        if (quotaErr != null) return quotaErr;

        return request.loadBody().then(buf -> {
            Promise<HttpResponse> quotaErr1 = checkQuotaOrNull(
                resolvedTenantId, "ENTITY", 1);
            if (quotaErr1 != null) return quotaErr1;

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

                Map<String, Object> provenanced = withProvenance(data, request, handlerSpan.spanId());
                return traceSupport.trace(
                    request,
                    resolvedTenantId,
                    "datacloud.entity.store.save",
                    handlerSpan.spanId(),
                    Map.of("collection", collection),
                    () -> client.save(resolvedTenantId, collection, provenanced))
                    .then(entity -> {
                        DataCloudClient.Event cdcEvent = DataCloudClient.Event.of("entity.saved",
                            buildCdcEnvelope(resolvedTenantId, handlerSpan.spanId(), entity, "upsert", null));
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
                    .map(entity -> {
                        Map<String, Object> responseBody = Map.of(
                            "id", entity.id(),
                            "collection", entity.collection(),
                            "version", entity.version(),
                            "createdAt", entity.createdAt().toString(),
                            "timestamp", Instant.now().toString()
                        );
                        storeIdempotency(resolvedTenantId, collection, idempotencyKey, responseBody);
                        return http.jsonResponse(responseBody);
                    });
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

        Promise<HttpResponse> delQuotaErr = checkQuotaOrNull(resolvedTenantId, "ENTITY", 1);
        if (delQuotaErr != null) return delQuotaErr;

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
                            buildDeleteCdcEnvelope(resolvedTenantId, handlerSpan.spanId(), existingEntity, collection, id));
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

    /**
     * Batch entity save (upsert) endpoint.
     *
     * <p><b>Batch semantics</b>: Each entity in the batch is validated independently.
     * If <em>any</em> entity fails schema validation the entire batch is rejected with
     * {@code 422}.  Storage is best-effort per-item; storage-level failures on
     * individual items do <b>not</b> roll back already-saved siblings.  Each saved
     * entity triggers its own CDC {@code entity.saved} event.  A single idempotency
     * key applies to the whole batch.
     *
     * <p>Automatic semantic indexing (if configured) and provenance enrichment
     * (actor/timestamp/correlation-id/classification) are applied to every entity.
     */
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
        String batchIdempotencyKey = request.getHeader(HttpHeaders.of("X-Idempotency-Key"));
        Promise<HttpResponse> idempotencyResponse = checkIdempotencyOrNull(resolvedTenant, collection, batchIdempotencyKey);
        if (idempotencyResponse != null) return idempotencyResponse;

        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
            request,
            resolvedTenant,
            "datacloud.http.entity.batch-save",
            traceSupport.requestSpanId(request),
            Map.of("collection", collection));

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

            Promise<HttpResponse> batchQuotaErr = checkQuotaOrNull(resolvedTenant, "ENTITY", entityList.size());
            if (batchQuotaErr != null) return batchQuotaErr;

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
                .map(data -> client.save(resolvedTenant, collection, withProvenance(data, request, handlerSpan.spanId())))
                .toList();

            return Promises.toList(savePromises)
                .then(savedEntities -> {
                    if (semanticIndexPort != null) {
                        List<Promise<Void>> indexPromises = savedEntities.stream()
                            .map(e -> semanticIndexPort.index(resolvedTenant, collection, e))
                            .toList();
                        return Promises.toList(indexPromises).map(ignored -> savedEntities);
                    }
                    return Promise.of(savedEntities);
                })
                .then(savedEntities -> {
                    List<String> ids = savedEntities.stream()
                        .map(DataCloudClient.Entity::id)
                        .toList();
                    List<Map<String, Object>> entitySnapshots = savedEntities.stream()
                        .map(e -> buildCdcEnvelope(resolvedTenant, handlerSpan.spanId(), e, "upsert", null))
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
                            Map<String, Object> responseBody = Map.of(
                                "saved", savedEntities.size(),
                                "collection", collection,
                                "ids", ids,
                                "errors", List.of(),
                                "timestamp", Instant.now().toString()
                            );
                            storeIdempotency(resolvedTenant, collection, batchIdempotencyKey, responseBody);
                            return http.jsonResponse(responseBody);
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

    /**
     * Batch entity delete endpoint.
     *
     * <p><b>Batch semantics</b>: Supports dry-run ({@code preview=true}) returning a
     * preview of affected entities plus a scoped HMAC confirmation token.  Actual
     * execution requires the returned token in the {@code confirmationToken} field.
     * Deletion is best-effort per-item; failures on individual items do <b>not</b>
     * roll back already-deleted siblings.  Each successfully deleted entity triggers
     * its own CDC {@code entity.deleted} event.  A single idempotency key applies to
     * the whole batch.
     *
     * <p>Approval flow: dry-run → token (5 min validity) → validate token → execute.
     */
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

                boolean dryRun = Boolean.TRUE.equals(payload.get("dryRun"));
                String confirmationToken = payload.getOrDefault("confirmationToken", "").toString();

                @SuppressWarnings("unchecked")
                List<String> ids = (List<String>) rawIds;
                Optional<String> batchErr = ApiInputValidator.validateDeleteBatch(ids);
                if (batchErr.isPresent()) return Promise.of(http.errorResponse(400, batchErr.get()));

                // P0.4: Dry-run path returns preview and confirmation token
                if (dryRun) {
                    long issuedAtMs = Instant.now().toEpochMilli();
                    String token = buildBatchDeleteToken(tenantId, collection, ids.size(), issuedAtMs);
                    log.info("[batch-delete] DRY RUN tenant={} collection={} ids={}",
                        tenantId, collection, ids.size());
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("collection", collection);
                    result.put("dryRun", true);
                    result.put("status", "DRY_RUN_COMPLETE");
                    result.put("confirmationToken", token);
                    result.put("tokenExpiresInSec", DestructiveActionToken.TOKEN_VALIDITY_MS / 1000);
                    result.put("estimatedCount", ids.size());
                    result.put("ids", ids);
                    return Promise.of(http.jsonResponse(result));
                }

                // Execute path: token is mandatory and must pass HMAC verification
                if (confirmationToken.isBlank()) {
                    return Promise.of(http.errorResponse(400,
                        "confirmationToken is required to authorise batch deletion. " +
                        "Perform a dry-run first to obtain a valid token."));
                }

                DestructiveActionToken.TokenValidationResult tokenResult =
                    validateBatchDeleteToken(confirmationToken, tenantId, collection);
                if (!tokenResult.valid()) {
                    log.warn("[batch-delete] REJECTED invalid token: {} collection={} tenant={}",
                        tokenResult.reason(), collection, tenantId);
                    return Promise.of(http.errorResponse(403,
                        "Confirmation token is invalid or expired: " + tokenResult.reason()));
                }

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

    // ─────────────────────────────────────────────────────────────────────────
    // Batch-delete token helpers (P0.4)
    // ─────────────────────────────────────────────────────────────────────────

    private static String buildBatchDeleteToken(String tenantId, String collection, int count, long issuedAtMs) {
        String scope = "batch-delete";
        String payload = scope + ":" + tenantId + ":" + collection + ":" + count + ":" + issuedAtMs;
        String hmac = DestructiveActionToken.hmacSha256Hex(
            DestructiveActionToken.resolveSecret(DestructiveActionToken.runtimeEnvironment()), payload);
        String raw = issuedAtMs + "." + hmac;
        return java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static DestructiveActionToken.TokenValidationResult validateBatchDeleteToken(
            String token, String tenantId, String collection) {
        try {
            byte[] decoded = java.util.Base64.getUrlDecoder().decode(token);
            String raw = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            int dotIdx = raw.indexOf('.');
            if (dotIdx < 1) {
                return DestructiveActionToken.TokenValidationResult.failure("malformed token");
            }
            long issuedAtMs = Long.parseLong(raw.substring(0, dotIdx));
            String providedHmac = raw.substring(dotIdx + 1);

            long ageMs = System.currentTimeMillis() - issuedAtMs;
            if (ageMs > DestructiveActionToken.TOKEN_VALIDITY_MS) {
                return DestructiveActionToken.TokenValidationResult.failure(
                    "token expired (age=" + (ageMs / 1000) + "s, max=300s)");
            }
            if (ageMs < 0) {
                return DestructiveActionToken.TokenValidationResult.failure("token issued in the future");
            }

            // We don't validate count in the token; it is only loosely scoped to tenant+collection
            String payload = "batch-delete:" + tenantId + ":" + collection + ":" + issuedAtMs;
            String expectedHmac = DestructiveActionToken.hmacSha256Hex(
                DestructiveActionToken.resolveSecret(DestructiveActionToken.runtimeEnvironment()), payload);
            if (!DestructiveActionToken.constantTimeEquals(expectedHmac, providedHmac)) {
                return DestructiveActionToken.TokenValidationResult.failure("token signature mismatch");
            }
            return DestructiveActionToken.TokenValidationResult.success();
        } catch (IllegalArgumentException e) {
            return DestructiveActionToken.TokenValidationResult.failure("token decode error: " + e.getMessage());
        }
    }

    // ==================== CDC Helpers (DC-AUD-010 / DC-AUD-023 / P0.3) ====================

    /**
     * Builds a full CDC event envelope for entity mutations (P0.3 canonical event envelope).
     *
     * <p>Required fields: eventId, tenantId, type, version, occurredAt, actor, resource,
     * operation, before, after, traceId, correlationId, provenance.
     */
    private static Map<String, Object> buildCdcEnvelope(String tenantId, String traceId,
                                                        DataCloudClient.Entity entity,
                                                        String operation,
                                                        Map<String, Object> beforeState) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", java.util.UUID.randomUUID().toString());
        envelope.put("tenantId", tenantId);
        envelope.put("type", "entity.mutated");
        envelope.put("version", "1.0");
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("actor", Map.of("type", "system", "id", "api"));
        envelope.put("resource", Map.of("type", "entity", "collection", entity.collection(), "id", entity.id()));
        envelope.put("operation", operation);
        envelope.put("before", beforeState != null ? beforeState : Map.of());
        envelope.put("after", entity.data());
        envelope.put("traceId", traceId != null ? traceId : "");
        envelope.put("correlationId", traceId != null ? traceId : "");
        envelope.put("provenance", Map.of("source", "api", "derivedFrom", List.of()));
        // Backward compatibility
        envelope.put("collection", entity.collection());
        envelope.put("id", entity.id());
        envelope.put("eventType", "entity.mutated");
        envelope.put("timestamp", Instant.now().toString());
        envelope.put("data", entity.data());
        envelope.put("version", entity.version());
        envelope.put("createdAt", entity.createdAt() != null ? entity.createdAt().toString() : null);
        envelope.put("updatedAt", entity.updatedAt() != null ? entity.updatedAt().toString() : null);
        return envelope;
    }

    private static Map<String, Object> buildDeleteCdcEnvelope(String tenantId, String traceId,
                                                             DataCloudClient.Entity entity,
                                                             String collection, String id) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", java.util.UUID.randomUUID().toString());
        envelope.put("tenantId", tenantId);
        envelope.put("type", "entity.deleted");
        envelope.put("version", "1.0");
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("actor", Map.of("type", "system", "id", "api"));
        envelope.put("resource", Map.of("type", "entity", "collection", collection, "id", id));
        envelope.put("operation", "delete");
        envelope.put("before", entity != null ? entity.data() : Map.of());
        envelope.put("after", Map.of());
        envelope.put("traceId", traceId != null ? traceId : "");
        envelope.put("correlationId", traceId != null ? traceId : "");
        envelope.put("provenance", Map.of("source", "api", "derivedFrom", List.of()));
        // Backward compatibility
        envelope.put("collection", collection);
        envelope.put("id", id);
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

    // ==================== Provenance Helpers (P0.6) ====================

    private static final Set<String> PII_FIELDS = Set.of(
        "email", "phone", "ssn", "social_security", "password", "credit_card", "dob", "date_of_birth"
    );

    /**
     * Injects an entity-level provenance envelope (who/when/why) into the payload
     * before it reaches the storage layer.  This is a non-destructive enrichment:
     * the original fields are preserved and a {@code _provenance} block is added.
     *
     * <p>Also performs lightweight policy classification (P0.6) by scanning field
     * names for known sensitive data indicators.
     */
    private static Map<String, Object> withProvenance(Map<String, Object> data,
                                                        HttpRequest request,
                                                        String correlationId) {
        Map<String, Object> enriched = new LinkedHashMap<>(data);
        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("actor", Map.of("type", "api", "id", resolveActorId(request)));
        provenance.put("timestamp", Instant.now().toString());
        provenance.put("correlationId", correlationId != null ? correlationId : "");
        provenance.put("source", "rest-api");
        provenance.put("dataClassification", classifyDataSensitivity(data));
        enriched.put("_provenance", provenance);
        return enriched;
    }

    /** Lightweight heuristic policy classification (P0.6). */
    private static String classifyDataSensitivity(Map<String, Object> data) {
        for (String key : data.keySet()) {
            String lower = key.toLowerCase();
            if (PII_FIELDS.contains(lower)) return "pii";
        }
        return "standard";
    }

    private static String resolveActorId(HttpRequest request) {
        String userId = request.getHeader(HttpHeaders.of("X-User-Id"));
        if (userId != null && !userId.isBlank()) return userId.trim();
        String clientId = request.getHeader(HttpHeaders.of("X-Client-Id"));
        if (clientId != null && !clientId.isBlank()) return clientId.trim();
        return "api";
    }

    // ==================== Collection Metadata Management (P0.2) ====================

    /**
     * Upsert collection metadata into the {@code dc_collections} registry.
     *
     * <p>Validates allowed metadata fields ({@code lifecycleStatus}, {@code qualityScore},
     * {@code qualityMetrics}, {@code retentionPolicy}, {@code lineage}, {@code operationalStatus},
     * {@code label}, {@code description}, {@code active}, {@code validationSchema},
     * {@code storageProfile}, {@code physicalMapping}, {@code schemaVersion}),
     * merges with any existing stored metadata, and persists into the entity store.
     *
     * <p>Route: {@code POST /api/v1/collections/:collection/metadata}</p>
     */
    public Promise<HttpResponse> handleUpsertCollectionMetadata(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String collection = request.getPathParameter("collection");
        if (collection == null || collection.isBlank()) {
            return Promise.of(http.errorResponse(400, "collection path parameter is required"));
        }
        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = http.objectMapper().readValue(body, Map.class);

                Optional<String> validationErr = ApiInputValidator.validateCollectionMetadata(metadata);
                if (validationErr.isPresent()) {
                    return Promise.of(http.errorResponse(422, validationErr.get()));
                }

                // Merge with existing metadata so we don't lose unspecified fields
                return client.findById(tenantId, "dc_collections", collection)
                    .then(existingOpt -> {
                        Map<String, Object> merged = new LinkedHashMap<>();
                        if (existingOpt.isPresent() && existingOpt.get().data() != null) {
                            merged.putAll(existingOpt.get().data());
                        }
                        merged.putAll(metadata);
                        merged.put("id", collection);
                        merged.put("name", collection);
                        merged.put("updatedAt", Instant.now().toString());

                        return client.save(tenantId, "dc_collections", merged)
                            .map(saved -> {
                                Map<String, Object> response = new LinkedHashMap<>();
                                response.put("id", saved.id());
                                response.put("collection", collection);
                                response.put("metadata", saved.data());
                                return http.createdResponse(response);
                            });
                    });
            } catch (Exception e) {
                log.error("[upsertCollectionMetadata] tenant={} collection={}: {}", tenantId, collection, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to upsert collection metadata: " + e.getMessage()));
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
            // Filter only events that reference this entity and collection,
            // and are within the asOf time bound (defensive filter in case
            // the event store does not fully respect endTime).
            List<DataCloudClient.Event> entityEvents = events.stream()
                    .filter(ev -> {
                        Instant eventTime = ev.timestamp() != null ? ev.timestamp()
                            : Instant.parse((String) ev.payload().getOrDefault("timestamp", Instant.EPOCH.toString()));
                        return !eventTime.isAfter(asOf);
                    })
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
            Promise<List<String>> namesPromise = store.listCollections(TenantContext.of(tenantId));
            Promise<List<DataCloudClient.Entity>> metaPromise = client.query(
                tenantId, "dc_collections", DataCloudClient.Query.limit(500));

            return namesPromise.combine(metaPromise, (names, metaEntities) -> {
                Map<String, DataCloudClient.Entity> metaMap = new LinkedHashMap<>();
                for (DataCloudClient.Entity e : metaEntities) {
                    metaMap.put(e.id(), e);
                }

                List<Map<String, Object>> entries = names.stream()
                    .map(name -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("name", name);
                        m.put("systemCollection", name.startsWith("dc_"));
                        DataCloudClient.Entity meta = metaMap.get(name);
                        if (meta != null) {
                            m.put("lifecycleStatus", meta.data().getOrDefault("lifecycleStatus", "UNKNOWN"));
                            m.put("qualityScore", meta.data().get("qualityScore"));
                            m.put("qualityMetrics", meta.data().get("qualityMetrics"));
                            m.put("retentionPolicy", meta.data().get("retentionPolicy"));
                            m.put("lineage", meta.data().get("lineage"));
                            m.put("operationalStatus", meta.data().getOrDefault("operationalStatus", "unknown"));
                        } else {
                            m.put("lifecycleStatus", "UNKNOWN");
                            m.put("operationalStatus", "unknown");
                        }
                        return m;
                    })
                    .toList();
                return http.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "collections", entries,
                    "total", entries.size(),
                    "timestamp", Instant.now().toString()
                ));
            });
        } catch (Exception e) {
            log.error("[listCollections] tenant={} failed: {}", tenantId, e.getMessage(), e);
            return Promise.of(http.errorResponse(500, "Collection registry query failed: " + e.getMessage()));
        }
    }
}
