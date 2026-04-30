package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.plugins.DataCloudRuntimePluginManager;
import com.ghatana.datacloud.launcher.http.plugins.WorkflowExecutionCapability;
import com.ghatana.platform.security.annotation.Secured;
import io.activej.http.HttpHeader;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP endpoints for plugin-backed workflow execution.
 *
 * <h2>Security</h2>
 * All workflow execution operations require authentication.
 *
 * @doc.type class
 * @doc.purpose HTTP endpoints for plugin-backed workflow execution
 * @doc.layer product
 * @doc.pattern Handler
 */
@Secured
public final class WorkflowExecutionHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionHandler.class);

    private final HttpHandlerSupport http;
    private final DataCloudRuntimePluginManager runtimePluginManager;
    private DataCloudClient client; // optional; enables checkpoint persistence

    public WorkflowExecutionHandler(HttpHandlerSupport http, DataCloudRuntimePluginManager runtimePluginManager) {
        this.http = http;
        this.runtimePluginManager = runtimePluginManager;
    }

    public WorkflowExecutionHandler withClient(DataCloudClient client) {
        this.client = client;
        return this;
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleExecutePipeline(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        WorkflowExecutionCapability capability = workflowCapability();
        if (capability == null) {
            return Promise.of(http.errorResponse(503, "Workflow execution plugin is not available"));
        }
        String pipelineId = request.getPathParameter("pipelineId");
        return request.loadBody().then(buffer -> {
            try {
                Map<String, Object> payload = buffer.readRemaining() == 0
                    ? Map.of()
                    : http.objectMapper().readValue(buffer.getString(StandardCharsets.UTF_8), Map.class);
                Map<String, Object> input = payload.get("input") instanceof Map<?, ?> inputMap
                    ? (Map<String, Object>) inputMap
                    : Map.of();
                return capability.execute(tenantId, pipelineId, input)
                    .map(snapshot -> http.jsonResponse(202, Map.of(
                        "executionId", snapshot.id(),
                        "workflowId", snapshot.workflowId(),
                        "status", snapshot.status(),
                        "startedAt", snapshot.startedAt()
                    )));
            } catch (Exception exception) {
                return Promise.of(http.errorResponse(400, "Invalid execution payload: " + exception.getMessage()));
            }
        }).then(
            response -> Promise.of(response),
            exception -> Promise.of(http.errorResponse(404, exception.getMessage()))
        );
    }

    public Promise<HttpResponse> handleListExecutions(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        WorkflowExecutionCapability capability = workflowCapability();
        if (capability == null) {
            return Promise.of(http.errorResponse(503, "Workflow execution plugin is not available"));
        }
        String pipelineId = request.getPathParameter("pipelineId");
        int limit  = HttpHandlerSupport.parseIntParam(request.getQueryParameter("limit"), 50);
        int offset = HttpHandlerSupport.parseIntParam(request.getQueryParameter("offset"), 0);
        String statusFilter = request.getQueryParameter("status");
        return capability.listExecutions(tenantId, pipelineId)
            .map(executions -> {
                List<WorkflowExecutionCapability.ExecutionSnapshot> filtered = (statusFilter != null && !statusFilter.isBlank())
                    ? executions.stream()
                        .filter(e -> statusFilter.equalsIgnoreCase(e.status()))
                        .toList()
                    : executions;
                int total = filtered.size();
                int from = Math.min(offset, total);
                int to   = Math.min(offset + limit, total);
                List<Map<String, Object>> items = filtered.subList(from, to).stream()
                    .map(this::toWorkflowExecution)
                    .toList();
                return http.jsonResponse(Map.of(
                    "items", items,
                    "total", total,
                    "offset", offset,
                    "limit", limit,
                    "hasMore", to < total
                ));
            });
    }

    public Promise<HttpResponse> handleGetExecution(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        WorkflowExecutionCapability capability = workflowCapability();
        if (capability == null) {
            return Promise.of(http.errorResponse(503, "Workflow execution plugin is not available"));
        }
        String executionId = request.getPathParameter("executionId");
        return capability.getExecution(tenantId, executionId)
            .map(optionalExecution -> optionalExecution
                .map(snapshot -> http.jsonResponse(toExecutionState(snapshot)))
                .orElse(http.errorResponse(404, "Execution not found: " + executionId)));
    }

    public Promise<HttpResponse> handleGetWorkflowExecution(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        WorkflowExecutionCapability capability = workflowCapability();
        if (capability == null) {
            return Promise.of(http.errorResponse(503, "Workflow execution plugin is not available"));
        }
        String executionId = request.getPathParameter("executionId");
        return capability.getExecution(tenantId, executionId)
            .map(optionalExecution -> optionalExecution
                .map(snapshot -> http.jsonResponse(toWorkflowExecution(snapshot)))
                .orElse(http.errorResponse(404, "Execution not found: " + executionId)));
    }

    public Promise<HttpResponse> handleCancelExecution(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        WorkflowExecutionCapability capability = workflowCapability();
        if (capability == null) {
            return Promise.of(http.errorResponse(503, "Workflow execution plugin is not available"));
        }
        String executionId = request.getPathParameter("executionId");
        return capability.cancelExecution(tenantId, executionId)
            .map(snapshot -> http.jsonResponse(toWorkflowExecution(snapshot)))
            .then(
                response -> Promise.of(response),
                exception -> Promise.of(http.errorResponse(404, exception.getMessage()))
            );
    }

    public Promise<HttpResponse> handleRetryExecution(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        WorkflowExecutionCapability capability = workflowCapability();
        if (capability == null) {
            return Promise.of(http.errorResponse(503, "Workflow execution plugin is not available"));
        }
        String executionId = request.getPathParameter("executionId");
        return capability.retryExecution(tenantId, executionId)
            .map(snapshot -> http.jsonResponse(toWorkflowExecution(snapshot)))
            .then(
                response -> Promise.of(response),
                exception -> Promise.of(http.errorResponse(404, exception.getMessage()))
            );
    }

    public Promise<HttpResponse> handleExecutionLogs(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        WorkflowExecutionCapability capability = workflowCapability();
        if (capability == null) {
            return Promise.of(http.errorResponse(503, "Workflow execution plugin is not available"));
        }
        String executionId = request.getPathParameter("executionId");
        return capability.getExecutionLogs(tenantId, executionId)
            .map(logs -> http.jsonBodyResponse(logs.stream().map(this::toLogEntry).toList()));
    }

    private WorkflowExecutionCapability workflowCapability() {
        return runtimePluginManager.findCapability(WorkflowExecutionCapability.class).orElse(null);
    }

    private Map<String, Object> toWorkflowExecution(WorkflowExecutionCapability.ExecutionSnapshot snapshot) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", snapshot.id());
        response.put("workflowId", snapshot.workflowId());
        response.put("status", normalizeLifecycleStatus(snapshot.status()));
        response.put("progress", snapshot.progress());
        response.put("isTerminal", snapshot.isTerminal());
        response.put("retries", 0);
        response.put("startedAt", snapshot.startedAt());
        if (snapshot.completedAt() != null) {
            response.put("completedAt", snapshot.completedAt());
        }
        if (snapshot.duration() != null) {
            response.put("duration", snapshot.duration());
        }
        if (snapshot.output() instanceof Map<?, ?> output) {
            response.put("output", output);
        }
        if (snapshot.error() != null) {
            response.put("error", snapshot.error());
        }
        // Audit trail (derived from execution metadata)
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("createdAt", snapshot.startedAt());
        audit.put("updatedAt", snapshot.completedAt() != null ? snapshot.completedAt() : Instant.now().toString());
        response.put("audit", audit);
        // Notifications placeholder (enriched by execution engine when available)
        Map<String, Object> notifications = new LinkedHashMap<>();
        notifications.put("onStart", false);
        notifications.put("onCompletion", snapshot.isTerminal());
        notifications.put("onFailure", snapshot.error() != null);
        response.put("notifications", notifications);
        return response;
    }

    private Map<String, Object> toExecutionState(WorkflowExecutionCapability.ExecutionSnapshot snapshot) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", snapshot.id());
        response.put("pipelineId", snapshot.workflowId());
        response.put("pipelineName", snapshot.workflowName());
        response.put("status", normalizeLifecycleStatus(snapshot.status()));
        response.put("progress", snapshot.progress());
        response.put("isTerminal", snapshot.isTerminal());
        response.put("retries", 0);
        response.put("startTime", snapshot.startedAt());
        if (snapshot.completedAt() != null) {
            response.put("endTime", snapshot.completedAt());
        }
        List<WorkflowExecutionCapability.NodeSnapshot> nodeStatuses = snapshot.nodeStatuses();
        long completed = nodeStatuses.stream().filter(node -> "COMPLETED".equals(node.state())).count();
        response.put("completedNodes", completed);
        response.put("totalNodes", nodeStatuses.size());
        response.put("nodes", nodeStatuses.stream().map(node -> {
            Integer nodeDuration = java.util.Objects.requireNonNullElse(node.duration(), Integer.valueOf(0));
            Map<String, Object> nodeMap = new LinkedHashMap<>();
            nodeMap.put("id", node.nodeId());
            nodeMap.put("name", node.nodeName());
            nodeMap.put("status", normalizeLifecycleStatus(node.state()));
            nodeMap.put("startTime", node.startedAt());
            nodeMap.put("endTime", node.completedAt());
            nodeMap.put("duration", nodeDuration);
            nodeMap.put("error", node.error() == null ? "" : node.error());
            return nodeMap;
        }).toList());
        if (snapshot.error() != null) {
            response.put("error", snapshot.error());
        }
        // Audit trail
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("createdAt", snapshot.startedAt());
        audit.put("updatedAt", snapshot.completedAt() != null ? snapshot.completedAt() : Instant.now().toString());
        response.put("audit", audit);
        // Notifications
        Map<String, Object> notifications = new LinkedHashMap<>();
        notifications.put("onStart", false);
        notifications.put("onCompletion", snapshot.isTerminal());
        notifications.put("onFailure", snapshot.error() != null);
        response.put("notifications", notifications);
        return response;
    }

    private static String normalizeLifecycleStatus(String raw) {
        if (raw == null) return "unknown";
        return switch (raw.toUpperCase()) {
            case "PENDING" -> "pending";
            case "RUNNING", "IN_PROGRESS" -> "running";
            case "COMPLETED" -> "completed";
            case "SUCCEEDED", "SUCCESS" -> "succeeded";
            case "FAILED", "FAILURE" -> "failed";
            case "CANCELLED", "CANCELED" -> "cancelled";
            case "RETRYING" -> "retrying";
            default -> raw.toLowerCase();
        };
    }

    private Map<String, Object> toLogEntry(WorkflowExecutionCapability.ExecutionLogEntry logEntry) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", logEntry.timestamp());
        response.put("level", logEntry.level());
        response.put("message", logEntry.message());
        if (logEntry.nodeId() != null) {
            response.put("nodeId", logEntry.nodeId());
        }
        if (logEntry.metadata() != null && !logEntry.metadata().isEmpty()) {
            response.put("metadata", logEntry.metadata());
        }
        return response;
    }

    /**
     * {@code POST /api/v1/executions/:executionId/rollback}
     *
     * <p>Initiates a compensating rollback for a workflow execution (P2.2).
     * Each completed step is matched with a compensating action if available;
     * the response returns the rollback plan and status per step without
     * performing actual side-effects (the plan is handed to the execution
     * engine).
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleRollbackExecution(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        String executionId = request.getPathParameter("executionId");
        if (executionId == null || executionId.isBlank()) {
            return Promise.of(http.jsonResponse(400, Map.of(
                "error", "MISSING_EXECUTION_ID",
                "message", "executionId path parameter is required",
                "timestamp", Instant.now().toString()
            )));
        }
        if (client == null) {
            return Promise.of(http.errorResponse(503, "Rollback checkpoint persistence is not available"));
        }

        return request.loadBody().then(buf -> {
            try {
                Map<String, Object> body = buf.getString(StandardCharsets.UTF_8).isBlank()
                    ? Map.of()
                    : http.objectMapper().readValue(buf.getString(StandardCharsets.UTF_8), Map.class);
                List<String> targetSteps = (List<String>) body.getOrDefault("targetSteps", List.of());
                boolean dryRun = Boolean.TRUE.equals(body.get("dryRun"));

                return client.query(tenantId, "dc_execution_checkpoints",
                        DataCloudClient.Query.builder()
                            .filters(List.of(
                                DataCloudClient.Filter.eq("executionId", executionId),
                                DataCloudClient.Filter.eq("status", "completed")
                            ))
                            .sorts(List.of(DataCloudClient.Sort.desc("stepIndex")))
                            .build())
                    .map(entities -> {
                        if (entities.isEmpty()) {
                            return http.errorResponse(404, "No completed checkpoints found for execution: " + executionId);
                        }

                        List<Map<String, Object>> rollbackPlan = new ArrayList<>();
                        for (DataCloudClient.Entity entity : entities) {
                            Map<String, Object> checkpoint = entity.data();
                            String stepId = String.valueOf(checkpoint.getOrDefault("stepId", "unknown-step"));
                            if (!targetSteps.isEmpty() && !targetSteps.contains(stepId)) {
                                continue;
                            }
                            Map<String, Object> stepPlan = new LinkedHashMap<>();
                            stepPlan.put("checkpointId", entity.id());
                            stepPlan.put("stepId", stepId);
                            stepPlan.put("stepIndex", checkpoint.getOrDefault("stepIndex", 0));
                            stepPlan.put("compensatingAction", "rollback-" + stepId);
                            stepPlan.put("state", checkpoint.getOrDefault("state", Map.of()));
                            stepPlan.put("status", dryRun ? "simulated" : "queued");
                            rollbackPlan.add(stepPlan);
                        }

                        if (rollbackPlan.isEmpty()) {
                            return http.errorResponse(404, "No checkpoints matched requested rollback target steps");
                        }

                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("tenantId", tenantId);
                        result.put("executionId", executionId);
                        result.put("dryRun", dryRun);
                        result.put("rollbackPlan", rollbackPlan);
                        result.put("rollbackSteps", rollbackPlan.size());
                        result.put("status", dryRun ? "ROLLBACK_SIMULATED" : "ROLLBACK_QUEUED");
                        result.put("requestedAt", Instant.now().toString());

                        if (!dryRun) {
                            client.appendEvent(tenantId,
                                DataCloudClient.Event.of("execution.rollback", Map.of(
                                    "executionId", executionId,
                                    "rollbackSteps", rollbackPlan.size(),
                                    "targetSteps", targetSteps,
                                    "requestedAt", Instant.now().toString()
                                )))
                                .whenException(e -> log.warn("Failed to emit rollback event tenant={} exec={}: {}",
                                    tenantId, executionId, e.getMessage()));
                        }
                        return http.jsonResponse(result);
                    })
                    .then(Promise::of, e -> {
                        log.error("Rollback request failed tenant={} exec={}: {}", tenantId, executionId, e.getMessage(), e);
                        return Promise.of(http.errorResponse(500, "Rollback request failed: " + e.getMessage()));
                    });
            } catch (Exception e) {
                return Promise.of(http.errorResponse(400, "Malformed request body: " + e.getMessage()));
            }
        });
    }

    // ── Durable Execution: Event-Backed Checkpoints (P2.2) ───────────────────

    /**
     * {@code POST /api/v1/executions/:executionId/checkpoint}
     *
     * <p>Saves a checkpoint for a workflow execution step to enable durable
     * recovery. Writes the checkpoint state to the `dc_execution_checkpoints`
     * collection and emits an event to the event log.
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleCreateCheckpoint(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        String executionId = request.getPathParameter("executionId");
        if (executionId == null || executionId.isBlank()) {
            return Promise.of(http.jsonResponse(400, Map.of(
                "error", "MISSING_EXECUTION_ID",
                "message", "executionId path parameter is required",
                "timestamp", Instant.now().toString()
            )));
        }
        if (client == null) {
            return Promise.of(http.errorResponse(503, "Checkpoint persistence is not available"));
        }

        return request.loadBody().then(buf -> {
            try {
                Map<String, Object> body = buf.getString(StandardCharsets.UTF_8).isBlank()
                    ? Map.of()
                    : http.objectMapper().readValue(buf.getString(StandardCharsets.UTF_8), Map.class);

                String stepId = String.valueOf(body.getOrDefault("stepId", "unknown"));
                int stepIndex = Integer.parseInt(String.valueOf(body.getOrDefault("stepIndex", 0)));
                Map<String, Object> state = (Map<String, Object>) body.getOrDefault("state", Map.of());

                Map<String, Object> checkpoint = new LinkedHashMap<>();
                checkpoint.put("id", executionId + "-step-" + stepIndex);
                checkpoint.put("tenantId", tenantId);
                checkpoint.put("executionId", executionId);
                checkpoint.put("stepId", stepId);
                checkpoint.put("stepIndex", stepIndex);
                checkpoint.put("state", state);
                checkpoint.put("status", "completed");
                checkpoint.put("createdAt", Instant.now().toString());

                return client.save(tenantId, "dc_execution_checkpoints", checkpoint)
                    .map(saved -> {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("checkpointId", saved.id());
                        result.put("tenantId", tenantId);
                        result.put("executionId", executionId);
                        result.put("stepId", stepId);
                        result.put("stepIndex", stepIndex);
                        result.put("status", "saved");
                        result.put("savedAt", Instant.now().toString());

                        client.appendEvent(tenantId,
                            DataCloudClient.Event.of("execution.checkpoint", result))
                            .whenException(e -> log.warn("Failed to emit checkpoint event tenant={} exec={} step={}: {}",
                                tenantId, executionId, stepId, e.getMessage()));

                        return http.jsonResponse(result);
                    })
                    .then(Promise::of, e -> {
                        log.error("Checkpoint save failed tenant={} exec={}: {}", tenantId, executionId, e.getMessage(), e);
                        return Promise.of(http.errorResponse(500, "Checkpoint persistence failed: " + e.getMessage()));
                    });
            } catch (Exception e) {
                return Promise.of(http.errorResponse(400, "Invalid checkpoint payload: " + e.getMessage()));
            }
        });
    }

    /**
     * {@code GET /api/v1/executions/:executionId/checkpoints}
     *
     * <p>Lists all checkpoints for a given workflow execution, ordered by step
     * index. Enables recovery from the last successful checkpoint.
     */
    public Promise<HttpResponse> handleListCheckpoints(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        String executionId = request.getPathParameter("executionId");
        if (executionId == null || executionId.isBlank()) {
            return Promise.of(http.jsonResponse(400, Map.of(
                "error", "MISSING_EXECUTION_ID",
                "message", "executionId path parameter is required",
                "timestamp", Instant.now().toString()
            )));
        }
        if (client == null) {
            return Promise.of(http.errorResponse(503, "Checkpoint persistence is not available"));
        }

        return client.query(tenantId, "dc_execution_checkpoints",
                DataCloudClient.Query.builder()
                    .filter(DataCloudClient.Filter.eq("executionId", executionId))
                    .sorts(List.of(DataCloudClient.Sort.asc("stepIndex")))
                    .build())
            .map(entities -> {
                List<Map<String, Object>> checkpoints = entities.stream()
                    .map(e -> {
                        Map<String, Object> cp = new LinkedHashMap<>(e.data());
                        cp.put("checkpointId", e.id());
                        return cp;
                    })
                    .toList();
                return http.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "executionId", executionId,
                    "checkpointCount", checkpoints.size(),
                    "checkpoints", checkpoints
                ));
            })
            .then(Promise::of, e -> {
                log.error("Checkpoint query failed tenant={} exec={}: {}", tenantId, executionId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Checkpoint query failed: " + e.getMessage()));
            });
    }

    /**
     * {@code POST /api/v1/executions/:executionId/restore}
     *
     * <p>Restores a workflow execution from the most recent successful
     * checkpoint. Returns the checkpoint state so the execution engine can
     * resume from the recovered step.
     */
    public Promise<HttpResponse> handleRestoreCheckpoint(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(missingTenantResponse());
        }
        String executionId = request.getPathParameter("executionId");
        if (executionId == null || executionId.isBlank()) {
            return Promise.of(http.jsonResponse(400, Map.of(
                "error", "MISSING_EXECUTION_ID",
                "message", "executionId path parameter is required",
                "timestamp", Instant.now().toString()
            )));
        }
        if (client == null) {
            return Promise.of(http.errorResponse(503, "Checkpoint persistence is not available"));
        }

        return client.query(tenantId, "dc_execution_checkpoints",
                DataCloudClient.Query.builder()
                    .filters(List.of(
                        DataCloudClient.Filter.eq("executionId", executionId),
                        DataCloudClient.Filter.eq("status", "completed")
                    ))
                    .sorts(List.of(DataCloudClient.Sort.desc("stepIndex")))
                    .limit(1)
                    .build())
            .map(entities -> {
                if (entities.isEmpty()) {
                    return http.errorResponse(404, "No checkpoint found for execution: " + executionId);
                }
                Map<String, Object> latest = entities.getFirst().data();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("tenantId", tenantId);
                result.put("executionId", executionId);
                result.put("checkpointId", entities.getFirst().id());
                result.put("stepId", latest.get("stepId"));
                result.put("stepIndex", latest.get("stepIndex"));
                result.put("state", latest.get("state"));
                result.put("restoredAt", Instant.now().toString());
                result.put("message", "Resume execution from step " + latest.get("stepId"));

                client.appendEvent(tenantId,
                    DataCloudClient.Event.of("execution.restore", Map.of(
                        "executionId", executionId,
                        "checkpointId", entities.getFirst().id(),
                        "stepId", latest.get("stepId"),
                        "restoredAt", Instant.now().toString()
                    )))
                    .whenException(e -> log.warn("Failed to emit restore event tenant={} exec={}: {}",
                        tenantId, executionId, e.getMessage()));

                return http.jsonResponse(result);
            })
            .then(Promise::of, e -> {
                log.error("Restore query failed tenant={} exec={}: {}", tenantId, executionId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Restore failed: " + e.getMessage()));
            });
    }

    private String resolveTenantId(HttpRequest request) {
        String tenantId = request.getHeader(io.activej.http.HttpHeaders.of("X-Tenant-Id"));
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = request.getQueryParameter("tenantId");
        }
        return tenantId;
    }

    private HttpResponse missingTenantResponse() {
        return http.jsonResponse(400, Map.of(
            "error", "MISSING_TENANT",
            "message", "X-Tenant-Id header or tenantId query parameter is required",
            "timestamp", Instant.now().toString()
        ));
    }
}