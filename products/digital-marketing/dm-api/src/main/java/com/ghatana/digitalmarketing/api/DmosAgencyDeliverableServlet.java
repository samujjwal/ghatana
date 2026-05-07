package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.agency.AgencyDeliverableService;
import com.ghatana.digitalmarketing.application.agency.AgencyDeliverableService.CreateDeliverableCommand;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.agency.AgencyDeliverable;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.json.Json;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * API servlet for agency deliverable management in agency operations.
 *
 * @doc.type class
 * @doc.purpose API endpoints for agency deliverable CRUD (P3-002)
 * @doc.layer product
 * @doc.pattern Servlet
 */
public final class DmosAgencyDeliverableServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosAgencyDeliverableServlet.class);

    private final Eventloop eventloop;
    private final AgencyDeliverableService agencyDeliverableService;
    private final DmosHttpContextFactory httpContextFactory;
    private final ObjectMapper objectMapper;
    private final DmosMetricsCollector metrics;

    public DmosAgencyDeliverableServlet(
            Eventloop eventloop,
            AgencyDeliverableService agencyDeliverableService,
            DmosHttpContextFactory httpContextFactory,
            ObjectMapper objectMapper,
            DmosMetricsCollector metrics) {
        this.eventloop = eventloop;
        this.agencyDeliverableService = agencyDeliverableService;
        this.httpContextFactory = httpContextFactory;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
            io.activej.http.RoutingServlet.builder(eventloop)
                .with(HttpMethod.POST, "/v1/agency/deliverables",
                    this::handleCreate)
                .with(HttpMethod.POST, "/v1/agency/deliverables/:deliverableId/start",
                    this::handleStart)
                .with(HttpMethod.POST, "/v1/agency/deliverables/:deliverableId/submit",
                    this::handleSubmit)
                .with(HttpMethod.POST, "/v1/agency/deliverables/:deliverableId/complete",
                    this::handleComplete)
                .with(HttpMethod.POST, "/v1/agency/deliverables/:deliverableId/reject",
                    this::handleReject)
                .with(HttpMethod.GET, "/v1/agency/deliverables/:deliverableId",
                    this::handleGetById)
                .with(HttpMethod.GET, "/v1/agency/deliverables",
                    this::handleList)
                .build(),
            metrics,
            "agency-deliverable"
        );
    }

    private Promise<HttpResponse> handleCreate(HttpRequest request) {
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            CreateRequest createRequest = objectMapper.readValue(request.getBody(), CreateRequest.class);

            CreateDeliverableCommand command = new CreateDeliverableCommand(
                createRequest.contractId(),
                createRequest.deliverableType(),
                createRequest.title(),
                createRequest.description(),
                LocalDate.parse(createRequest.dueDate()),
                createRequest.assignedTo(),
                createRequest.metadata()
            );

            return agencyDeliverableService.create(ctx, command)
                .then(deliverable -> {
                    metrics.incrementCounter("agency_deliverable.create.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(deliverable)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_deliverable.create.error");
                    LOG.error("Failed to create agency deliverable", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_deliverable.create.error");
            LOG.error("Failed to parse create request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleStart(HttpRequest request) {
        String deliverableId = request.pathParameter("deliverableId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            return agencyDeliverableService.start(ctx, deliverableId)
                .then(deliverable -> {
                    metrics.incrementCounter("agency_deliverable.start.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(deliverable)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_deliverable.start.error");
                    LOG.error("Failed to start agency deliverable", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_deliverable.start.error");
            LOG.error("Failed to process start request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleSubmit(HttpRequest request) {
        String deliverableId = request.pathParameter("deliverableId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            return agencyDeliverableService.submitForReview(ctx, deliverableId)
                .then(deliverable -> {
                    metrics.incrementCounter("agency_deliverable.submit.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(deliverable)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_deliverable.submit.error");
                    LOG.error("Failed to submit agency deliverable", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_deliverable.submit.error");
            LOG.error("Failed to process submit request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleComplete(HttpRequest request) {
        String deliverableId = request.pathParameter("deliverableId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            return agencyDeliverableService.complete(ctx, deliverableId)
                .then(deliverable -> {
                    metrics.incrementCounter("agency_deliverable.complete.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(deliverable)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_deliverable.complete.error");
                    LOG.error("Failed to complete agency deliverable", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_deliverable.complete.error");
            LOG.error("Failed to process complete request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleReject(HttpRequest request) {
        String deliverableId = request.pathParameter("deliverableId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            RejectRequest rejectRequest = objectMapper.readValue(request.getBody(), RejectRequest.class);

            return agencyDeliverableService.reject(ctx, deliverableId, rejectRequest.reason())
                .then(deliverable -> {
                    metrics.incrementCounter("agency_deliverable.reject.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(deliverable)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_deliverable.reject.error");
                    LOG.error("Failed to reject agency deliverable", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_deliverable.reject.error");
            LOG.error("Failed to parse reject request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleGetById(HttpRequest request) {
        String deliverableId = request.pathParameter("deliverableId");
        boolean isWriteOperation = false;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            return agencyDeliverableService.findById(ctx, deliverableId)
                .then(deliverableOpt -> {
                    if (deliverableOpt.isEmpty()) {
                        return Promise.of(HttpResponse.ofCode(404).withJson(Json.of("{\"error\":\"Deliverable not found\"}")));
                    }
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(deliverableOpt.get())))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_deliverable.get.error");
                    LOG.error("Failed to get agency deliverable", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_deliverable.get.error");
            LOG.error("Failed to process get request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleList(HttpRequest request) {
        boolean isWriteOperation = false;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            return agencyDeliverableService.list(ctx)
                .then(deliverables -> {
                    Object[] dtos = deliverables.stream().map(this::toDto).toArray();
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(dtos))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_deliverable.list.error");
                    LOG.error("Failed to list agency deliverables", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_deliverable.list.error");
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

    private DeliverableDto toDto(AgencyDeliverable deliverable) {
        return new DeliverableDto(
            deliverable.getId(),
            deliverable.getContractId(),
            deliverable.getAgencyTenantId(),
            deliverable.getClientId(),
            deliverable.getDeliverableType(),
            deliverable.getTitle(),
            deliverable.getDescription(),
            deliverable.getDueDate() != null ? deliverable.getDueDate().toString() : null,
            deliverable.getCompletedDate() != null ? deliverable.getCompletedDate().toString() : null,
            deliverable.getAssignedTo(),
            deliverable.getMetadata(),
            deliverable.getStatus().name(),
            deliverable.getCreatedAt().toString(),
            deliverable.getUpdatedAt() != null ? deliverable.getUpdatedAt().toString() : null
        );
    }

    private record CreateRequest(
        String contractId,
        String deliverableType,
        String title,
        String description,
        String dueDate,
        String assignedTo,
        Map<String, Object> metadata
    ) {}

    private record RejectRequest(
        String reason
    ) {}

    private record DeliverableDto(
        String id,
        String contractId,
        String agencyTenantId,
        String clientId,
        String deliverableType,
        String title,
        String description,
        String dueDate,
        String completedDate,
        String assignedTo,
        Map<String, Object> metadata,
        String status,
        String createdAt,
        String updatedAt
    ) {}
}
