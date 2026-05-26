package com.ghatana.phr.api.routes;

import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provider (clinician) API for the PHR product.
 *
 * <p>Provides clinician-scoped endpoints for listing patients and accessing
 * clinical summaries. All endpoints require clinical role.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for provider patient roster and clinical summary access
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrProviderRoutes {

    private final Eventloop eventloop;

    public PhrProviderRoutes(Eventloop eventloop) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
    }

    /**
     * Returns the routing servlet for provider endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/patients", this::handleGetPatients)
            .with(HttpMethod.GET, "/patient/:patientId/summary", this::handleGetPatientSummary)
            .build();
    }

    private Promise<HttpResponse> handleGetPatients(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!PhrRouteSupport.hasClinicalRole(context)) {
            return PhrRouteSupport.errorResponse(403, "CLINICAL_ROLE_REQUIRED",
                "Only clinician or admin principals may access the patient roster",
                context.correlationId());
        }

        return Promise.of(List.of(
            Map.of(
                "id", "pat-1",
                "name", "Patient One",
                "age", 45,
                "status", "active",
                "lastVisit", "2024-03-01"
            )
        )).then(patients -> PhrRouteSupport.jsonResponse(200, patients, context.correlationId()));
    }

    private Promise<HttpResponse> handleGetPatientSummary(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!PhrRouteSupport.hasClinicalRole(context)) {
            return PhrRouteSupport.errorResponse(403, "CLINICAL_ROLE_REQUIRED",
                "Only clinician or admin principals may view patient clinical summaries",
                context.correlationId());
        }

        String patientId = request.getPathParameter("patientId");
        return Promise.of(Map.of(
            "patientId", patientId,
            "tenantId", context.tenantId(),
            "clinicianId", context.principalId(),
            "summary", "Clinical summary for patient"
        )).then(summary -> PhrRouteSupport.jsonResponse(200, summary, context.correlationId()));
    }
}
