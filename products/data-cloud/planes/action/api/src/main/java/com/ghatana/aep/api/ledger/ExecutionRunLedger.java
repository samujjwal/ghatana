/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.api.ledger;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Execution run ledger for tracking pipeline execution runs.
 * 
 * P7.3: Tracks logs, checkpoints, retries, rollback, cancellation, and policy decisions
 * for each pipeline execution run. Provides observability and audit trail for pipeline operations.
 * 
 * @doc.type class
 * @doc.purpose Track pipeline execution runs with full observability
 * @doc.layer product
 * @doc.pattern Ledger
 */
public final class ExecutionRunLedger {

    private final ExecutionRunStore store;

    public ExecutionRunLedger(ExecutionRunStore store) {
        this.store = store;
    }

    /**
     * Creates a new execution run record.
     *
     * @param pipelineId the pipeline ID
     * @param tenantId the tenant ID
     * @param triggeredBy who triggered the execution
     * @return the created execution run
     */
    public ExecutionRun createRun(String pipelineId, String tenantId, String triggeredBy) {
        ExecutionRun run = new ExecutionRun(
            java.util.UUID.randomUUID().toString(),
            pipelineId,
            tenantId,
            ExecutionRunStatus.STARTED,
            triggeredBy,
            Instant.now(),
            null,
            0,
            List.of(),
            List.of(),
            Map.of()
        );
        store.save(run);
        return run;
    }

    /**
     * Records a checkpoint in the execution run.
     *
     * @param runId the execution run ID
     * @param checkpoint the checkpoint to record
     */
    public void recordCheckpoint(String runId, Checkpoint checkpoint) {
        ExecutionRun run = store.findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
        
        ExecutionRun updated = new ExecutionRun(
            run.id(),
            run.pipelineId(),
            run.tenantId(),
            run.status(),
            run.triggeredBy(),
            run.startedAt(),
            run.completedAt(),
            run.retryCount(),
            Stream.concat(run.checkpoints().stream(), Stream.of(checkpoint)).toList(),
            run.logs(),
            run.policyDecisions()
        );
        store.save(updated);
    }

    /**
     * Records a log entry in the execution run.
     *
     * @param runId the execution run ID
     * @param log the log entry to record
     */
    public void recordLog(String runId, LogEntry log) {
        ExecutionRun run = store.findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
        
        ExecutionRun updated = new ExecutionRun(
            run.id(),
            run.pipelineId(),
            run.tenantId(),
            run.status(),
            run.triggeredBy(),
            run.startedAt(),
            run.completedAt(),
            run.retryCount(),
            run.checkpoints(),
            Stream.concat(run.logs().stream(), Stream.of(log)).toList(),
            run.policyDecisions()
        );
        store.save(updated);
    }

    /**
     * Records a policy decision in the execution run.
     *
     * @param runId the execution run ID
     * @param decision the policy decision to record
     */
    public void recordPolicyDecision(String runId, PolicyDecision decision) {
        ExecutionRun run = store.findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
        
        Map<String, PolicyDecision> updatedDecisions = new java.util.LinkedHashMap<>(run.policyDecisions());
        updatedDecisions.put(decision.policyId(), decision);
        
        ExecutionRun updated = new ExecutionRun(
            run.id(),
            run.pipelineId(),
            run.tenantId(),
            run.status(),
            run.triggeredBy(),
            run.startedAt(),
            run.completedAt(),
            run.retryCount(),
            run.checkpoints(),
            run.logs(),
            updatedDecisions
        );
        store.save(updated);
    }

    /**
     * Increments the retry count for the execution run.
     *
     * @param runId the execution run ID
     */
    public void incrementRetry(String runId) {
        ExecutionRun run = store.findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
        
        ExecutionRun updated = new ExecutionRun(
            run.id(),
            run.pipelineId(),
            run.tenantId(),
            run.status(),
            run.triggeredBy(),
            run.startedAt(),
            run.completedAt(),
            run.retryCount() + 1,
            run.checkpoints(),
            run.logs(),
            run.policyDecisions()
        );
        store.save(updated);
    }

    /**
     * Marks the execution run as completed.
     *
     * @param runId the execution run ID
     * @param status the final status
     */
    public void completeRun(String runId, ExecutionRunStatus status) {
        ExecutionRun run = store.findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
        
        ExecutionRun updated = new ExecutionRun(
            run.id(),
            run.pipelineId(),
            run.tenantId(),
            status,
            run.triggeredBy(),
            run.startedAt(),
            Instant.now(),
            run.retryCount(),
            run.checkpoints(),
            run.logs(),
            run.policyDecisions()
        );
        store.save(updated);
    }

