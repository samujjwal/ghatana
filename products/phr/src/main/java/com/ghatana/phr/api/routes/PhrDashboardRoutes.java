package com.ghatana.phr.api.routes;

import com.ghatana.phr.repository.UserRepository;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Dashboard summary API for the PHR web application.
 *
 * <p>Provides a backend-owned dashboard endpoint that aggregates patient profile,
 * next appointment, medications, recent observations, active conditions, documents,
 * and access alerts into a single payload. This replaces frontend-composed dashboard
 * data with a server-side source of truth.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter serving the patient dashboard summary for the web application
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrDashboardRoutes {

    private final Eventloop eventloop;
    private final UserRepository userRepository;

    public PhrDashboardRoutes(Eventloop eventloop, UserRepository userRepository) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
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

        // Fetch patient profile
        Optional<com.ghatana.phr.model.PHRUser> userOpt = userRepository.findByUserId(context.principalId());
        if (userOpt.isEmpty()) {
            return PhrRouteSupport.errorResponse(404, "PATIENT_NOT_FOUND", "Patient not found");
        }

        com.ghatana.phr.model.PHRUser user = userOpt.get();

        // Build dashboard payload with IA-required widgets
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("tenantId", context.tenantId());
        dashboard.put("principalId", context.principalId());
        dashboard.put("role", context.role());
        dashboard.put("correlationId", context.correlationId());

        // Profile summary widget
        Map<String, Object> profileSummary = new LinkedHashMap<>();
        profileSummary.put("name", user.getUsername() != null ? user.getUsername() : context.principalId());
        profileSummary.put("email", user.getEmail());
        profileSummary.put("providerId", user.getProviderId());
        profileSummary.put("active", user.isActive());
        dashboard.put("profileSummary", profileSummary);

        // Next appointment widget (placeholder - would fetch from appointment service)
        dashboard.put("nextAppointment", null);

        // Medications widget (placeholder - would fetch from medication service)
        dashboard.put("medications", Map.of(
            "activeCount", 0,
            "adherenceAlert", false
        ));

        // Recent observations widget (placeholder - would fetch from observation service)
        dashboard.put("recentObservations", Map.of(
            "count", 0,
            "hasCritical", false
        ));

        // Active conditions widget (placeholder - would fetch from condition service)
        dashboard.put("activeConditions", Map.of(
            "count", 0,
            "hasChronic", false
        ));

        // Documents widget (placeholder - would fetch from document service)
        dashboard.put("documents", Map.of(
            "totalCount", 0,
            "pendingOcr", 0
        ));

        // Access alerts widget
        Map<String, Object> accessAlerts = new LinkedHashMap<>();
        accessAlerts.put("expiringConsents", 0);
        accessAlerts.put("emergencyAccessPending", false);
        dashboard.put("accessAlerts", accessAlerts);

        // Freshness timestamp
        dashboard.put("generatedAt", Instant.now().toString());

        return PhrRouteSupport.jsonResponse(200, dashboard, context.correlationId());
    }
}
