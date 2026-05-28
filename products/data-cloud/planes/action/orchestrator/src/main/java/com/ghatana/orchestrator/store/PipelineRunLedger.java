package com.ghatana.orchestrator.store;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.*;

/**
 * Ledger entry for pipeline execution runs.
 *
 * <p><b>Purpose</b><br>
 * Tracks all pipeline executions with retry, cancel, and rollback support.
 * Enables audit trails, performance monitoring, and failure recovery.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PipelineRunLedger run = PipelineRunLedger.builder()
 *     .pipelineId("pipeline-123")
 *     .tenantId("tenant-abc")
 *     .runId(UUID.randomUUID())
 *     .status(RunStatus.RUNNING)
 *     .startedBy("user-xyz")
 *     .build();
 * }</pre>
 *
 * <p><b>Run States</b><br>
 * <ul>
 *   <li><b>PENDING</b>: Scheduled but not started</li>
 *   <li><b>RUNNING</b>: Currently executing</li>
 *   <li><b>COMPLETED</b>: Finished successfully</li>
 *   <li><b>FAILED</b>: Failed with error</li>
 *   <li><b>CANCELLED</b>: Cancelled by user or system</li>
 *   <li><b>RETRYING</b>: Retry in progress</li>
 *   <li><b>ROLLING_BACK</b>: Rollback in progress</li>
 *   <li><b>ROLLED_BACK</b>: Rollback completed</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Pipeline execution run ledger with retry/cancel/rollback tracking
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Entity)
 */
