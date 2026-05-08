package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
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

/**
 * Handles pipeline registry and checkpoint HTTP endpoints.
 *
 * <p>Covers DC-3 pipeline and checkpoint CRUD stored in DataCloud collections.
 * Agent registry operations were migrated to the AEP Central Registry (v2.5).
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

    public PipelineCheckpointHandler(DataCloudClient client, HttpHandlerSupport http) {
        this.client = client;
        this.http = http;
    }

    // ==================== Pipeline Endpoints ====================

    public Promise<HttpResponse> handleListPipelines(HttpRequest request) {
        String tenantId = resolveExplicitTenantId(request);
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
        com.ghatana.datacloud.spi.TenantContext tenantContext = com.ghatana.datacloud.spi.TenantContext.of(tenantId);
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
        String tenantId = resolveExplicitTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = http.objectMapper().readValue(body, Map.class);
                return client.save(tenantId, DC_PIPELINES_COLLECTION, data)
                        .map(entity -> http.createdResponse(flattenPipelineEntity(entity, tenantId)));
            } catch (Exception e) {
                log.warn("[DC-Pipelines] save failed tenant={}: {}", tenantId, e.getMessage());
                return Promise.of(http.errorResponse(400, "Invalid pipeline definition: " + e.getMessage()));
            }
        });
    }

    public Promise<HttpResponse> handleGetPipeline(HttpRequest request) {
        String tenantId = resolveExplicitTenantId(request);
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
        String tenantId = resolveExplicitTenantId(request);
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
                data.put("id", pipelineId);
                return client.save(tenantId, DC_PIPELINES_COLLECTION, data)
                        .map(entity -> http.jsonResponse(flattenPipelineEntity(entity, tenantId)));
            } catch (Exception e) {
                log.warn("[DC-Pipelines] update failed pipelineId={} tenant={}: {}", pipelineId, tenantId, e.getMessage());
                return Promise.of(http.errorResponse(400, "Invalid pipeline update: " + e.getMessage()));
            }
        });
    }

    public Promise<HttpResponse> handleDeletePipeline(HttpRequest request) {
        String tenantId = resolveExplicitTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        String pipelineId = request.getPathParameter("pipelineId");
        if (pipelineId == null || pipelineId.isBlank()) {
            return Promise.of(http.errorResponse(400, "pipelineId path parameter is required"));
        }
        return client.delete(tenantId, DC_PIPELINES_COLLECTION, pipelineId)
                .map(v -> http.noContentResponse());
    }

    // ==================== Checkpoint Endpoints ====================

    public Promise<HttpResponse> handleListCheckpoints(HttpRequest request) {
        String tenantId = resolveExplicitTenantId(request);
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
        String tenantId = resolveExplicitTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> checkpointData = http.objectMapper().readValue(body, Map.class);
                return client.save(tenantId, "dc_checkpoints", checkpointData)
                    .map(entity -> http.jsonResponse(Map.of(
                        "id", entity.id(),
                        "tenantId", tenantId,
                        "savedAt", Instant.now().toString()
                    )));
            } catch (Exception e) {
                log.warn("Failed to save checkpoint for tenant {}: {}", tenantId, e.getMessage());
                return Promise.of(http.errorResponse(400, "Invalid checkpoint data: " + e.getMessage()));
            }
        });
    }

    public Promise<HttpResponse> handleGetCheckpoint(HttpRequest request) {
        String tenantId = resolveExplicitTenantId(request);
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
        String tenantId = resolveExplicitTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        String checkpointId = request.getPathParameter("checkpointId");
        if (checkpointId == null || checkpointId.isBlank()) {
            return Promise.of(http.errorResponse(400, "checkpointId path parameter is required"));
        }
        return client.delete(tenantId, "dc_checkpoints", checkpointId)
            .map(v -> http.jsonResponse(Map.of(
                "deleted", true,
                "checkpointId", checkpointId,
                "tenantId", tenantId,
                "timestamp", Instant.now().toString()
            )));
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

    private String resolveExplicitTenantId(HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId;
        }
        return http.requireTenantIdOrFail(request);
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
}
