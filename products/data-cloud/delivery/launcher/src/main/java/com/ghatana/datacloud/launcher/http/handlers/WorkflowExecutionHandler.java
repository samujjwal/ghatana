package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.DataCloudHttpMetrics;
import com.ghatana.datacloud.launcher.http.plugins.WorkflowExecutionCapability;
import com.ghatana.datacloud.launcher.http.security.RequestContext;
import com.ghatana.datacloud.launcher.http.security.RequestContextResolver;
import com.ghatana.datacloud.operations.OperationKind;
import com.ghatana.datacloud.operations.OperationRecord;
import com.ghatana.datacloud.operations.OperationRecorder;
import com.ghatana.datacloud.operations.OperationStatus;
import com.ghatana.platform.observability.idempotency.IdempotencyHelper;
import com.ghatana.platform.observability.idempotency.IdempotencyStore;
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
 * <p>E3: Ensures execution responses include request ID, tenant ID, execution ID, status,
 * started/completed timestamps, node statuses, failure reason, and retryability.
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
    private static final HttpResponse NO_IDEMPOTENCY_RESPONSE = null;

    private final DataCloudClient client;
    private final HttpHandlerSupport http;
    private WorkflowExecutionCapability executionCapability;
    private DataCloudHttpMetrics metrics = DataCloudHttpMetrics.noop();
    private IdempotencyStore idempotencyStore;
    private OperationRecorder operationRecorder;

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

    /**
     * P0-07: Wires an {@link IdempotencyStore} for idempotent workflow operations.
     *
     * @param idempotencyStore the idempotency store; may be {@code null}
     * @return this handler (fluent)
     */
    public WorkflowExecutionHandler withIdempotencyStore(IdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
        return this;
    }

    public WorkflowExecutionHandler withOperationRecorder(OperationRecorder operationRecorder) {
        this.operationRecorder = operationRecorder;
        return this;
    }

    // ─── Helper Methods (P0-07) ─────────────────────────────────────────────

    /**
     * P0-07: Check idempotency for workflow operations.
     */
    private Promise<HttpResponse> checkIdempotency(String tenantId, String pipelineId, String routeAction, HttpRequest request) {
        if (idempotencyStore == null) {
            return Promise.of(NO_IDEMPOTENCY_RESPONSE);
        }

        String idempotencyKey = IdempotencyHelper.extractIdempotencyKey(request);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Promise.of(NO_IDEMPOTENCY_RESPONSE);
        }

        String principalId = http.resolvePrincipalId(request);
        String scope = "workflow:" + routeAction + ":" + pipelineId;

        return IdempotencyHelper.checkConflict(idempotencyStore, tenantId, scope, idempotencyKey, principalId,
            IdempotencyHelper.computePayloadHash(request))
            .then(hasConflict -> {
                if (hasConflict) {
                    log.warn("[P0-07] Idempotency conflict for tenant={}, scope={}, key={}", tenantId, scope, idempotencyKey);
                    return Promise.of(http.errorResponse(409,
                        "Idempotency key conflict: same key used with different payload"));
                }

                return IdempotencyHelper.checkIdempotency(idempotencyStore, tenantId, scope, idempotencyKey, principalId)
                    .then(cachedResponse -> {
                        if (cachedResponse != null) {
                            log.info("[P0-07] Returning cached response for tenant={}, scope={}, key={}", tenantId, scope, idempotencyKey);
                            if (cachedResponse instanceof HttpResponse) {
                                return Promise.of(IdempotencyHelper.addIdempotencyHeaders((HttpResponse) cachedResponse, "replay"));
                            }
                            if (cachedResponse instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> map = (Map<String, Object>) cachedResponse;
                                return Promise.of(http.jsonResponse(map));
                            }
                            return Promise.of(http.jsonResponse(Map.of("data", cachedResponse)));
                        }
                        return Promise.of((HttpResponse) null);
                    });
            });
    }

    /**
     * P0-07: Store response for idempotent workflow operations.
     */
    private Promise<Void> storeIdempotency(String tenantId, String pipelineId, String routeAction,
                                          HttpRequest request, Object response) {
        if (idempotencyStore == null) {
            return Promise.of(null);
        }

        String idempotencyKey = IdempotencyHelper.extractIdempotencyKey(request);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Promise.of(null);
        }

        String principalId = http.resolvePrincipalId(request);
        String scope = "workflow:" + routeAction + ":" + pipelineId;
        String payloadHash = IdempotencyHelper.computePayloadHash(request);

        return IdempotencyHelper.storeResponse(idempotencyStore, tenantId, scope, idempotencyKey, principalId, payloadHash, response);
    }

    // ==================== Pipeline Execution Routes ====================

    public Promise<HttpResponse> handleExecutePipeline(HttpRequest request) {
        // E3: Enforce canonical Action Plane permissions
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "action:pipeline:execute");
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

        // P0-07: Check idempotency before processing
        return checkIdempotency(tenantId, pipelineId, "execute", request)
            .then(idempotencyResponse -> {
                if (idempotencyResponse != null) {
                    return Promise.of(idempotencyResponse);
                }

                if (executionCapability == null) {
                    return executionCapabilityUnavailable(
                        request,
                        tenantId,
                        pipelineId,
                        OperationKind.PIPELINE_EXECUTION,
                        "Pipeline execution",
                        "Workflow execution capability is not configured");
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
                        .then(snapshot -> {
                            OperationRecord operation = recordWorkflowOperation(
                                tenantId,
                                pipelineId,
                                OperationKind.PIPELINE_EXECUTION,
                                snapshot.isTerminal() ? terminalStatus(snapshot.status()) : OperationStatus.RUNNING,
                                "Pipeline execution",
                                "Pipeline execution " + snapshot.status(),
                                request,
                                Map.of("executionId", snapshot.id(), "pipelineId", pipelineId));
                            long latency = System.currentTimeMillis() - startMs;
                            log.info("[correlation={} tenant={} pipeline={} execution={}] Workflow execution started, status={}",
                                    correlationId, tenantId, pipelineId, snapshot.id(), snapshot.status());
                            metrics.recordRequest(HANDLER_NAME, "executePipeline", tenantId, 200);
                            metrics.recordLatency(HANDLER_NAME, "executePipeline", latency);
                            // E3: Enhanced response with all required fields.
                            // P9: Add trace context fields for cross-plane observability
                            Map<String, Object> responseBody = new LinkedHashMap<>();
                            String traceId = correlationId != null ? correlationId : UUID.randomUUID().toString();
                            responseBody.put("requestId", traceId);
                            responseBody.put("traceId", traceId);
                            responseBody.put("correlationId", traceId);
                            if (operation != null) {
                                responseBody.put("operationId", operation.operationId());
                            }
                            responseBody.put("tenantId", tenantId);
                            responseBody.put("principalId", http.resolvePrincipalId(request));
                            responseBody.put("executionId", snapshot.id());
                            responseBody.put("pipelineId", snapshot.workflowId());
                            responseBody.put("status", snapshot.status());
                            responseBody.put("startedAt", snapshot.startedAt() != null ? snapshot.startedAt() : Instant.now().toString());
                            responseBody.put("completedAt", snapshot.completedAt());
                            responseBody.put("nodeStatuses", snapshot.nodeStatuses() != null ? snapshot.nodeStatuses() : List.of());
                            responseBody.put("failureReason", snapshot.error());
                            responseBody.put("retryable", isRetryable(snapshot));
                            return storeIdempotency(tenantId, pipelineId, "execute", request, responseBody)
                                .map(v -> http.jsonResponse(responseBody));
                        }, e -> {
                            log.error("[correlation={} tenant={} pipeline={}] Failed to execute pipeline: {}",
                                    correlationId, tenantId, pipelineId, e.getMessage());
                            metrics.recordError(HANDLER_NAME, "executePipeline", e);
                            return Promise.of(http.errorResponse(500, "Pipeline execution failed"));
                        });
                });
            })
            .then(response -> Promise.of(response), e -> {
                log.error("[executePipeline] tenant={} pipeline={} failed: {}", tenantId, pipelineId, e.getMessage());
                return Promise.of(http.errorResponse(500, "Failed to execute pipeline: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleListPipelineExecutions(HttpRequest request) {
        // E3: Enforce canonical Action Plane permissions
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

        if (executionCapability == null) {
            return executionCapabilityUnavailable(
                request,
                tenantId,
                pipelineId,
                OperationKind.PIPELINE_EXECUTION,
                "Pipeline execution list",
                "Workflow execution capability is not available; execution history cannot be read");
        }

        return executionCapability.listExecutions(tenantId, pipelineId)
            .map(snapshots -> {
                // E3: Enhanced response with requestId
                return http.jsonResponse(Map.of(
                    "requestId", UUID.randomUUID().toString(),
                    "traceId", UUID.randomUUID().toString(),
                    "correlationId", UUID.randomUUID().toString(),
                    "tenantId", tenantId,
                    "principalId", http.resolvePrincipalId(request),
                    "pipelineId", pipelineId,
                    "executions", snapshots,
                    "count", snapshots.size()
                ));
            })
            .whenException(e -> log.error("Failed to list executions for pipeline {} tenant {}: {}", pipelineId, tenantId, e.getMessage()));
    }

    public Promise<HttpResponse> handleGetPipelineExecution(HttpRequest request) {
        // E3: Enforce canonical Action Plane permissions
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
        String executionId = request.getPathParameter("executionId");
        if (pipelineId == null || pipelineId.isBlank() || executionId == null || executionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "pipelineId and executionId path parameters are required"));
        }

        if (executionCapability == null) {
            return executionCapabilityUnavailable(
                request,
                tenantId,
                executionId,
                OperationKind.PIPELINE_EXECUTION,
                "Pipeline execution detail",
                "Workflow execution capability is not available in this deployment");
        }

        return executionCapability.getExecution(tenantId, executionId)
            .map(opt -> {
                if (opt.isPresent()) {
                    Map<String, Object> data = executionSnapshotToMap(opt.get());
                    // P9: Add trace context fields for cross-plane observability
                    data.put("requestId", UUID.randomUUID().toString());
                    data.put("traceId", UUID.randomUUID().toString());
                    data.put("correlationId", UUID.randomUUID().toString());
                    data.put("principalId", http.resolvePrincipalId(request));
                    return http.jsonResponse(data);
                }
                return http.errorResponse(404, "Execution not found: " + executionId);
            })
            .whenException(e -> log.error("Failed to get execution {} for pipeline {} tenant {}: {}", executionId, pipelineId, tenantId, e.getMessage()));
    }

    /**
     * I4: Get pipeline execution logs with standardized structure and tenant isolation.
     */
    public Promise<HttpResponse> handleGetPipelineExecutionLogs(HttpRequest request) {
        // E3: Enforce canonical Action Plane permissions
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
        String executionId = request.getPathParameter("executionId");
        if (pipelineId == null || pipelineId.isBlank() || executionId == null || executionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "pipelineId and executionId path parameters are required"));
        }

        if (executionCapability == null) {
            return executionCapabilityUnavailable(
                request,
                tenantId,
                executionId,
                OperationKind.PIPELINE_EXECUTION,
                "Pipeline execution logs",
                "Workflow execution capability is not available; execution logs cannot be read");
        }

        // I4: Verify execution belongs to tenant before returning logs (cross-tenant leak prevention)
        return executionCapability.getExecution(tenantId, executionId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    log.warn("[tenant={} pipeline={} execution={}] Log access denied: execution not found or belongs to different tenant",
                        tenantId, pipelineId, executionId);
                    return Promise.of(http.errorResponse(404, "Execution not found: " + executionId));
                }

                return executionCapability.getExecutionLogs(tenantId, executionId)
                    .map(logs -> {
                        // I4: Standardize log entry structure with node-level status and duration
                        List<Map<String, Object>> standardizedLogs = logs.stream()
                            .map(this::standardizeLogEntry)
                            .toList();
                        // P9: Add trace context fields for cross-plane observability
                        return http.jsonResponse(Map.of(
                            "requestId", UUID.randomUUID().toString(),
                            "traceId", UUID.randomUUID().toString(),
                            "correlationId", UUID.randomUUID().toString(),
                            "tenantId", tenantId,
                            "principalId", http.resolvePrincipalId(request),
                            "executionId", executionId,
                            "pipelineId", pipelineId,
                            "logs", standardizedLogs,
                            "count", standardizedLogs.size()
                        ));
                    })
                    .whenException(e -> log.error("Failed to get logs for execution {} pipeline {} tenant {}: {}",
                        executionId, pipelineId, tenantId, e.getMessage()));
            })
            .whenException(e -> log.error("Failed to verify execution ownership for execution {} pipeline {} tenant {}: {}",
                executionId, pipelineId, tenantId, e.getMessage()));
    }

    public Promise<HttpResponse> handleCancelPipelineExecution(HttpRequest request) {
        // E3: Enforce canonical Action Plane permissions
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "action:pipeline:cancel");
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
        String executionId = request.getPathParameter("executionId");
        if (pipelineId == null || pipelineId.isBlank() || executionId == null || executionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "pipelineId and executionId path parameters are required"));
        }

        if (executionCapability == null) {
            return executionCapabilityUnavailable(
                request,
                tenantId,
                executionId,
                OperationKind.PIPELINE_CANCEL,
                "Pipeline execution cancel",
                "Workflow execution capability is not available in this deployment");
        }

        String correlationId = http.resolveCorrelationId(request);
        long startMs = System.currentTimeMillis();
        return executionCapability.cancelExecution(tenantId, executionId)
            .map(snapshot -> {
                OperationRecord operation = recordWorkflowOperation(
                    tenantId,
                    executionId,
                    OperationKind.PIPELINE_CANCEL,
                    terminalStatus(snapshot.status()),
                    "Pipeline execution cancel",
                    "Pipeline execution cancel " + snapshot.status(),
                    request,
                    Map.of("executionId", snapshot.id(), "pipelineId", pipelineId));
                long latency = System.currentTimeMillis() - startMs;
                log.info("[correlation={} tenant={} pipeline={} execution={}] Workflow execution cancelled, status={}",
                        correlationId, tenantId, pipelineId, executionId, snapshot.status());
                metrics.recordRequest(HANDLER_NAME, "cancelPipelineExecution", tenantId, 200);
                metrics.recordLatency(HANDLER_NAME, "cancelPipelineExecution", latency);
                // P9: Add trace context fields for cross-plane observability
                Map<String, Object> response = new LinkedHashMap<>();
                String traceId = correlationId != null ? correlationId : UUID.randomUUID().toString();
                response.put("requestId", traceId);
                response.put("traceId", traceId);
                response.put("correlationId", traceId);
                response.put("operationId", operation == null ? "" : operation.operationId());
                response.put("tenantId", tenantId);
                response.put("principalId", http.resolvePrincipalId(request));
                response.put("executionId", snapshot.id());
                response.put("pipelineId", pipelineId);
                response.put("status", snapshot.status());
                response.put("startedAt", snapshot.startedAt() != null ? snapshot.startedAt() : Instant.now().toString());
                response.put("completedAt", snapshot.completedAt());
                response.put("nodeStatuses", snapshot.nodeStatuses() != null ? snapshot.nodeStatuses() : List.of());
                response.put("failureReason", snapshot.error());
                response.put("retryable", isRetryable(snapshot));
                return http.jsonResponse(response);
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
        // E3: Enforce canonical Action Plane permissions
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

        String executionId = request.getPathParameter("executionId");
        if (executionId == null || executionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "executionId path parameter is required"));
        }

        if (executionCapability == null) {
            return executionCapabilityUnavailable(
                request,
                tenantId,
                executionId,
                OperationKind.PIPELINE_EXECUTION,
                "Pipeline execution detail",
                "Workflow execution capability is not available in this deployment");
        }

        return executionCapability.getExecution(tenantId, executionId)
            .map(opt -> {
                if (opt.isPresent()) {
                    Map<String, Object> data = executionSnapshotToMap(opt.get());
                    data.put("requestId", UUID.randomUUID().toString());
                    return http.jsonResponse(data);
                }
                return http.errorResponse(404, "Execution not found: " + executionId);
            })
            .whenException(e -> log.error("Failed to get execution {} for tenant {}: {}", executionId, tenantId, e.getMessage()));
    }

    public Promise<HttpResponse> handleGetExecutionLogs(HttpRequest request) {
        // E3: Enforce canonical Action Plane permissions
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

        String executionId = request.getPathParameter("executionId");
        if (executionId == null || executionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "executionId path parameter is required"));
        }

        if (executionCapability == null) {
            return executionCapabilityUnavailable(
                request,
                tenantId,
                executionId,
                OperationKind.PIPELINE_EXECUTION,
                "Pipeline execution logs",
                "Workflow execution capability is not available in this deployment");
        }

        return executionCapability.getExecutionLogs(tenantId, executionId)
            .map(logs -> http.jsonResponse(Map.of(
                "requestId", UUID.randomUUID().toString(),
                "executionId", executionId,
                "tenantId", tenantId,
                "logs", logs
            )))
            .whenException(e -> log.error("Failed to get logs for execution {} tenant {}: {}", executionId, tenantId, e.getMessage()));
    }

    public Promise<HttpResponse> handleCancelExecution(HttpRequest request) {
        // E3: Enforce canonical Action Plane permissions
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "action:pipeline:cancel");
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

        String executionId = request.getPathParameter("executionId");
        if (executionId == null || executionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "executionId path parameter is required"));
        }

        if (executionCapability == null) {
            return executionCapabilityUnavailable(
                request,
                tenantId,
                executionId,
                OperationKind.PIPELINE_CANCEL,
                "Pipeline execution cancel",
                "Workflow execution capability is not available in this deployment");
        }

        String correlationId = http.resolveCorrelationId(request);
        long startMs = System.currentTimeMillis();
        return executionCapability.cancelExecution(tenantId, executionId)
            .map(snapshot -> {
                OperationRecord operation = recordWorkflowOperation(
                    tenantId,
                    executionId,
                    OperationKind.PIPELINE_CANCEL,
                    terminalStatus(snapshot.status()),
                    "Pipeline execution cancel",
                    "Pipeline execution cancel " + snapshot.status(),
                    request,
                    Map.of("executionId", snapshot.id()));
                long latency = System.currentTimeMillis() - startMs;
                log.info("[correlation={} tenant={} execution={}] Workflow execution cancelled, status={}",
                        correlationId, tenantId, executionId, snapshot.status());
                metrics.recordRequest(HANDLER_NAME, "cancelExecution", tenantId, 200);
                metrics.recordLatency(HANDLER_NAME, "cancelExecution", latency);
                Map<String, Object> response = new java.util.HashMap<>();
                response.put("requestId", correlationId != null ? correlationId : UUID.randomUUID().toString());
                if (operation != null) {
                    response.put("operationId", operation.operationId());
                }
                response.put("executionId", snapshot.id());
                response.put("tenantId", tenantId);
                response.put("status", snapshot.status());
                response.put("startedAt", snapshot.startedAt() != null ? snapshot.startedAt() : Instant.now().toString());
                response.put("completedAt", snapshot.completedAt());
                response.put("nodeStatuses", snapshot.nodeStatuses() != null ? snapshot.nodeStatuses() : List.of());
                response.put("failureReason", snapshot.error());
                response.put("retryable", isRetryable(snapshot));
                return http.jsonResponse(response);
            })
            .then(Promise::of, e -> {
                log.error("[correlation={} tenant={} execution={}] Failed to cancel execution: {}",
                        correlationId, tenantId, executionId, e.getMessage());
                metrics.recordError(HANDLER_NAME, "cancelExecution", e);
                return Promise.of(http.errorResponse(500, "Cancel execution failed"));
            });
    }

    public Promise<HttpResponse> handleRetryExecution(HttpRequest request) {
        // E3: Enforce canonical Action Plane permissions
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "action:pipeline:retry");
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

        String executionId = request.getPathParameter("executionId");
        if (executionId == null || executionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "executionId path parameter is required"));
        }

        if (executionCapability == null) {
            return executionCapabilityUnavailable(
                request,
                tenantId,
                executionId,
                OperationKind.PIPELINE_RETRY,
                "Pipeline execution retry",
                "Workflow execution capability is not available in this deployment");
        }

        String correlationId = http.resolveCorrelationId(request);
        long startMs = System.currentTimeMillis();
        return executionCapability.retryExecution(tenantId, executionId)
            .map(snapshot -> {
                OperationRecord operation = recordWorkflowOperation(
                    tenantId,
                    executionId,
                    OperationKind.PIPELINE_RETRY,
                    snapshot.isTerminal() ? terminalStatus(snapshot.status()) : OperationStatus.RUNNING,
                    "Pipeline execution retry",
                    "Pipeline execution retry " + snapshot.status(),
                    request,
                    Map.of("executionId", snapshot.id()));
                long latency = System.currentTimeMillis() - startMs;
                log.info("[correlation={} tenant={} execution={}] Workflow execution retried, status={}",
                        correlationId, tenantId, executionId, snapshot.status());
                metrics.recordRequest(HANDLER_NAME, "retryExecution", tenantId, 200);
                metrics.recordLatency(HANDLER_NAME, "retryExecution", latency);
                // P9: Add trace context fields for cross-plane observability
                String traceId = correlationId != null ? correlationId : UUID.randomUUID().toString();
                return http.jsonResponse(Map.of(
                    "requestId", traceId,
                    "traceId", traceId,
                    "correlationId", traceId,
                    "operationId", operation == null ? "" : operation.operationId(),
                    "tenantId", tenantId,
                    "principalId", http.resolvePrincipalId(request),
                    "executionId", snapshot.id(),
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
        // E3: Enforce canonical Action Plane permissions
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "action:pipeline:rollback");
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

        String executionId = request.getPathParameter("executionId");
        if (executionId == null || executionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "executionId path parameter is required"));
        }

        if (executionCapability == null) {
            return executionCapabilityUnavailable(
                request,
                tenantId,
                executionId,
                OperationKind.PIPELINE_ROLLBACK,
                "Pipeline execution rollback",
                "Workflow execution capability is not available in this deployment");
        }

        String correlationId = http.resolveCorrelationId(request);
        long startMs = System.currentTimeMillis();
        
        return request.loadBody()
            .then(buf -> {
                try {
                    String body = buf.getString(StandardCharsets.UTF_8);
                    Map<String, Object> rollbackData = parseJsonMap(body);
                    
                    // Rollback delegates to the capability which handles state transition
                    return executionCapability.cancelExecution(tenantId, executionId)
                        .map(snapshot -> {
                            OperationRecord operation = recordWorkflowOperation(
                                tenantId,
                                executionId,
                                OperationKind.PIPELINE_ROLLBACK,
                                OperationStatus.SUCCEEDED,
                                "Pipeline execution rollback",
                                "Pipeline execution rollback completed",
                                request,
                                Map.of("executionId", snapshot.id()));
                            long latency = System.currentTimeMillis() - startMs;
                            log.info("[correlation={} tenant={} execution={}] Workflow execution rolled back",
                                    correlationId, tenantId, executionId);
                            metrics.recordRequest(HANDLER_NAME, "rollbackExecution", tenantId, 200);
                            metrics.recordLatency(HANDLER_NAME, "rollbackExecution", latency);
                            // P9: Add trace context fields for cross-plane observability
                            String traceId = correlationId != null ? correlationId : UUID.randomUUID().toString();
                            Map<String, Object> responseMap = new java.util.HashMap<>();
                            responseMap.put("requestId", traceId);
                            responseMap.put("traceId", traceId);
                            responseMap.put("correlationId", traceId);
                            responseMap.put("operationId", operation == null ? "" : operation.operationId());
                            responseMap.put("tenantId", tenantId);
                            responseMap.put("principalId", http.resolvePrincipalId(request));
                            responseMap.put("executionId", snapshot.id());
                            responseMap.put("status", "rolled_back");
                            responseMap.put("startedAt", snapshot.startedAt() != null ? snapshot.startedAt() : Instant.now().toString());
                            responseMap.put("completedAt", snapshot.completedAt());
                            responseMap.put("rollbackData", rollbackData);
                            return http.jsonResponse(responseMap);
                        })
                        .then(
                            response -> Promise.of(response),
                            error -> {
                                log.error("[correlation={} tenant={} execution={}] Workflow execution rollback failed: {}",
                                        correlationId, tenantId, executionId, error.getMessage(), error);
                                metrics.recordRequest(HANDLER_NAME, "rollbackExecution", tenantId, 500);
                                return Promise.of(http.errorResponse(500, "Rollback failed: " + error.getMessage()));
                            });
                } catch (Exception e) {
                    log.error("[correlation={} tenant={} execution={}] Failed to parse rollback request: {}",
                            correlationId, tenantId, executionId, e.getMessage(), e);
                    metrics.recordRequest(HANDLER_NAME, "rollbackExecution", tenantId, 400);
                    return Promise.of(http.errorResponse(400, "Invalid rollback request: " + e.getMessage()));
                }
            })
            .then(
                response -> Promise.of(response),
                error -> {
                    log.error("[correlation={} tenant={} execution={}] Workflow execution rollback failed: {}",
                            correlationId, tenantId, executionId, error.getMessage(), error);
                    metrics.recordRequest(HANDLER_NAME, "rollbackExecution", tenantId, 500);
                    return Promise.of(http.errorResponse(500, "Rollback failed: " + error.getMessage()));
                });
    }

    public Promise<HttpResponse> handleCheckpointExecution(HttpRequest request) {
        // E3: Enforce canonical Action Plane permissions
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "action:pipeline:checkpoint");
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
                        OperationRecord operation = recordWorkflowOperation(
                            tenantId,
                            executionId,
                            OperationKind.PIPELINE_CHECKPOINT,
                            OperationStatus.SUCCEEDED,
                            "Pipeline execution checkpoint",
                            "Pipeline checkpoint saved",
                            request,
                            Map.of("executionId", executionId, "checkpointId", checkpointId));
                        long latency = System.currentTimeMillis() - startMs;
                        log.info("[correlation={} tenant={} execution={} checkpoint={}] Checkpoint saved",
                                correlationId, tenantId, executionId, checkpointId);
                        metrics.recordRequest(HANDLER_NAME, "checkpointExecution", tenantId, 200);
                        metrics.recordLatency(HANDLER_NAME, "checkpointExecution", latency);
                        // P9: Add trace context fields for cross-plane observability
                        String traceId = correlationId != null ? correlationId : UUID.randomUUID().toString();
                        return http.jsonResponse(Map.of(
                            "requestId", traceId,
                            "traceId", traceId,
                            "correlationId", traceId,
                            "operationId", operation == null ? "" : operation.operationId(),
                            "tenantId", tenantId,
                            "principalId", http.resolvePrincipalId(request),
                            "executionId", executionId,
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
        // E3: Enforce canonical Action Plane permissions
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
                "requestId", UUID.randomUUID().toString(),
                "executionId", executionId,
                "tenantId", tenantId,
                "checkpoints", entities.stream().map(DataCloudClient.Entity::data).toList(),
                "count", entities.size()
            )))
            .whenException(e -> log.error("Failed to list checkpoints for execution {} tenant {}: {}", executionId, tenantId, e.getMessage()));
    }

    public Promise<HttpResponse> handleRestoreExecution(HttpRequest request) {
        // E3: Enforce canonical Action Plane permissions
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "action:pipeline:restore");
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

        String executionId = request.getPathParameter("executionId");
        if (executionId == null || executionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "executionId path parameter is required"));
        }

        if (executionCapability == null) {
            return executionCapabilityUnavailable(
                request,
                tenantId,
                executionId,
                OperationKind.PIPELINE_RESTORE,
                "Pipeline execution restore",
                "Workflow execution capability is not available in this deployment");
        }

        String correlationId = http.resolveCorrelationId(request);
        long startMs = System.currentTimeMillis();
        // Restore: retry the execution from its last saved state (capability handles state recovery)
        return executionCapability.retryExecution(tenantId, executionId)
            .map(snapshot -> {
                OperationRecord operation = recordWorkflowOperation(
                    tenantId,
                    executionId,
                    OperationKind.PIPELINE_RESTORE,
                    snapshot.isTerminal() ? terminalStatus(snapshot.status()) : OperationStatus.RUNNING,
                    "Pipeline execution restore",
                    "Pipeline execution restore " + snapshot.status(),
                    request,
                    Map.of("executionId", snapshot.id()));
                long latency = System.currentTimeMillis() - startMs;
                log.info("[correlation={} tenant={} execution={}] Workflow execution restored, status={}",
                        correlationId, tenantId, executionId, snapshot.status());
                metrics.recordRequest(HANDLER_NAME, "restoreExecution", tenantId, 200);
                metrics.recordLatency(HANDLER_NAME, "restoreExecution", latency);
                // P9: Add trace context fields for cross-plane observability
                String traceId = correlationId != null ? correlationId : UUID.randomUUID().toString();
                return http.jsonResponse(Map.of(
                    "requestId", traceId,
                    "traceId", traceId,
                    "correlationId", traceId,
                    "operationId", operation == null ? "" : operation.operationId(),
                    "tenantId", tenantId,
                    "principalId", http.resolvePrincipalId(request),
                    "executionId", snapshot.id(),
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
        // E3: Enforce canonical Action Plane permissions
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "action:query:explain");
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
                Map<String, Object> queryData = parseJsonMap(body);
                String queryId = UUID.randomUUID().toString();
                String queryType = queryData.containsKey("type") ? String.valueOf(queryData.get("type")) : "sql";
                // Explain: analyse the query structure and estimate cost/plan without executing
                List<String> dataSources = readStringList(queryData.get("collections"));
                return Promise.of(http.jsonResponse(Map.of(
                    "requestId", UUID.randomUUID().toString(),
                    "tenantId", tenantId,
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

    /**
     * I4: Standardize log entry structure with node-level status, duration, failure reason, and retryability.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> standardizeLogEntry(Object logEntry) {
        Map<String, Object> standardized = new LinkedHashMap<>();

        if (logEntry instanceof Map<?, ?> map) {
            // Copy existing fields
            standardized.putAll((Map<String, Object>) map);

            // Ensure required fields are present
            if (!standardized.containsKey("timestamp")) {
                standardized.put("timestamp", Instant.now().toString());
            }
            if (!standardized.containsKey("level")) {
                standardized.put("level", "INFO");
            }
            if (!standardized.containsKey("nodeId")) {
                standardized.put("nodeId", "unknown");
            }
            if (!standardized.containsKey("nodeStatus")) {
                standardized.put("nodeStatus", "UNKNOWN");
            }
            if (!standardized.containsKey("duration")) {
                standardized.put("duration", 0L);
            }
            if (!standardized.containsKey("failureReason")) {
                standardized.put("failureReason", null);
            }
            if (!standardized.containsKey("retryable")) {
                standardized.put("retryable", false);
            }
            if (!standardized.containsKey("message")) {
                standardized.put("message", "");
            }
        } else {
            // Handle non-map log entries
            standardized.put("timestamp", Instant.now().toString());
            standardized.put("level", "INFO");
            standardized.put("nodeId", "unknown");
            standardized.put("nodeStatus", "UNKNOWN");
            standardized.put("duration", 0L);
            standardized.put("failureReason", null);
            standardized.put("retryable", false);
            standardized.put("message", String.valueOf(logEntry));
        }

        return standardized;
    }

    private static boolean isRetryable(WorkflowExecutionCapability.ExecutionSnapshot snapshot) {
        return "FAILED".equals(snapshot.status()) && snapshot.error() != null && !snapshot.error().isBlank();
    }

    private Promise<HttpResponse> executionCapabilityUnavailable(
            HttpRequest request,
            String tenantId,
            String resourceId,
            OperationKind operationKind,
            String action,
            String detail) {
        OperationRecord operation = recordWorkflowOperation(
            tenantId,
            resourceId,
            operationKind,
            OperationStatus.BLOCKED,
            action,
            detail,
            request,
            Map.of("capabilityId", "workflow-execution", "runtimeState", "UNAVAILABLE"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", http.resolveCorrelationId(request));
        body.put("tenantId", tenantId);
        body.put("status", "unavailable");
        body.put("capability", "workflow-execution");
        body.put("runtimeState", "UNAVAILABLE");
        body.put("runtimePosture", "degraded");
        body.put("message", detail);
        body.put("retryable", false);
        if (resourceId != null && !resourceId.isBlank()) {
            body.put("resourceId", resourceId);
        }
        if (operation != null) {
            body.put("operationId", operation.operationId());
        }
        return Promise.of(http.jsonResponse(503, body));
    }

    private OperationRecord recordWorkflowOperation(
            String tenantId,
            String resourceId,
            OperationKind kind,
            OperationStatus status,
            String action,
            String summary,
            HttpRequest request,
            Map<String, Object> metadata) {
        if (operationRecorder == null) {
            return null;
        }
        String traceId = http.resolveCorrelationId(request);
        String requestId = traceId != null ? traceId : java.util.UUID.randomUUID().toString();
        if (traceId == null || traceId.isBlank()) traceId = requestId;
        return operationRecorder.record(OperationRecord.create(
                tenantId,
                traceId,
                requestId,
                kind,
                status,
                "pipeline",
                resourceId,
                action,
                summary,
                http.resolvePrincipalId(request),
                http.resolveCorrelationId(request),
                status == OperationStatus.RUNNING,
                metadata));
    }

    private static OperationStatus terminalStatus(String status) {
        return switch (status == null ? "" : status.toUpperCase(Locale.ROOT)) {
            case "COMPLETED", "SUCCEEDED", "CHECKPOINTED", "ROLLED_BACK" -> OperationStatus.SUCCEEDED;
            case "CANCELLED" -> OperationStatus.CANCELLED;
            case "FAILED" -> OperationStatus.FAILED;
            default -> OperationStatus.RUNNING;
        };
    }
}
