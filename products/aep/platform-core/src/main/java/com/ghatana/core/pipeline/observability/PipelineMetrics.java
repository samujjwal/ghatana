package com.ghatana.core.pipeline.observability;

import com.ghatana.platform.observability.MetricsCollector;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Observability facade for pipeline execution metrics.
 *
 * <p>Provides domain-specific metrics methods for pipeline operations,
 * abstracting metric naming conventions and tag strategies. All pipeline
 * metrics follow the {@code pipeline.*} namespace convention.</p>
 *
 * <h2>Metric Categories</h2>
 * <ul>
 *   <li><b>Execution metrics:</b> pipeline.execution.count, pipeline.execution.duration</li>
 *   <li><b>Stage metrics:</b> pipeline.stage.count, pipeline.stage.duration, pipeline.stage.errors</li>
 *   <li><b>Validation metrics:</b> pipeline.validation.count, pipeline.validation.failures</li>
 *   <li><b>Throughput metrics:</b> pipeline.events.processed, pipeline.events.emitted</li>
 * </ul>
 *
 * <h2>Standard Tags</h2>
 * <ul>
 *   <li>{@code pipeline_id} — Pipeline identifier</li>
 *   <li>{@code tenant_id} — Tenant for multi-tenant isolation</li>
 *   <li>{@code stage_id} — Stage within pipeline (for stage-level metrics)</li>
 *   <li>{@code status} — success/failure/timeout</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Observability facade for pipeline execution metrics
 * @doc.layer core
 * @doc.pattern Facade
 */
public class PipelineMetrics {

    // ════════════════════════════════════════════════════════════════
    // Metric name constants
    // ════════════════════════════════════════════════════════════════

    public static final String EXECUTION_COUNT = "pipeline.execution.count";
    public static final String EXECUTION_DURATION_MS = "pipeline.execution.duration";
    public static final String EXECUTION_ERRORS = "pipeline.execution.errors";

    public static final String STAGE_COUNT = "pipeline.stage.count";
    public static final String STAGE_DURATION_MS = "pipeline.stage.duration";
    public static final String STAGE_ERRORS = "pipeline.stage.errors";
    public static final String STAGE_SKIPPED = "pipeline.stage.skipped";

    public static final String VALIDATION_COUNT = "pipeline.validation.count";
    public static final String VALIDATION_FAILURES = "pipeline.validation.failures";

    public static final String EVENTS_PROCESSED = "pipeline.events.processed";
    public static final String EVENTS_EMITTED = "pipeline.events.emitted";

    public static final String CHECKPOINT_SAVED = "pipeline.checkpoint.saved";
    public static final String CHECKPOINT_RESTORED = "pipeline.checkpoint.restored";
    public static final String CHECKPOINT_ERRORS = "pipeline.checkpoint.errors";

    // ════════════════════════════════════════════════════════════════
    // Tag key constants
    // ════════════════════════════════════════════════════════════════

    public static final String TAG_PIPELINE_ID = "pipeline_id";
    public static final String TAG_TENANT_ID = "tenant_id";
    public static final String TAG_STAGE_ID = "stage_id";
    public static final String TAG_STATUS = "status";
    public static final String TAG_ERROR_TYPE = "error_type";

    // ════════════════════════════════════════════════════════════════
    // Fields
    // ════════════════════════════════════════════════════════════════

    private final MetricsCollector collector;

    /**
     * Creates a PipelineMetrics facade wrapping the given MetricsCollector.
     *
     * @param collector the underlying metrics collector (must not be null)
     */
    public PipelineMetrics(MetricsCollector collector) {
        this.collector = Objects.requireNonNull(collector, "MetricsCollector must not be null");
    }

    // ════════════════════════════════════════════════════════════════
    // Execution-level metrics
    // ════════════════════════════════════════════════════════════════

    /**
     * Records a pipeline execution start.
     *
     * @param pipelineId the pipeline ID
     * @param tenantId   the tenant ID
     */
    public void recordExecutionStarted(String pipelineId, String tenantId) {
        collector.incrementCounter(EXECUTION_COUNT,
                TAG_PIPELINE_ID, pipelineId,
                TAG_TENANT_ID, tenantId,
                TAG_STATUS, "started");
    }

    /**
     * Records a successful pipeline execution with duration.
     *
     * @param pipelineId the pipeline ID
     * @param tenantId   the tenant ID
     * @param durationMs execution duration in milliseconds
     */
    public void recordExecutionCompleted(String pipelineId, String tenantId, long durationMs) {
        collector.incrementCounter(EXECUTION_COUNT,
                TAG_PIPELINE_ID, pipelineId,
                TAG_TENANT_ID, tenantId,
                TAG_STATUS, "success");
        collector.recordTimer(EXECUTION_DURATION_MS, durationMs,
                TAG_PIPELINE_ID, pipelineId,
                TAG_TENANT_ID, tenantId);
    }

