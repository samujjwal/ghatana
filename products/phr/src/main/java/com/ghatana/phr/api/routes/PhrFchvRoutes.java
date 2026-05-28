package com.ghatana.phr.api.routes;

import com.ghatana.phr.security.PhrPolicyEvaluator;
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
    private final PhrPolicyEvaluator policyEvaluator;

    public PhrFchvRoutes(Eventloop eventloop, PhrPolicyEvaluator policyEvaluator) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.policyEvaluator = Objects.requireNonNull(policyEvaluator, "policyEvaluator must not be null");
    }

    /**
     * Returns the routing servlet for FCHV endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/dashboard", this::handleGetFchvDashboard)
            .with(HttpMethod.GET, "/patients", this::handleListPatients)
            .with(HttpMethod.POST, "/patients", this::handleRegisterPatient)
            .with(HttpMethod.GET, "/patients/:patientId", this::handleGetFchvPatient)
            .with(HttpMethod.POST, "/patients/:patientId/vitals", this::handleRecordVitals)
            .with(HttpMethod.GET, "/sync/status", this::handleGetSyncStatus)
            .with(HttpMethod.POST, "/sync", this::handleSyncOperations)
            .build();
    }

    private Promise<HttpResponse> handleGetFchvDashboard(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!"fchv".equals(context.role()) && !"admin".equals(context.role())) {
            return PhrRouteSupport.errorResponse(403, "FCHV_ROLE_REQUIRED",
                "Only FCHV or admin principals may access the FCHV dashboard",
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

    private Promise<HttpResponse> handleListPatients(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!"fchv".equals(context.role()) && !"admin".equals(context.role())) {
            return PhrRouteSupport.errorResponse(403, "FCHV_ROLE_REQUIRED",
                "Only FCHV or admin principals may list patients",
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
                "fchvId", context.principalId(),
                "tenantId", context.tenantId(),
                "patients", patients,
                "total", patients.size()
            ),
            context.correlationId()));
    }

    private Promise<HttpResponse> handleRegisterPatient(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!"fchv".equals(context.role()) && !"admin".equals(context.role())) {
            return PhrRouteSupport.errorResponse(403, "FCHV_ROLE_REQUIRED",
                "Only FCHV or admin principals may register patients",
                context.correlationId());
        }

        return request.loadBody()
            .then(body -> {
                try {
                    String json = body.getString(java.nio.charset.StandardCharsets.UTF_8);
                    var node = PhrRouteSupport.JSON.readTree(json);
                    
                    String patientId = "PAT-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    
                    return PhrRouteSupport.jsonResponse(201, Map.of(
                        "patientId", patientId,
                        "registeredBy", context.principalId(),
                        "tenantId", context.tenantId(),
                        "status", "REGISTERED",
                        "registeredAt", java.time.Instant.now().toString()
                    ), context.correlationId());
                } catch (Exception ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_REQUEST",
                        "Invalid patient registration format", context.correlationId());
                }
            });
    }

    private Promise<HttpResponse> handleGetFchvPatient(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!"fchv".equals(context.role()) && !"admin".equals(context.role())) {
            return PhrRouteSupport.errorResponse(403, "FCHV_ROLE_REQUIRED",
                "Only FCHV or admin principals may access FCHV patient records",
                context.correlationId());
        }

        String patientId = request.getPathParameter("patientId");
        return policyEvaluator.canAccessPhiResourceAsync(
            context,
            patientId,
            "fchv-patient-summary",
            "READ",
            context.tenantId(),
            context.facilityId()
        ).then(decision -> {
            if (!decision.isAllowed()) {
                return PhrRouteSupport.policyDenialResponse(403, context.correlationId());
            }

            return Promise.of(
                Map.of(
                    "id", patientId,
                    "name", "[REDACTED]",
                    "village", "Sindhuli-3",
                    "riskLevel", "medium",
                    "lastContact", "2026-05-21"
                )
            ).then(patient -> PhrRouteSupport.jsonResponseWithCorrelation(200, patient, context.correlationId()));
        });
    }

    private Promise<HttpResponse> handleRecordVitals(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!"fchv".equals(context.role()) && !"admin".equals(context.role())) {
            return PhrRouteSupport.errorResponse(403, "FCHV_ROLE_REQUIRED",
                "Only FCHV or admin principals may record vitals",
                context.correlationId());
        }

        String patientId = request.getPathParameter("patientId");
        return policyEvaluator.canAccessPhiResourceAsync(
            context,
            patientId,
            "fchv-vitals",
            "WRITE",
            context.tenantId(),
            context.facilityId()
        ).then(decision -> {
            if (!decision.isAllowed()) {
                return PhrRouteSupport.policyDenialResponse(403, context.correlationId());
            }

            return Promise.of(
                Map.of(
                    "patientId", patientId,
                    "recordedBy", context.principalId(),
                    "status", "RECORDED",
                    "timestamp", java.time.Instant.now().toString()
                )
            ).then(result -> PhrRouteSupport.jsonResponseWithCorrelation(201, result, context.correlationId()));
        });
    }

    private Promise<HttpResponse> handleGetSyncStatus(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!"fchv".equals(context.role()) && !"admin".equals(context.role())) {
            return PhrRouteSupport.errorResponse(403, "FCHV_ROLE_REQUIRED",
                "Only FCHV or admin principals may check sync status",
                context.correlationId());
        }

        return PhrRouteSupport.jsonResponse(200, Map.of(
            "fchvId", context.principalId(),
            "tenantId", context.tenantId(),
            "syncStatus", "SYNCED",
            "pendingOperations", 0,
            "lastSyncAt", java.time.Instant.now().toString(),
            "queueSize", 0
        ), context.correlationId());
    }

    private Promise<HttpResponse> handleSyncOperations(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if (!"fchv".equals(context.role()) && !"admin".equals(context.role())) {
            return PhrRouteSupport.errorResponse(403, "FCHV_ROLE_REQUIRED",
                "Only FCHV or admin principals may trigger sync",
                context.correlationId());
        }

        return request.loadBody()
            .then(body -> {
                try {
                    String json = body.getString(java.nio.charset.StandardCharsets.UTF_8);
                    var node = PhrRouteSupport.JSON.readTree(json);
                    
                    return PhrRouteSupport.jsonResponse(200, Map.of(
                        "fchvId", context.principalId(),
                        "tenantId", context.tenantId(),
                        "syncStatus", "COMPLETED",
                        "operationsProcessed", 0,
                        "syncedAt", java.time.Instant.now().toString()
                    ), context.correlationId());
                } catch (Exception ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_REQUEST",
                        "Invalid sync request format", context.correlationId());
                }
            });
    }
}
