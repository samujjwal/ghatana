package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.agency.AgencyRetainerService;
import com.ghatana.digitalmarketing.application.agency.AgencyRetainerService.CreateRetainerCommand;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.agency.AgencyRetainer;
import com.ghatana.digitalmarketing.domain.agency.AgencyRetainerStatus;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.json.Json;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * API servlet for agency retainer management in agency operations.
 *
 * @doc.type class
 * @doc.purpose API endpoints for agency retainer CRUD (P3-002)
 * @doc.layer product
 * @doc.pattern Servlet
 */
public final class DmosAgencyRetainerServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosAgencyRetainerServlet.class);

    private final Eventloop eventloop;
    private final AgencyRetainerService agencyRetainerService;
    private final DmosHttpContextFactory httpContextFactory;
    private final ObjectMapper objectMapper;
    private final DmosMetricsCollector metrics;

    public DmosAgencyRetainerServlet(
            Eventloop eventloop,
            AgencyRetainerService agencyRetainerService,
            DmosHttpContextFactory httpContextFactory,
            ObjectMapper objectMapper,
            DmosMetricsCollector metrics) {
        this.eventloop = eventloop;
        this.agencyRetainerService = agencyRetainerService;
        this.httpContextFactory = httpContextFactory;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
            io.activej.http.RoutingServlet.builder(eventloop)
                .with(HttpMethod.POST, "/v1/agency/retainers",
                    this::handleCreate)
                .with(HttpMethod.POST, "/v1/agency/retainers/:retainerId/activate",
                    this::handleActivate)
                .with(HttpMethod.POST, "/v1/agency/retainers/:retainerId/suspend",
                    this::handleSuspend)
                .with(HttpMethod.POST, "/v1/agency/retainers/:retainerId/cancel",
                    this::handleCancel)
                .with(HttpMethod.GET, "/v1/agency/retainers/:retainerId",
                    this::handleGetById)
                .with(HttpMethod.GET, "/v1/agency/retainers",
                    this::handleList)
                .build(),
            metrics,
            "agency-retainer"
        );
    }

    private Promise<HttpResponse> handleCreate(HttpRequest request) {
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            CreateRequest createRequest = objectMapper.readValue(request.getBody(), CreateRequest.class);

            CreateRetainerCommand command = new CreateRetainerCommand(
                createRequest.contractId(),
                new BigDecimal(createRequest.monthlyAmount()),
                createRequest.currency(),
                LocalDate.parse(createRequest.billingCycleStart()),
                createRequest.billingDayOfMonth(),
                createRequest.serviceAllowances(),
                createRequest.overageRate() != null ? new BigDecimal(createRequest.overageRate()) : null
            );

            return agencyRetainerService.create(ctx, command)
                .then(retainer -> {
                    metrics.incrementCounter("agency_retainer.create.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(retainer)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_retainer.create.error");
                    LOG.error("Failed to create agency retainer", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_retainer.create.error");
            LOG.error("Failed to parse create request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleActivate(HttpRequest request) {
        String retainerId = request.pathParameter("retainerId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            return agencyRetainerService.activate(ctx, retainerId)
                .then(retainer -> {
                    metrics.incrementCounter("agency_retainer.activate.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(retainer)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_retainer.activate.error");
                    LOG.error("Failed to activate agency retainer", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_retainer.activate.error");
            LOG.error("Failed to process activate request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleSuspend(HttpRequest request) {
        String retainerId = request.pathParameter("retainerId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            SuspendRequest suspendRequest = objectMapper.readValue(request.getBody(), SuspendRequest.class);

            return agencyRetainerService.suspend(ctx, retainerId, suspendRequest.reason())
                .then(retainer -> {
                    metrics.incrementCounter("agency_retainer.suspend.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(retainer)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_retainer.suspend.error");
                    LOG.error("Failed to suspend agency retainer", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_retainer.suspend.error");
            LOG.error("Failed to parse suspend request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleCancel(HttpRequest request) {
        String retainerId = request.pathParameter("retainerId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            CancelRequest cancelRequest = objectMapper.readValue(request.getBody(), CancelRequest.class);

            return agencyRetainerService.cancel(ctx, retainerId, cancelRequest.reason())
                .then(retainer -> {
                    metrics.incrementCounter("agency_retainer.cancel.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(retainer)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_retainer.cancel.error");
                    LOG.error("Failed to cancel agency retainer", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_retainer.cancel.error");
            LOG.error("Failed to parse cancel request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleGetById(HttpRequest request) {
        String retainerId = request.pathParameter("retainerId");
        boolean isWriteOperation = false;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            return agencyRetainerService.findById(ctx, retainerId)
                .then(retainerOpt -> {
                    if (retainerOpt.isEmpty()) {
                        return Promise.of(HttpResponse.ofCode(404).withJson(Json.of("{\"error\":\"Retainer not found\"}")));
                    }
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(retainerOpt.get())))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_retainer.get.error");
                    LOG.error("Failed to get agency retainer", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_retainer.get.error");
            LOG.error("Failed to process get request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleList(HttpRequest request) {
        boolean isWriteOperation = false;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            return agencyRetainerService.list(ctx)
                .then(retainers -> {
                    Object[] dtos = retainers.stream().map(this::toDto).toArray();
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(dtos))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_retainer.list.error");
                    LOG.error("Failed to list agency retainers", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_retainer.list.error");
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

    private RetainerDto toDto(AgencyRetainer retainer) {
        return new RetainerDto(
            retainer.getId(),
            retainer.getContractId(),
            retainer.getAgencyTenantId(),
            retainer.getClientId(),
            retainer.getMonthlyAmount().toString(),
            retainer.getCurrency(),
            retainer.getBillingCycleStart() != null ? retainer.getBillingCycleStart().toString() : null,
            retainer.getBillingDayOfMonth(),
            retainer.getServiceAllowances(),
            retainer.getOverageRate() != null ? retainer.getOverageRate().toString() : null,
            retainer.getStatus().name(),
            retainer.getCreatedAt().toString(),
            retainer.getUpdatedAt() != null ? retainer.getUpdatedAt().toString() : null
        );
    }

    private record CreateRequest(
        String contractId,
        String monthlyAmount,
        String currency,
        String billingCycleStart,
        int billingDayOfMonth,
        Map<String, Integer> serviceAllowances,
        String overageRate
    ) {}

    private record SuspendRequest(
        String reason
    ) {}

    private record CancelRequest(
        String reason
    ) {}

    private record RetainerDto(
        String id,
        String contractId,
        String agencyTenantId,
        String clientId,
        String monthlyAmount,
        String currency,
        String billingCycleStart,
        int billingDayOfMonth,
        Map<String, Integer> serviceAllowances,
        String overageRate,
        String status,
        String createdAt,
        String updatedAt
    ) {}
}