    /**
     * Cancels the execution run.
     *
     * @param runId the execution run ID
     * @param reason the cancellation reason
     */
    public void cancelRun(String runId, String reason) {
        ExecutionRun run = store.findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
        
        recordLog(runId, new LogEntry(
            Instant.now(),
            "INFO",
            "CANCELLED",
            "Run cancelled: " + reason
        ));
        
        completeRun(runId, ExecutionRunStatus.CANCELLED);
    }

    /**
     * Retrieves an execution run by ID.
     *
     * @param runId the execution run ID
     * @return the execution run if found
     */
    public Optional<ExecutionRun> getRun(String runId) {
        return store.findById(runId);
    }

    /**
     * Retrieves execution runs for a pipeline.
     *
     * @param pipelineId the pipeline ID
     * @return list of execution runs for the pipeline
     */
    public List<ExecutionRun> getRunsForPipeline(String pipelineId) {
        return store.findByPipelineId(pipelineId);
    }

    /**
     * Execution run record.
     *
     * @param id unique identifier
     * @param pipelineId the pipeline ID
     * @param tenantId the tenant ID
     * @param status current status
     * @param triggeredBy who triggered the execution
     * @param startedAt when the run started
     * @param completedAt when the run completed (null if still running)
     * @param retryCount number of retries attempted
     * @param checkpoints recorded checkpoints
     * @param logs recorded log entries
     * @param policyDecisions policy decisions made during execution
     */
    public record ExecutionRun(
            String id,
            String pipelineId,
            String tenantId,
            ExecutionRunStatus status,
            String triggeredBy,
            Instant startedAt,
            Instant completedAt,
            int retryCount,
            List<Checkpoint> checkpoints,
            List<LogEntry> logs,
            Map<String, PolicyDecision> policyDecisions) {

        public boolean isRunning() {
            return status == ExecutionRunStatus.STARTED || status == ExecutionRunStatus.RETRYING;
        }

        public boolean isCompleted() {
            return status == ExecutionRunStatus.COMPLETED || 
                   status == ExecutionRunStatus.FAILED || 
                   status == ExecutionRunStatus.CANCELLED;
        }

        public long durationMillis() {
            if (completedAt == null) {
                return Instant.now().toEpochMilli() - startedAt.toEpochMilli();
            }
            return completedAt.toEpochMilli() - startedAt.toEpochMilli();
        }
    }

    /**
     * Execution run status.
     */
    public enum ExecutionRunStatus {
        STARTED,
        RETRYING,
        COMPLETED,
        FAILED,
        CANCELLED,
        ROLLED_BACK
    }

    /**
     * Checkpoint recorded during execution.
     *
     * @param id checkpoint ID
     * @param timestamp when the checkpoint was recorded
     * @param stage the pipeline stage
     * @param data checkpoint data
     */
    public record Checkpoint(
            String id,
            Instant timestamp,
            String stage,
            Map<String, Object> data) {

        public Checkpoint(String stage, Map<String, Object> data) {
            this(java.util.UUID.randomUUID().toString(), Instant.now(), stage, data);
        }
    }

    /**
     * Log entry recorded during execution.
     *
     * @param timestamp when the log was recorded
     * @param level log level (INFO, WARN, ERROR)
     * @param category log category
     * @param message log message
     */
    public record LogEntry(
            Instant timestamp,
            String level,
            String category,
            String message) {

        public LogEntry(String level, String category, String message) {
            this(Instant.now(), level, category, message);
        }
    }

    /**
     * Policy decision made during execution.
     *
     * @param policyId the policy ID
     * @param decision the decision (APPROVE, REJECT, ESCALATE)
     * @param reason the reason for the decision
     * @param timestamp when the decision was made
     */
    public record PolicyDecision(
            String policyId,
            Decision decision,
            String reason,
            Instant timestamp) {

        public PolicyDecision(String policyId, Decision decision, String reason) {
            this(policyId, decision, reason, Instant.now());
        }

        public enum Decision {
            APPROVE,
            REJECT,
            ESCALATE
        }
    }

    /**
     * Store interface for execution runs.
     */
    public interface ExecutionRunStore {
        void save(ExecutionRun run);
        Optional<ExecutionRun> findById(String runId);
        List<ExecutionRun> findByPipelineId(String pipelineId);
    }
}
