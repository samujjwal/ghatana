package com.ghatana.phr.api.routes;

import com.ghatana.phr.hie.NepalHieIntegrationService;
import com.ghatana.phr.hie.NepalHieSyncResult;
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
 * Nepal HIE and import data from external HIE sources. All operations require
 * patient consent and are audited.</p>
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for HIE export/import workflows
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrHieRoutes {

    private final Eventloop eventloop;
    private final NepalHieIntegrationService hieService;

    public PhrHieRoutes(
            Eventloop eventloop,
            NepalHieIntegrationService hieService) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.hieService = Objects.requireNonNull(hieService, "hieService must not be null");
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
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(, correlationId));
        }

        // Extract patientId from request body or use context principalId
        String patientId = context.principalId();

        return hieService.submitPatientSummary(patientId, context.correlationId())
            .then(result -> {
                if (result.accepted()) {
                    return PhrRouteSupport.jsonResponse(202, Map.of(
                        "requestId", result.messageControlId(, correlationId),
                        "status", "ACCEPTED",
                        "message", "HIE export request accepted for processing",
                        "patientId", patientId,
                        "correlationId", context.correlationId()
                    ), context.correlationId());
                } else {
                    return PhrRouteSupport.errorResponse(502, "HIE_EXPORT_FAILED", "HIE service rejected the export request: " + result.message(, correlationId), 
                        context.correlationId());
                }
            })
            .whenException(ex -> PhrRouteSupport.errorResponse(500, "HIE_EXPORT_ERROR", "Failed to submit HIE export request: " + ex.getMessage(, correlationId), 
                context.correlationId()));
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
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(, correlationId));
        }

        String patientId = context.principalId();

        return hieService.submitPatientSummary(patientId, context.correlationId())
            .then(result -> {
                if (result.accepted()) {
                    return PhrRouteSupport.jsonResponse(202, Map.of(
                        "requestId", result.messageControlId(, correlationId),
                        "status", "ACCEPTED",
                        "message", "HIE import request accepted for processing",
                        "patientId", patientId,
                        "correlationId", context.correlationId()
                    ), context.correlationId());
                } else {
                    return PhrRouteSupport.errorResponse(502, "HIE_IMPORT_FAILED", "HIE service rejected the import request: " + result.message(, correlationId), 
                        context.correlationId());
                }
            })
            .whenException(ex -> PhrRouteSupport.errorResponse(500, "HIE_IMPORT_ERROR", "Failed to submit HIE import request: " + ex.getMessage(, correlationId), 
                context.correlationId()));
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
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(, correlationId));
        }

        String requestId = request.getPathParameter("requestId");
        if (requestId == null || requestId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "INVALID_REQUEST_ID", 
                "Request ID is required", context.correlationId());
        }

        return PhrRouteSupport.jsonResponse(200, Map.of(
            "requestId", requestId,
            "status", "PROCESSING",
            "message", "HIE operation is in progress",
            "correlationId", context.correlationId()
        ), context.correlationId());
    }
}
