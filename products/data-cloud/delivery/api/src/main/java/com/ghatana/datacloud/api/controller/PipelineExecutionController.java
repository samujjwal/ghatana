package com.ghatana.datacloud.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.application.WorkflowService;
import com.ghatana.datacloud.entity.WorkflowExecution;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * REST controller for pipeline (workflow) execution operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides HTTP API endpoints for triggering and monitoring workflow executions.
 * Supports starting a pipeline run, listing execution history, and retrieving
 * execution status.
 *
 * <p>
 * <b>Endpoints</b><br>
 * <ul>
 * <li><b>POST /api/v1/pipelines/{workflowId}/execute:</b> Start a pipeline execution
 * <li><b>GET /api/v1/pipelines/{workflowId}/executions:</b> List executions for a pipeline
 * <li><b>GET /api/v1/pipelines/executions/{executionId}:</b> Get execution status by ID
 * </ul>
 *
 * <p>
 * <b>Multi-Tenancy</b><br>
 * All operations extract tenantId from X-Tenant-ID header and enforce tenant isolation.
 *
 * <p>
 * <b>Error Handling</b><br>
 * - 400: Invalid request (missing fields, invalid IDs)
 * - 401: Missing tenant context
 * - 404: Workflow or execution not found
 * - 409: Workflow not in an executable state
 * - 500: Internal server error
 *
 * <p>
 * <b>Performance</b><br>
 * All operations async (Promise-based) for non-blocking execution.
 *
 * @see WorkflowService
 * @see WorkflowExecution
 * @doc.type class
 * @doc.purpose REST API controller for pipeline execution management
 * @doc.layer product
 * @doc.pattern Controller (API Layer)
 */
