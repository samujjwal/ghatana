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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

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
 * @doc.purpose Kubernetes health probes with dependency-status aggregation
 * @doc.layer product
 * @doc.pattern HealthCheck
 */
public class HealthController implements AepController {

    private volatile boolean ready = false;
    private final String version;
    /** Synchronous dependency-status checks registered at startup. */
    private final List<Map.Entry<String, Supplier<String>>> componentChecks = new CopyOnWriteArrayList<>();

    public HealthController(String version) {
        this.version = version != null ? version : "unknown";
    }

    /**
     * Registers a lightweight, synchronous dependency check displayed in {@code /health} responses.
     *
     * <p>The supplier is called on every health request. It must be fast and non-blocking.
     * Return {@code "ok"} for a healthy component; any other string marks the overall
     * status as {@code "degraded"}.
     *
     * @param name  component label (e.g. "data-cloud", "review-queue")
     * @param check status supplier — returns "ok" or a short status description
     */
    public void addDependencyCheck(String name, Supplier<String> check) {
        componentChecks.add(Map.entry(name, check));
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
        Map<String, Object> components = new LinkedHashMap<>();
        boolean allHealthy = true;
        for (Map.Entry<String, Supplier<String>> entry : componentChecks) {
            try {
                String status = entry.getValue().get();
                components.put(entry.getKey(), status);
                if (!"ok".equals(status)) allHealthy = false;
            } catch (Exception e) {
                components.put(entry.getKey(), "error: " + e.getMessage());
                allHealthy = false;
            }
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", allHealthy ? "healthy" : "degraded");
        response.put("version", version);
        response.put("timestamp", Instant.now().toString());
        if (!components.isEmpty()) {
            response.put("components", components);
        }
        return Promise.of(HttpHelper.jsonResponse(response));
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
