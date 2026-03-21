/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.server.http.HttpHelper;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;

/**
 * Health check controller for liveness and readiness probes.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /health - Liveness probe</li>
 *   <li>GET /ready - Readiness probe</li>
 *   <li>GET /live - Alternative health check</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Kubernetes health probes
 * @doc.layer product
 */
public class HealthController implements AepController {

    private volatile boolean ready = false;
    private final String version;

    public HealthController(String version) {
        this.version = version != null ? version : "unknown";
    }

    @Override
    public String getBasePath() {
        return "/";
    }

    @Override
    public Promise<HttpResponse> handle(HttpRequest request, String path) throws Exception {
        if (request.getMethod() != HttpMethod.GET) {
            return Promise.of(HttpHelper.errorResponse(405, "Method not allowed"));
        }

        if ("health".equals(path)) return handleHealth();
        if ("ready".equals(path)) return handleReady();
        if ("live".equals(path)) return handleLive();
        return Promise.of(HttpHelper.errorResponse(404, "Not found"));
    }

    private Promise<HttpResponse> handleHealth() {
        return Promise.of(HttpHelper.jsonResponse(Map.of(
            "status", "healthy",
            "version", version,
            "timestamp", Instant.now().toString()
        )));
    }

    private Promise<HttpResponse> handleReady() {
        if (!ready) {
            return Promise.of(HttpHelper.jsonResponse(Map.of(
                "ready", false,
                "reason", "Initializing",
                "timestamp", Instant.now().toString()
            )));
        }
        return Promise.of(HttpHelper.jsonResponse(Map.of(
            "ready", true,
            "timestamp", Instant.now().toString()
        )));
    }

    private Promise<HttpResponse> handleLive() {
        return handleHealth();
    }

    /**
     * Mark the service as ready (called after initialization).
     */
    public void markReady() {
        this.ready = true;
    }

    /**
     * Mark the service as not ready (called during shutdown).
     */
    public void markNotReady() {
        this.ready = false;
    }
}
