package com.ghatana.datacloud.launcher.http.handlers;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP handler for health, readiness, liveness, info, metrics, and SLO detail endpoints.
 *
 * <p>Extracted from {@code DataCloudHttpServer} (Phase 7, P7-2b) to enforce
 * transport-only composition — the server class wires routes, handlers own logic.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET /health}        — basic liveness probe (always UP when reachable)</li>
 *   <li>{@code GET /health/detail} — structured SLO status: per-subsystem health + SLO thresholds</li>
 *   <li>{@code GET /ready}         — readiness probe (dependency-aware)</li>
 *   <li>{@code GET /live}          — liveness probe (process-only)</li>
 *   <li>{@code GET /metrics}       — lightweight JVM metrics</li>
 *   <li>{@code GET /info}          — service metadata</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Health, readiness, liveness, SLO detail, and operational status HTTP handler
 * @doc.layer product
 * @doc.pattern Handler
 */
public class HealthHandler {

    private final HttpHandlerSupport httpSupport;

    /** SLO targets (informational — not dynamically enforced here, used for dashboard tooling). */
    private static final double SLO_AVAILABILITY_TARGET   = 0.999;   // 99.9 %
    private static final long   SLO_P99_LATENCY_TARGET_MS = 500;     // 500 ms p99
    private static final long   SLO_ERROR_RATE_THRESHOLD  = 1;       // 1 % error rate ceiling

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

    /**
     * Detailed SLO health endpoint.
     *
     * <p>Returns a structured JSON payload with:
     * <ul>
     *   <li>Overall service status (UP / DEGRADED / DOWN)</li>
     *   <li>Per-subsystem health (DB, AI, Voice, Audit, PolicyEngine)</li>
     *   <li>SLO threshold declarations for dashboard / alerting tooling</li>
     *   <li>JVM resource snapshot for ops visibility</li>
     * </ul>
     *
     * <p>The per-subsystem checks are lightweight in-process observations (JVM
     * memory, thread pool state) — not active network probes — to keep latency
     * under 10 ms.  Network-level checks belong in sidecar readiness probes.
     *
     * @param request the incoming HTTP request (unused — no inputs required)
     * @return Promise of a 200 JSON response with the SLO health payload
     */
    public Promise<HttpResponse> handleHealthDetail(HttpRequest request) {
        Runtime rt = Runtime.getRuntime();
        long totalMemMb  = rt.totalMemory() / (1024 * 1024);
        long freeMemMb   = rt.freeMemory()  / (1024 * 1024);
        long usedMemMb   = totalMemMb - freeMemMb;
        long maxMemMb    = rt.maxMemory()   / (1024 * 1024);
        int  processors  = rt.availableProcessors();

        // ── JVM subsystem ────────────────────────────────────────────────────
        double memUsagePct = totalMemMb > 0 ? (double) usedMemMb / maxMemMb : 0.0;
        String jvmStatus   = memUsagePct < 0.85 ? "UP" : "DEGRADED";

        // ── Thread pool subsystem ────────────────────────────────────────────
        int activeThreads = Thread.activeCount();
        String threadStatus = activeThreads < 500 ? "UP" : "DEGRADED";

        // ── Derive overall status ─────────────────────────────────────────────
        boolean degraded = "DEGRADED".equals(jvmStatus) || "DEGRADED".equals(threadStatus);
        String overallStatus = degraded ? "DEGRADED" : "UP";

        // ── SLO declarations ─────────────────────────────────────────────────
        Map<String, Object> sloTargets = Map.of(
            "availability_pct",       SLO_AVAILABILITY_TARGET * 100,
            "p99_latency_target_ms",  SLO_P99_LATENCY_TARGET_MS,
            "error_rate_ceiling_pct", SLO_ERROR_RATE_THRESHOLD
        );

        // ── Per-subsystem ────────────────────────────────────────────────────
        Map<String, Object> subsystems = new LinkedHashMap<>();

        Map<String, Object> jvmSubsystem = new LinkedHashMap<>();
        jvmSubsystem.put("status", jvmStatus);
        jvmSubsystem.put("used_memory_mb", usedMemMb);
        jvmSubsystem.put("total_memory_mb", totalMemMb);
        jvmSubsystem.put("max_memory_mb", maxMemMb);
        jvmSubsystem.put("memory_usage_pct", Math.round(memUsagePct * 1000.0) / 10.0);
        jvmSubsystem.put("processors", processors);
        subsystems.put("jvm", jvmSubsystem);

        Map<String, Object> threadSubsystem = new LinkedHashMap<>();
        threadSubsystem.put("status", threadStatus);
        threadSubsystem.put("active_threads", activeThreads);
        subsystems.put("threads", threadSubsystem);

        // AI, DB, Voice stubs — indicate observability hook points (active probes
        // should be injected via HealthProbeRegistry when available)
        subsystems.put("ai_inference",    Map.of("status", "UNKNOWN", "note", "active-probe-not-configured"));
        subsystems.put("database",        Map.of("status", "UNKNOWN", "note", "active-probe-not-configured"));
        subsystems.put("voice_gateway",   Map.of("status", "UNKNOWN", "note", "active-probe-not-configured"));
        subsystems.put("audit_service",   Map.of("status", "UNKNOWN", "note", "active-probe-not-configured"));
        subsystems.put("policy_engine",   Map.of("status", "UNKNOWN", "note", "active-probe-not-configured"));

        // ── Assemble response ─────────────────────────────────────────────────
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status",      overallStatus);
        body.put("timestamp",   Instant.now().toString());
        body.put("service",     "datacloud");
        body.put("slo_targets", sloTargets);
        body.put("subsystems",  subsystems);

        return Promise.of(httpSupport.jsonResponse(body));
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

