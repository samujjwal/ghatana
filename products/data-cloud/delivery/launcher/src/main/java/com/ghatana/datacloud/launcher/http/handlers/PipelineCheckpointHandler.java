package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.entity.PipelineDefinition;
import com.ghatana.datacloud.entity.PipelineValidationResult;
import com.ghatana.datacloud.launcher.http.security.RequestContext;
import com.ghatana.datacloud.launcher.http.security.RequestContextResolver;
import com.ghatana.datacloud.spi.WriteIdempotencyStore;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.governance.security.Principal;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles pipeline registry and checkpoint HTTP endpoints.
 *
 * <p>Covers DC-3 pipeline and checkpoint CRUD stored in DataCloud collections.
 * Agent registry operations were migrated to the AEP Central Registry (v2.5).
 *
 * <p>E2: Enforces canonical Action Plane permissions, validates pipeline payloads,
 * persists audit events, and returns stable errors for invalid pipeline definitions.
 *
 * @doc.type class
 * @doc.purpose Pipeline and checkpoint registry HTTP handlers
 * @doc.layer product
 * @doc.pattern Handler
 */
public class PipelineCheckpointHandler {

    private static final Logger log = LoggerFactory.getLogger(PipelineCheckpointHandler.class);
    private static final String DC_PIPELINES_COLLECTION = "dc_pipelines";
    private static final String MISSING_TENANT_MESSAGE = "X-Tenant-Id header or tenantId query parameter is required";

    private final DataCloudClient client;
    private final HttpHandlerSupport http;
    private final AuditService auditService;
    /** DC-BE-002: Generic idempotency store for pipeline operations. */
    private WriteIdempotencyStore idempotencyStore;

    public PipelineCheckpointHandler(DataCloudClient client, HttpHandlerSupport http, AuditService auditService) {
        this.client = client;
        this.http = http;
        this.auditService = auditService;
    }

    /**
     * DC-BE-002: Attaches a generic idempotency store for pipeline operations.
     *
     * @param idempotencyStore the idempotency store
     * @return {@code this} for method chaining
     */
    public PipelineCheckpointHandler withIdempotencyStore(WriteIdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
        return this;
    }

    // ==================== Pipeline Endpoints ====================

