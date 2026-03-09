/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.store;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Day 39: Interface for storing and retrieving pipeline execution checkpoints.
 * Provides exactly-once semantics and enables pipeline restart from last successful checkpoint.
 */
public interface CheckpointStore {

    /**
     * Create a new pipeline execution instance.
     * 
     * @param tenantId Tenant identifier
     * @param pipelineId Pipeline identifier
     * @param instanceId Unique execution instance identifier
     * @param idempotencyKey Unique key to prevent duplicate executions
     * @param initialState Initial pipeline state/context
     * @return The created checkpoint
     */
    PipelineCheckpoint createExecution(String tenantId, String pipelineId, String instanceId,
                                     String idempotencyKey, Map<String, Object> initialState);

    /**
     * Update checkpoint with step completion.
     * 
     * @param instanceId Pipeline execution instance identifier
     * @param stepId Step identifier within the pipeline
     * @param stepName Human-readable step name
     * @param status Step completion status
     * @param result Step execution result/output
     * @param state Updated pipeline state after step completion
     * @return Updated checkpoint
     */
    PipelineCheckpoint updateCheckpoint(String instanceId, String stepId, String stepName,
                                      PipelineCheckpointStatus status, Map<String, Object> result,
                                      Map<String, Object> state);

    /**
     * Find checkpoint by instance ID.
     * 
     * @param instanceId Pipeline execution instance identifier
     * @return Checkpoint if found
     */
    Optional<PipelineCheckpoint> findByInstanceId(String instanceId);

    /**
     * Find checkpoint by idempotency key.
     * 
     * @param tenantId Tenant identifier
     * @param idempotencyKey Unique execution key
     * @return Checkpoint if found
     */
    Optional<PipelineCheckpoint> findByIdempotencyKey(String tenantId, String idempotencyKey);

    /**
     * List all checkpoints for a pipeline.
     * 
     * @param tenantId Tenant identifier
     * @param pipelineId Pipeline identifier
     * @param limit Maximum number of results
     * @return List of checkpoints ordered by creation time (newest first)
     */
    List<PipelineCheckpoint> findByPipelineId(String tenantId, String pipelineId, int limit);

    /**
     * Find active (running) pipeline executions.
     * 
     * @param limit Maximum number of results
     * @return List of active checkpoints
     */
    List<PipelineCheckpoint> findActive(int limit);

    /**
     * Find stale executions that have been running too long.
     * 
     * @param staleBefore Executions started before this time are considered stale
     * @return List of stale checkpoints
     */
    List<PipelineCheckpoint> findStale(Instant staleBefore);

    /**
     * Mark execution as completed (success or failure).
     * 
     * @param instanceId Pipeline execution instance identifier
     * @param status Final execution status
     * @param finalResult Final execution result
     */
    void completeExecution(String instanceId, PipelineCheckpointStatus status, Map<String, Object> finalResult);

    /**
     * Delete old completed checkpoints for cleanup.
     * 
     * @param completedBefore Delete checkpoints completed before this time
     * @return Number of deleted checkpoints
     */
    int cleanupOldCheckpoints(Instant completedBefore);

    /**
     * Check if an idempotency key has already been processed.
     * 
     * @param tenantId Tenant identifier
     * @param idempotencyKey Unique execution key
     * @return true if already processed (duplicate), false if new
     */
    boolean isDuplicate(String tenantId, String idempotencyKey);

    /**
     * Get the last successful checkpoint for resuming execution.
     * 
     * @param instanceId Pipeline execution instance identifier
     * @return Last successful step checkpoint, or empty if none
     */
    Optional<StepCheckpoint> getLastSuccessfulStep(String instanceId);

    /**
     * Record step checkpoint for resume capability.
     * 
     * @param instanceId Pipeline execution instance identifier
     * @param stepCheckpoint Step execution details
     */
    void recordStepCheckpoint(String instanceId, StepCheckpoint stepCheckpoint);

    /**
     * Check if a checkpoint allows execution.
     * 
     * @param instanceId Pipeline execution instance identifier
     * @return true if the checkpoint status allows execution
     */
    boolean isExecutionAllowed(String instanceId);
}
