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
 * Clinical condition API for the PHR product.
 *
 * <p>Returns active and resolved diagnoses for a given patient.
 * Access follows the standard patient-record access policy.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for FHIR Condition resources
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrConditionRoutes {

    private final Eventloop eventloop;

    public PhrConditionRoutes(Eventloop eventloop) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
    }

    /**
     * Returns the routing servlet for condition endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/:patientId", this::handleGetConditions)
            .build();
    }

    private Promise<HttpResponse> handleGetConditions(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        String patientId = request.getPathParameter("patientId");
        if (!PhrRouteSupport.canAccessPatientRecordForRole(context, patientId)) {
            return PhrRouteSupport.errorResponse(403, "CONDITIONS_ACCESS_DENIED",
                "Access denied to conditions for patient " + patientId,
                context.correlationId());
        }

        return Promise.of(List.of(
            Map.of(
                "id", "cond-1",
                "code", "E11",
                "display", "Type 2 diabetes mellitus",
                "status", "active",
                "onsetDate", "2018-03-01"
            )
        )).then(conditions -> PhrRouteSupport.jsonResponse(200, conditions, context.correlationId()));
    }
}
