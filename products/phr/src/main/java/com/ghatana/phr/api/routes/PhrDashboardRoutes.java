package com.ghatana.phr.api.routes;

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
 * Dashboard summary API for the PHR web application.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter serving the patient dashboard summary for the web application
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrDashboardRoutes {

    private final Eventloop eventloop;

    public PhrDashboardRoutes(Eventloop eventloop) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
    }

    /**
     * Returns the routing servlet for dashboard endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/", this::handleGetDashboard)
            .build();
    }

    private Promise<HttpResponse> handleGetDashboard(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        return Promise.of(Map.of(
            "tenantId", context.tenantId(),
            "principalId", context.principalId(),
            "role", context.role(),
            "summary", "Dashboard summary for patient",
            "correlationId", context.correlationId()
        )).then(summary -> PhrRouteSupport.jsonResponse(200, summary, context.correlationId()));
    }
}