@Tag(name = "Pipeline Executions", description = "Pipeline execution trigger and status endpoints")
public class PipelineExecutionController {

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutionController.class);

    private static final HttpHeader HEADER_TENANT_ID = HttpHeaders.of("X-Tenant-ID");
    private static final HttpHeader HEADER_USER_ID = HttpHeaders.of("X-User-ID");

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final WorkflowService workflowService;
    private final MetricsCollector metrics;
    private final ObjectMapper mapper;

    /**
     * Creates a new pipeline execution controller.
     *
     * @param workflowService service for workflow and execution operations
     * @param metrics         metrics collector for observability
     * @param mapper          JSON object mapper
     */
    public PipelineExecutionController(
            WorkflowService workflowService,
            MetricsCollector metrics,
            ObjectMapper mapper) {
        this.workflowService = Objects.requireNonNull(workflowService, "WorkflowService cannot be null");
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector cannot be null");
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper cannot be null");
        log.info("PipelineExecutionController initialized");
    }

    /**
     * Dispatches incoming execution-related requests.
     *
     * @param request HTTP request
     * @return Promise of HTTP response
     */
    public Promise<HttpResponse> handle(HttpRequest request) {
        String tenantId = extractTenantId(request);
        if (tenantId == null || tenantId.isBlank()) {
            metrics.incrementCounter("controller.pipeline_execution.error",
                    "error_type", "MISSING_TENANT_ID");
            return Promise.of(ResponseBuilder.badRequest()
                    .json(Collections.singletonMap("error", "X-Tenant-ID header is required"))
                    .build());
        }

        String path = request.getPath();
        HttpMethod method = request.getMethod();

        try {
                if (method == HttpMethod.POST && (path.matches("/api/v1/action/pipelines/[a-f0-9-]+/execute")
                    || path.matches("/api/v1/pipelines/[a-f0-9-]+/execute"))) {
                UUID workflowId = extractWorkflowIdFromExecutePath(path);
                return triggerExecution(request, tenantId, workflowId, path);
                } else if (method == HttpMethod.GET && (path.matches("/api/v1/action/pipelines/[a-f0-9-]+/executions")
                    || path.matches("/api/v1/pipelines/[a-f0-9-]+/executions"))) {
                UUID workflowId = extractWorkflowIdFromExecutionsPath(path);
                return listExecutions(request, tenantId, workflowId, path);
            } else if (method == HttpMethod.GET && (path.matches("/api/v1/action/pipelines/executions/[a-f0-9-]+")
                || path.matches("/api/v1/pipelines/executions/[a-f0-9-]+"))) {
                UUID executionId = extractExecutionIdFromPath(path);
                return getExecution(tenantId, executionId, path);
            } else {
                metrics.incrementCounter("controller.pipeline_execution.error",
                        "error_type", "NOT_FOUND");
                return Promise.of(ResponseBuilder.notFound()
                        .json(Collections.singletonMap("error", "Endpoint not found"))
                        .build());
            }
        } catch (IllegalArgumentException e) {
            metrics.incrementCounter("controller.pipeline_execution.error",
                    "error_type", "BAD_REQUEST");
            log.warn("Bad request for pipeline execution: tenantId={}, path={}, error={}",
                    tenantId, path, e.getMessage());
            return Promise.of(ResponseBuilder.badRequest()
                    .json(Collections.singletonMap("error", e.getMessage()))
                    .build());
        } catch (Exception e) {
            metrics.incrementCounter("controller.pipeline_execution.error",
                    "error_type", e.getClass().getSimpleName());
            log.error("Error handling pipeline execution request: tenantId={}, path={}", tenantId, path, e);
            return Promise.of(ResponseBuilder.internalServerError()
                    .json(Collections.singletonMap("error", "Internal server error"))
                    .build());
        }
    }

    /**
     * Triggers a new pipeline execution.
     *
     * <p>POST /api/v1/pipelines/{workflowId}/execute
     *
     * @param request    HTTP request containing optional input variables
     * @param tenantId   tenant identifier
     * @param workflowId pipeline/workflow to execute
     * @return 202 Accepted with execution ID and status
     */
    @Operation(summary = "Trigger a pipeline execution",
            description = "Start a new execution run for the given pipeline.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Execution accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Missing tenant context"),
            @ApiResponse(responseCode = "404", description = "Pipeline not found"),
            @ApiResponse(responseCode = "409", description = "Pipeline not in executable state")
    })
    private Promise<HttpResponse> triggerExecution(
            HttpRequest request,
            String tenantId,
            UUID workflowId,
            String requestPath) {

        String userId = extractUserId(request);
        if (userId == null || userId.isBlank()) {
            metrics.incrementCounter("controller.pipeline_execution.error",
                "error_type", "MISSING_USER_ID");
            return Promise.of(ResponseBuilder.unauthorized()
                .json(Collections.singletonMap("error", "X-User-ID header is required"))
                .build());
        }

        return request.loadBody().then(bodyBuf -> {
            final Map<String, Object> inputVariables;
            try {
            inputVariables = parseInputVariables(bodyBuf != null ? bodyBuf.asArray() : null);
            } catch (IllegalArgumentException e) {
            metrics.incrementCounter("controller.pipeline_execution.error",
                "error_type", "INVALID_INPUT_VARIABLES");
            return Promise.of(ResponseBuilder.badRequest()
                .json(Collections.singletonMap("error", e.getMessage()))
                .build());
            }

            final String triggeredBy = userId;
            metrics.incrementCounter("controller.pipeline_execution.trigger.attempt",
                "tenant", tenantId);

            return workflowService.executeWorkflow(tenantId, workflowId, triggeredBy, inputVariables)
                .then(execution -> {
                metrics.incrementCounter("controller.pipeline_execution.trigger.success",
                    "tenant", tenantId);
                log.info("Pipeline execution triggered: tenantId={}, workflowId={}, executionId={}, triggeredBy={}",
                    tenantId, workflowId, execution.getId(), triggeredBy);
                Map<String, Object> body = Map.of(
                    "executionId", execution.getId().toString(),
                    "workflowId", execution.getWorkflowId().toString(),
                    "status", execution.getStatus().name(),
                    "startedAt", execution.getStartedAt().toString(),
                    "triggeredBy", execution.getStartedBy()
                );
                return Promise.of(ResponseBuilder.accepted()
                    .json(body)
                    .build());
                })
                .map(response -> maybeAddLegacyDeprecationHeaders(response, requestPath,
                    "/api/v1/action/pipelines/" + workflowId + "/execute"))
                .whenException(ex -> {
                if (ex instanceof IllegalArgumentException) {
                    metrics.incrementCounter("controller.pipeline_execution.trigger.not_found",
                        "tenant", tenantId);
                } else if (ex instanceof IllegalStateException) {
                    metrics.incrementCounter("controller.pipeline_execution.trigger.not_executable",
                        "tenant", tenantId);
                } else {
                    metrics.incrementCounter("controller.pipeline_execution.trigger.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                    log.error("Failed to trigger pipeline execution: tenantId={}, workflowId={}",
                        tenantId, workflowId, ex);
                }
                });
        });
    }

    /**
     * Lists executions for a pipeline.
     *
     * <p>GET /api/v1/pipelines/{workflowId}/executions
     *
     * @param request    HTTP request with optional pagination query params
     * @param tenantId   tenant identifier
     * @param workflowId pipeline/workflow whose executions to list
     * @return 200 OK with paginated list of execution summaries
     */
    private Promise<HttpResponse> listExecutions(
            HttpRequest request,
            String tenantId,
            UUID workflowId,
            String requestPath) {

        int limit = parseIntParam(request, "limit", DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE);
        int offset = parseIntParam(request, "offset", 0, 0, Integer.MAX_VALUE);

        metrics.incrementCounter("controller.pipeline_execution.list.attempt",
                "tenant", tenantId);

        return workflowService.listExecutions(tenantId, workflowId, offset, limit)
                .then(executions -> {
                    metrics.incrementCounter("controller.pipeline_execution.list.success",
                            "tenant", tenantId);
                    List<Map<String, Object>> items = executions.stream()
                            .map(this::toSummaryMap)
                            .toList();
                    Map<String, Object> body = Map.of(
                            "items", items,
                            "total", items.size(),
                            "offset", offset,
                            "limit", limit
                    );
                    return Promise.of(ResponseBuilder.ok()
                            .json(body)
                            .build());
                })
                .map(response -> maybeAddLegacyDeprecationHeaders(response, requestPath,
                    "/api/v1/action/pipelines/" + workflowId + "/executions"))
                .whenException(ex -> {
                    metrics.incrementCounter("controller.pipeline_execution.list.error",
                            "tenant", tenantId,
                            "error", ex.getClass().getSimpleName());
                    log.error("Failed to list executions: tenantId={}, workflowId={}",
                            tenantId, workflowId, ex);
                });
    }

    /**
     * Gets a single execution by ID.
     *
     * <p>GET /api/v1/pipelines/executions/{executionId}
     *
     * @param tenantId    tenant identifier
     * @param executionId execution to retrieve
     * @return 200 OK with execution detail, or 404 if not found
     */
    private Promise<HttpResponse> getExecution(String tenantId, UUID executionId, String requestPath) {
        metrics.incrementCounter("controller.pipeline_execution.get.attempt",
                "tenant", tenantId);

        return workflowService.getExecution(tenantId, executionId)
                .then(executionOpt -> {
                    if (executionOpt.isEmpty()) {
                        metrics.incrementCounter("controller.pipeline_execution.get.not_found",
                                "tenant", tenantId);
                        return Promise.of(ResponseBuilder.notFound()
                                .json(Collections.singletonMap("error",
                                        "Execution not found: " + executionId))
                                .build());
                    }
                    metrics.incrementCounter("controller.pipeline_execution.get.success",
                            "tenant", tenantId);
                    return Promise.of(ResponseBuilder.ok()
                            .json(toDetailMap(executionOpt.get()))
                            .build());
                })
                .map(response -> maybeAddLegacyDeprecationHeaders(response, requestPath,
                    "/api/v1/action/pipelines/executions/" + executionId))
                .whenException(ex -> {
                    metrics.incrementCounter("controller.pipeline_execution.get.error",
                            "tenant", tenantId,
                            "error", ex.getClass().getSimpleName());
                    log.error("Failed to get execution: tenantId={}, executionId={}",
                            tenantId, executionId, ex);
                });
    }

    // -------------------------------------------------------------------------
    // Serialisation helpers
    // -------------------------------------------------------------------------

    private Map<String, Object> toSummaryMap(WorkflowExecution execution) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("executionId", execution.getId().toString());
        map.put("workflowId", execution.getWorkflowId().toString());
        map.put("status", execution.getStatus().name());
        map.put("startedAt", execution.getStartedAt().toString());
        map.put("startedBy", execution.getStartedBy());
        if (execution.getCompletedAt() != null) {
            map.put("completedAt", execution.getCompletedAt().toString());
        }
        if (execution.getErrorMessage() != null) {
            map.put("errorMessage", execution.getErrorMessage());
        }
        return Collections.unmodifiableMap(map);
    }

    private Map<String, Object> toDetailMap(WorkflowExecution execution) {
        Map<String, Object> map = new LinkedHashMap<>(toSummaryMap(execution));
        map.put("inputVariables", execution.getInputVariables());
        map.put("outputVariables", execution.getOutputVariables());
        List<Map<String, Object>> nodes = execution.getNodeExecutions().stream()
                .map(n -> {
                    Map<String, Object> nm = new LinkedHashMap<>();
                    nm.put("nodeId", n.getNodeId());
                    nm.put("nodeName", n.getNodeName());
                    nm.put("status", n.getStatus().name());
                    if (n.getStartedAt() != null) nm.put("startedAt", n.getStartedAt().toString());
                    if (n.getCompletedAt() != null) nm.put("completedAt", n.getCompletedAt().toString());
                    if (n.getErrorMessage() != null) nm.put("errorMessage", n.getErrorMessage());
                    return Collections.unmodifiableMap(nm);
                })
                .toList();
        map.put("nodeExecutions", nodes);
        return Collections.unmodifiableMap(map);
    }

    // -------------------------------------------------------------------------
    // Request parsing helpers
    // -------------------------------------------------------------------------

    private String extractTenantId(HttpRequest request) {
        return request.getHeader(HEADER_TENANT_ID);
    }

    private String extractUserId(HttpRequest request) {
        return request.getHeader(HEADER_USER_ID);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseInputVariables(byte[] body) {
        if (body == null || body.length == 0) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = mapper.readValue(body, Map.class);
            Object variables = parsed.get("inputVariables");
            if (variables == null) {
                return Map.of();
            }
            if (variables instanceof Map<?, ?> varMap) {
                Map<String, Object> result = new HashMap<>();
                varMap.forEach((k, v) -> {
                    if (k instanceof String key) {
                        result.put(key, v);
                    }
                });
                return Collections.unmodifiableMap(result);
            }
            throw new IllegalArgumentException("inputVariables must be a JSON object when provided");
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid request body JSON");
        }
    }

    private int parseIntParam(HttpRequest request, String name, int defaultValue, int min, int max) {
        String raw = request.getQueryParameter(name);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private UUID extractWorkflowIdFromExecutePath(String path) {
        // Patterns:
        // - /api/v1/pipelines/{workflowId}/execute
        // - /api/v1/action/pipelines/{workflowId}/execute
        String[] segments = path.split("/");
        int workflowIdIndex = "action".equals(segments[3]) ? 5 : 4;
        return UUID.fromString(segments[workflowIdIndex]);
    }

    private UUID extractWorkflowIdFromExecutionsPath(String path) {
        // Patterns:
        // - /api/v1/pipelines/{workflowId}/executions
        // - /api/v1/action/pipelines/{workflowId}/executions
        String[] segments = path.split("/");
        int workflowIdIndex = "action".equals(segments[3]) ? 5 : 4;
        return UUID.fromString(segments[workflowIdIndex]);
    }

    private UUID extractExecutionIdFromPath(String path) {
        // Patterns:
        // - /api/v1/pipelines/executions/{executionId}
        // - /api/v1/action/pipelines/executions/{executionId}
        String[] segments = path.split("/");
        int executionIdIndex = "action".equals(segments[3]) ? 6 : 5;
        return UUID.fromString(segments[executionIdIndex]);
    }

    private HttpResponse maybeAddLegacyDeprecationHeaders(HttpResponse response, String requestPath, String successorPath) {
        if (!requestPath.startsWith("/api/v1/pipelines/")) {
            return response;
        }

        HttpResponse.Builder builder = HttpResponse.ofCode(response.getCode())
            .withBody(response.getBody());
        copyHeaderIfPresent(response, builder, HttpHeaders.CONTENT_TYPE);
        builder.withHeader(HttpHeaders.of("Deprecation"), "true");
        builder.withHeader(HttpHeaders.of("Sunset"), "Thu, 31 Dec 2026 00:00:00 GMT");
        builder.withHeader(HttpHeaders.of("Link"), "<" + successorPath + ">; rel=\"successor-version\"");
        return builder.build();
    }

    private void copyHeaderIfPresent(HttpResponse response, HttpResponse.Builder builder, io.activej.http.HttpHeader header) {
        String value = response.getHeader(header);
        if (value != null && !value.isBlank()) {
            builder.withHeader(header, value);
        }
    }
}
