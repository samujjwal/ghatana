package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.agency.AgencyApprovalSLAService;
import com.ghatana.digitalmarketing.application.agency.AgencyApprovalSLAService.CreateSLACommand;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.agency.AgencyApprovalSLA;
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
import java.util.Optional;
import java.util.UUID;

/**
 * API servlet for agency approval SLA management in agency operations.
 *
 * @doc.type class
 * @doc.purpose API endpoints for agency approval SLA CRUD (P3-002)
 * @doc.layer product
 * @doc.pattern Servlet
 */
public final class DmosAgencyApprovalSLAServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosAgencyApprovalSLAServlet.class);

    private final Eventloop eventloop;
    private final AgencyApprovalSLAService agencyApprovalSLAService;
    private final DmosHttpContextFactory httpContextFactory;
    private final ObjectMapper objectMapper;
    private final DmosMetricsCollector metrics;

    public DmosAgencyApprovalSLAServlet(
            Eventloop eventloop,
            AgencyApprovalSLAService agencyApprovalSLAService,
            DmosHttpContextFactory httpContextFactory,
            ObjectMapper objectMapper,
            DmosMetricsCollector metrics) {
        this.eventloop = eventloop;
        this.agencyApprovalSLAService = agencyApprovalSLAService;
        this.httpContextFactory = httpContextFactory;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
            io.activej.http.RoutingServlet.builder(eventloop)
                .with(HttpMethod.POST, "/v1/agency/approval-slas",
                    this::handleCreate)
                .with(HttpMethod.POST, "/v1/agency/approval-slas/:slaId/activate",
                    this::handleActivate)
                .with(HttpMethod.POST, "/v1/agency/approval-slas/:slaId/deactivate",
                    this::handleDeactivate)
                .with(HttpMethod.POST, "/v1/agency/approval-slas/:slaId/escalation",
                    this::handleUpdateEscalation)
                .with(HttpMethod.GET, "/v1/agency/approval-slas/:slaId",
                    this::handleGetById)
                .with(HttpMethod.GET, "/v1/agency/approval-slas",
                    this::handleList)
                .build(),
            metrics,
            "agency-approval-sla"
        );
    }

    private Promise<HttpResponse> handleCreate(HttpRequest request) {
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            CreateRequest createRequest = objectMapper.readValue(request.getBody(), CreateRequest.class);

            CreateSLACommand command = new CreateSLACommand(
                createRequest.contractId(),
                createRequest.approvalType(),
                Duration.parse(createRequest.maxApprovalTime()),
                createRequest.escalationTimeouts(),
                createRequest.escalationProcedure()
            );

            return agencyApprovalSLAService.create(ctx, command)
                .then(sla -> {
                    metrics.incrementCounter("agency_approval_sla.create.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(sla)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_approval_sla.create.error");
                    LOG.error("Failed to create approval SLA", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_approval_sla.create.error");
            LOG.error("Failed to parse create request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleActivate(HttpRequest request) {
        String slaId = request.pathParameter("slaId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            return agencyApprovalSLAService.activate(ctx, slaId)
                .then(sla -> {
                    metrics.incrementCounter("agency_approval_sla.activate.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(sla)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_approval_sla.activate.error");
                    LOG.error("Failed to activate approval SLA", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_approval_sla.activate.error");
            LOG.error("Failed to process activate request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleDeactivate(HttpRequest request) {
        String slaId = request.pathParameter("slaId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            DeactivateRequest deactivateRequest = objectMapper.readValue(request.getBody(), DeactivateRequest.class);

            return agencyApprovalSLAService.deactivate(ctx, slaId, deactivateRequest.reason())
                .then(sla -> {
                    metrics.incrementCounter("agency_approval_sla.deactivate.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(sla)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_approval_sla.deactivate.error");
                    LOG.error("Failed to deactivate approval SLA", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_approval_sla.deactivate.error");
            LOG.error("Failed to parse deactivate request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleUpdateEscalation(HttpRequest request) {
        String slaId = request.pathParameter("slaId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            EscalationRequest escalationRequest = objectMapper.readValue(request.getBody(), EscalationRequest.class);

            return agencyApprovalSLAService.updateEscalationLevel(ctx, slaId, escalationRequest.newLevel())
                .then(sla -> {
                    metrics.incrementCounter("agency_approval_sla.escalation.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(sla)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_approval_sla.escalation.error");
                    LOG.error("Failed to update escalation level", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_approval_sla.escalation.error");
            LOG.error("Failed to parse escalation request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleGetById(HttpRequest request) {
        String slaId = request.pathParameter("slaId");
        boolean isWriteOperation = false;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            return agencyApprovalSLAService.findById(ctx, slaId)
                .then(slaOpt -> {
                    if (slaOpt.isEmpty()) {
                        return Promise.of(HttpResponse.ofCode(404).withJson(Json.of("{\"error\":\"Approval SLA not found\"}")));
                    }
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(slaOpt.get())))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_approval_sla.get.error");
                    LOG.error("Failed to get approval SLA", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_approval_sla.get.error");
            LOG.error("Failed to process get request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleList(HttpRequest request) {
        boolean isWriteOperation = false;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            return agencyApprovalSLAService.list(ctx)
                .then(slas -> {
                    Object[] dtos = slas.stream().map(this::toDto).toArray();
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(dtos))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_approval_sla.list.error");
                    LOG.error("Failed to list approval SLAs", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_approval_sla.list.error");
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

    private SLADto toDto(AgencyApprovalSLA sla) {
        return new SLADto(
            sla.getId(),
            sla.getContractId(),
            sla.getAgencyTenantId(),
            sla.getClientId(),
            sla.getApprovalType(),
            sla.getMaxApprovalTime() != null ? sla.getMaxApprovalTime().toString() : null,
            sla.getEscalationLevel(),
            sla.getEscalationTimeouts(),
            sla.getEscalationProcedure(),
            sla.isActive(),
            sla.getCreatedAt().toString(),
            sla.getUpdatedAt() != null ? sla.getUpdatedAt().toString() : null
        );
    }

    private record CreateRequest(
        String contractId,
        String approvalType,
        String maxApprovalTime,
        Map<String, String> escalationTimeouts,
        String escalationProcedure
    ) {}

    private record DeactivateRequest(
        String reason
    ) {}

    private record EscalationRequest(
        int newLevel
    ) {}

    private record SLADto(
        String id,
        String contractId,
        String agencyTenantId,
        String clientId,
        String approvalType,
        String maxApprovalTime,
        int escalationLevel,
        Map<String, Duration> escalationTimeouts,
        String escalationProcedure,
        boolean active,
        String createdAt,
        String updatedAt
    ) {}
}
