package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.launcher.http.plugins.DataCloudRuntimePluginManager;
import com.ghatana.datacloud.launcher.http.plugins.WorkflowExecutionCapability;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose HTTP endpoints for plugin-backed workflow execution
 * @doc.layer product
 * @doc.pattern Handler
 */
public final class WorkflowExecutionHandler {

    private final HttpHandlerSupport http;
    private final DataCloudRuntimePluginManager runtimePluginManager;

    public WorkflowExecutionHandler(HttpHandlerSupport http, DataCloudRuntimePluginManager runtimePluginManager) {
        this.http = http;
        this.runtimePluginManager = runtimePluginManager;
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
        return capability.listExecutions(tenantId, pipelineId)
            .map(executions -> http.jsonResponse(Map.of(
                "items", executions.stream().map(this::toWorkflowExecution).toList(),
                "total", executions.size(),
                "page", 1,
                "pageSize", executions.size(),
                "hasMore", false
            )));
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
        response.put("status", snapshot.status().toLowerCase());
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
        return response;
    }

    private Map<String, Object> toExecutionState(WorkflowExecutionCapability.ExecutionSnapshot snapshot) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", snapshot.id());
        response.put("pipelineId", snapshot.workflowId());
        response.put("pipelineName", snapshot.workflowName());
        response.put("status", snapshot.status().toLowerCase());
        response.put("startTime", snapshot.startedAt());
        if (snapshot.completedAt() != null) {
            response.put("endTime", snapshot.completedAt());
        }
        List<WorkflowExecutionCapability.NodeSnapshot> nodeStatuses = snapshot.nodeStatuses();
        response.put("completedNodes", nodeStatuses.stream().filter(node -> "COMPLETED".equals(node.state())).count());
        response.put("totalNodes", nodeStatuses.size());
        response.put("nodes", nodeStatuses.stream().map(node -> {
            Integer nodeDuration = java.util.Objects.requireNonNullElse(node.duration(), Integer.valueOf(0));
            return Map.of(
            "id", node.nodeId(),
            "name", node.nodeName(),
            "status", node.state().toLowerCase(),
            "startTime", node.startedAt(),
            "endTime", node.completedAt(),
            "duration", nodeDuration,
            "error", node.error() == null ? "" : node.error()
            );
        }).toList());
        if (snapshot.error() != null) {
            response.put("error", snapshot.error());
        }
        return response;
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

    private HttpResponse missingTenantResponse() {
        return http.jsonResponse(400, Map.of(
            "error", "MISSING_TENANT",
            "message", "X-Tenant-Id header or tenantId query parameter is required",
            "timestamp", Instant.now().toString()
        ));
    }
}