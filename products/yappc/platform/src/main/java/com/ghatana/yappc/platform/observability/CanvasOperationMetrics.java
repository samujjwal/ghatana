package com.ghatana.yappc.platform.observability;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.inject.annotation.Inject;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Business metrics for YAPPC canvas operations.
 *
 * <p>Records KPIs for every canvas action: element creation, updates, deletions,
 * layout changes, and collaboration conflicts — all tagged by {@code operation},
 * {@code tenant}, and {@code canvas_type}. These feed directly into the Grafana
 * YAPPC dashboard alongside the agent execution metrics.
 *
 * <p>Metric naming follows OBS-001 conventions:
 * {@code yappc.canvas.<operation>.<outcome>}, lowercase, dot-separated.
 *
 * <p>Inject this class at the canvas HTTP handler layer or canvas domain service.
 *
 * @doc.type class
 * @doc.purpose Publish business-level canvas-operation counters, timers, and
 *              conflict rates to the YAPPC observability stack.
 * @doc.layer product
 * @doc.pattern Facade
 */
public final class CanvasOperationMetrics {

    private static final Logger log = LoggerFactory.getLogger(CanvasOperationMetrics.class);

    private static final String PREFIX = "yappc.canvas";

    // Counter and timer metric names
    static final String METRIC_OPERATION_STARTED    = PREFIX + ".operation.started";
    static final String METRIC_OPERATION_SUCCEEDED  = PREFIX + ".operation.succeeded";
    static final String METRIC_OPERATION_FAILED     = PREFIX + ".operation.failed";
    static final String METRIC_OPERATION_DURATION   = PREFIX + ".operation.duration";
    static final String METRIC_COLLAB_CONFLICT      = PREFIX + ".collaboration.conflict";
    static final String METRIC_COLLAB_RESOLVED      = PREFIX + ".collaboration.resolved";

    // Tag keys
    private static final String TAG_OPERATION   = "operation";
    private static final String TAG_TENANT      = "tenant";
    private static final String TAG_CANVAS_TYPE = "canvas_type";
    private static final String TAG_ERROR_TYPE  = "error_type";

    private final MetricsCollector metricsCollector;
    private final MeterRegistry meterRegistry;

    /**
     * @param metricsCollector platform metrics API for counter-based operations
     * @param meterRegistry    Micrometer registry for high-resolution timer samples
     */
    @Inject
    public CanvasOperationMetrics(MetricsCollector metricsCollector, MeterRegistry meterRegistry) {
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    /**
     * Start timing a canvas operation. Call {@link #recordOperationSuccess} or
     * {@link #recordOperationFailure} with the returned sample to stop the timer.
     *
     * @param operation  the canvas operation type (e.g. {@code "element.create"},
     *                   {@code "layout.update"}, {@code "connection.delete"})
     * @param tenant     the tenant performing the operation
     * @param canvasType the canvas category (e.g. {@code "architecture"},
     *                   {@code "wireframe"}, {@code "kanban"})
     * @return a started {@link Timer.Sample} to pass back when the operation completes
     */
    public Timer.Sample startOperation(String operation, String tenant, String canvasType) {
        metricsCollector.incrementCounter(
                METRIC_OPERATION_STARTED,
                TAG_OPERATION, operation,
                TAG_TENANT, tenant,
                TAG_CANVAS_TYPE, canvasType);
        log.debug("Canvas operation started: {} tenant={} canvasType={}", operation, tenant, canvasType);
        return Timer.start(meterRegistry);
    }

    /**
     * Record a successful canvas operation and stop the latency timer.
     *
     * @param sample     the {@link Timer.Sample} returned by {@link #startOperation}; ignored if {@code null}
     * @param operation  the canvas operation type
     * @param tenant     the tenant performing the operation
     * @param canvasType the canvas category
     */
    public void recordOperationSuccess(Timer.Sample sample, String operation, String tenant, String canvasType) {
        metricsCollector.incrementCounter(
                METRIC_OPERATION_SUCCEEDED,
                TAG_OPERATION, operation,
                TAG_TENANT, tenant,
                TAG_CANVAS_TYPE, canvasType);
        if (sample != null) {
            sample.stop(meterRegistry.timer(
                    METRIC_OPERATION_DURATION,
                    Tags.of(TAG_OPERATION, operation, TAG_TENANT, tenant, TAG_CANVAS_TYPE, canvasType)));
        }
        log.debug("Canvas operation succeeded: {} tenant={} canvasType={}", operation, tenant, canvasType);
    }

    /**
     * Record a failed canvas operation and stop the latency timer.
     *
     * @param sample     the {@link Timer.Sample} returned by {@link #startOperation}; ignored if {@code null}
     * @param operation  the canvas operation type
     * @param tenant     the tenant performing the operation
     * @param canvasType the canvas category
     * @param cause      optional exception for structured error logging; {@code null} is accepted
     */
    public void recordOperationFailure(
            Timer.Sample sample,
            String operation,
            String tenant,
            String canvasType,
            Throwable cause) {
        String errorType = cause != null ? cause.getClass().getSimpleName() : "unknown";
        metricsCollector.incrementCounter(
                METRIC_OPERATION_FAILED,
                TAG_OPERATION, operation,
                TAG_TENANT, tenant,
                TAG_CANVAS_TYPE, canvasType,
                TAG_ERROR_TYPE, errorType);
        if (sample != null) {
            sample.stop(meterRegistry.timer(
                    METRIC_OPERATION_DURATION,
                    Tags.of(TAG_OPERATION, operation, TAG_TENANT, tenant, TAG_CANVAS_TYPE, canvasType)));
        }
        if (cause != null) {
            log.warn("Canvas operation failed: {} tenant={} canvasType={} errorType={}",
                    operation, tenant, canvasType, errorType, cause);
        } else {
            log.warn("Canvas operation failed: {} tenant={} canvasType={}", operation, tenant, canvasType);
        }
    }

    /**
     * Record a CRDT/collaboration conflict event on this canvas.
     *
     * @param tenant     the tenant owning the canvas
     * @param canvasType the canvas category where the conflict occurred
     */
    public void recordCollaborationConflict(String tenant, String canvasType) {
        metricsCollector.incrementCounter(
                METRIC_COLLAB_CONFLICT,
                TAG_TENANT, tenant,
                TAG_CANVAS_TYPE, canvasType);
        log.debug("Canvas collaboration conflict: tenant={} canvasType={}", tenant, canvasType);
    }

    /**
     * Record successful resolution of a CRDT/collaboration conflict.
     *
     * @param tenant     the tenant owning the canvas
     * @param canvasType the canvas category where the conflict was resolved
     */
    public void recordCollaborationResolved(String tenant, String canvasType) {
        metricsCollector.incrementCounter(
                METRIC_COLLAB_RESOLVED,
                TAG_TENANT, tenant,
                TAG_CANVAS_TYPE, canvasType);
        log.debug("Canvas collaboration conflict resolved: tenant={} canvasType={}", tenant, canvasType);
    }
}