    /**
     * Records a failed pipeline execution.
     *
     * @param pipelineId the pipeline ID
     * @param tenantId   the tenant ID
     * @param errorType  classification of the error
     * @param durationMs execution duration in milliseconds
     */
    public void recordExecutionFailed(String pipelineId, String tenantId,
                                       String errorType, long durationMs) {
        collector.incrementCounter(EXECUTION_ERRORS,
                TAG_PIPELINE_ID, pipelineId,
                TAG_TENANT_ID, tenantId,
                TAG_ERROR_TYPE, errorType);
        collector.recordTimer(EXECUTION_DURATION_MS, durationMs,
                TAG_PIPELINE_ID, pipelineId,
                TAG_TENANT_ID, tenantId);
    }

    // ════════════════════════════════════════════════════════════════
    // Stage-level metrics
    // ════════════════════════════════════════════════════════════════

    /**
     * Records a stage execution with timing.
     *
     * @param pipelineId the pipeline ID
     * @param stageId    the stage ID
     * @param durationMs stage duration in milliseconds
     * @param success    whether the stage succeeded
     */
    public void recordStageExecution(String pipelineId, String stageId,
                                      long durationMs, boolean success) {
        String status = success ? "success" : "failure";
        collector.incrementCounter(STAGE_COUNT,
                TAG_PIPELINE_ID, pipelineId,
                TAG_STAGE_ID, stageId,
                TAG_STATUS, status);
        collector.recordTimer(STAGE_DURATION_MS, durationMs,
                TAG_PIPELINE_ID, pipelineId,
                TAG_STAGE_ID, stageId);
        if (!success) {
            collector.incrementCounter(STAGE_ERRORS,
                    TAG_PIPELINE_ID, pipelineId,
                    TAG_STAGE_ID, stageId);
        }
    }

    /**
     * Records a skipped stage (e.g., due to deadline exceeded).
     *
     * @param pipelineId the pipeline ID
     * @param stageId    the stage ID
     * @param reason     reason for skipping
     */
    public void recordStageSkipped(String pipelineId, String stageId, String reason) {
        collector.incrementCounter(STAGE_SKIPPED,
                TAG_PIPELINE_ID, pipelineId,
                TAG_STAGE_ID, stageId,
                "reason", reason);
    }

    // ════════════════════════════════════════════════════════════════
    // Validation metrics
    // ════════════════════════════════════════════════════════════════

    /**
     * Records a pipeline validation attempt.
     *
     * @param pipelineId the pipeline ID
     * @param valid      whether validation passed
     */
    public void recordValidation(String pipelineId, boolean valid) {
        collector.incrementCounter(VALIDATION_COUNT,
                TAG_PIPELINE_ID, pipelineId,
                TAG_STATUS, valid ? "valid" : "invalid");
        if (!valid) {
            collector.incrementCounter(VALIDATION_FAILURES,
                    TAG_PIPELINE_ID, pipelineId);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Event throughput metrics
    // ════════════════════════════════════════════════════════════════

    /**
     * Records events processed by a stage.
     *
     * @param pipelineId the pipeline ID
     * @param stageId    the stage ID
     * @param count      number of events processed
     */
    public void recordEventsProcessed(String pipelineId, String stageId, int count) {
        collector.increment(EVENTS_PROCESSED, count, Map.of(
                TAG_PIPELINE_ID, pipelineId,
                TAG_STAGE_ID, stageId));
    }

    /**
     * Records events emitted by a stage.
     *
     * @param pipelineId the pipeline ID
     * @param stageId    the stage ID
     * @param count      number of events emitted
     */
    public void recordEventsEmitted(String pipelineId, String stageId, int count) {
        collector.increment(EVENTS_EMITTED, count, Map.of(
                TAG_PIPELINE_ID, pipelineId,
                TAG_STAGE_ID, stageId));
    }

    // ════════════════════════════════════════════════════════════════
    // Checkpoint metrics
    // ════════════════════════════════════════════════════════════════

    /**
     * Records a checkpoint save operation.
     *
     * @param pipelineId the pipeline ID
     * @param tenantId   the tenant ID
     */
    public void recordCheckpointSaved(String pipelineId, String tenantId) {
        collector.incrementCounter(CHECKPOINT_SAVED,
                TAG_PIPELINE_ID, pipelineId,
                TAG_TENANT_ID, tenantId);
    }

    /**
     * Records a checkpoint restore operation.
     *
     * @param pipelineId the pipeline ID
     * @param tenantId   the tenant ID
     */
    public void recordCheckpointRestored(String pipelineId, String tenantId) {
        collector.incrementCounter(CHECKPOINT_RESTORED,
                TAG_PIPELINE_ID, pipelineId,
                TAG_TENANT_ID, tenantId);
    }

    /**
     * Records a checkpoint error.
     *
     * @param pipelineId the pipeline ID
     * @param errorType  type of error
     */
    public void recordCheckpointError(String pipelineId, String errorType) {
        collector.incrementCounter(CHECKPOINT_ERRORS,
                TAG_PIPELINE_ID, pipelineId,
                TAG_ERROR_TYPE, errorType);
    }

    /**
     * Returns the underlying MetricsCollector.
     *
     * @return the metrics collector
     */
    public MetricsCollector getCollector() {
        return collector;
    }
}
