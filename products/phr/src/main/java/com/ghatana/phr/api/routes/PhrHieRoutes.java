package com.ghatana.phr.api.routes;

import com.ghatana.phr.hie.HieIntegrationContract;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.Objects;

/**
 * HIE (Health Information Exchange) API routes for the PHR product.
 *
 * <p>Provides user-facing endpoints for patients to export their data to the
 * configured HIE plugins and import data from external HIE sources. All operations require
 * patient consent and are audited.</p>
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for HIE export/import workflows
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrHieRoutes {

    private static final String HIE_RESOURCE_TYPE = "hie-integration";

    private final Eventloop eventloop;
    private final HieIntegrationContract hieContract;
    private final PhrPolicyEvaluator policyEvaluator;

    public PhrHieRoutes(
            Eventloop eventloop,
            HieIntegrationContract hieContract,
            PhrPolicyEvaluator policyEvaluator) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.hieContract = Objects.requireNonNull(hieContract, "hieContract must not be null");
        this.policyEvaluator = Objects.requireNonNull(policyEvaluator, "policyEvaluator must not be null");
    }

    /**
     * Returns the routing servlet for HIE endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/export", this::handleExportToHie)
            .with(HttpMethod.POST, "/import", this::handleImportFromHie)
            .with(HttpMethod.POST, "/sync", this::handleSyncWithHie)
            .with(HttpMethod.GET, "/status/:requestId", this::handleGetStatus)
            .build();
    }

    /**
     * Handles export of patient data to the Nepal HIE.
     *
     * <p>This endpoint requires patient consent and triggers an asynchronous
     * export of the patient's summary to the HIE. The patient must have
     * valid consent for data sharing.</p>
     */
    private Promise<HttpResponse> handleExportToHie(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        return submitOperation(HieIntegrationContract.Operation.EXPORT, context);
    }

    /**
     * Handles import of patient data from the Nepal HIE.
     *
     * <p>This endpoint requires patient consent and triggers an asynchronous
     * import of the patient's data from the HIE. The patient must have
     * valid consent for data retrieval.</p>
     */
    private Promise<HttpResponse> handleImportFromHie(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        return submitOperation(HieIntegrationContract.Operation.IMPORT, context);
    }

    private Promise<HttpResponse> handleSyncWithHie(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        return submitOperation(HieIntegrationContract.Operation.SYNC, context);
    }

    /**
     * Handles status check for HIE export/import requests.
     *
     * <p>Returns the current status of an asynchronous HIE operation.</p>
     */
    private Promise<HttpResponse> handleGetStatus(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(), correlationId);
        }

        String requestId = request.getPathParameter("requestId");
        if (requestId == null || requestId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "INVALID_REQUEST_ID",
                "Request ID is required", context.correlationId());
        }

        return authorize(context, "READ")
            .then(decision -> {
                if (!decision.isAllowed()) {
                    return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
                }
                return hieContract.getStatus(requestId, context.correlationId())
                    .then(status -> PhrRouteSupport.jsonResponse(200, statusDto(status, context.correlationId()), context.correlationId()));
            });
    }

    private Promise<HttpResponse> submitOperation(
            HieIntegrationContract.Operation operation,
            PhrRouteSupport.PhrRequestContext context) {
        return authorize(context, operation.name())
            .then(decision -> {
                if (!decision.isAllowed()) {
                    return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
                }
                HieIntegrationContract.HieIntegrationRequest integrationRequest =
                    new HieIntegrationContract.HieIntegrationRequest(
                        operation,
                        context.principalId(),
                        context.correlationId(),
                        context.principalId(),
                        context.tenantId(),
                        "FHIR_R4_BUNDLE",
                        Map.of("persona", context.persona(), "tier", context.tier())
                    );
                return hieContract.submit(integrationRequest)
                    .then(result -> result.accepted()
                        ? PhrRouteSupport.jsonResponse(202, resultDto(result, context.correlationId()), context.correlationId())
                        : PhrRouteSupport.errorResponse(409, result.safeReasonCode(), result.message(), context.correlationId()));
            })
            .whenException(ex -> PhrRouteSupport.errorResponse(502, "HIE_CONTRACT_ERROR", "HIE contract failed to submit operation",
                context.correlationId()));
    }

    private Promise<PhrPolicyEvaluator.PolicyDecision> authorize(
            PhrRouteSupport.PhrRequestContext context,
            String action) {
        return policyEvaluator.canAccessPhiResourceAsync(
            context,
            context.principalId(),
            HIE_RESOURCE_TYPE,
            action,
            context.tenantId(),
            context.facilityId()
        );
    }

    private static Map<String, Object> resultDto(
            HieIntegrationContract.HieIntegrationResult result,
            String correlationId) {
        return Map.of(
            "requestId", result.requestId(),
            "operation", result.operation().name(),
            "contractId", result.contractId(),
            "status", result.status(),
            "reasonCode", result.safeReasonCode(),
            "correlationId", correlationId
        );
    }

    private static Map<String, Object> statusDto(
            HieIntegrationContract.HieIntegrationStatus status,
            String correlationId) {
        Map<String, Object> dto = new java.util.LinkedHashMap<>();
        dto.put("requestId", status.requestId());
        dto.put("contractId", status.contractId());
        dto.put("status", status.status());
        dto.put("reasonCode", status.safeReasonCode());
        dto.put("message", status.message());
        dto.put("correlationId", correlationId);
        if (status.operation() != null) {
            dto.put("operation", status.operation().name());
        }
        return dto;
    }
}