    public Promise<HttpResponse> handleListPipelines(HttpRequest request) {
        // E2: Enforce canonical Action Plane permissions
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "action:pipeline:read");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }
        String tenantId = contextResult.context().map(RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        int limit  = HttpHandlerSupport.parseIntParam(request.getQueryParameter("limit"), 500);
        int offset = parseOffset(request.getQueryParameter("offset"));
        String search = request.getQueryParameter("search");

        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .offset(offset)
                .limit(limit)
                .sorts(parseSorts(request.getQueryParameter("sort")))
                .filters(mergeFilters(parseFilters(request.getQueryParameter("filter")), search))
                .build();

        com.ghatana.datacloud.spi.EntityStore store = client.entityStore();
        com.ghatana.platform.domain.eventstore.TenantContext tenantContext = com.ghatana.platform.domain.eventstore.TenantContext.of(tenantId);
        com.ghatana.datacloud.spi.EntityStore.QuerySpec countSpec = toEntityStoreQuerySpec(DC_PIPELINES_COLLECTION, query);

        Promise<Long> totalPromise = store != null
                ? store.count(tenantContext, countSpec)
                : Promise.of(-1L);

        return client.query(tenantId, DC_PIPELINES_COLLECTION, query)
                .combine(totalPromise, (entities, total) -> {
                    List<Map<String, Object>> pipelines = entities.stream()
                            .map(e -> flattenPipelineEntity(e, tenantId))
                            .toList();
                    boolean hasMore = total >= 0 && offset + entities.size() < total;
                    return http.jsonResponse(Map.of(
                            "tenantId", tenantId,
                            "pipelines", pipelines,
                            "total", total >= 0 ? total : pipelines.size(),
                            "count", pipelines.size(),
                            "offset", offset,
                            "limit", limit,
                            "hasMore", hasMore,
                            "timestamp", Instant.now().toString()));
                });
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleSavePipeline(HttpRequest request) {
        // E2: Enforce canonical Action Plane permissions
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "action:pipeline:create");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }
        String tenantId = contextResult.context().map(RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }

        // DC-BE-002: Check idempotency for pipeline save
        String idempotencyKey = request.getHeader(HttpHeaders.of("X-Idempotency-Key"));
        String operationScope = "pipelines:save";
        if (idempotencyStore != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
            var cached = idempotencyStore.get(tenantId, operationScope, idempotencyKey);
            if (cached.isPresent()) {
                log.info("[DC-BE-002] Returning cached pipeline save response for key={}", idempotencyKey);
                return Promise.of(http.jsonResponse(cached.get()));
            }
        }

        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = http.objectMapper().readValue(body, Map.class);

                // E2: Validate pipeline payload against contract schema
                PipelineValidationResult validation = validatePipelineDefinition(data);
                if (!Boolean.TRUE.equals(validation.getValid())) {
                    log.warn("[E2] Pipeline validation failed: {}", validation.getErrors());
                    return Promise.of(http.errorResponse(400, "Invalid pipeline definition: " + formatValidationErrors(validation)));
                }

                return client.save(tenantId, DC_PIPELINES_COLLECTION, data)
                        .map(entity -> {
                            Map<String, Object> responseBody = flattenPipelineEntity(entity, tenantId);
                            // DC-BE-002: Store idempotency response
                            if (idempotencyStore != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
                                idempotencyStore.put(tenantId, operationScope, idempotencyKey, responseBody);
                            }
                            // E2: Audit event for pipeline creation
                            emitAuditEvent(tenantId, principalName(contextResult, null), "pipeline.created", Map.of("pipelineId", entity.id(), "name", data.get("name")));
                            return http.createdResponse(responseBody);
                        });
            } catch (Exception e) {
                log.warn("[DC-Pipelines] save failed tenant={}: {}", tenantId, e.getMessage());
                return Promise.of(http.errorResponse(400, "Invalid pipeline definition: " + e.getMessage()));
            }
        });
    }

    public Promise<HttpResponse> handleGetPipeline(HttpRequest request) {
        // E2: Enforce canonical Action Plane permissions
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "action:pipeline:read");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }
        String tenantId = contextResult.context().map(RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        String pipelineId = request.getPathParameter("pipelineId");
        if (pipelineId == null || pipelineId.isBlank()) {
            return Promise.of(http.errorResponse(400, "pipelineId path parameter is required"));
        }
        return client.findById(tenantId, DC_PIPELINES_COLLECTION, pipelineId)
                .map(opt -> opt
                        .map(e -> http.jsonResponse(flattenPipelineEntity(e, tenantId)))
                        .orElse(http.errorResponse(404, "Pipeline not found: " + pipelineId)));
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleUpdatePipeline(HttpRequest request) {
        // E2: Enforce canonical Action Plane permissions
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "action:pipeline:update");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }
        String tenantId = contextResult.context().map(RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        String pipelineId = request.getPathParameter("pipelineId");
        if (pipelineId == null || pipelineId.isBlank()) {
            return Promise.of(http.errorResponse(400, "pipelineId path parameter is required"));
        }
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = http.objectMapper().readValue(body, Map.class);

                // E2: Validate pipeline payload against contract schema
                PipelineValidationResult validation = validatePipelineDefinition(data);
                if (!Boolean.TRUE.equals(validation.getValid())) {
                    log.warn("[E2] Pipeline validation failed: {}", validation.getErrors());
                    return Promise.of(http.errorResponse(400, "Invalid pipeline definition: " + formatValidationErrors(validation)));
                }

                data.put("id", pipelineId);
                return client.save(tenantId, DC_PIPELINES_COLLECTION, data)
                        .map(entity -> {
                            // E2: Audit event for pipeline update
                            emitAuditEvent(tenantId, principalName(contextResult, null), "pipeline.updated", Map.of("pipelineId", pipelineId, "name", data.get("name")));
                            return http.jsonResponse(flattenPipelineEntity(entity, tenantId));
                        });
            } catch (Exception e) {
                log.warn("[DC-Pipelines] update failed pipelineId={} tenant={}: {}", pipelineId, tenantId, e.getMessage());
                return Promise.of(http.errorResponse(400, "Invalid pipeline update: " + e.getMessage()));
            }
        });
    }

    public Promise<HttpResponse> handleDeletePipeline(HttpRequest request) {
        // E2: Enforce canonical Action Plane permissions
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "action:pipeline:delete");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }
        String tenantId = contextResult.context().map(RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        String pipelineId = request.getPathParameter("pipelineId");
        if (pipelineId == null || pipelineId.isBlank()) {
            return Promise.of(http.errorResponse(400, "pipelineId path parameter is required"));
        }
        return client.delete(tenantId, DC_PIPELINES_COLLECTION, pipelineId)
                .map(v -> {
                    // E2: Audit event for pipeline deletion
                    emitAuditEvent(tenantId, principalName(contextResult, null), "pipeline.deleted", Map.of("pipelineId", pipelineId));
                    return http.noContentResponse();
                });
    }

    // ==================== Checkpoint Endpoints ====================

    public Promise<HttpResponse> handleListCheckpoints(HttpRequest request) {
        // E2: Enforce canonical Action Plane permissions
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "action:checkpoint:read");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }
        String tenantId = contextResult.context().map(RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        String limitStr = request.getQueryParameter("limit");
        int limit = limitStr != null ? HttpHandlerSupport.parseIntParam(limitStr, 100) : 100;
        return client.query(tenantId, "dc_checkpoints", DataCloudClient.Query.limit(limit))
            .map(entities -> http.jsonResponse(Map.of(
                "tenantId", tenantId,
                "checkpoints", entities.stream()
                    .map(e -> Map.<String, Object>of("id", e.id(), "data", e.data()))
                    .toList(),
                "count", entities.size(),
                "timestamp", Instant.now().toString()
            )));
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleSaveCheckpoint(HttpRequest request) {
        // E2: Enforce canonical Action Plane permissions
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "action:checkpoint:create");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }
        String tenantId = contextResult.context().map(RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> checkpointData = http.objectMapper().readValue(body, Map.class);
                return client.save(tenantId, "dc_checkpoints", checkpointData)
                    .map(entity -> {
                        emitAuditEvent(tenantId, principalName(contextResult, null), "checkpoint.created", Map.of("checkpointId", entity.id()));
                        return http.jsonResponse(Map.of(
                            "id", entity.id(),
                            "tenantId", tenantId,
                            "savedAt", Instant.now().toString()
                        ));
                    });
            } catch (Exception e) {
                log.warn("Failed to save checkpoint for tenant {}: {}", tenantId, e.getMessage());
                return Promise.of(http.errorResponse(400, "Invalid checkpoint data: " + e.getMessage()));
            }
        });
    }

    public Promise<HttpResponse> handleGetCheckpoint(HttpRequest request) {
        // E2: Enforce canonical Action Plane permissions
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "action:checkpoint:read");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }
        String tenantId = contextResult.context().map(RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        String checkpointId = request.getPathParameter("checkpointId");
        if (checkpointId == null || checkpointId.isBlank()) {
            return Promise.of(http.errorResponse(400, "checkpointId path parameter is required"));
        }
        return client.findById(tenantId, "dc_checkpoints", checkpointId)
            .map(optEntity -> optEntity
                .map(e -> http.jsonResponse(Map.of("id", e.id(), "data", e.data(), "tenantId", tenantId)))
                .orElse(http.errorResponse(404, "Checkpoint not found: " + checkpointId)));
    }

    public Promise<HttpResponse> handleDeleteCheckpoint(HttpRequest request) {
        // E2: Enforce canonical Action Plane permissions
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "action:checkpoint:delete");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }
        String tenantId = contextResult.context().map(RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        String checkpointId = request.getPathParameter("checkpointId");
        if (checkpointId == null || checkpointId.isBlank()) {
            return Promise.of(http.errorResponse(400, "checkpointId path parameter is required"));
        }
        return client.delete(tenantId, "dc_checkpoints", checkpointId)
            .map(v -> {
                emitAuditEvent(tenantId, principalName(contextResult, null), "checkpoint.deleted", Map.of("checkpointId", checkpointId));
                return http.jsonResponse(Map.of(
                    "deleted", true,
                    "checkpointId", checkpointId,
                    "tenantId", tenantId,
                    "timestamp", Instant.now().toString()
                ));
            });
    }

    // ==================== Helpers ====================

    private Map<String, Object> flattenPipelineEntity(DataCloudClient.Entity e, String tenantId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", e.id());
        result.put("tenantId", tenantId);
        if (e.data() != null) {
            result.putAll(e.data());
        }
        return result;
    }

    /**
     * E2: Emit audit event for pipeline and checkpoint operations.
     */
    private void emitAuditEvent(String tenantId, String userId, String action, Map<String, Object> data) {
        if (auditService != null) {
            try {
                AuditEvent event = AuditEvent.builder()
                    .tenantId(tenantId)
                    .principal(userId != null ? userId : "system")
                    .eventType(action)
                    .resourceType(action.contains(".") ? action.substring(0, action.indexOf('.')) : "pipeline")
                    .success(true)
                    .timestamp(Instant.now())
                    .details(data)
                    .build();
                auditService.record(event).whenException(e ->
                    log.warn("Failed to record audit event for action: {}", action, e));
            } catch (Exception e) {
                log.warn("Failed to emit audit event for action: {}", action, e);
            }
        }
    }

    private String principalName(RequestContextResolver.ResolutionResult contextResult, String fallback) {
        return contextResult.context()
            .flatMap(RequestContext::principal)
            .map(principal -> principal.getName())
            .orElse(fallback);
    }

    private HttpResponse missingTenantResponse() {
        return http.jsonResponse(400, Map.of(
            "error", "MISSING_TENANT",
            "message", MISSING_TENANT_MESSAGE,
            "timestamp", Instant.now().toString()
        ));
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

    private List<DataCloudClient.Filter> mergeFilters(List<DataCloudClient.Filter> parsedFilters, String search) {
        if (search == null || search.isBlank()) {
            return parsedFilters;
        }
        List<DataCloudClient.Filter> merged = new ArrayList<>(parsedFilters);
        merged.add(DataCloudClient.Filter.like("name", "*" + search + "*"));
        return List.copyOf(merged);
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
            case EQ -> com.ghatana.datacloud.spi.EntityStore.Filter.eq(filter.field(), filter.value());
            case NE -> com.ghatana.datacloud.spi.EntityStore.Filter.ne(filter.field(), filter.value());
            case GT -> com.ghatana.datacloud.spi.EntityStore.Filter.gt(filter.field(), filter.value());
            case GTE -> com.ghatana.datacloud.spi.EntityStore.Filter.gte(filter.field(), filter.value());
            case LT -> com.ghatana.datacloud.spi.EntityStore.Filter.lt(filter.field(), filter.value());
            case LTE -> com.ghatana.datacloud.spi.EntityStore.Filter.lte(filter.field(), filter.value());
            case LIKE -> com.ghatana.datacloud.spi.EntityStore.Filter.like(filter.field(), filter.value().toString());
            default -> com.ghatana.datacloud.spi.EntityStore.Filter.eq(filter.field(), filter.value());
        };
    }

    // ==================== E2: Pipeline Validation ====================

    /**
     * E2: Validates pipeline definition against contract schema.
     * Returns stable errors for invalid DAGs, missing nodes, duplicate node IDs, invalid edges.
     */
    private PipelineValidationResult validatePipelineDefinition(Map<String, Object> data) {
        List<PipelineValidationResult.ValidationError> errors = new ArrayList<>();

        // Check required fields
        if (!data.containsKey("name") || data.get("name") == null) {
            errors.add(validationError("PIPELINE_NAME_REQUIRED", "Pipeline name is required"));
        }
        if (!data.containsKey("nodes") || !(data.get("nodes") instanceof List)) {
            errors.add(validationError("PIPELINE_NODES_REQUIRED", "Pipeline nodes array is required"));
        } else {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) data.get("nodes");

            // Check for duplicate node IDs
            Set<String> nodeIds = new java.util.HashSet<>();
            for (Map<String, Object> node : nodes) {
                String nodeId = (String) node.get("id");
                if (nodeId == null || nodeId.isBlank()) {
                    errors.add(validationError("NODE_ID_REQUIRED", "Node ID is required for all nodes"));
                } else if (nodeIds.contains(nodeId)) {
                    errors.add(validationError("DUPLICATE_NODE_ID", "Duplicate node ID: " + nodeId, List.of(nodeId)));
                } else {
                    nodeIds.add(nodeId);
                }

                // Check node type
                if (!node.containsKey("type") || node.get("type") == null) {
                    errors.add(validationError("NODE_TYPE_REQUIRED", "Node type is required for node: " + nodeId, nodeId == null ? List.of() : List.of(nodeId)));
                }
            }

            // Validate edges if present
            if (data.containsKey("edges") && data.get("edges") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> edges = (List<Map<String, Object>>) data.get("edges");
                for (Map<String, Object> edge : edges) {
                    String from = (String) edge.get("from");
                    String to = (String) edge.get("to");
                    if (from == null || from.isBlank()) {
                        errors.add(validationError("EDGE_FROM_REQUIRED", "Edge 'from' is required"));
                    } else if (!nodeIds.contains(from)) {
                        errors.add(validationError("EDGE_FROM_UNKNOWN", "Edge references non-existent node: " + from, List.of(from)));
                    }
                    if (to == null || to.isBlank()) {
                        errors.add(validationError("EDGE_TO_REQUIRED", "Edge 'to' is required"));
                    } else if (!nodeIds.contains(to)) {
                        errors.add(validationError("EDGE_TO_UNKNOWN", "Edge references non-existent node: " + to, List.of(to)));
                    }
                }
            }
        }

        return PipelineValidationResult.builder()
                .valid(errors.isEmpty())
                .validationTimestamp(Instant.now())
                .errors(errors)
                .build();
    }

    private static PipelineValidationResult.ValidationError validationError(String code, String message) {
        return validationError(code, message, List.of());
    }

    private static PipelineValidationResult.ValidationError validationError(
            String code,
            String message,
            List<String> affectedNodes) {
        return new PipelineValidationResult.ValidationError(
                code,
                message,
                "ERROR",
                "PIPELINE_CONTRACT",
                affectedNodes,
                Map.of());
    }

    private static String formatValidationErrors(PipelineValidationResult validation) {
        return validation.getErrors().stream()
                .map(PipelineValidationResult.ValidationError::message)
                .toList()
                .stream()
                .reduce((left, right) -> left + ", " + right)
                .orElse("unknown validation error");
    }
}
