package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.agency.AgencyContractService;
import com.ghatana.digitalmarketing.application.agency.AgencyContractService.CreateContractCommand;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.agency.AgencyContract;
import com.ghatana.digitalmarketing.domain.agency.AgencyContractStatus;
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
import java.util.UUID;

/**
 * API servlet for agency contract management in agency operations.
 *
 * @doc.type class
 * @doc.purpose API endpoints for agency contract CRUD (P3-002)
 * @doc.layer product
 * @doc.pattern Servlet
 */
public final class DmosAgencyContractServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosAgencyContractServlet.class);

    private final Eventloop eventloop;
    private final AgencyContractService agencyContractService;
    private final DmosHttpContextFactory httpContextFactory;
    private final ObjectMapper objectMapper;
    private final DmosMetricsCollector metrics;

    public DmosAgencyContractServlet(
            Eventloop eventloop,
            AgencyContractService agencyContractService,
            DmosHttpContextFactory httpContextFactory,
            ObjectMapper objectMapper,
            DmosMetricsCollector metrics) {
        this.eventloop = eventloop;
        this.agencyContractService = agencyContractService;
        this.httpContextFactory = httpContextFactory;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
            io.activej.http.RoutingServlet.builder(eventloop)
                .with(HttpMethod.POST, "/v1/agency/contracts",
                    this::handleCreate)
                .with(HttpMethod.POST, "/v1/agency/contracts/:contractId/activate",
                    this::handleActivate)
                .with(HttpMethod.POST, "/v1/agency/contracts/:contractId/terminate",
                    this::handleTerminate)
                .with(HttpMethod.POST, "/v1/agency/contracts/:contractId/renew",
                    this::handleRenew)
                .with(HttpMethod.GET, "/v1/agency/contracts/:contractId",
                    this::handleGetById)
                .with(HttpMethod.GET, "/v1/agency/contracts",
                    this::handleList)
                .build(),
            metrics,
            "agency-contract"
        );
    }

    private Promise<HttpResponse> handleCreate(HttpRequest request) {
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            CreateRequest createRequest = objectMapper.readValue(request.getBody(), CreateRequest.class);

            CreateContractCommand command = new CreateContractCommand(
                createRequest.clientId(),
                createRequest.contractNumber(),
                createRequest.contractType(),
                LocalDate.parse(createRequest.startDate()),
                LocalDate.parse(createRequest.endDate()),
                new BigDecimal(createRequest.monthlyRetainer()),
                createRequest.currency(),
                createRequest.terms()
            );

            return agencyContractService.create(ctx, command)
                .then(contract -> {
                    metrics.incrementCounter("agency_contract.create.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(contract)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_contract.create.error");
                    LOG.error("Failed to create agency contract", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_contract.create.error");
            LOG.error("Failed to parse create request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleActivate(HttpRequest request) {
        String contractId = request.pathParameter("contractId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            return agencyContractService.activate(ctx, contractId)
                .then(contract -> {
                    metrics.incrementCounter("agency_contract.activate.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(contract)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_contract.activate.error");
                    LOG.error("Failed to activate agency contract", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_contract.activate.error");
            LOG.error("Failed to process activate request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleTerminate(HttpRequest request) {
        String contractId = request.pathParameter("contractId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            TerminateRequest terminateRequest = objectMapper.readValue(request.getBody(), TerminateRequest.class);

            return agencyContractService.terminate(ctx, contractId, terminateRequest.reason())
                .then(contract -> {
                    metrics.incrementCounter("agency_contract.terminate.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(contract)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_contract.terminate.error");
                    LOG.error("Failed to terminate agency contract", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_contract.terminate.error");
            LOG.error("Failed to parse terminate request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleRenew(HttpRequest request) {
        String contractId = request.pathParameter("contractId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            RenewRequest renewRequest = objectMapper.readValue(request.getBody(), RenewRequest.class);

            return agencyContractService.renew(ctx, contractId, LocalDate.parse(renewRequest.newEndDate()))
                .then(contract -> {
                    metrics.incrementCounter("agency_contract.renew.success");
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(contract)))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_contract.renew.error");
                    LOG.error("Failed to renew agency contract", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_contract.renew.error");
            LOG.error("Failed to parse renew request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleGetById(HttpRequest request) {
        String contractId = request.pathParameter("contractId");
        boolean isWriteOperation = false;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            return agencyContractService.findById(ctx, contractId)
                .then(contractOpt -> {
                    if (contractOpt.isEmpty()) {
                        return Promise.of(HttpResponse.ofCode(404).withJson(Json.of("{\"error\":\"Contract not found\"}")));
                    }
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(toDto(contractOpt.get())))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_contract.get.error");
                    LOG.error("Failed to get agency contract", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_contract.get.error");
            LOG.error("Failed to process get request", e);
            return Promise.of(HttpResponse.ofCode(400).withJson(Json.of("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")));
        }
    }

    private Promise<HttpResponse> handleList(HttpRequest request) {
        boolean isWriteOperation = false;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            return agencyContractService.list(ctx)
                .then(contracts -> {
                    Object[] dtos = contracts.stream().map(this::toDto).toArray();
                    return Promise.of(HttpResponse.ok()
                        .withJson(Json.of(objectMapper.writeValueAsString(dtos))));
                })
                .whenException(e -> {
                    metrics.incrementCounter("agency_contract.list.error");
                    LOG.error("Failed to list agency contracts", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.incrementCounter("agency_contract.list.error");
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

    private ContractDto toDto(AgencyContract contract) {
        return new ContractDto(
            contract.getId(),
            contract.getAgencyTenantId(),
            contract.getClientId(),
            contract.getContractNumber(),
            contract.getContractType(),
            contract.getStartDate().toString(),
            contract.getEndDate() != null ? contract.getEndDate().toString() : null,
            contract.getMonthlyRetainer().toString(),
            contract.getCurrency(),
            contract.getStatus().name(),
            contract.getTerms(),
            contract.getCreatedAt().toString(),
            contract.getUpdatedAt() != null ? contract.getUpdatedAt().toString() : null
        );
    }

    private record CreateRequest(
        String clientId,
        String contractNumber,
        String contractType,
        String startDate,
        String endDate,
        String monthlyRetainer,
        String currency,
        String terms
    ) {}

    private record TerminateRequest(
        String reason
    ) {}

    private record RenewRequest(
        String newEndDate
    ) {}

    private record ContractDto(
        String id,
        String agencyTenantId,
        String clientId,
        String contractNumber,
        String contractType,
        String startDate,
        String endDate,
        String monthlyRetainer,
        String currency,
        String status,
        String terms,
        String createdAt,
        String updatedAt
    ) {}
}
