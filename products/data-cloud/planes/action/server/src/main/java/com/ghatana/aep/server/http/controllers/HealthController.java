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
import io.activej.promise.Promises;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
    private volatile Supplier<Map<String, Object>> deepResponseMetadataSupplier = Map::of;
    /**
     * Required dependency names: when any of these are not "ok" or "disabled" in production
     * mode the readiness probe returns 503 rather than 200.
     */
    private final List<String> requiredDependencies = new CopyOnWriteArrayList<>();
    /** Synchronous dependency-status checks registered at startup. */
    private final List<Map.Entry<String, Supplier<String>>> componentChecks = new CopyOnWriteArrayList<>();
    /** Deeper startup/runtime checks used by {@code /health/deep}. */
    private final List<Map.Entry<String, Supplier<String>>> deepComponentChecks = new CopyOnWriteArrayList<>();
    /** Asynchronous deep dependency checks used for runtime connectivity verification. */
    private final List<Map.Entry<String, Supplier<Promise<String>>>> asyncDeepComponentChecks = new CopyOnWriteArrayList<>();

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

    /**
     * Marks a dependency as required for readiness. If this dependency is not "ok" or "disabled"
     * the readiness probe returns 503 in production mode.
     *
     * @param name dependency name (must already be registered via {@link #addDependencyCheck})
     */
    public void requireForReadiness(String name) {
        requiredDependencies.add(name);
    }

    /**
     * Registers a deeper dependency probe exposed only through {@code /health/deep}.
     *
     * <p>Deep checks may inspect durable-storage backing services or configuration shape.
     * They still must remain fast and non-blocking because they execute on the request path.
     *
     * @param name  component label
     * @param check status supplier — returns "ok" or a short status description
     */
    public void addDeepDependencyCheck(String name, Supplier<String> check) {
        deepComponentChecks.add(Map.entry(name, check));
    }

    /**
     * Registers an asynchronous deep dependency probe exposed only through {@code /health/deep}.
     *
     * <p>Use this for lightweight connectivity probes that complete asynchronously
     * without blocking the request thread.
     */
    public void addAsyncDeepDependencyCheck(String name, Supplier<Promise<String>> check) {
        asyncDeepComponentChecks.add(Map.entry(name, check));
    }

    /**
     * Registers additional structured metadata to include only in {@code /health/deep} responses.
     */
    public void setDeepResponseMetadataSupplier(Supplier<Map<String, Object>> metadataSupplier) {
        this.deepResponseMetadataSupplier = metadataSupplier != null ? metadataSupplier : Map::of;
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
        if ("health/deep".equals(path)) return handleDeepHealth();
        if ("ready".equals(path)) return handleReady();
        if ("live".equals(path)) return handleLive();
        return Promise.of(HttpHelper.errorResponse(404, "Not found"));
    }

    private Promise<HttpResponse> handleHealth() {
        return Promise.of(buildHealthResponse(evaluateChecks(componentChecks), "shallow"));
    }

    private Promise<HttpResponse> handleDeepHealth() {
        List<Map.Entry<String, Supplier<String>>> checks = new ArrayList<>(componentChecks);
        checks.addAll(deepComponentChecks);
        Map<String, Object> components = evaluateChecks(checks);
        Map<String, Object> responseMetadata = evaluateDeepResponseMetadata();
        if (asyncDeepComponentChecks.isEmpty()) {
            return Promise.of(buildHealthResponse(components, "deep", responseMetadata));
        }

        List<Promise<Map.Entry<String, String>>> asyncChecks = asyncDeepComponentChecks.stream()
            .map(this::evaluateAsyncCheck)
            .toList();

        return Promises.toList(asyncChecks)
            .map(results -> {
                for (Map.Entry<String, String> entry : results) {
                    components.put(entry.getKey(), entry.getValue());
                }
                return buildHealthResponse(components, "deep", responseMetadata);
            });
    }

    private Promise<HttpResponse> handleReady() {
        if (!ready) {
            return Promise.of(HttpHelper.jsonResponse(Map.of(
                "ready", false,
                "reason", "Initializing",
                "timestamp", Instant.now().toString()
            )));
        }

        // T-04: evaluate required dependency checks to determine true readiness
        Map<String, Object> components = evaluateChecks(componentChecks);
        List<String> degraded = requiredDependencies.stream()
            .filter(name -> {
                Object status = components.get(name);
                return !("ok".equals(status) || "disabled".equals(status));
            })
            .collect(Collectors.toList());

        boolean productionMode = isProductionMode();
        if (!degraded.isEmpty() && productionMode) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("ready", false);
            response.put("reason", "Required dependencies are degraded");
            response.put("degradedDependencies", degraded);
            response.put("timestamp", Instant.now().toString());
            return Promise.of(HttpHelper.jsonResponse(503, response));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ready", true);
        if (!degraded.isEmpty()) {
            response.put("degraded", true);
            response.put("degradedDependencies", degraded);
        }
        response.put("timestamp", Instant.now().toString());
        return Promise.of(HttpHelper.jsonResponse(response));
    }

    private static boolean isProductionMode() {
        String profile = System.getenv("AEP_PROFILE");
        if (profile == null || profile.isBlank()) {
            profile = System.getProperty("AEP_PROFILE", "");
        }
        return "production".equalsIgnoreCase(profile.trim());
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

    private Map<String, Object> evaluateChecks(List<Map.Entry<String, Supplier<String>>> checks) {
        Map<String, Object> components = new LinkedHashMap<>();
        for (Map.Entry<String, Supplier<String>> entry : checks) {
            try {
                String status = entry.getValue().get();
                components.put(entry.getKey(), status);
            } catch (Exception e) {
                components.put(entry.getKey(), errorStatus(e));
            }
        }

        return components;
    }

    private Promise<Map.Entry<String, String>> evaluateAsyncCheck(Map.Entry<String, Supplier<Promise<String>>> entry) {
        try {
            Promise<String> promise = entry.getValue().get();
            if (promise == null) {
                return Promise.of(Map.entry(entry.getKey(), "error: null health promise"));
            }
            return promise
                .map(status -> Map.entry(entry.getKey(), status))
                .then(Promise::of, error -> Promise.of(Map.entry(entry.getKey(), errorStatus(error))));
        } catch (Exception error) {
            return Promise.of(Map.entry(entry.getKey(), errorStatus(error)));
        }
    }

    private HttpResponse buildHealthResponse(Map<String, Object> components, String probeType) {
        return buildHealthResponse(components, probeType, Map.of());
    }

    private HttpResponse buildHealthResponse(
            Map<String, Object> components,
            String probeType,
            Map<String, Object> responseMetadata) {
        boolean allHealthy = components.values().stream().allMatch("ok"::equals);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", allHealthy ? "healthy" : "degraded");
        response.put("probe", probeType);
        response.put("version", version);
        response.put("timestamp", Instant.now().toString());
        if (!responseMetadata.isEmpty()) {
            response.putAll(responseMetadata);
        }
        if (!components.isEmpty()) {
            response.put("components", components);
        }
        return HttpHelper.jsonResponse(response);
    }

    private Map<String, Object> evaluateDeepResponseMetadata() {
        try {
            Map<String, Object> metadata = deepResponseMetadataSupplier.get();
            return metadata != null ? metadata : Map.of();
        } catch (Exception error) {
            return Map.of("deepProbeMetadataError", errorStatus(error));
        }
    }

    private String errorStatus(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "error" : "error: " + message;
    }
}
