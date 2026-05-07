package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.funnel.TrialOnboardingService;
import com.ghatana.digitalmarketing.application.funnel.TrialOnboardingService.CreateTrialOnboardingCommand;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.funnel.TrialOnboarding;
import com.ghatana.digitalmarketing.domain.funnel.TrialOnboardingStatus;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.json.Json;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * API servlet for trial onboarding workflow management in self-marketing acquisition funnel.
 *
 * @doc.type class
 * @doc.purpose API endpoints for trial onboarding CRUD (P3-001)
 * @doc.layer product
 * @doc.pattern Servlet
 */
public final class DmosTrialOnboardingServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosTrialOnboardingServlet.class);

    private final Eventloop eventloop;
    private final TrialOnboardingService trialOnboardingService;
    private final DmosHttpContextFactory httpContextFactory;
    private final ObjectMapper objectMapper;
    private final DmosMetricsCollector metrics;

    public DmosTrialOnboardingServlet(
            Eventloop eventloop,
            TrialOnboardingService trialOnboardingService,
            DmosHttpContextFactory httpContextFactory,
            ObjectMapper objectMapper,
            DmosMetricsCollector metrics) {
        this.eventloop = eventloop;
        this.trialOnboardingService = trialOnboardingService;
        this.httpContextFactory = httpContextFactory;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
            io.activej.http.RoutingServlet.builder(eventloop)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/trial-onboardings",
                    this::handleCreate)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/trial-onboardings/:onboardingId/start",
                    this::handleStart)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/trial-onboardings/:onboardingId/advance",
                    this::handleAdvanceStep)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/trial-onboardings/:onboardingId/complete",
                    this::handleComplete)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/trial-onboardings/:onboardingId/cancel",
                    this::handleCancel)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/trial-onboardings/:onboardingId",
                    this::handleGetById)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/trial-onboardings",
                    this::handleList)
                .build(),
            metrics,
            "trial-onboarding"
        );
    }

    private Promise<HttpResponse> handleCreate(HttpRequest request) {
        String workspaceId = request.pathParameter("workspaceId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, isWriteOperation);

            CreateRequest createRequest = objectMapper.readValue(request.getBody(), CreateRequest.class);

            CreateTrialOnboardingCommand command = new CreateTrialOnboardingCommand(
                createRequest.leadId(),
                createRequest.demoWorkspaceId(),
                createRequest.totalSteps()
            );

            return trialOnboardingService.create(ctx, command)
                .then(onboarding -> {
                    metrics.incrementCounter("trial_onboarding.create.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(onboarding)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("trial_onboarding.create.error");
                    LOG.error("Failed to create trial onboarding", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("trial_onboarding.create.error");
            LOG.error("Failed to parse create request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleStart(HttpRequest request) {
        String workspaceId = request.pathParameter("workspaceId");
        String onboardingId = request.pathParameter("onboardingId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, isWriteOperation);

            return trialOnboardingService.start(ctx, onboardingId)
                .then(onboarding -> {
                    metrics.incrementCounter("trial_onboarding.start.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(onboarding)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("trial_onboarding.start.error");
                    LOG.error("Failed to start trial onboarding", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("trial_onboarding.start.error");
            LOG.error("Failed to process start request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleAdvanceStep(HttpRequest request) {
        String workspaceId = request.pathParameter("workspaceId");
        String onboardingId = request.pathParameter("onboardingId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, isWriteOperation);

            AdvanceStepRequest advanceRequest = objectMapper.readValue(request.getBody(), AdvanceStepRequest.class);

            return trialOnboardingService.advanceStep(ctx, onboardingId, advanceRequest.stepNumber(), advanceRequest.progress())
                .then(onboarding -> {
                    metrics.incrementCounter("trial_onboarding.advance.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(onboarding)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("trial_onboarding.advance.error");
                    LOG.error("Failed to advance trial onboarding step", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("trial_onboarding.advance.error");
            LOG.error("Failed to parse advance step request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleComplete(HttpRequest request) {
        String workspaceId = request.pathParameter("workspaceId");
        String onboardingId = request.pathParameter("onboardingId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, isWriteOperation);

            return trialOnboardingService.complete(ctx, onboardingId)
                .then(onboarding -> {
                    metrics.incrementCounter("trial_onboarding.complete.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(onboarding)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("trial_onboarding.complete.error");
                    LOG.error("Failed to complete trial onboarding", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("trial_onboarding.complete.error");
            LOG.error("Failed to process complete request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleCancel(HttpRequest request) {
        String workspaceId = request.pathParameter("workspaceId");
        String onboardingId = request.pathParameter("onboardingId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, isWriteOperation);

            CancelRequest cancelRequest = objectMapper.readValue(request.getBody(), CancelRequest.class);

            return trialOnboardingService.cancel(ctx, onboardingId, cancelRequest.reason())
                .then(onboarding -> {
                    metrics.incrementCounter("trial_onboarding.cancel.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(onboarding)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("trial_onboarding.cancel.error");
                    LOG.error("Failed to cancel trial onboarding", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("trial_onboarding.cancel.error");
            LOG.error("Failed to parse cancel request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleGetById(HttpRequest request) {
        String workspaceId = request.pathParameter("workspaceId");
        String onboardingId = request.pathParameter("onboardingId");
        boolean isWriteOperation = false;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, isWriteOperation);

            return trialOnboardingService.findById(ctx, onboardingId)
                .then(onboardingOpt -> {
                    if (onboardingOpt.isEmpty()) {
                        return Promise.of(HttpResponse.ofCode(404).withJson(Json.of("{\"error\":\"Trial onboarding not found\"}")));
                    }
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(onboardingOpt.get())))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("trial_onboarding.get.error");
                    LOG.error("Failed to get trial onboarding", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("trial_onboarding.get.error");
            LOG.error("Failed to process get request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleList(HttpRequest request) {
        String workspaceId = request.pathParameter("workspaceId");
        boolean isWriteOperation = false;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, isWriteOperation);

            return trialOnboardingService.list(ctx)
                .then(onboardings -> {
                    Object[] dtos = onboardings.stream().map(this::toDto).toArray();
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(dtos))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("trial_onboarding.list.error");
                    LOG.error("Failed to list trial onboardings", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("trial_onboarding.list.error");
            LOG.error("Failed to process list request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private HttpResponse mapServiceError(Exception e) {
        if (e instanceof IllegalArgumentException) {
            return HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"" + e.getMessage() + "\"}"));
        }
        return HttpResponse.ofCode(500).withJson(Json.of("{\"error\":\"Internal server error\"}"));
    }

    private TrialOnboardingDto toDto(TrialOnboarding onboarding) {
        return new TrialOnboardingDto(
            onboarding.getId(),
            onboarding.getTenantId(),
            onboarding.getWorkspaceId(),
            onboarding.getLeadId(),
            onboarding.getDemoWorkspaceId(),
            onboarding.getStatus().name(),
            onboarding.getCurrentStep(),
            onboarding.getTotalSteps(),
            onboarding.getProgressPercentage(),
            onboarding.getStepProgress(),
            onboarding.getCreatedAt().toString(),
            onboarding.getStartedAt() != null ? onboarding.getStartedAt().toString() : null,
            onboarding.getCompletedAt() != null ? onboarding.getCompletedAt().toString() : null,
            onboarding.getCancellationReason()
        );
    }

    private record CreateRequest(
        String leadId,
        String demoWorkspaceId,
        int totalSteps
    ) {}

    private record AdvanceStepRequest(
        int stepNumber,
        Map<String, Object> progress
    ) {}

    private record CancelRequest(
        String reason
    ) {}

    private record TrialOnboardingDto(
        String id,
        String tenantId,
        String workspaceId,
        String leadId,
        String demoWorkspaceId,
        String status,
        int currentStep,
        int totalSteps,
        double progressPercentage,
        Map<String, Object> stepProgress,
        String createdAt,
        String startedAt,
        String completedAt,
        String cancellationReason
    ) {}
}
