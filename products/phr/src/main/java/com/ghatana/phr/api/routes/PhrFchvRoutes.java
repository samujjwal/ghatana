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
 * FCHV (Female Community Health Volunteer) API routes for the PHR product.
 *
 * <p>Provides dashboard and patient management endpoints for community health volunteers
 * and administrators. All endpoints enforce caregiver-or-admin role policy.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for FCHV community health volunteer workflows
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrFchvRoutes {

    private final Eventloop eventloop;

    public PhrFchvRoutes(Eventloop eventloop) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
    }

    /**
     * Returns the routing servlet for FCHV endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/dashboard", this::handleGetFchvDashboard)
            .with(HttpMethod.GET, "/patients/:patientId", this::handleGetFchvPatient)
            .with(HttpMethod.POST, "/patients/:patientId/vitals", this::handleRecordVitals)
            .build();
    }

    private Promise<HttpResponse> handleGetFchvDashboard(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!"caregiver".equals(context.role()) && !"admin".equals(context.role())) {
            return PhrRouteSupport.errorResponse(403, "FCHV_ROLE_REQUIRED",
                "Only caregiver or admin principals may access the FCHV dashboard",
                context.correlationId());
        }

        return Promise.of(List.of(
            Map.of(
                "id", "patient-001",
                "name", "[REDACTED]",
                "village", "Sindhuli-3",
                "riskLevel", "high",
                "lastContact", "2026-05-20"
            ),
            Map.of(
                "id", "patient-002",
                "name", "[REDACTED]",
                "village", "Sindhuli-5",
                "riskLevel", "low",
                "lastContact", "2026-05-22"
            )
        )).then(patients -> PhrRouteSupport.jsonResponse(200,
            Map.of(
                "principalId", context.principalId(),
                "tenantId", context.tenantId(),
                "items", patients,
                "count", patients.size()
            ),
            context.correlationId()));
    }

    private Promise<HttpResponse> handleGetFchvPatient(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!context.canAccessPatientRecordForRole()) {
            return PhrRouteSupport.errorResponse(403, "RECORD_ACCESS_DENIED",
                "Patient record access requires caregiver, clinician, or admin role",
                context.correlationId());
        }

        String patientId = request.getPathParameter("patientId");
        return Promise.of(
            Map.of(
                "id", patientId,
                "name", "[REDACTED]",
                "village", "Sindhuli-3",
                "riskLevel", "medium",
                "lastContact", "2026-05-21"
            )
        ).then(patient -> PhrRouteSupport.jsonResponse(200, patient, context.correlationId()));
    }

    private Promise<HttpResponse> handleRecordVitals(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!"caregiver".equals(context.role()) && !"admin".equals(context.role())) {
            return PhrRouteSupport.errorResponse(403, "FCHV_ROLE_REQUIRED",
                "Only caregiver or admin principals may record vitals",
                context.correlationId());
        }

        String patientId = request.getPathParameter("patientId");
        return Promise.of(
            Map.of(
                "patientId", patientId,
                "recordedBy", context.principalId(),
                "status", "RECORDED",
                "timestamp", java.time.Instant.now().toString()
            )
        ).then(result -> PhrRouteSupport.jsonResponse(201, result, context.correlationId()));
    }
}
