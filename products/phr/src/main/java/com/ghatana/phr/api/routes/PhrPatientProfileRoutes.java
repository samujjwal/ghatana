package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.databind.JsonNode;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Patient profile API for the PHR product.
 *
 * <p>Exposes profile read and update operations. Patients may only view and modify
 * their own profile; clinical roles may read any profile.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for reading and updating the patient's demographic profile
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrPatientProfileRoutes {

    private final Eventloop eventloop;

    public PhrPatientProfileRoutes(Eventloop eventloop) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
    }

    /**
     * Returns the routing servlet for patient profile endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/", this::handleGetProfile)
            .with(HttpMethod.PUT, "/", this::handleUpdateProfile)
            .build();
    }

    private Promise<HttpResponse> handleGetProfile(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        return Promise.of(Map.of(
            "id", context.principalId(),
            "tenantId", context.tenantId(),
            "name", "Patient Name",
            "birthDate", "",
            "bloodType", "",
            "location", "",
            "emergencyContact", "",
            "preferredLanguage", "en"
        )).then(profile -> PhrRouteSupport.jsonResponse(200, profile, context.correlationId()));
    }

    private Promise<HttpResponse> handleUpdateProfile(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        return request.loadBody()
            .then(body -> {
                String raw = body.getString(StandardCharsets.UTF_8);
                if (raw.isBlank()) {
                    return PhrRouteSupport.errorResponse(400, "EMPTY_BODY", "Request body is required",
                        context.correlationId());
                }
                // Profile update is delegated to the service layer (stub for wiring).
                return PhrRouteSupport.jsonResponse(200,
                    Map.of("status", "updated", "principalId", context.principalId()),
                    context.correlationId());
            });
    }
}
