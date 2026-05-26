package com.ghatana.phr.api.routes;

import com.ghatana.phr.fhir.server.PhrFhirR4Server;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Supplier;
import com.ghatana.platform.health.HealthStatus;

/**
 * Health check routes for the PHR product.
 * <p>
 * Handles liveness and readiness probes for the PHR HTTP server.
 * </p>
 *
 * @doc.type class
 * @doc.purpose Health check handlers for PHR
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrHealthRoutes {

    private static final Logger LOG = LoggerFactory.getLogger(PhrHealthRoutes.class);
    private static final String CONTENT_JSON = "application/json";

    private final Eventloop eventloop;
    private final PhrFhirR4Server fhirServer;
    private final Supplier<HealthStatus> evidenceHealthSupplier;
    private volatile boolean started = false;

    public PhrHealthRoutes(Eventloop eventloop, PhrFhirR4Server fhirServer) {
        this(eventloop, fhirServer, () -> HealthStatus.healthy("No regulated evidence outbox configured"));
    }

    public PhrHealthRoutes(Eventloop eventloop, PhrFhirR4Server fhirServer, Supplier<HealthStatus> evidenceHealthSupplier) {
        this.eventloop = eventloop;
        this.fhirServer = fhirServer;
        this.evidenceHealthSupplier = evidenceHealthSupplier;
    }

    /**
     * Returns the routing servlet for health endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/", this::handleHealth)
            .build();
    }

    /**
     * Returns the servlet for the ready endpoint.
     *
     * @return servlet; never null
     */
    public AsyncServlet getReadyServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/", this::handleReady)
            .build();
    }

    /**
     * Liveness probe — returns 200 when the server process is running.
     */
    private Promise<HttpResponse> handleHealth(HttpRequest request) {
        boolean healthy = isHealthy();
        int code = healthy ? 200 : 503;
        HealthStatus evidenceHealth = evidenceHealthSupplier.get();
        return jsonResponse(code, Map.of(
            "status", healthy ? "UP" : "DOWN",
            "service", "phr-http-server",
            "evidenceOutbox", evidenceHealth.getStatus().name(),
            "evidenceOutboxDetails", evidenceHealth.getDetails()
        ));
    }

    /**
     * Readiness probe — returns 200 only when started and the FHIR server is ready.
     */
    private Promise<HttpResponse> handleReady(HttpRequest request) {
        HealthStatus evidenceHealth = evidenceHealthSupplier.get();
        boolean ready = started && fhirServer.isHealthy() && !evidenceHealth.isUnhealthy();
        int code = ready ? 200 : 503;
        return jsonResponse(code, Map.of(
            "ready", ready,
            "service", "phr-http-server",
            "evidenceOutbox", evidenceHealth.getStatus().name(),
            "evidenceOutboxDetails", evidenceHealth.getDetails()
        ));
    }

    /**
     * Sets the started state.
     */
    public void setStarted(boolean started) {
        this.started = started;
    }

    /**
     * Checks if the service is healthy.
     */
    public boolean isHealthy() {
        return started && fhirServer.isHealthy() && !evidenceHealthSupplier.get().isUnhealthy();
    }

    private static Promise<HttpResponse> jsonResponse(int statusCode, Object body) {
        String json = com.ghatana.platform.core.util.JsonUtils.toJsonSafe(body);
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
