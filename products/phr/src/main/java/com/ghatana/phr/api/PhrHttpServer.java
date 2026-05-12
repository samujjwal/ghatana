/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.phr.api;

import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.phr.api.routes.PhrEntitlementRoutes;
import com.ghatana.phr.api.routes.PhrFhirRoutes;
import com.ghatana.phr.api.routes.PhrHealthRoutes;
import com.ghatana.phr.fhir.server.PhrFhirR4Server;
import com.ghatana.phr.kernel.service.BillingService;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final Eventloop eventloop;
    private final PhrFhirRoutes fhirRoutes;
    private final PhrEntitlementRoutes entitlementRoutes;
    private final PhrHealthRoutes healthRoutes;
    private volatile boolean started = false;

    /**
     * Creates a new PHR HTTP server.
     *
     * @param eventloop          the eventloop; must not be null
     * @param fhirRoutes          the FHIR route handlers; must not be null
     * @param entitlementRoutes    the entitlement route handlers; must not be null
     * @param healthRoutes         the health check route handlers; must not be null
     */
    public PhrHttpServer(Eventloop eventloop, PhrFhirRoutes fhirRoutes, PhrEntitlementRoutes entitlementRoutes, PhrHealthRoutes healthRoutes) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop cannot be null");
        this.fhirRoutes = Objects.requireNonNull(fhirRoutes, "fhirRoutes cannot be null");
        this.entitlementRoutes = Objects.requireNonNull(entitlementRoutes, "entitlementRoutes cannot be null");
        this.healthRoutes = Objects.requireNonNull(healthRoutes, "healthRoutes cannot be null");
    }

    // -------------------------------------------------------------------------
    // KernelLifecycleAware
    // -------------------------------------------------------------------------

    @Override
    public Promise<Void> start() {
        started = true;
        healthRoutes.setStarted(true);
        LOG.info("PhrHttpServer started");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        started = false;
        healthRoutes.setStarted(false);
        LOG.info("PhrHttpServer stopped");
        return Promise.complete();
    }

    @Override
    public boolean isHealthy() {
        return healthRoutes.isHealthy();
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
        return RoutingServlet.builder(eventloop)
            .with("/fhir/*", fhirRoutes.getServlet())
            .with("/route-entitlements", entitlementRoutes.getServlet())
            .with("/health", healthRoutes.getServlet())
            .with("/ready", healthRoutes.getReadyServlet())
            .build();
    }
}
