/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.workflow;

import com.ghatana.platform.workflow.engine.DurableWorkflowEngine;
import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.JsonUtils;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for starting and monitoring durable workflow runs.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code POST /api/v1/workflows/:templateId/start} — start a new workflow run</li>
 *   <li>{@code GET  /api/v1/workflows/runs/:runId/status} — get run status</li>
 * </ul>
 *
 * <h2>Start Request Body (JSON)</h2>
 * <pre>
 * {
 *   "variables": { "key": "value" }   // optional initial context variables
 * }
 * </pre>
 *
 * <h2>Start Response Body (JSON, 202 Accepted)</h2>
 * <pre>
 * {
 *   "runId": "new-feature-&lt;uuid&gt;",
 *   "templateId": "new-feature",
 *   "status": "RUNNING"
 * }
 * </pre>
 *
 * <h2>Status Response Body (JSON, 200 OK)</h2>
 * <pre>
 * {
 *   "runId": "new-feature-&lt;uuid&gt;",
 *   "status": "RUNNING|COMPLETED|FAILED|COMPENSATED",
 *   "stepStatuses": ["COMPLETED", "RUNNING", "PENDING"],
 *   "failureReason": null
 * }
 * </pre>
 *
 * @doc.type class
 * @doc.purpose REST API for starting and monitoring YAPPC workflow runs
 * @doc.layer api
 * @doc.pattern Controller
 * @doc.gaa.lifecycle perceive, act
 */
public class WorkflowExecutionController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionController.class);

    private final WorkflowMaterializer materializer;

    /**
     * Creates a new {@code WorkflowExecutionController}.
     *
     * @param materializer the materializer holding registered templates and active runs
     */
    public WorkflowExecutionController(WorkflowMaterializer materializer) {
        this.materializer = materializer;
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/workflows/:templateId/start
    // -------------------------------------------------------------------------

    /**
     * Starts a new durable workflow run for the specified template.
     *
     * <p>Returns {@code 202 Accepted} immediately; the run proceeds asynchronously.
     *
     * @param request    the HTTP request (must be authenticated)
     * @param templateId the registered workflow template ID
     * @return Promise resolving to the HTTP response
     */
    public Promise<HttpResponse> startWorkflow(HttpRequest request, String templateId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx ->
                        JsonUtils.parseBody(request, StartWorkflowRequest.class)
                                .map(body -> {
                                    log.info("Starting workflow templateId='{}' tenant='{}'",
                                            templateId, ctx.tenantId());

                                    Map<String, Object> vars = body.variables() != null
                                            ? body.variables()
                                            : Collections.emptyMap();

                                    DurableWorkflowEngine.WorkflowExecution exec =
                                            materializer.startWorkflow(templateId, ctx.tenantId(), vars);

                                    Map<String, Object> resp = new LinkedHashMap<>();
                                    resp.put("runId",      exec.run().workflowId());
                                    resp.put("templateId", templateId);
                                    resp.put("status",     exec.run().status().name());

                                    return ApiResponse.accepted(resp);
                                }))
                .then(Promise::of, e -> {
                    if (e instanceof IllegalArgumentException) {
                        log.warn("Unknown template requested: {}", templateId);
                        return Promise.of(ApiResponse.notFound("Workflow template not found: " + templateId));
                    }
                    log.error("Failed to start workflow '{}': {}", templateId, e.getMessage(), e);
                    return Promise.of(ApiResponse.fromException(e));
                });
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/workflows/runs/:runId/status
    // -------------------------------------------------------------------------

    /**
     * Returns the current status of a running or completed workflow run.
     *
     * @param request the HTTP request (must be authenticated)
     * @param runId   the run ID returned by the start endpoint
     * @return Promise resolving to the HTTP response
     */
    public Promise<HttpResponse> getRunStatus(HttpRequest request, String runId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .map(ctx -> {
                    log.debug("Status query for runId='{}' tenant='{}'", runId, ctx.tenantId());

                    return materializer.getRunStatus(runId)
                            .map(run -> {
                                List<String> stepStatuses = Arrays.stream(run.stepStatuses())
                                        .map(Enum::name)
                                        .collect(Collectors.toList());

                                Map<String, Object> resp = new LinkedHashMap<>();
                                resp.put("runId",         run.workflowId());
                                resp.put("status",        run.status().name());
                                resp.put("stepStatuses",  stepStatuses);
                                resp.put("failureReason", run.failureReason());

                                return ApiResponse.ok(resp);
                            })
                            .orElseGet(() -> ApiResponse.notFound("Run not found: " + runId));
                })
                .then(Promise::of, e -> {
                    log.error("Error fetching status for run '{}': {}", runId, e.getMessage(), e);
                    return Promise.of(ApiResponse.fromException(e));
                });
    }

    // -------------------------------------------------------------------------
    // Inner DTO
    // -------------------------------------------------------------------------

    /**
     * Request body for {@code POST /api/v1/workflows/:templateId/start}.
     *
     * @param variables optional initial context variables (key → value)
     */
    record StartWorkflowRequest(Map<String, Object> variables) {}
}
