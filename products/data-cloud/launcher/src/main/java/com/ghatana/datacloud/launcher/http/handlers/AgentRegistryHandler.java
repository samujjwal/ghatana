package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.ApiInputValidator;
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
 * Handles agent registry, pipeline registry, and checkpoint HTTP endpoints.
 *
 * <p>Extracted from {@code DataCloudHttpServer} to reduce the god-class size.
 * Covers DC-3 agent/pipeline/checkpoint CRUD stored in Data-Cloud collections.
 *
 * @doc.type class
 * @doc.purpose Agent, pipeline, and checkpoint registry HTTP handlers
 * @doc.layer product
 * @doc.pattern Handler
 */
public class AgentRegistryHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistryHandler.class);
    private static final String DC_PIPELINES_COLLECTION = "dc_pipelines";

    private final DataCloudClient client;
    private final HttpHandlerSupport http;

    public AgentRegistryHandler(DataCloudClient client, HttpHandlerSupport http) {
        this.client = client;
        this.http = http;
    }

    // ==================== Agent Endpoints ====================

    public Promise<HttpResponse> handleListAgents(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
        return client.query(tenantId, "dc_agents", DataCloudClient.Query.limit(1000))
            .map(entities -> {
                List<Map<String, Object>> agentSummaries = entities.stream()
                    .map(e -> Map.<String, Object>of(
                        "id", e.id(),
                        "collection", e.collection(),
                        "data", e.data()
                    ))
                    .toList();
                return http.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "agents", agentSummaries,
                    "count", agentSummaries.size(),
                    "timestamp", Instant.now().toString()
                ));
            });
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleRegisterAgent(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> agentData = http.objectMapper().readValue(body, Map.class);
                return client.save(tenantId, "dc_agents", agentData)
                    .map(entity -> http.jsonResponse(Map.of(
                        "id", entity.id(),
                        "tenantId", tenantId,
                        "registeredAt", Instant.now().toString()
                    )));
            } catch (Exception e) {
                log.warn("Failed to register agent for tenant {}: {}", tenantId, e.getMessage());
                return Promise.of(http.errorResponse(400, "Invalid agent definition: " + e.getMessage()));
            }
        });
    }

    public Promise<HttpResponse> handleGetAgent(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(http.errorResponse(400, "agentId path parameter is required"));
        }
        return client.findById(tenantId, "dc_agents", agentId)
            .map(optEntity -> optEntity
                .map(e -> http.jsonResponse(Map.of("id", e.id(), "data", e.data(), "tenantId", tenantId)))
                .orElse(http.errorResponse(404, "Agent not found: " + agentId)));
    }

    public Promise<HttpResponse> handleDeleteAgent(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(http.errorResponse(400, "agentId path parameter is required"));
        }
        return client.delete(tenantId, "dc_agents", agentId)
            .map(v -> http.jsonResponse(Map.of(
                "deleted", true,
                "agentId", agentId,
                "tenantId", tenantId,
                "timestamp", Instant.now().toString()
            )));
    }

    // ==================== Pipeline Endpoints ====================

    public Promise<HttpResponse> handleListPipelines(HttpRequest request) {
        String tenantId = http.resolveQueryOrHeaderTenantId(request);
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
        String tenantId = http.resolveQueryOrHeaderTenantId(request);
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = http.objectMapper().readValue(body, Map.class);
                return client.save(tenantId, DC_PIPELINES_COLLECTION, data)
                        .map(entity -> http.jsonResponse(flattenPipelineEntity(entity, tenantId)));
            } catch (Exception e) {
                log.warn("[DC-Pipelines] save failed tenant={}: {}", tenantId, e.getMessage());
                return Promise.of(http.errorResponse(400, "Invalid pipeline definition: " + e.getMessage()));
            }
        });
    }

    public Promise<HttpResponse> handleGetPipeline(HttpRequest request) {
        String tenantId = http.resolveQueryOrHeaderTenantId(request);
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
        String tenantId = http.resolveQueryOrHeaderTenantId(request);
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
        String tenantId = http.resolveQueryOrHeaderTenantId(request);
        String pipelineId = request.getPathParameter("pipelineId");
        if (pipelineId == null || pipelineId.isBlank()) {
            return Promise.of(http.errorResponse(400, "pipelineId path parameter is required"));
        }
        return client.delete(tenantId, DC_PIPELINES_COLLECTION, pipelineId)
                .map(v -> http.jsonResponse(Map.of(
                        "deleted", true,
                        "pipelineId", pipelineId,
                        "tenantId", tenantId,
                        "timestamp", Instant.now().toString())));
    }

    // ==================== Checkpoint Endpoints ====================

    public Promise<HttpResponse> handleListCheckpoints(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
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
        String tenantId = http.resolveTenantId(request);
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
        String tenantId = http.resolveTenantId(request);
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
        String tenantId = http.resolveTenantId(request);
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
}
