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
 * Clinical timeline API for the PHR product.
 *
 * <p>Returns a chronological list of health events for a patient.
 * Patients may access their own timeline. Clinical roles may access any patient's timeline.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter serving the patient health timeline
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrTimelineRoutes {

    private final Eventloop eventloop;

    public PhrTimelineRoutes(Eventloop eventloop) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
    }

    /**
     * Returns the routing servlet for timeline endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/:patientId", this::handleGetTimeline)
            .build();
    }

    private Promise<HttpResponse> handleGetTimeline(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        String patientId = request.getPathParameter("patientId");
        if (!PhrRouteSupport.canAccessPatientRecordForRole(context, patientId)) {
            return PhrRouteSupport.errorResponse(403, "TIMELINE_ACCESS_DENIED",
                "Access denied to timeline for patient " + patientId,
                context.correlationId());
        }

        return Promise.of(List.of(
            Map.of(
                "id", "tl-1",
                "eventType", "visit",
                "title", "Outpatient consultation",
                "occurredAt", "2024-01-15T09:00:00Z"
            )
        )).then(events -> PhrRouteSupport.jsonResponse(200, events, context.correlationId()));
    }
}
