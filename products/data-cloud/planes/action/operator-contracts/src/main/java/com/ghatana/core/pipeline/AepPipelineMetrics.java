/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.pipeline;

import com.ghatana.platform.observability.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Standardised business metrics facade for AEP pipeline executions.
 *
 * <p>Centralises all metric names and tag conventions for the
 * {@link PipelineExecutionEngine} so consumers emit consistently
 * named counters and timers.
 *
 * <h2>Metric names</h2>
 * <pre>
 *   aep.pipeline.executions.started    [pipelineId, tenantId]  — Counter
 *   aep.pipeline.executions.succeeded  [pipelineId, tenantId]  — Counter
 *   aep.pipeline.executions.failed     [pipelineId, tenantId]  — Counter
 *   aep.pipeline.execution.latency.ms  [pipelineId, tenantId]  — Timer (ms)
 *   aep.pipeline.stages.executed       [pipelineId, tenantId]  — Counter (per run)
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AepPipelineMetrics metrics = AepPipelineMetrics.of(metricsCollector);
 * metrics.recordStarted(pipelineId, tenantId);
 * // ... execution ...
 * metrics.recordSucceeded(pipelineId, tenantId, latencyMs, stagesExecuted);
 * }</pre>
 *
 * <h2>Safety</h2>
 * All methods are non-throwing — failures are logged at DEBUG level
 * so metrics never affect the pipeline execution path.
 *
 * @doc.type class
 * @doc.purpose Standardised AEP pipeline execution business metrics
 * @doc.layer product
 * @doc.pattern Facade
 */
public final class AepPipelineMetrics {

    private static final Logger log = LoggerFactory.getLogger(AepPipelineMetrics.class);

    // ── Canonical metric names ────────────────────────────────────────────────

    public static final String METRIC_STARTED   = "aep.pipeline.executions.started";
    public static final String METRIC_SUCCEEDED = "aep.pipeline.executions.succeeded";
    public static final String METRIC_FAILED    = "aep.pipeline.executions.failed";
    public static final String METRIC_LATENCY   = "aep.pipeline.execution.latency.ms";
    public static final String METRIC_STAGES    = "aep.pipeline.stages.executed";

    // ── Tag keys ─────────────────────────────────────────────────────────────

    public static final String TAG_PIPELINE_ID = "pipelineId";
    public static final String TAG_TENANT_ID   = "tenantId";

    /** Noop instance for test / minimal deployments. */
    private static final AepPipelineMetrics NOOP = new AepPipelineMetrics(null);

    private final MetricsCollector delegate;

    private AepPipelineMetrics(MetricsCollector delegate) {
        this.delegate = delegate;
    }

    /**
     * Creates an instance backed by the given {@link MetricsCollector}.
     *
     * @param collector platform metrics collector; must not be {@code null}
     * @return a new metrics instance
     */
    public static AepPipelineMetrics of(MetricsCollector collector) {
        return new AepPipelineMetrics(Objects.requireNonNull(collector, "MetricsCollector must not be null"));
    }

    /**
     * Returns a no-op instance that never emits any metrics.
     * Useful for tests and deployments without a metrics backend.
     *
     * @return shared noop instance
     */
    public static AepPipelineMetrics noop() {
        return NOOP;
    }

    /**
     * Records that a pipeline execution has started.
     *
     * @param pipelineId identifier of the pipeline being executed
     * @param tenantId   scoping tenant
     */
    public void recordStarted(String pipelineId, String tenantId) {
        if (delegate == null) return;
        try {
            delegate.incrementCounter(METRIC_STARTED,
                    TAG_PIPELINE_ID, safe(pipelineId),
                    TAG_TENANT_ID,   safe(tenantId));
        } catch (Exception e) {
            log.debug("AepPipelineMetrics.recordStarted failed silently: {}", e.getMessage());
        }
    }

    /**
     * Records that a pipeline execution completed successfully.
     *
     * @param pipelineId     identifier of the pipeline
     * @param tenantId       scoping tenant
     * @param latencyMs      total wall-clock execution time in milliseconds
     * @param stagesExecuted number of stages that ran during this execution
     */
    public void recordSucceeded(String pipelineId, String tenantId,
                                long latencyMs, int stagesExecuted) {
        if (delegate == null) return;
        try {
            delegate.incrementCounter(METRIC_SUCCEEDED,
                    TAG_PIPELINE_ID, safe(pipelineId),
                    TAG_TENANT_ID,   safe(tenantId));
            delegate.recordTimer(METRIC_LATENCY, latencyMs,
                    TAG_PIPELINE_ID, safe(pipelineId),
                    TAG_TENANT_ID,   safe(tenantId));
            if (stagesExecuted > 0) {
                for (int i = 0; i < stagesExecuted; i++) {
                    delegate.incrementCounter(METRIC_STAGES,
                            TAG_PIPELINE_ID, safe(pipelineId),
                            TAG_TENANT_ID,   safe(tenantId));
                }
            }
        } catch (Exception e) {
            log.debug("AepPipelineMetrics.recordSucceeded failed silently: {}", e.getMessage());
        }
    }

    /**
     * Records that a pipeline execution failed or was aborted.
     *
     * @param pipelineId identifier of the pipeline
     * @param tenantId   scoping tenant
     * @param latencyMs  wall-clock time elapsed before failure in milliseconds
     */
    public void recordFailed(String pipelineId, String tenantId, long latencyMs) {
        if (delegate == null) return;
        try {
            delegate.incrementCounter(METRIC_FAILED,
                    TAG_PIPELINE_ID, safe(pipelineId),
                    TAG_TENANT_ID,   safe(tenantId));
            delegate.recordTimer(METRIC_LATENCY, latencyMs,
                    TAG_PIPELINE_ID, safe(pipelineId),
                    TAG_TENANT_ID,   safe(tenantId));
        } catch (Exception e) {
            log.debug("AepPipelineMetrics.recordFailed failed silently: {}", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String safe(String value) {
        return (value == null || value.isBlank()) ? "unknown" : value;
    }
}
