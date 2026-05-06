/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.phr.api;

import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.phr.fhir.server.PhrFhirR4Server;
import com.ghatana.phr.kernel.service.BillingService;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpHeaders;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ActiveJ HTTP server exposing the PHR product's FHIR and operational API.
 *
 * <h2>Endpoints</h2>
 * <pre>
 *   POST   /fhir/:resourceType                          — Create a FHIR R4 resource
 *   GET    /fhir/:resourceType/:id                      — Read a FHIR R4 resource by ID
 *   GET    /fhir/:resourceType                          — Search FHIR R4 resources
 *   GET    /route-entitlements                          — Route/content entitlement payload
 *   GET    /health                                      — Liveness probe
 *   GET    /ready                                       — Readiness probe
 * </pre>
 *
 * <p>Register the servlet returned by {@link #getServlet()} into the product-level router.
 * This class does not bind a port; binding is handled by the product entry-point.
 *
 * @doc.type class
 * @doc.purpose PHR HTTP server exposing FHIR R4 and operational health endpoints
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 * @author Ghatana PHR Team
 * @since 1.1.0
 */
public final class PhrHttpServer implements KernelLifecycleAware {

    private static final Logger LOG = LoggerFactory.getLogger(PhrHttpServer.class);
    private static final String CONTENT_JSON = "application/json";

    private final FhirController fhirController;
    private final PhrFhirR4Server fhirServer;
    private final BillingService billingService;
    private volatile boolean started = false;

    /**
     * Creates a new PHR HTTP server.
     *
     * @param fhirServer     the underlying FHIR server; must not be null
     * @param fhirController the FHIR controller delegate; must not be null
     * @param billingService the billing service for encounter management; may be null for FHIR-only mode
     */
    public PhrHttpServer(PhrFhirR4Server fhirServer, FhirController fhirController, BillingService billingService) {
        this.fhirServer = Objects.requireNonNull(fhirServer, "fhirServer cannot be null");
        this.fhirController = Objects.requireNonNull(fhirController, "fhirController cannot be null");
        this.billingService = billingService;
    }

    // -------------------------------------------------------------------------
    // KernelLifecycleAware
    // -------------------------------------------------------------------------

    @Override
    public Promise<Void> start() {
        started = true;
        LOG.info("PhrHttpServer started");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        started = false;
        LOG.info("PhrHttpServer stopped");
        return Promise.complete();
    }

    @Override
    public boolean isHealthy() {
        return started && fhirServer.isHealthy();
    }

    @Override
    public String getName() {
        return "phr-http-server";
    }

    // -------------------------------------------------------------------------
    // Servlet factory
    // -------------------------------------------------------------------------

    /**
     * Returns the ActiveJ {@link RoutingServlet} for all PHR API routes.
     *
     * <p>Mount into a parent router at the desired base path, e.g.:
     * <pre>{@code
     *   RoutingServlet root = RoutingServlet.create()
     *       .map("/api/v1/phr/*", phrHttpServer.getServlet());
     * }</pre>
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        RoutingServlet.Builder builder = RoutingServlet.builder(Eventloop.create());
        
        // FHIR endpoints
        builder
            .with(HttpMethod.POST, "/fhir/:resourceType", this::handleCreateFhirResource)
            .with(HttpMethod.GET, "/fhir/:resourceType/:id", this::handleGetFhirResource)
            .with(HttpMethod.GET, "/fhir/:resourceType", this::handleSearchFhirResources)
            .with(HttpMethod.GET, "/route-entitlements", this::handleRouteEntitlements)
            .with(HttpMethod.GET, "/health", this::handleHealth)
            .with(HttpMethod.GET, "/ready", this::handleReady);
        
        return builder.build();
    }

    // -------------------------------------------------------------------------
    // FHIR handlers
    // -------------------------------------------------------------------------

    private Promise<HttpResponse> handleCreateFhirResource(HttpRequest request) {
        String resourceType = request.getPathParameter("resourceType");
        return request.loadBody()
                .then(body -> {
                    String resourceJson = body.getString(StandardCharsets.UTF_8);
                    return fhirController.createResource(resourceType, resourceJson)
                            .map(fhirResponse -> HttpResponse.ofCode(fhirResponse.statusCode())
                                    .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                                    .withJson(fhirResponse.body())
                                    .build());
                });
    }

    private Promise<HttpResponse> handleGetFhirResource(HttpRequest request) {
        String resourceType = request.getPathParameter("resourceType");
        String id = request.getPathParameter("id");
        return fhirController.getResource(resourceType, id)
                .map(fhirResponse -> HttpResponse.ofCode(fhirResponse.statusCode())
                        .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                        .withJson(fhirResponse.body())
                        .build());
    }

    private Promise<HttpResponse> handleSearchFhirResources(HttpRequest request) {
        String resourceType = request.getPathParameter("resourceType");
        Map<String, String> params = request.getQueryParameters();
        return fhirController.searchResources(resourceType, params)
                .map(fhirResponse -> HttpResponse.ofCode(fhirResponse.statusCode())
                        .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                        .withJson(fhirResponse.body())
                        .build());
    }

    private Promise<HttpResponse> handleRouteEntitlements(HttpRequest request) {
        String role = headerOrDefault(request, "X-Role", "patient");
        Map<String, Object> entitlement = Map.of(
            "product", "phr",
            "principalId", headerOrDefault(request, "X-Principal-ID", "anonymous"),
            "tenantId", headerOrDefault(request, "X-Tenant-ID", "default"),
            "role", role,
            "persona", headerOrDefault(request, "X-Persona", "patient"),
            "tier", headerOrDefault(request, "X-Tier", "core"),
            "routes", phrRoutesFor(role),
            "actions", List.of(
                entitledAction("view-patient-summary", "View patient summary", "/dashboard"),
                entitledAction("view-records", "View records", "/records"),
                entitledAction("manage-consent", "Manage consent", "/consents")
            ),
            "cards", List.of(
                entitledCard("patient-summary", "Patient summary", "/dashboard"),
                entitledCard("active-consent-grants", "Active consent grants", "/consents")
            )
        );
        return jsonResponse(200, entitlement);
    }

    // -------------------------------------------------------------------------
    // Operational handlers
    // -------------------------------------------------------------------------

    /**
     * Liveness probe — returns 200 when the server process is running.
     */
    private Promise<HttpResponse> handleHealth(HttpRequest request) {
        boolean healthy = isHealthy();
        int code = healthy ? 200 : 503;
        return jsonResponse(code, Map.of("status", healthy ? "UP" : "DOWN", "service", "phr-http-server"));
    }

    /**
     * Readiness probe — returns 200 only when started and the FHIR server is ready.
     */
    private Promise<HttpResponse> handleReady(HttpRequest request) {
        boolean ready = started && fhirServer.isHealthy();
        int code = ready ? 200 : 503;
        return jsonResponse(code, Map.of("ready", ready, "service", "phr-http-server"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Promise<HttpResponse> jsonResponse(int statusCode, Object body) {
        String json = JsonUtils.toJsonSafe(body);
        if (json == null) {
            json = "{\"error\":\"SERIALIZATION_ERROR\",\"message\":\"Failed to serialize response\"}";
            statusCode = 500;
        }
        return Promise.of(HttpResponse.ofCode(statusCode)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withJson(json)
                .build());
    }

    private static String headerOrDefault(HttpRequest request, String name, String defaultValue) {
        String value = request.getHeader(HttpHeaders.of(name));
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static List<Map<String, Object>> phrRoutesFor(String role) {
        List<Map<String, Object>> patientRoutes = List.of(
            route("/dashboard", "Dashboard", "patient", List.of("view-patient-summary"), List.of("patient-summary")),
            route("/records", "Records", "patient", List.of("view-records"), List.of("record-highlights")),
            route("/consents", "Consents", "patient", List.of("manage-consent"), List.of("active-consent-grants")),
            route("/appointments", "Appointments", "patient", List.of("schedule-visit"), List.of("upcoming-appointments")),
            route("/settings", "Settings", "patient", List.of("manage-profile-settings"), List.of("profile-controls"))
        );
        if ("clinician".equals(role) || "admin".equals(role)) {
            return List.of(
                patientRoutes.get(0),
                patientRoutes.get(1),
                patientRoutes.get(2),
                patientRoutes.get(3),
                route("/labs", "Labs", "caregiver", List.of("review-lab-results"), List.of("recent-lab-results")),
                route("/medications", "Medications", "caregiver", List.of("review-medications"), List.of("medication-adherence")),
                route("/emergency", "Emergency", "clinician", List.of("break-glass-review"), List.of("override-audit-timeline")),
                patientRoutes.get(4)
            );
        }
        if ("caregiver".equals(role)) {
            return List.of(
                patientRoutes.get(0),
                patientRoutes.get(1),
                patientRoutes.get(2),
                patientRoutes.get(3),
                route("/labs", "Labs", "caregiver", List.of("review-lab-results"), List.of("recent-lab-results")),
                route("/medications", "Medications", "caregiver", List.of("review-medications"), List.of("medication-adherence")),
                patientRoutes.get(4)
            );
        }
        return patientRoutes;
    }

    private static Map<String, Object> route(
            String path,
            String label,
            String minimumRole,
            List<String> actions,
            List<String> cards) {
        return Map.of(
            "path", path,
            "label", label,
            "minimumRole", minimumRole,
            "personas", List.of("patient", "caregiver", "clinician", "admin"),
            "tiers", List.of("core"),
            "actions", actions,
            "cards", cards
        );
    }

    private static Map<String, Object> entitledAction(String id, String label, String routePath) {
        return Map.of("id", id, "label", label, "routePath", routePath);
    }

    private static Map<String, Object> entitledCard(String id, String title, String routePath) {
        return Map.of("id", id, "title", title, "routePath", routePath, "surface", "dashboard");
    }
}
