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
 * Mobile dashboard API for the PHR mobile application.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter serving dashboard data to mobile clients with session-header enforcement
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrMobileRoutes {

    private final Eventloop eventloop;

    public PhrMobileRoutes(Eventloop eventloop) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
    }

    /**
     * Returns the routing servlet for mobile dashboard endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/dashboard", this::handleGetMobileDashboard)
            .build();
    }

    private Promise<HttpResponse> handleGetMobileDashboard(HttpRequest request) {
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
            "mobileSummary", "Mobile dashboard payload",
            "correlationId", context.correlationId()
        )).then(payload -> PhrRouteSupport.jsonResponse(200, payload, context.correlationId()));
    }
}
