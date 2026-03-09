package com.ghatana.products.yappc.domain.workflow.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.products.yappc.domain.workflow.*;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP Controller for AI Workflow operations.
 * <p>
 * Provides REST endpoints for workflow CRUD, state transitions,
 * and AI plan management.
 *
 * @doc.type class
 * @doc.purpose HTTP API for AI workflows
 * @doc.layer product
 * @doc.pattern Controller
 */
public class WorkflowController {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowController.class);

    private final AiWorkflowService workflowService;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new WorkflowController.
     *
     * @param workflowService The workflow service
     * @param objectMapper JSON mapper
     */
    public WorkflowController(
        @NotNull AiWorkflowService workflowService,
        @NotNull ObjectMapper objectMapper
    ) {
        this.workflowService = Objects.requireNonNull(workflowService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    // ==================== WORKFLOW CRUD ====================

    /**
     * Creates a new workflow.
     * POST /api/v1/workflows
     */
    @NotNull
    public Promise<HttpResponse> createWorkflow(@NotNull HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    CreateWorkflowDto dto = objectMapper.readValue(
                        body.getString(StandardCharsets.UTF_8),
                        CreateWorkflowDto.class
                    );

                    String tenantId = extractTenantId(request);

                    AiWorkflowService.CreateWorkflowRequest createRequest =
                        new AiWorkflowService.CreateWorkflowRequest(
                            tenantId,
                            dto.name(),
                            dto.description(),
                            dto.type(),
                            dto.createdBy()
                        );

                    return workflowService.createWorkflow(createRequest)
                        .map(workflow -> ResponseBuilder.created().json(workflow).build());

                } catch (Exception e) {
                    LOG.error("Failed to create workflow", e);
                    return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "Invalid request: " + e.getMessage()))
                        .build());
                }
            });
    }

    /**
     * Gets a workflow by ID.
     * GET /api/v1/workflows/:id
     */
    @NotNull
    public Promise<HttpResponse> getWorkflow(@NotNull HttpRequest request, @NotNull String id) {
        String tenantId = extractTenantId(request);

        return workflowService.getWorkflow(id, tenantId)
            .map(optWorkflow -> optWorkflow
                .map(w -> ResponseBuilder.ok().json(w).build())
                .orElseGet(() -> ResponseBuilder.notFound()
                    .json(Map.of("error", "Workflow not found: " + id))
                    .build()));
    }

    /**
     * Lists workflows for a tenant.
     * GET /api/v1/workflows
     */
    @NotNull
    public Promise<HttpResponse> listWorkflows(@NotNull HttpRequest request) {
        String tenantId = extractTenantId(request);
        String statusParam = request.getQueryParameter("status");
        int limit = getIntParam(request, "limit", 20);
        int offset = getIntParam(request, "offset", 0);

        AiWorkflowInstance.WorkflowStatus status = null;
        if (statusParam != null && !statusParam.isEmpty()) {
            try {
                status = AiWorkflowInstance.WorkflowStatus.valueOf(statusParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Promise.of(ResponseBuilder.badRequest()
                    .json(Map.of("error", "Invalid status: " + statusParam))
                    .build());
            }
        }

        return workflowService.listWorkflows(tenantId, status, limit, offset)
            .map(workflows -> ResponseBuilder.ok()
                .json(Map.of(
                    "workflows", workflows,
                    "count", workflows.size(),
                    "limit", limit,
                    "offset", offset
                ))
                .build());
    }

    /**
     * Deletes a workflow.
     * DELETE /api/v1/workflows/:id
     */
    @NotNull
    public Promise<HttpResponse> deleteWorkflow(@NotNull HttpRequest request, @NotNull String id) {
        String tenantId = extractTenantId(request);

        return workflowService.deleteWorkflow(id, tenantId)
            .map(deleted -> deleted
                ? ResponseBuilder.noContent().build()
                : ResponseBuilder.notFound()
                    .json(Map.of("error", "Workflow not found: " + id))
                    .build());
    }

    // ==================== STATE TRANSITIONS ====================

    /**
     * Starts a workflow.
     * POST /api/v1/workflows/:id/start
     */
    @NotNull
    public Promise<HttpResponse> startWorkflow(@NotNull HttpRequest request, @NotNull String id) {
        String tenantId = extractTenantId(request);

        return workflowService.startWorkflow(id, tenantId)
            .map(workflow -> ResponseBuilder.ok().json(workflow).build())
            .then(Promise::of, this::handleWorkflowException);
    }

    /**
     * Pauses a workflow.
     * POST /api/v1/workflows/:id/pause
     */
    @NotNull
    public Promise<HttpResponse> pauseWorkflow(@NotNull HttpRequest request, @NotNull String id) {
        String tenantId = extractTenantId(request);

        return workflowService.pauseWorkflow(id, tenantId)
            .map(workflow -> ResponseBuilder.ok().json(workflow).build())
            .then(Promise::of, this::handleWorkflowException);
    }

    /**
     * Resumes a workflow.
     * POST /api/v1/workflows/:id/resume
     */
    @NotNull
    public Promise<HttpResponse> resumeWorkflow(@NotNull HttpRequest request, @NotNull String id) {
        String tenantId = extractTenantId(request);

        return workflowService.resumeWorkflow(id, tenantId)
            .map(workflow -> ResponseBuilder.ok().json(workflow).build())
            .then(Promise::of, this::handleWorkflowException);
    }

    /**
     * Cancels a workflow.
     * POST /api/v1/workflows/:id/cancel
     */
    @NotNull
    public Promise<HttpResponse> cancelWorkflow(@NotNull HttpRequest request, @NotNull String id) {
        String tenantId = extractTenantId(request);

        return workflowService.cancelWorkflow(id, tenantId)
            .map(workflow -> ResponseBuilder.ok().json(workflow).build())
            .then(Promise::of, this::handleWorkflowException);
    }

    // ==================== STEP OPERATIONS ====================

    /**
     * Advances to the next step.
     * POST /api/v1/workflows/:id/steps/advance
     */
    @NotNull
    public Promise<HttpResponse> advanceStep(@NotNull HttpRequest request, @NotNull String id) {
        return request.loadBody()
            .then(body -> {
                try {
                    String tenantId = extractTenantId(request);
                    StepResultDto dto = objectMapper.readValue(body.getString(StandardCharsets.UTF_8), StepResultDto.class);

                    AiWorkflowInstance.AiWorkflowStepResult stepResult =
                        new AiWorkflowInstance.AiWorkflowStepResult(
                            dto.stepId(),
                            dto.stepName(),
                            dto.status(),
                            dto.output(),
                            dto.aiGenerated(),
                            dto.userModified(),
                            dto.startedAt(),
                            dto.completedAt(),
                            dto.errorMessage(),
                            dto.metadata()
                        );

                    return workflowService.advanceStep(id, tenantId, stepResult)
                        .map(workflow -> ResponseBuilder.ok().json(workflow).build())
                        .then(Promise::of, this::handleWorkflowException);

                } catch (Exception e) {
                    LOG.error("Failed to advance step", e);
                    return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "Invalid request: " + e.getMessage()))
                        .build());
                }
            });
    }

    /**
     * Goes to a specific step.
     * POST /api/v1/workflows/:id/steps/:stepId/goto
     */
    @NotNull
    public Promise<HttpResponse> goToStep(
        @NotNull HttpRequest request,
        @NotNull String id,
        @NotNull String stepId
    ) {
        String tenantId = extractTenantId(request);

        return workflowService.goToStep(id, tenantId, stepId)
            .map(workflow -> ResponseBuilder.ok().json(workflow).build())
            .then(Promise::of, this::handleWorkflowException);
    }

    // ==================== AI PLAN OPERATIONS ====================

    /**
     * Generates an AI plan for the workflow.
     * POST /api/v1/workflows/:id/plans/generate
     */
    @NotNull
    public Promise<HttpResponse> generatePlan(@NotNull HttpRequest request, @NotNull String id) {
        return request.loadBody()
            .then(body -> {
                try {
                    String tenantId = extractTenantId(request);
                    GeneratePlanDto dto = objectMapper.readValue(
                        body.getString(StandardCharsets.UTF_8),
                        GeneratePlanDto.class
                    );

                    return workflowService.generatePlan(id, tenantId, dto.objective())
                        .map(plan -> ResponseBuilder.ok().json(plan).build())
                        .then(Promise::of, this::handleWorkflowException);

                } catch (Exception e) {
                    LOG.error("Failed to generate plan", e);
                    return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "Invalid request: " + e.getMessage()))
                        .build());
                }
            });
    }

    /**
     * Approves an AI plan.
     * POST /api/v1/workflows/:workflowId/plans/:planId/approve
     */
    @NotNull
    public Promise<HttpResponse> approvePlan(
        @NotNull HttpRequest request,
        @NotNull String workflowId,
        @NotNull String planId
    ) {
        String tenantId = extractTenantId(request);

        return workflowService.approvePlan(planId, tenantId)
            .map(plan -> ResponseBuilder.ok().json(plan).build())
            .then(Promise::of, this::handleWorkflowException);
    }

    /**
     * Rejects an AI plan.
     * POST /api/v1/workflows/:workflowId/plans/:planId/reject
     */
    @NotNull
    public Promise<HttpResponse> rejectPlan(
        @NotNull HttpRequest request,
        @NotNull String workflowId,
        @NotNull String planId
    ) {
        return request.loadBody()
            .then(body -> {
                try {
                    String tenantId = extractTenantId(request);
                    RejectPlanDto dto = objectMapper.readValue(
                        body.getString(StandardCharsets.UTF_8),
                        RejectPlanDto.class
                    );

                    return workflowService.rejectPlan(planId, tenantId, dto.reason())
                        .map(plan -> ResponseBuilder.ok().json(plan).build())
                        .then(Promise::of, this::handleWorkflowException);

                } catch (Exception e) {
                    LOG.error("Failed to reject plan", e);
                    return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "Invalid request: " + e.getMessage()))
                        .build());
                }
            });
    }

    /**
     * Modifies plan steps.
     * PUT /api/v1/workflows/:workflowId/plans/:planId/steps
     */
    @NotNull
    public Promise<HttpResponse> modifyPlanSteps(
        @NotNull HttpRequest request,
        @NotNull String workflowId,
        @NotNull String planId
    ) {
        return request.loadBody()
            .then(body -> {
                try {
                    String tenantId = extractTenantId(request);
                    ModifyStepsDto dto = objectMapper.readValue(
                        body.getString(StandardCharsets.UTF_8),
                        ModifyStepsDto.class
                    );

                    return workflowService.modifyPlanSteps(planId, tenantId, dto.steps())
                        .map(plan -> ResponseBuilder.ok().json(plan).build())
                        .then(Promise::of, this::handleWorkflowException);

                } catch (Exception e) {
                    LOG.error("Failed to modify plan steps", e);
                    return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "Invalid request: " + e.getMessage()))
                        .build());
                }
            });
    }

    // ==================== ROUTING ====================

    /**
     * Gets routing decision for workflow.
     * POST /api/v1/workflows/:id/route
     */
    @NotNull
    public Promise<HttpResponse> routeWorkflow(@NotNull HttpRequest request, @NotNull String id) {
        return request.loadBody()
            .then(body -> {
                try {
                    String tenantId = extractTenantId(request);
                    RouteRequestDto dto = objectMapper.readValue(
                        body.getString(StandardCharsets.UTF_8),
                        RouteRequestDto.class
                    );

                    return workflowService.routeWorkflow(id, tenantId, dto.userInput())
                        .map(decision -> ResponseBuilder.ok().json(decision).build())
                        .then(Promise::of, this::handleWorkflowException);

                } catch (Exception e) {
                    LOG.error("Failed to route workflow", e);
                    return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "Invalid request: " + e.getMessage()))
                        .build());
                }
            });
    }

    // ==================== HELPER METHODS ====================

    private String extractTenantId(HttpRequest request) {
        String tenantId = request.getHeader(HttpHeaders.of("X-Tenant-ID"));
        return tenantId != null ? tenantId : "default";
    }

    private int getIntParam(HttpRequest request, String name, int defaultValue) {
        String value = request.getQueryParameter(name);
        if (value != null && !value.isEmpty()) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private Promise<HttpResponse> handleWorkflowException(Exception e) {
        if (e instanceof AiWorkflowService.WorkflowNotFoundException) {
            return Promise.of(ResponseBuilder.notFound()
                .json(Map.of("error", e.getMessage()))
                .build());
        } else if (e instanceof AiWorkflowService.InvalidWorkflowStateException) {
            return Promise.of(ResponseBuilder.conflict()
                .json(Map.of("error", e.getMessage()))
                .build());
        } else if (e instanceof AiWorkflowService.WorkflowExecutionException) {
            return Promise.of(ResponseBuilder.internalServerError()
                .json(Map.of("error", e.getMessage()))
                .build());
        } else {
            LOG.error("Unexpected workflow error", e);
            return Promise.of(ResponseBuilder.internalServerError()
                .json(Map.of("error", "Workflow operation failed: " + e.getMessage()))
                .build());
        }
    }

    // ==================== DTOs ====================

    /**
     * DTO for creating a workflow
     */
    public record CreateWorkflowDto(
        @NotNull String name,
        @NotNull String description,
        @NotNull AiWorkflowInstance.WorkflowType type,
        @Nullable String createdBy
    ) {}

    /**
     * DTO for step result
     */
    public record StepResultDto(
        @NotNull String stepId,
        @NotNull String stepName,
        @NotNull AiWorkflowInstance.AiWorkflowStepResult.StepStatus status,
        @Nullable Object output,
        @Nullable String aiGenerated,
        @Nullable String userModified,
        @NotNull java.time.Instant startedAt,
        @Nullable java.time.Instant completedAt,
        @Nullable String errorMessage,
        @Nullable Map<String, Object> metadata
    ) {}

    /**
     * DTO for plan generation
     */
    public record GeneratePlanDto(
        @NotNull String objective
    ) {}

    /**
     * DTO for plan rejection
     */
    public record RejectPlanDto(
        @Nullable String reason
    ) {}

    /**
     * DTO for modifying steps
     */
    public record ModifyStepsDto(
        @NotNull List<AiPlan.PlanStep> steps
    ) {}

    /**
     * DTO for routing request
     */
    public record RouteRequestDto(
        @Nullable String userInput
    ) {}
}
