/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.DataCloudHttpMetrics;
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
    private static final String HANDLER_NAME = "WorkflowExecutionHandler";

    private final DataCloudClient client;
    private final HttpHandlerSupport http;
    private WorkflowExecutionCapability executionCapability;
    private DataCloudHttpMetrics metrics = DataCloudHttpMetrics.noop();

    public WorkflowExecutionHandler(DataCloudClient client, HttpHandlerSupport http) {
        this.client = client;
        this.http = http;
    }

    /**
     * Wires a {@link DataCloudHttpMetrics} instance for observability.
     *
     * @param metrics metrics collector; may be {@code null} to revert to noop
     * @return this handler (fluent)
     */
    public WorkflowExecutionHandler withMetrics(DataCloudHttpMetrics metrics) {
        this.metrics = metrics != null ? metrics : DataCloudHttpMetrics.noop();
        return this;
    }

    /**
     * Wires a {@link WorkflowExecutionCapability} to delegate execution operations.
     * When set, all execution operations are delegated to the capability.
      * Without it, execution endpoints return 503 Service Unavailable.
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
            return Promise.of(http.errorResponse(501, "Workflow execution capability is not available in this deployment."));
        }

        String correlationId = http.resolveCorrelationId(request);
        long startMs = System.currentTimeMillis();
        return request.loadBody().then(buf -> {
            Map<String, Object> input;
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                input = parseJsonMap(body);
            } catch (Exception e) {
                log.warn("[correlation={} tenant={} pipeline={}] Invalid execute pipeline request body: {}",
                        correlationId, tenantId, pipelineId, e.getMessage());
                metrics.recordError(HANDLER_NAME, "executePipeline", "InvalidRequest");
                return Promise.of(http.errorResponse(400, "Invalid request body: " + e.getMessage()));
            }
            return executionCapability.execute(tenantId, pipelineId, input)
                .map(snapshot -> {
                    long latency = System.currentTimeMillis() - startMs;
                    log.info("[correlation={} tenant={} pipeline={} execution={}] Workflow execution started, status={}",
                            correlationId, tenantId, pipelineId, snapshot.id(), snapshot.status());
                    metrics.recordRequest(HANDLER_NAME, "executePipeline", tenantId, 200);
                    metrics.recordLatency(HANDLER_NAME, "executePipeline", latency);
                    return http.jsonResponse(Map.of(
                        "executionId", snapshot.id(),
                        "pipelineId", snapshot.workflowId(),
                        "tenantId", tenantId,
                        "status", snapshot.status(),
                        "startedAt", snapshot.startedAt() != null ? snapshot.startedAt() : Instant.now().toString()
                    ));
                })
                .then(Promise::of, e -> {
                    log.error("[correlation={} tenant={} pipeline={}] Failed to execute pipeline: {}",
                            correlationId, tenantId, pipelineId, e.getMessage());
                    metrics.recordError(HANDLER_NAME, "executePipeline", e);
                    return Promise.of(http.errorResponse(500, "Pipeline execution failed"));
                });
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
            return Promise.of(http.errorResponse(501, "Workflow execution capability is not available in this deployment."));
        }

        return executionCapability.getExecution(tenantId, executionId)
            .map(opt -> opt.isPresent()
                ? http.jsonResponse(executionSnapshotToMap(opt.get()))
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
            return Promise.of(http.errorResponse(501, "Workflow execution capability is not available in this deployment."));
        }

        String correlationId = http.resolveCorrelationId(request);
        long startMs = System.currentTimeMillis();
        return executionCapability.cancelExecution(tenantId, executionId)
            .map(snapshot -> {
                long latency = System.currentTimeMillis() - startMs;
                log.info("[correlation={} tenant={} pipeline={} execution={}] Workflow execution cancelled, status={}",
                        correlationId, tenantId, pipelineId, executionId, snapshot.status());
                metrics.recordRequest(HANDLER_NAME, "cancelPipelineExecution", tenantId, 200);
                metrics.recordLatency(HANDLER_NAME, "cancelPipelineExecution", latency);
                return http.jsonResponse(Map.of(
                    "executionId", snapshot.id(),
                    "pipelineId", pipelineId,
                    "tenantId", tenantId,
                    "status", snapshot.status()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[correlation={} tenant={} pipeline={} execution={}] Failed to cancel execution: {}",
                        correlationId, tenantId, pipelineId, executionId, e.getMessage());
                metrics.recordError(HANDLER_NAME, "cancelPipelineExecution", e);
                return Promise.of(http.errorResponse(500, "Cancel execution failed"));
            });
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
            return Promise.of(http.errorResponse(501, "Workflow execution capability is not available in this deployment."));
        }

        return executionCapability.getExecution(tenantId, executionId)
            .map(opt -> opt.isPresent()
                ? http.jsonResponse(executionSnapshotToMap(opt.get()))
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
            return Promise.of(http.errorResponse(501, "Workflow execution capability is not available in this deployment."));
        }

        String correlationId = http.resolveCorrelationId(request);
        long startMs = System.currentTimeMillis();
        return executionCapability.cancelExecution(tenantId, executionId)
            .map(snapshot -> {
                long latency = System.currentTimeMillis() - startMs;
                log.info("[correlation={} tenant={} execution={}] Workflow execution cancelled, status={}",
                        correlationId, tenantId, executionId, snapshot.status());
                metrics.recordRequest(HANDLER_NAME, "cancelExecution", tenantId, 200);
                metrics.recordLatency(HANDLER_NAME, "cancelExecution", latency);
                return http.jsonResponse(Map.of(
                    "executionId", snapshot.id(),
                    "tenantId", tenantId,
                    "status", snapshot.status()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[correlation={} tenant={} execution={}] Failed to cancel execution: {}",
                        correlationId, tenantId, executionId, e.getMessage());
                metrics.recordError(HANDLER_NAME, "cancelExecution", e);
                return Promise.of(http.errorResponse(500, "Cancel execution failed"));
            });
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
            return Promise.of(http.errorResponse(501, "Workflow execution capability is not available in this deployment."));
        }

        String correlationId = http.resolveCorrelationId(request);
        long startMs = System.currentTimeMillis();
        return executionCapability.retryExecution(tenantId, executionId)
            .map(snapshot -> {
                long latency = System.currentTimeMillis() - startMs;
                log.info("[correlation={} tenant={} execution={}] Workflow execution retried, status={}",
                        correlationId, tenantId, executionId, snapshot.status());
                metrics.recordRequest(HANDLER_NAME, "retryExecution", tenantId, 200);
                metrics.recordLatency(HANDLER_NAME, "retryExecution", latency);
                return http.jsonResponse(Map.of(
                    "executionId", snapshot.id(),
                    "tenantId", tenantId,
                    "status", snapshot.status(),
                    "startedAt", snapshot.startedAt() != null ? snapshot.startedAt() : Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[correlation={} tenant={} execution={}] Failed to retry execution: {}",
                        correlationId, tenantId, executionId, e.getMessage());
                metrics.recordError(HANDLER_NAME, "retryExecution", e);
                return Promise.of(http.errorResponse(500, "Retry execution failed"));
            });
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
            return Promise.of(http.errorResponse(501, "Workflow execution capability is not available in this deployment."));
        }

        String correlationId = http.resolveCorrelationId(request);
        long startMs = System.currentTimeMillis();
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> rollbackData = parseJsonMap(body);
                // Rollback delegates to the capability which handles state transition
                return executionCapability.cancelExecution(tenantId, executionId)
                    .map(snapshot -> {
                        long latency = System.currentTimeMillis() - startMs;
                        log.info("[correlation={} tenant={} execution={}] Workflow execution rolled back",
                                correlationId, tenantId, executionId);
                        metrics.recordRequest(HANDLER_NAME, "rollbackExecution", tenantId, 200);
                        metrics.recordLatency(HANDLER_NAME, "rollbackExecution", latency);
                        return http.jsonResponse(Map.of(
                            "executionId", snapshot.id(),
                            "tenantId", tenantId,
                            "status", "rolled_back",
                            "rollbackData", rollbackData
                        ));
                    })
                    .then(Promise::of, e -> {
                        log.error("[correlation={} tenant={} execution={}] Failed to rollback execution: {}",
                                correlationId, tenantId, executionId, e.getMessage());
                        metrics.recordError(HANDLER_NAME, "rollbackExecution", e);
                        return Promise.of(http.errorResponse(500, "Rollback execution failed"));
                    });
            } catch (Exception e) {
                log.warn("[correlation={} tenant={} execution={}] Invalid rollback data: {}",
                        correlationId, tenantId, executionId, e.getMessage());
                metrics.recordError(HANDLER_NAME, "rollbackExecution", "InvalidRequest");
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
                Map<String, Object> checkpointData = parseJsonMap(body);
                String checkpointId = UUID.randomUUID().toString();
                String savedAt = Instant.now().toString();
                Map<String, Object> checkpointRecord = new java.util.HashMap<>();
                checkpointRecord.put("checkpointId", checkpointId);
                checkpointRecord.put("executionId", executionId);
                checkpointRecord.put("tenantId", tenantId);
                checkpointRecord.put("savedAt", savedAt);
                checkpointRecord.put("data", checkpointData);
                String correlationId = http.resolveCorrelationId(request);
                long startMs = System.currentTimeMillis();
                return client.save(tenantId, "dc_execution_checkpoints", checkpointRecord)
                    .map(entity -> {
                        long latency = System.currentTimeMillis() - startMs;
                        log.info("[correlation={} tenant={} execution={} checkpoint={}] Checkpoint saved",
                                correlationId, tenantId, executionId, checkpointId);
                        metrics.recordRequest(HANDLER_NAME, "checkpointExecution", tenantId, 200);
                        metrics.recordLatency(HANDLER_NAME, "checkpointExecution", latency);
                        return http.jsonResponse(Map.of(
                            "executionId", executionId,
                            "tenantId", tenantId,
                            "checkpointId", checkpointId,
                            "savedAt", savedAt,
                            "status", "checkpointed"
                        ));
                    })
                    .then(Promise::of, e -> {
                        log.error("[correlation={} tenant={} execution={}] Failed to save checkpoint: {}",
                                correlationId, tenantId, executionId, e.getMessage());
                        metrics.recordError(HANDLER_NAME, "checkpointExecution", e);
                        return Promise.of(http.errorResponse(500, "Checkpoint save failed"));
                    });
            } catch (Exception e) {
                log.warn("[correlation={} tenant={} execution={}] Invalid checkpoint data: {}",
                        http.resolveCorrelationId(request), tenantId, executionId, e.getMessage());
                metrics.recordError(HANDLER_NAME, "checkpointExecution", "InvalidRequest");
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
            return Promise.of(http.errorResponse(501, "Workflow execution capability is not available in this deployment."));
        }

        String correlationId = http.resolveCorrelationId(request);
        long startMs = System.currentTimeMillis();
        // Restore: retry the execution from its last saved state (capability handles state recovery)
        return executionCapability.retryExecution(tenantId, executionId)
            .map(snapshot -> {
                long latency = System.currentTimeMillis() - startMs;
                log.info("[correlation={} tenant={} execution={}] Workflow execution restored, status={}",
                        correlationId, tenantId, executionId, snapshot.status());
                metrics.recordRequest(HANDLER_NAME, "restoreExecution", tenantId, 200);
                metrics.recordLatency(HANDLER_NAME, "restoreExecution", latency);
                return http.jsonResponse(Map.of(
                    "executionId", snapshot.id(),
                    "tenantId", tenantId,
                    "status", snapshot.status(),
                    "startedAt", snapshot.startedAt() != null ? snapshot.startedAt() : Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[correlation={} tenant={} execution={}] Failed to restore execution: {}",
                        correlationId, tenantId, executionId, e.getMessage());
                metrics.recordError(HANDLER_NAME, "restoreExecution", e);
                return Promise.of(http.errorResponse(500, "Restore execution failed"));
            });
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
                Map<String, Object> queryData = parseJsonMap(body);
                String queryId = UUID.randomUUID().toString();
                String queryType = queryData.containsKey("type") ? String.valueOf(queryData.get("type")) : "sql";
                // Explain: analyse the query structure and estimate cost/plan without executing
                List<String> dataSources = readStringList(queryData.get("collections"));
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

    private Map<String, Object> parseJsonMap(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        return http.objectMapper().readValue(body, new TypeReference<>() {});
    }

    private static List<String> readStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .toList();
    }

    private static Map<String, Object> executionSnapshotToMap(WorkflowExecutionCapability.ExecutionSnapshot snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", snapshot.id());
        data.put("tenantId", snapshot.tenantId());
        data.put("workflowId", snapshot.workflowId());
        data.put("workflowName", snapshot.workflowName());
        data.put("status", snapshot.status());
        data.put("progress", snapshot.progress());
        data.put("startedAt", snapshot.startedAt());
        data.put("completedAt", snapshot.completedAt());
        data.put("duration", snapshot.duration());
        data.put("nodeStatuses", snapshot.nodeStatuses() != null ? snapshot.nodeStatuses() : List.of());
        data.put("output", snapshot.output());
        data.put("error", snapshot.error());
        return data;
    }
}
