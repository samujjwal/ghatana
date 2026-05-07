package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.funnel.DemoWorkspaceService;
import com.ghatana.digitalmarketing.application.funnel.DemoWorkspaceService.ProvisionDemoWorkspaceCommand;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.funnel.DemoWorkspace;
import com.ghatana.digitalmarketing.domain.funnel.DemoWorkspaceStatus;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.json.Json;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * API servlet for demo workspace management in self-marketing acquisition funnel.
 *
 * @doc.type class
 * @doc.purpose API endpoints for demo workspace CRUD (P3-001)
 * @doc.layer product
 * @doc.pattern Servlet
 */
public final class DmosDemoWorkspaceServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosDemoWorkspaceServlet.class);

    private final Eventloop eventloop;
    private final DemoWorkspaceService demoWorkspaceService;
    private final DmosHttpContextFactory httpContextFactory;
    private final ObjectMapper objectMapper;
    private final DmosMetricsCollector metrics;

    public DmosDemoWorkspaceServlet(
            Eventloop eventloop,
            DemoWorkspaceService demoWorkspaceService,
            DmosHttpContextFactory httpContextFactory,
            ObjectMapper objectMapper,
            DmosMetricsCollector metrics) {
        this.eventloop = eventloop;
        this.demoWorkspaceService = demoWorkspaceService;
        this.httpContextFactory = httpContextFactory;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
            io.activej.http.RoutingServlet.builder(eventloop)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/demo-workspaces",
                    this::handleProvision)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/demo-workspaces/:workspaceId/activate",
                    this::handleActivate)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/demo-workspaces/:workspaceId/deactivate",
                    this::handleDeactivate)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/demo-workspaces/:workspaceId/expire",
                    this::handleExpire)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/demo-workspaces/:workspaceId",
                    this::handleGetById)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/demo-workspaces",
                    this::handleList)
                .build(),
            metrics,
            "demo-workspace"
        );
    }

    private Promise<HttpResponse> handleProvision(HttpRequest request) {
        String workspaceId = request.pathParameter("workspaceId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, isWriteOperation);

            ProvisionRequest provisionRequest = objectMapper.readValue(request.getBody(), ProvisionRequest.class);

            ProvisionDemoWorkspaceCommand command = new ProvisionDemoWorkspaceCommand(
                provisionRequest.leadId(),
                provisionRequest.templateId(),
                provisionRequest.templateConfig(),
                Duration.parse(provisionRequest.trialDuration())
            );

            return demoWorkspaceService.provision(ctx, command)
                .then(workspace -> {
                    metrics.incrementCounter("demo_workspace.provision.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(workspace)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("demo_workspace.provision.error");
                    LOG.error("Failed to provision demo workspace", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("demo_workspace.provision.error");
            LOG.error("Failed to parse provision request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleActivate(HttpRequest request) {
        String workspaceId = request.pathParameter("workspaceId");
        String demoWorkspaceId = request.pathParameter("workspaceId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, isWriteOperation);

            return demoWorkspaceService.activate(ctx, demoWorkspaceId)
                .then(workspace -> {
                    metrics.incrementCounter("demo_workspace.activate.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(workspace)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("demo_workspace.activate.error");
                    LOG.error("Failed to activate demo workspace", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("demo_workspace.activate.error");
            LOG.error("Failed to process activate request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleDeactivate(HttpRequest request) {
        String workspaceId = request.pathParameter("workspaceId");
        String demoWorkspaceId = request.pathParameter("workspaceId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, isWriteOperation);

            DeactivateRequest deactivateRequest = objectMapper.readValue(request.getBody(), DeactivateRequest.class);

            return demoWorkspaceService.deactivate(ctx, demoWorkspaceId, deactivateRequest.reason())
                .then(workspace -> {
                    metrics.incrementCounter("demo_workspace.deactivate.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(workspace)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("demo_workspace.deactivate.error");
                    LOG.error("Failed to deactivate demo workspace", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("demo_workspace.deactivate.error");
            LOG.error("Failed to parse deactivate request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleExpire(HttpRequest request) {
        String workspaceId = request.pathParameter("workspaceId");
        String demoWorkspaceId = request.pathParameter("workspaceId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, isWriteOperation);

            return demoWorkspaceService.expire(ctx, demoWorkspaceId)
                .then(workspace -> {
                    metrics.incrementCounter("demo_workspace.expire.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(workspace)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("demo_workspace.expire.error");
                    LOG.error("Failed to expire demo workspace", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("demo_workspace.expire.error");
            LOG.error("Failed to process expire request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleGetById(HttpRequest request) {
        String workspaceId = request.pathParameter("workspaceId");
        String demoWorkspaceId = request.pathParameter("workspaceId");
        boolean isWriteOperation = false;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, isWriteOperation);

            return demoWorkspaceService.findById(ctx, demoWorkspaceId)
                .then(workspaceOpt -> {
                    if (workspaceOpt.isEmpty()) {
                        return Promise.of(HttpResponse.ofCode(404).withJson(Json.of("{\"error\":\"Demo workspace not found\"}")));
                    }
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(workspaceOpt.get())))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("demo_workspace.get.error");
                    LOG.error("Failed to get demo workspace", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("demo_workspace.get.error");
            LOG.error("Failed to process get request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleList(HttpRequest request) {
        String workspaceId = request.pathParameter("workspaceId");
        boolean isWriteOperation = false;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, isWriteOperation);

            return demoWorkspaceService.list(ctx)
                .then(workspaces -> {
                    Object[] dtos = workspaces.stream().map(this::toDto).toArray();
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(dtos))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("demo_workspace.list.error");
                    LOG.error("Failed to list demo workspaces", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("demo_workspace.list.error");
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

    private DemoWorkspaceDto toDto(DemoWorkspace workspace) {
        return new DemoWorkspaceDto(
            workspace.getId(),
            workspace.getTenantId(),
            workspace.getWorkspaceId(),
            workspace.getLeadId(),
            workspace.getTemplateId(),
            workspace.getStatus().name(),
            workspace.getTemplateConfig(),
            workspace.getCreatedAt().toString(),
            workspace.getActivatedAt() != null ? workspace.getActivatedAt().toString() : null,
            workspace.getExpiresAt() != null ? workspace.getExpiresAt().toString() : null,
            workspace.getDeactivationReason()
        );
    }

    private record ProvisionRequest(
        String leadId,
        String templateId,
        Map<String, Object> templateConfig,
        String trialDuration
    ) {}

    private record DeactivateRequest(
        String reason
    ) {}

    private record DemoWorkspaceDto(
        String id,
        String tenantId,
        String workspaceId,
        String leadId,
        String templateId,
        String status,
        Map<String, Object> templateConfig,
        String createdAt,
        String activatedAt,
        String expiresAt,
        String deactivationReason
    ) {}
}
