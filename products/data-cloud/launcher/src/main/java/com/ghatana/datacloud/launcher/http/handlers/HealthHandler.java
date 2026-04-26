package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.http.HttpRequest;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

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
    private final Map<String, Supplier<Map<String, Object>>> subsystemSuppliers;
    private final MetricsCollector metricsCollector;

    /** SLO targets (informational — not dynamically enforced here, used for dashboard tooling). */
    private static final double SLO_AVAILABILITY_TARGET   = 0.999;   // 99.9 %
    private static final long   SLO_P99_LATENCY_TARGET_MS = 500;     // 500 ms p99
    private static final long   SLO_ERROR_RATE_THRESHOLD  = 1;       // 1 % error rate ceiling

    public HealthHandler(HttpHandlerSupport httpSupport) {
        this(httpSupport, Map.of(), null);
    }

    public HealthHandler(HttpHandlerSupport httpSupport,
                         Map<String, Supplier<Map<String, Object>>> subsystemSuppliers) {
        this(httpSupport, subsystemSuppliers, null);
    }

    public HealthHandler(HttpHandlerSupport httpSupport,
                         Map<String, Supplier<Map<String, Object>>> subsystemSuppliers,
                         MetricsCollector metricsCollector) {
        this.httpSupport = httpSupport;
        Objects.requireNonNull(subsystemSuppliers, "subsystemSuppliers must not be null");
        this.subsystemSuppliers = Map.copyOf(subsystemSuppliers);
        this.metricsCollector = metricsCollector;
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
        Map<String, Object> subsystems = buildSubsystemSnapshot();

        return Promise.of(buildHealthDetailResponse(subsystems));
    }

    public Promise<HttpResponse> handleHealthDeep(HttpRequest request) {
        Map<String, Object> subsystems = normalizeDeepSubsystemSnapshot(buildSubsystemSnapshot());

        return Promise.of(buildHealthDetailResponse(subsystems));
    }

    private HttpResponse buildHealthDetailResponse(Map<String, Object> subsystems) {

        Map<String, Object> sloTargets = Map.of(
            "availability_pct",       SLO_AVAILABILITY_TARGET * 100,
            "p99_latency_target_ms",  SLO_P99_LATENCY_TARGET_MS,
            "error_rate_ceiling_pct", SLO_ERROR_RATE_THRESHOLD
        );

        // ── Assemble response ─────────────────────────────────────────────────
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status",      deriveOverallStatus(subsystems));
        body.put("timestamp",   Instant.now().toString());
        body.put("service",     "datacloud");
        body.put("slo_targets", sloTargets);
        body.put("subsystems",  subsystems);

        return httpSupport.jsonResponse(body);
    }

    private Map<String, Object> buildSubsystemSnapshot() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemMb = runtime.totalMemory() / (1024 * 1024);
        long freeMemMb = runtime.freeMemory() / (1024 * 1024);
        long usedMemMb = totalMemMb - freeMemMb;
        long maxMemMb = runtime.maxMemory() / (1024 * 1024);
        int processors = runtime.availableProcessors();
        double memUsagePct = maxMemMb > 0 ? (double) usedMemMb / maxMemMb : 0.0;

        Map<String, Object> subsystems = new LinkedHashMap<>();

        Map<String, Object> jvmSubsystem = new LinkedHashMap<>();
        jvmSubsystem.put("status", memUsagePct < 0.85 ? "UP" : "DEGRADED");
        jvmSubsystem.put("used_memory_mb", usedMemMb);
        jvmSubsystem.put("total_memory_mb", totalMemMb);
        jvmSubsystem.put("max_memory_mb", maxMemMb);
        jvmSubsystem.put("memory_usage_pct", Math.round(memUsagePct * 1000.0) / 10.0);
        jvmSubsystem.put("processors", processors);
        subsystems.put("jvm", jvmSubsystem);

        Map<String, Object> threadSubsystem = new LinkedHashMap<>();
        threadSubsystem.put("status", Thread.activeCount() < 500 ? "UP" : "DEGRADED");
        threadSubsystem.put("active_threads", Thread.activeCount());
        subsystems.put("threads", threadSubsystem);

        subsystems.put("ai_inference", notConfiguredSubsystem());
        subsystems.put("database", notConfiguredSubsystem());
        subsystems.put("event_store", notConfiguredSubsystem());
        subsystems.put("voice_gateway", notConfiguredSubsystem());
        subsystems.put("audit_service", notConfiguredSubsystem());
        subsystems.put("policy_engine", notConfiguredSubsystem());

        for (Map.Entry<String, Supplier<Map<String, Object>>> entry : subsystemSuppliers.entrySet()) {
            subsystems.put(entry.getKey(), safeSubsystemSnapshot(entry.getKey(), entry.getValue()));
        }

        return subsystems;
    }

    private Map<String, Object> normalizeDeepSubsystemSnapshot(Map<String, Object> subsystems) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : subsystems.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> subsystem && "NOT_CONFIGURED".equals(subsystem.get("status"))) {
                Map<String, Object> enriched = new LinkedHashMap<>();
                subsystem.forEach((key, nestedValue) -> enriched.put(String.valueOf(key), nestedValue));
                enriched.put("status", "UNKNOWN");
                normalized.put(entry.getKey(), Collections.unmodifiableMap(enriched));
                continue;
            }
            normalized.put(entry.getKey(), value);
        }
        return normalized;
    }

    private Map<String, Object> safeSubsystemSnapshot(String name,
                                                      Supplier<Map<String, Object>> supplier) {
        long startedAtNanos = System.nanoTime();
        try {
            Map<String, Object> snapshot = supplier.get();
            if (snapshot == null || snapshot.isEmpty()) {
                return Map.of(
                    "status", "UNKNOWN",
                    "note", "empty-health-snapshot",
                    "response_time_ms", elapsedMillis(startedAtNanos)
                );
            }
            Map<String, Object> enrichedSnapshot = new LinkedHashMap<>(snapshot);
            enrichedSnapshot.putIfAbsent("response_time_ms", elapsedMillis(startedAtNanos));
            return Collections.unmodifiableMap(enrichedSnapshot);
        } catch (RuntimeException exception) {
            return Map.of(
                "status", "DOWN",
                "error", exception.getClass().getSimpleName(),
                "message", exception.getMessage() == null ? (name + " probe failed") : exception.getMessage(),
                "response_time_ms", elapsedMillis(startedAtNanos)
            );
        }
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private Map<String, Object> notConfiguredSubsystem() {
        return Map.of("status", "NOT_CONFIGURED", "note", "dependency-not-configured");
    }

    private String deriveOverallStatus(Map<String, Object> subsystems) {
        boolean down = false;
        boolean degraded = false;

        for (Object value : subsystems.values()) {
            if (!(value instanceof Map<?, ?> subsystem)) {
                continue;
            }
            Object status = subsystem.get("status");
            if ("DOWN".equals(status)) {
                down = true;
            } else if ("DEGRADED".equals(status)) {
                degraded = true;
            }
        }

        if (down) {
            return "DOWN";
        }
        if (degraded) {
            return "DEGRADED";
        }
        return "UP";
    }

    public Promise<HttpResponse> handleReady(HttpRequest request) {
        Map<String, Object> subsystems = buildSubsystemSnapshot();
        boolean criticalDown = isCriticalSubsystemDown(subsystems);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", criticalDown ? "NOT_READY" : "READY");
        body.put("timestamp", Instant.now().toString());
        if (criticalDown) {
            body.put("message", "Critical dependencies are not ready");
        }
        body.put("subsystems", subsystems);
        HttpResponse response = criticalDown
                ? httpSupport.jsonResponse(503, body)
                : httpSupport.jsonResponse(body);
        return Promise.of(response);
    }

    private boolean isCriticalSubsystemDown(Map<String, Object> subsystems) {
        return isNotReady(subsystems.get("database")) || isNotReady(subsystems.get("event_store"));
    }

    private boolean isNotReady(Object subsystem) {
        if (!(subsystem instanceof Map<?, ?> values)) {
            return false;
        }
        String status = String.valueOf(values.get("status"));
        return "DOWN".equals(status) || "NOT_CONFIGURED".equals(status);
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
        MeterRegistry registry = metricsCollector != null ? metricsCollector.getMeterRegistry() : null;
        if (registry instanceof PrometheusMeterRegistry prometheusRegistry) {
            String requestId = httpSupport.resolveCorrelationId(request);
            return Promise.of(HttpResponse.ok200()
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("text/plain; version=0.0.4; charset=utf-8"))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(httpSupport.corsAllowOrigin()))
                .withHeader(HttpHeaders.of("X-Request-Id"), HttpHeaderValue.of(requestId))
                .withBody(prometheusRegistry.scrape().getBytes(StandardCharsets.UTF_8))
                .build());
        }

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