@Entity
@Table(name = "pipeline_run_ledger", indexes = {
    @Index(name = "idx_run_ledger_pipeline", columnList = "pipeline_id"),
    @Index(name = "idx_run_ledger_tenant", columnList = "tenant_id"),
    @Index(name = "idx_run_ledger_status", columnList = "status"),
    @Index(name = "idx_run_ledger_started", columnList = "started_at DESC"),
    @Index(name = "idx_run_ledger_parent", columnList = "parent_run_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineRunLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Unique identifier for this run.
     */
    @Column(name = "run_id", nullable = false, unique = true)
    private UUID runId;

    /**
     * Pipeline being executed.
     */
    @Column(name = "pipeline_id", nullable = false, length = 255)
    private String pipelineId;

    /**
     * Pipeline version being executed.
     */
    @Column(name = "pipeline_version", nullable = false)
    private Integer pipelineVersion;

    /**
     * Tenant identifier for multi-tenancy.
     */
    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    /**
     * Current run status.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private RunStatus status;

    /**
     * Parent run ID if this is a retry or rollback.
     */
    @Column(name = "parent_run_id")
    private UUID parentRunId;

    /**
     * Retry attempt number (0 for first attempt).
     */
    @Column(name = "retry_count")
    private Integer retryCount = 0;

    /**
     * Maximum retry attempts allowed.
     */
    @Column(name = "max_retries")
    private Integer maxRetries = 3;

    /**
     * When the run started.
     */
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /**
     * When the run completed (null if still running).
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Execution duration in milliseconds.
     */
    @Column(name = "duration_ms")
    private Long durationMs;

    /**
     * User or system that started the run.
     */
    @Column(name = "started_by", length = 255)
    private String startedBy;

    /**
     * User or system that completed/cancelled the run.
     */
    @Column(name = "completed_by", length = 255)
    private String completedBy;

    /**
     * Error message if run failed.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Error type/classification.
     */
    @Column(name = "error_type", length = 100)
    private String errorType;

    /**
     * Stack trace for debugging.
     */
    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    /**
     * Input parameters for the run.
     */
    @Column(name = "input_params", columnDefinition = "jsonb")
    private String inputParams;

    /**
     * Output results from the run.
     */
    @Column(name = "output_results", columnDefinition = "jsonb")
    private String outputResults;

    /**
     * Run metadata as JSONB.
     */
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @PrePersist
    protected void onCreate() {
        if (runId == null) {
            runId = UUID.randomUUID();
        }
        if (startedAt == null) {
            startedAt = Instant.now();
        }
        if (status == null) {
            status = RunStatus.PENDING;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (maxRetries == null) {
            maxRetries = 3;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        if (status == RunStatus.COMPLETED || status == RunStatus.FAILED 
            || status == RunStatus.CANCELLED || status == RunStatus.ROLLED_BACK) {
            if (completedAt == null) {
                completedAt = Instant.now();
            }
            if (durationMs == null && startedAt != null) {
                durationMs = java.time.Duration.between(startedAt, completedAt).toMillis();
            }
        }
    }

    /**
     * Mark this run as completed successfully.
     *
     * @param completedBy user or system completing the run
     * @param results output results
     */
    public void complete(String completedBy, String results) {
        this.status = RunStatus.COMPLETED;
        this.completedBy = completedBy;
        this.outputResults = results;
        this.completedAt = Instant.now();
        if (startedAt != null) {
            this.durationMs = java.time.Duration.between(startedAt, completedAt).toMillis();
        }
    }

    /**
     * Mark this run as failed.
     *
     * @param errorType error type
     * @param errorMessage error message
     * @param stackTrace stack trace
     */
    public void fail(String errorType, String errorMessage, String stackTrace) {
        this.status = RunStatus.FAILED;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.stackTrace = stackTrace;
        this.completedAt = Instant.now();
        if (startedAt != null) {
            this.durationMs = java.time.Duration.between(startedAt, completedAt).toMillis();
        }
    }

    /**
     * Mark this run as cancelled.
     *
     * @param cancelledBy user or system cancelling the run
     * @param reason cancellation reason
     */
    public void cancel(String cancelledBy, String reason) {
        this.status = RunStatus.CANCELLED;
        this.completedBy = cancelledBy;
        this.errorMessage = reason;
        this.completedAt = Instant.now();
        if (startedAt != null) {
            this.durationMs = java.time.Duration.between(startedAt, completedAt).toMillis();
        }
    }

    /**
     * Start a retry of this run.
     *
     * @return new run ledger entry for the retry
     */
    public PipelineRunLedger startRetry() {
        return PipelineRunLedger.builder()
            .pipelineId(this.pipelineId)
            .pipelineVersion(this.pipelineVersion)
            .tenantId(this.tenantId)
            .parentRunId(this.runId)
            .status(RunStatus.RETRYING)
            .retryCount(this.retryCount + 1)
            .maxRetries(this.maxRetries)
            .startedBy(this.startedBy)
            .inputParams(this.inputParams)
            .metadata(this.metadata)
            .build();
    }

    /**
     * Start a rollback of this run.
     *
     * @return new run ledger entry for the rollback
     */
    public PipelineRunLedger startRollback() {
        return PipelineRunLedger.builder()
            .pipelineId(this.pipelineId)
            .pipelineVersion(this.pipelineVersion)
            .tenantId(this.tenantId)
            .parentRunId(this.runId)
            .status(RunStatus.ROLLING_BACK)
            .retryCount(0)
            .maxRetries(0)
            .startedBy(this.startedBy)
            .inputParams(this.inputParams)
            .metadata(this.metadata)
            .build();
    }

    /**
     * Check if this run can be retried.
     *
     * @return true if retry is possible
     */
    public boolean canRetry() {
        return status == RunStatus.FAILED && retryCount < maxRetries;
    }

    /**
     * Check if this run is a retry.
     *
     * @return true if this is a retry run
     */
    public boolean isRetry() {
        return parentRunId != null && status == RunStatus.RETRYING;
    }

    /**
     * Check if this run is a rollback.
     *
     * @return true if this is a rollback run
     */
    public boolean isRollback() {
        return parentRunId != null && status == RunStatus.ROLLING_BACK;
    }

    /**
     * Check if this run is terminal (no further state changes).
     *
     * @return true if run is in terminal state
     */
    public boolean isTerminal() {
        return status == RunStatus.COMPLETED 
            || status == RunStatus.FAILED 
            || status == RunStatus.CANCELLED 
            || status == RunStatus.ROLLED_BACK;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PipelineRunLedger that = (PipelineRunLedger) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PipelineRunLedger{" +
                "id=" + id +
                ", runId=" + runId +
                ", pipelineId='" + pipelineId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", status=" + status +
                ", retryCount=" + retryCount +
                ", startedAt=" + startedAt +
                ", completedAt=" + completedAt +
                '}';
    }

    /**
     * Run status enum.
     */
    public enum RunStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED,
        RETRYING,
        ROLLING_BACK,
        ROLLED_BACK
    }
}
