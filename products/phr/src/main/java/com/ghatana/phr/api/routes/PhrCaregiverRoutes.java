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
 * Caregiver delegated-access API for the PHR product.
 *
 * <p>Provides caregiver-scoped endpoints for listing dependents and accessing
 * patient records under an active consent grant.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for caregiver dependent listing and delegated patient access
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrCaregiverRoutes {

    private final Eventloop eventloop;

    public PhrCaregiverRoutes(Eventloop eventloop) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
    }

    /**
     * Returns the routing servlet for caregiver endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/dependents", this::handleGetDependents)
            .with(HttpMethod.GET, "/patient/:patientId", this::handleGetPatientSummary)
            .build();
    }

    private Promise<HttpResponse> handleGetDependents(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!"caregiver".equals(context.role()) && !"admin".equals(context.role())) {
            return PhrRouteSupport.errorResponse(403, "CAREGIVER_ROLE_REQUIRED",
                "Only caregiver or admin principals may access dependent lists",
                context.correlationId());
        }

        return Promise.of(List.of(
            Map.of(
                "id", "dep-1",
                "name", "Dependent Name",
                "relationship", "parent",
                "age", 8,
                "consentGrantId", "cg-abc"
            )
        )).then(deps -> PhrRouteSupport.jsonResponse(200, deps, context.correlationId()));
    }

    private Promise<HttpResponse> handleGetPatientSummary(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        String patientId = request.getPathParameter("patientId");
        if (!PhrRouteSupport.canAccessPatientRecordForRole(context, patientId)) {
            return PhrRouteSupport.errorResponse(403, "CAREGIVER_PATIENT_ACCESS_DENIED",
                "Access denied to patient record for caregiver",
                context.correlationId());
        }

        return Promise.of(Map.of(
            "patientId", patientId,
            "tenantId", context.tenantId(),
            "summary", "Caregiver-scoped patient summary"
        )).then(summary -> PhrRouteSupport.jsonResponse(200, summary, context.correlationId()));
    }
}
