package com.ghatana.datacloud.launcher.http.handlers;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;

/**
 * HTTP handler for health, readiness, liveness, info, and metrics endpoints.
 *
 * <p>Extracted from {@code DataCloudHttpServer} (Phase 7, P7-2b) to enforce
 * transport-only composition — the server class wires routes, handlers own logic.
 *
 * @doc.type class
 * @doc.purpose Health and operational status HTTP handler
 * @doc.layer product
 * @doc.pattern Handler
 */
public class HealthHandler {

    private final HttpHandlerSupport httpSupport;

    public HealthHandler(HttpHandlerSupport httpSupport) {
        this.httpSupport = httpSupport;
    }

    public Promise<HttpResponse> handleHealth(HttpRequest request) {
        return Promise.of(httpSupport.jsonResponse(Map.of(
            "status", "UP",
            "timestamp", Instant.now().toString(),
            "service", "datacloud"
        )));
    }

    public Promise<HttpResponse> handleReady(HttpRequest request) {
        return Promise.of(httpSupport.jsonResponse(Map.of(
            "status", "READY",
            "timestamp", Instant.now().toString()
        )));
    }

    public Promise<HttpResponse> handleLive(HttpRequest request) {
        return Promise.of(httpSupport.jsonResponse(Map.of(
            "status", "LIVE",
            "timestamp", Instant.now().toString()
        )));
    }

    public Promise<HttpResponse> handleInfo(HttpRequest request) {
        return Promise.of(httpSupport.jsonResponse(Map.of(
            "service", "Data-Cloud",
            "version", "1.0.0-SNAPSHOT",
            "description", "Unified Data Platform",
            "timestamp", Instant.now().toString()
        )));
    }

    public Promise<HttpResponse> handleMetrics(HttpRequest request) {
        return Promise.of(httpSupport.jsonResponse(Map.of(
            "service", "datacloud",
            "uptime_seconds", System.currentTimeMillis() / 1000,
            "memory_used_mb", Runtime.getRuntime().totalMemory() / (1024 * 1024),
            "memory_free_mb", Runtime.getRuntime().freeMemory() / (1024 * 1024),
            "processors", Runtime.getRuntime().availableProcessors(),
            "timestamp", Instant.now().toString()
        )));
    }
}
