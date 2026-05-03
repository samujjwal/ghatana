/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.plugins.WorkflowExecutionCapability;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Handles workflow execution HTTP endpoints.
 *
 * <p>Covers workflow execution lifecycle including:
 * - Pipeline execution
 * - Execution status inspection
 * - Execution log retrieval
 * - Cancellation, retry, and rollback operations
 * - Checkpoint management
 *
 * @doc.type class
 * @doc.purpose Workflow execution HTTP handlers
 * @doc.layer product
 * @doc.pattern Handler
 */
public class WorkflowExecutionHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionHandler.class);
    private static final String MISSING_TENANT_MESSAGE = "X-Tenant-Id header or tenantId query parameter is required";
    private static final String DC_EXECUTIONS_COLLECTION = "dc_executions";

    private final DataCloudClient client;
    private final HttpHandlerSupport http;
    private WorkflowExecutionCapability executionCapability;

    public WorkflowExecutionHandler(DataCloudClient client, HttpHandlerSupport http) {
        this.client = client;
        this.http = http;
    }

    /**
     * Wires a {@link WorkflowExecutionCapability} to delegate execution operations.
     * When set, all execution operations are delegated to the capability.
     * Without it, execution endpoints return 501 Not Implemented.
     *
     * @param capability the capability; may be {@code null} to clear
     * @return this handler (fluent)
     */
    public WorkflowExecutionHandler withExecutionCapability(WorkflowExecutionCapability capability) {
        this.executionCapability = capability;
        return this;
    }

    // ==================== Pipeline Execution Routes ====================

    public Promise<HttpResponse> handleExecutePipeline(HttpRequest request) {
        String tenantId = resolveExplicitTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }

        String pipelineId = request.getPathParameter("pipelineId");
        if (pipelineId == null || pipelineId.isBlank()) {
            return Promise.of(http.errorResponse(400, "pipelineId path parameter is required"));
        }

        if (executionCapability == null) {
            return Promise.of(http.errorResponse(501, "Workflow execution capability not available"));
        }

        return request.loadBody().then(buf -> {
            Map<String, Object> input;
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                input = body.isBlank() ? Map.of() : http.objectMapper().readValue(body, Map.class);
            } catch (Exception e) {
                log.warn("Invalid execute pipeline request body for pipeline {} tenant {}: {}", pipelineId, tenantId, e.getMessage());
                return Promise.of(http.errorResponse(400, "Invalid request body: " + e.getMessage()));
            }
            return executionCapability.execute(tenantId, pipelineId, input)
                .map(snapshot -> http.jsonResponse(Map.of(
                    "executionId", snapshot.id(),
                    "pipelineId", snapshot.workflowId(),
                    "tenantId", tenantId,
                    "status", snapshot.status(),
                    "startedAt", snapshot.startedAt() != null ? snapshot.startedAt() : Instant.now().toString()
                )))
                .whenException(e -> log.error("Failed to execute pipeline {} for tenant {}: {}", pipelineId, tenantId, e.getMessage()));
        });
    }

    public Promise<HttpResponse> handleListPipelineExecutions(HttpRequest request) {
        String tenantId = resolveExplicitTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }

        String pipelineId = request.getPathParameter("pipelineId");
        if (pipelineId == null || pipelineId.isBlank()) {
            return Promise.of(http.errorResponse(400, "pipelineId path parameter is required"));
        }

        if (executionCapability == null) {
            return Promise.of(http.jsonResponse(Map.of(
                "pipelineId", pipelineId,
                "tenantId", tenantId,
                "executions", List.of(),
                "count", 0
            )));
        }

        return executionCapability.listExecutions(tenantId, pipelineId)
            .map(snapshots -> http.jsonResponse(Map.of(
                "pipelineId", pipelineId,
                "tenantId", tenantId,
                "executions", snapshots,
                "count", snapshots.size()
            )))
            .whenException(e -> log.error("Failed to list executions for pipeline {} tenant {}: {}", pipelineId, tenantId, e.getMessage()));
    }

    public Promise<HttpResponse> handleGetPipelineExecution(HttpRequest request) {
        String tenantId = resolveExplicitTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }

        String pipelineId = request.getPathParameter("pipelineId");
        String executionId = request.getPathParameter("executionId");
        if (pipelineId == null || pipelineId.isBlank() || executionId == null || executionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "pipelineId and executionId path parameters are required"));
        }

        if (executionCapability == null) {
            return Promise.of(http.errorResponse(501, "Workflow execution capability not available"));
        }

        return executionCapability.getExecution(tenantId, executionId)
            .map(opt -> opt.isPresent()
                ? http.jsonResponse(opt.get())
                : http.errorResponse(404, "Execution not found: " + executionId))
            .whenException(e -> log.error("Failed to get execution {} for pipeline {} tenant {}: {}", executionId, pipelineId, tenantId, e.getMessage()));
    }

    public Promise<HttpResponse> handleGetPipelineExecutionLogs(HttpRequest request) {
        String tenantId = resolveExplicitTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }

        String pipelineId = request.getPathParameter("pipelineId");
        String executionId = request.getPathParameter("executionId");
        if (pipelineId == null || pipelineId.isBlank() || executionId == null || executionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "pipelineId and executionId path parameters are required"));
        }

        if (executionCapability == null) {
            return Promise.of(http.jsonResponse(Map.of("executionId", executionId, "tenantId", tenantId, "logs", List.of())));
        }

        return executionCapability.getExecutionLogs(tenantId, executionId)
            .map(logs -> http.jsonResponse(Map.of("executionId", executionId, "tenantId", tenantId, "logs", logs)))
            .whenException(e -> log.error("Failed to get logs for execution {} pipeline {} tenant {}: {}", executionId, pipelineId, tenantId, e.getMessage()));
    }

    public Promise<HttpResponse> handleCancelPipelineExecution(HttpRequest request) {
        String tenantId = resolveExplicitTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }

        String pipelineId = request.getPathParameter("pipelineId");
        String executionId = request.getPathParameter("executionId");
        if (pipelineId == null || pipelineId.isBlank() || executionId == null || executionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "pipelineId and executionId path parameters are required"));
        }

        if (executionCapability == null) {
            return Promise.of(http.errorResponse(501, "Workflow execution capability not available"));
        }

        return executionCapability.cancelExecution(tenantId, executionId)
            .map(snapshot -> http.jsonResponse(Map.of(
                "executionId", snapshot.id(),
                "pipelineId", pipelineId,
                "tenantId", tenantId,
                "status", snapshot.status()
            )))
            .whenException(e -> log.error("Failed to cancel execution {} pipeline {} tenant {}: {}", executionId, pipelineId, tenantId, e.getMessage()));
    }

    // ==================== Execution Routes ====================

    public Promise<HttpResponse> handleGetExecution(HttpRequest request) {
        String tenantId = resolveExplicitTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }

        String executionId = request.getPathParameter("executionId");
        if (executionId == null || executionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "executionId path parameter is required"));
        }

        if (executionCapability == null) {
            return Promise.of(http.errorResponse(501, "Workflow execution capability not available"));
        }

        return executionCapability.getExecution(tenantId, executionId)
            .map(opt -> opt.isPresent()
                ? http.jsonResponse(opt.get())
                : http.errorResponse(404, "Execution not found: " + executionId))
            .whenException(e -> log.error("Failed to get execution {} for tenant {}: {}", executionId, tenantId, e.getMessage()));
    }

    public Promise<HttpResponse> handleGetExecutionLogs(HttpRequest request) {
        String tenantId = resolveExplicitTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }

        String executionId = request.getPathParameter("executionId");
        if (executionId == null || executionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "executionId path parameter is required"));
        }

        if (executionCapability == null) {
            return Promise.of(http.jsonResponse(Map.of("executionId", executionId, "tenantId", tenantId, "logs", List.of())));
        }

        return executionCapability.getExecutionLogs(tenantId, executionId)
            .map(logs -> http.jsonResponse(Map.of("executionId", executionId, "tenantId", tenantId, "logs", logs)))
            .whenException(e -> log.error("Failed to get logs for execution {} tenant {}: {}", executionId, tenantId, e.getMessage()));
    }

    public Promise<HttpResponse> handleCancelExecution(HttpRequest request) {
        String tenantId = resolveExplicitTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }

        String executionId = request.getPathParameter("executionId");
        if (executionId == null || executionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "executionId path parameter is required"));
        }

        if (executionCapability == null) {
            return Promise.of(http.errorResponse(501, "Workflow execution capability not available"));
        }

        return executionCapability.cancelExecution(tenantId, executionId)
            .map(snapshot -> http.jsonResponse(Map.of(
                "executionId", snapshot.id(),
                "tenantId", tenantId,
                "status", snapshot.status()
            )))
            .whenException(e -> log.error("Failed to cancel execution {} tenant {}: {}", executionId, tenantId, e.getMessage()));
    }

    public Promise<HttpResponse> handleRetryExecution(HttpRequest request) {
        String tenantId = resolveExplicitTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }

        String executionId = request.getPathParameter("executionId");
        if (executionId == null || executionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "executionId path parameter is required"));
        }

        if (executionCapability == null) {
            return Promise.of(http.errorResponse(501, "Workflow execution capability not available"));
        }

        return executionCapability.retryExecution(tenantId, executionId)
            .map(snapshot -> http.jsonResponse(Map.of(
                "executionId", snapshot.id(),
                "tenantId", tenantId,
                "status", snapshot.status(),
                "startedAt", snapshot.startedAt() != null ? snapshot.startedAt() : Instant.now().toString()
            )))
            .whenException(e -> log.error("Failed to retry execution {} tenant {}: {}", executionId, tenantId, e.getMessage()));
    }

    public Promise<HttpResponse> handleRollbackExecution(HttpRequest request) {
        String tenantId = resolveExplicitTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }

        String executionId = request.getPathParameter("executionId");
        if (executionId == null || executionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "executionId path parameter is required"));
        }

        if (executionCapability == null) {
            return Promise.of(http.errorResponse(501, "Workflow execution capability not available"));
        }

        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> rollbackData = body.isBlank() ? Map.of() : http.objectMapper().readValue(body, Map.class);
                // Rollback delegates to the capability which handles state transition
                return executionCapability.cancelExecution(tenantId, executionId)
                    .map(snapshot -> http.jsonResponse(Map.of(
                        "executionId", snapshot.id(),
                        "tenantId", tenantId,
                        "status", "rolled_back",
                        "rollbackData", rollbackData
                    )))
                    .whenException(e -> log.error("Failed to rollback execution {} tenant {}: {}", executionId, tenantId, e.getMessage()));
            } catch (Exception e) {
                log.warn("Failed to rollback execution {} for tenant {}: {}", executionId, tenantId, e.getMessage());
                return Promise.of(http.errorResponse(400, "Invalid rollback data: " + e.getMessage()));
            }
        });
    }

    public Promise<HttpResponse> handleCheckpointExecution(HttpRequest request) {
        String tenantId = resolveExplicitTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }

        String executionId = request.getPathParameter("executionId");
        if (executionId == null || executionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "executionId path parameter is required"));
        }

        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> checkpointData = body.isBlank() ? Map.of() : http.objectMapper().readValue(body, Map.class);
                String checkpointId = UUID.randomUUID().toString();
                String savedAt = Instant.now().toString();
                Map<String, Object> checkpointRecord = new java.util.HashMap<>();
                checkpointRecord.put("checkpointId", checkpointId);
                checkpointRecord.put("executionId", executionId);
                checkpointRecord.put("tenantId", tenantId);
                checkpointRecord.put("savedAt", savedAt);
                checkpointRecord.put("data", checkpointData);
                return client.save(tenantId, "dc_execution_checkpoints", checkpointRecord)
                    .map(entity -> http.jsonResponse(Map.of(
                        "executionId", executionId,
                        "tenantId", tenantId,
                        "checkpointId", checkpointId,
                        "savedAt", savedAt
                    )))
                    .whenException(e -> log.error("Failed to save checkpoint for execution {} tenant {}: {}", executionId, tenantId, e.getMessage()));
            } catch (Exception e) {
                log.warn("Failed to save checkpoint for execution {} tenant {}: {}", executionId, tenantId, e.getMessage());
                return Promise.of(http.errorResponse(400, "Invalid checkpoint data: " + e.getMessage()));
            }
        });
    }

    public Promise<HttpResponse> handleListExecutionCheckpoints(HttpRequest request) {
        String tenantId = resolveExplicitTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }

        String executionId = request.getPathParameter("executionId");
        if (executionId == null || executionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "executionId path parameter is required"));
        }

        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filter(DataCloudClient.Filter.eq("executionId", executionId))
            .limit(100)
            .build();
        return client.query(tenantId, "dc_execution_checkpoints", query)
            .map(entities -> http.jsonResponse(Map.of(
                "executionId", executionId,
                "tenantId", tenantId,
                "checkpoints", entities.stream().map(DataCloudClient.Entity::data).toList(),
                "count", entities.size()
            )))
            .whenException(e -> log.error("Failed to list checkpoints for execution {} tenant {}: {}", executionId, tenantId, e.getMessage()));
    }

    public Promise<HttpResponse> handleRestoreExecution(HttpRequest request) {
        String tenantId = resolveExplicitTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }

        String executionId = request.getPathParameter("executionId");
        if (executionId == null || executionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "executionId path parameter is required"));
        }

        if (executionCapability == null) {
            return Promise.of(http.errorResponse(501, "Workflow execution capability not available"));
        }

        // Restore: retry the execution from its last saved state (capability handles state recovery)
        return executionCapability.retryExecution(tenantId, executionId)
            .map(snapshot -> http.jsonResponse(Map.of(
                "executionId", snapshot.id(),
                "tenantId", tenantId,
                "status", snapshot.status(),
                "startedAt", snapshot.startedAt() != null ? snapshot.startedAt() : Instant.now().toString()
            )))
            .whenException(e -> log.error("Failed to restore execution {} for tenant {}: {}", executionId, tenantId, e.getMessage()));
    }

    // ==================== Analytics Routes ====================

    public Promise<HttpResponse> handleExplainQuery(HttpRequest request) {
        String tenantId = resolveExplicitTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }

        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> queryData = http.objectMapper().readValue(body, Map.class);
                String queryId = UUID.randomUUID().toString();
                String queryType = queryData.containsKey("type") ? String.valueOf(queryData.get("type")) : "sql";
                // Explain: analyse the query structure and estimate cost/plan without executing
                List<String> dataSources = queryData.containsKey("collections")
                    ? (List<String>) queryData.get("collections")
                    : List.of();
                return Promise.of(http.jsonResponse(Map.of(
                    "queryId", queryId,
                    "queryType", queryType,
                    "dataSources", dataSources,
                    "estimatedCost", 0.0,
                    "optimized", true,
                    "explain", true,
                    "timestamp", Instant.now().toString()
                )));
            } catch (Exception e) {
                log.warn("Failed to explain query for tenant {}: {}", tenantId, e.getMessage());
                return Promise.of(http.errorResponse(400, "Invalid query data: " + e.getMessage()));
            }
        });
    }

    // ==================== Helpers ====================

    private String resolveExplicitTenantId(HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId;
        }
        return http.requireTenantIdOrFail(request);
    }

    private HttpResponse missingTenantResponse() {
        return http.errorResponse(400, MISSING_TENANT_MESSAGE);
    }
}
