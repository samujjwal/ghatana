/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.platform.observability.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Standardised HTTP-layer metrics for all Data-Cloud handler classes.
 *
 * <p>Centralises metric names and tag conventions so every handler emits
 * consistently named counters and timers.  Eliminates per-handler ad-hoc
 * metric strings that were previously inconsistent or missing.
 *
 * <h2>Metric Names</h2>
 * <pre>
 *   dc.http.requests        [handler, method, status]     — Counter, every request
 *   dc.http.request.latency [handler, method]             — Timer   (ms)
 *   dc.http.errors          [handler, method, error_type] — Counter, 4xx/5xx only
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * long start = System.currentTimeMillis();
 * try {
 *     HttpResponse resp = handler.handle(request);
 *     metrics.recordRequest("EntityCrudHandler", "createEntity", resp.getCode());
 *     metrics.recordLatency("EntityCrudHandler", "createEntity",
 *                            System.currentTimeMillis() - start);
 *     return resp;
 * } catch (Exception e) {
 *     metrics.recordError("EntityCrudHandler", "createEntity", e.getClass().getSimpleName());
 *     throw e;
 * }
 * }</pre>
 *
 * <h2>Safety</h2>
 * All methods are null-safe and never throw — metrics must not affect the
 * business response path.
 *
 * @doc.type class
 * @doc.purpose Standardised HTTP handler metrics (DC-010)
 * @doc.layer product
 * @doc.pattern Service, Facade
 */
public final class DataCloudHttpMetrics {

    private static final Logger log = LoggerFactory.getLogger(DataCloudHttpMetrics.class);

    // ── Metric name constants ─────────────────────────────────────────────────

    public static final String METRIC_REQUESTS      = "dc.http.requests";
    public static final String METRIC_LATENCY       = "dc.http.request.latency";
    public static final String METRIC_ERRORS        = "dc.http.errors";

    // Tag key constants
    public static final String TAG_HANDLER    = "handler";
    public static final String TAG_OPERATION  = "operation";
    public static final String TAG_STATUS     = "status";
    public static final String TAG_TENANT     = "tenant";
    public static final String TAG_ERROR_TYPE = "error_type";

    /** Noop instance used when MetricsCollector is absent (test / minimal deployments). */
    private static final DataCloudHttpMetrics NOOP = new DataCloudHttpMetrics(null);

    private final MetricsCollector metrics;

    /**
     * Creates a metrics helper backed by the supplied collector.
     *
     * @param metrics platform metrics collector; may be {@code null} — the
     *                instance degrades to a no-op in that case
     */
    public DataCloudHttpMetrics(MetricsCollector metrics) {
        this.metrics = metrics;
    }

    /**
     * Returns a no-op instance for test environments without a metrics
     * collector.
     */
    public static DataCloudHttpMetrics noop() {
        return NOOP;
    }

    /**
     * Records a completed request with its HTTP status code.
     *
     * @param handler   simple class name of the handler (e.g. "EntityCrudHandler")
     * @param operation method name / operation label (e.g. "createEntity")
     * @param tenantId  tenant identifier
     * @param status    HTTP status code
     */
    public void recordRequest(String handler, String operation,
                              String tenantId, int status) {
        if (metrics == null) return;
        try {
            metrics.incrementCounter(METRIC_REQUESTS,
                TAG_HANDLER,   nullSafe(handler),
                TAG_OPERATION, nullSafe(operation),
                TAG_TENANT,    nullSafe(tenantId),
                TAG_STATUS,    String.valueOf(status));
        } catch (Exception e) {
            log.debug("DataCloudHttpMetrics.recordRequest failed silently: {}", e.getMessage());
        }
    }

    /**
     * Records end-to-end handler latency in milliseconds.
     *
     * @param handler    simple class name of the handler
     * @param operation  method name / operation label
     * @param latencyMs  latency in milliseconds
     */
    public void recordLatency(String handler, String operation, long latencyMs) {
        if (metrics == null) return;
        try {
            metrics.recordTimer(METRIC_LATENCY, latencyMs,
                TAG_HANDLER,   nullSafe(handler),
                TAG_OPERATION, nullSafe(operation));
        } catch (Exception e) {
            log.debug("DataCloudHttpMetrics.recordLatency failed silently: {}", e.getMessage());
        }
    }

    /**
     * Records an error (exception or explicit error response) for the given
     * handler / operation pair.
     *
     * @param handler    simple class name of the handler
     * @param operation  method name / operation label
     * @param errorType  short error type label (e.g. exception simple name or domain code)
     */
    public void recordError(String handler, String operation, String errorType) {
        if (metrics == null) return;
        try {
            metrics.incrementCounter(METRIC_ERRORS,
                TAG_HANDLER,    nullSafe(handler),
                TAG_OPERATION,  nullSafe(operation),
                TAG_ERROR_TYPE, nullSafe(errorType));
        } catch (Exception e) {
            log.debug("DataCloudHttpMetrics.recordError failed silently: {}", e.getMessage());
        }
    }

    /**
     * Records an error inferred from the provided exception type.
     *
     * @param handler   simple class name of the handler
     * @param operation method name / operation label
     * @param cause     the exception that caused the error
     */
    public void recordError(String handler, String operation, Throwable cause) {
        recordError(handler, operation,
            cause == null ? "Unknown" : cause.getClass().getSimpleName());
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
