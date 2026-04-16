package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        int limit = HttpHandlerSupport.parseIntParam(request.getQueryParameter("limit"), 500);
        return client.query(tenantId, DC_PIPELINES_COLLECTION, DataCloudClient.Query.limit(limit))
                .map(entities -> {
                    List<Map<String, Object>> pipelines = entities.stream()
                            .map(e -> flattenPipelineEntity(e, tenantId))
                            .toList();
                    return http.jsonResponse(Map.of(
                            "tenantId", tenantId,
                            "pipelines", pipelines,
                            "count", pipelines.size(),
                            "timestamp", Instant.now().toString()));
                });
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleSavePipeline(HttpRequest request) {
        String tenantId = resolveTenantId(request);
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
        String tenantId = resolveTenantId(request);
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
        String tenantId = resolveTenantId(request);
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
        String tenantId = resolveTenantId(request);
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
        String tenantId = resolveTenantId(request);
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
        String tenantId = resolveTenantId(request);
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
        String tenantId = resolveTenantId(request);
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
        String tenantId = resolveTenantId(request);
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

    private String resolveTenantId(HttpRequest request) {
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
}
