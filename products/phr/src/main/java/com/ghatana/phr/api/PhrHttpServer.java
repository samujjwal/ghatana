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
}
