package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.agency.AgencyContractService;
import com.ghatana.digitalmarketing.application.agency.AgencyContractService.CreateContractCommand;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.agency.AgencyContract;
import com.ghatana.digitalmarketing.domain.agency.AgencyContractStatus;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
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

            CreateRequest createRequest = objectMapper.readValue(request.getBody().getString(java.nio.charset.StandardCharsets.UTF_8), CreateRequest.class);

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
                    metrics.increment("agency_contract.create.success", Map.of());
                    return Promise.of(HttpResponse.ofCode(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody(objectMapper.writeValueAsBytes(toDto(contract)))
                        .build());
                })
                .then(r -> Promise.of(r), e -> {
                    metrics.increment("agency_contract.create.error", Map.of());
                    LOG.error("Failed to create agency contract", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.increment("agency_contract.create.error", Map.of());
            LOG.error("Failed to parse create request", e);
            return Promise.of(HttpResponse.ofCode(400)
                .withBody("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build());
        }
    }

    private Promise<HttpResponse> handleActivate(HttpRequest request) {
        String contractId = request.getPathParameter("contractId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            return agencyContractService.activate(ctx, contractId)
                .then(contract -> {
                    metrics.increment("agency_contract.activate.success", Map.of());
                    return Promise.of(HttpResponse.ofCode(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody(objectMapper.writeValueAsBytes(toDto(contract)))
                        .build());
                })
                .then(r -> Promise.of(r), e -> {
                    metrics.increment("agency_contract.activate.error", Map.of());
                    LOG.error("Failed to activate agency contract", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.increment("agency_contract.activate.error", Map.of());
            LOG.error("Failed to process activate request", e);
            return Promise.of(HttpResponse.ofCode(400)
                .withBody("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build());
        }
    }

    private Promise<HttpResponse> handleTerminate(HttpRequest request) {
        String contractId = request.getPathParameter("contractId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            TerminateRequest terminateRequest = objectMapper.readValue(request.getBody().getString(java.nio.charset.StandardCharsets.UTF_8), TerminateRequest.class);

            return agencyContractService.terminate(ctx, contractId, terminateRequest.reason())
                .then(contract -> {
                    metrics.increment("agency_contract.terminate.success", Map.of());
                    return Promise.of(HttpResponse.ofCode(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody(objectMapper.writeValueAsBytes(toDto(contract)))
                        .build());
                })
                .then(r -> Promise.of(r), e -> {
                    metrics.increment("agency_contract.terminate.error", Map.of());
                    LOG.error("Failed to terminate agency contract", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.increment("agency_contract.terminate.error", Map.of());
            LOG.error("Failed to parse terminate request", e);
            return Promise.of(HttpResponse.ofCode(400)
                .withBody("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build());
        }
    }

    private Promise<HttpResponse> handleRenew(HttpRequest request) {
        String contractId = request.getPathParameter("contractId");
        boolean isWriteOperation = true;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            RenewRequest renewRequest = objectMapper.readValue(request.getBody().getString(java.nio.charset.StandardCharsets.UTF_8), RenewRequest.class);

            return agencyContractService.renew(ctx, contractId, LocalDate.parse(renewRequest.newEndDate()))
                .map(contract -> {
                    metrics.increment("agency_contract.renew.success", Map.of());
                    return HttpResponse.ofCode(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody(objectMapper.writeValueAsBytes(toDto(contract)))
                        .build();
                })
                .then(r -> Promise.of(r), e -> {
                    metrics.increment("agency_contract.renew.error", Map.of());
                    LOG.error("Failed to renew agency contract", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.increment("agency_contract.renew.error", Map.of());
            LOG.error("Failed to parse renew request", e);
            return Promise.of(HttpResponse.ofCode(400)
                .withBody("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build());
        }
    }

    private Promise<HttpResponse> handleGetById(HttpRequest request) {
        String contractId = request.getPathParameter("contractId");
        boolean isWriteOperation = false;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            return agencyContractService.findById(ctx, contractId)
                .map(contractOpt -> {
                    if (contractOpt.isEmpty()) {
                        return HttpResponse.ofCode(404)
                            .withBody("{\"error\":\"Contract not found\"}")
                            .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                            .build();
                    }
                    return HttpResponse.ofCode(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody(objectMapper.writeValueAsBytes(toDto(contractOpt.get())))
                        .build();
                })
                .then(r -> Promise.of(r), e -> {
                    metrics.increment("agency_contract.get.error", Map.of());
                    LOG.error("Failed to get agency contract", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.increment("agency_contract.get.error", Map.of());
            LOG.error("Failed to process get request", e);
            return Promise.of(HttpResponse.ofCode(400)
                .withBody("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build());
        }
    }

    private Promise<HttpResponse> handleList(HttpRequest request) {
        boolean isWriteOperation = false;

        try {
            DmOperationContext ctx = httpContextFactory.buildContext(request, null, isWriteOperation);

            return agencyContractService.list(ctx)
                .map(contracts -> {
                    Object[] dtos = contracts.stream().map(this::toDto).toArray();
                    return HttpResponse.ofCode(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody(objectMapper.writeValueAsBytes(dtos))
                        .build();
                })
                .then(r -> Promise.of(r), e -> {
                    metrics.increment("agency_contract.list.error", Map.of());
                    LOG.error("Failed to list agency contracts", e);
                    return Promise.of(mapServiceError(e));
                });
        } catch (Exception e) {
            metrics.increment("agency_contract.list.error", Map.of());
            LOG.error("Failed to process list request", e);
            return Promise.of(HttpResponse.ofCode(400)
                .withBody("{\"error\":\"Invalid request: " + e.getMessage() + "\"}")
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build());
        }
    }

    private HttpResponse mapServiceError(Exception e) {
        if (e instanceof IllegalArgumentException) {
            return HttpResponse.ofCode(400)
                .withBody("{\"error\":\"" + e.getMessage() + "\"}")
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
        }
        return HttpResponse.ofCode(500)
            .withBody("{\"error\":\"Internal server error\"}")
            .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .build();
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
