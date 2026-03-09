/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.store;

import io.activej.inject.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Day 39: PostgreSQL-backed implementation of CheckpointStore.
 * Provides exactly-once semantics and checkpoint-based resume functionality
 * for pipeline executions using JPA and PostgreSQL.
 */
public class PostgresqlCheckpointStore implements CheckpointStore {

    private static final Logger logger = LoggerFactory.getLogger(PostgresqlCheckpointStore.class);

    private final PipelineCheckpointRepository pipelineRepository;
    private final StepCheckpointRepository stepRepository;

    @Inject
    public PostgresqlCheckpointStore(PipelineCheckpointRepository pipelineRepository,
                                   StepCheckpointRepository stepRepository) {
        this.pipelineRepository = pipelineRepository;
        this.stepRepository = stepRepository;
    }

    @Override
    public PipelineCheckpoint createExecution(String tenantId, String pipelineId, String instanceId,
                                             String idempotencyKey, Map<String, Object> initialState) {
        logger.info("Creating execution checkpoint: tenantId={}, pipelineId={}, instanceId={}, idempotencyKey={}",
                   tenantId, pipelineId, instanceId, idempotencyKey);

        // Check for duplicate
        if (pipelineRepository.existsByIdempotencyKey(tenantId, idempotencyKey)) {
            logger.warn("Duplicate execution attempt detected: tenantId={}, idempotencyKey={}", tenantId, idempotencyKey);
            throw new DuplicateExecutionException("Execution with idempotency key already exists: " + idempotencyKey);
        }

        // Create new checkpoint entity
        PipelineCheckpointEntity entity = new PipelineCheckpointEntity(
            instanceId, tenantId, pipelineId, idempotencyKey, PipelineCheckpointStatus.CREATED, initialState);

        // If the initial state contains a configured totalSteps, apply it to the entity
        if (initialState != null && initialState.containsKey("totalSteps")) {
            try {
                Object ts = initialState.get("totalSteps");
                int total = ts instanceof Number ? ((Number) ts).intValue() : Integer.parseInt(ts.toString());
                entity.setTotalSteps(total);
            } catch (Exception e) {
                logger.warn("Unable to parse totalSteps from initial state: {}", initialState.get("totalSteps"));
            }
        }

        // Save to database
        PipelineCheckpointEntity saved = pipelineRepository.save(entity);
        PipelineCheckpoint checkpoint = saved.toDomainObject();

        logger.info("Created execution checkpoint: instanceId={}, status={}", 
                   checkpoint.getInstanceId(), checkpoint.getStatus());

        return checkpoint;
    }

    @Override
    public PipelineCheckpoint updateCheckpoint(String instanceId, String stepId, String stepName,
                                             PipelineCheckpointStatus status, Map<String, Object> result,
                                             Map<String, Object> state) {
        logger.debug("Updating checkpoint: instanceId={}, stepId={}, status={}", instanceId, stepId, status);

        Optional<PipelineCheckpointEntity> entityOpt = pipelineRepository.findById(instanceId);
        if (entityOpt.isEmpty()) {
            throw new CheckpointNotFoundException("Pipeline checkpoint not found: " + instanceId);
        }

        PipelineCheckpointEntity entity = entityOpt.get();
        
        // Update checkpoint fields
        entity.setStatus(status == PipelineCheckpointStatus.STEP_SUCCESS ? PipelineCheckpointStatus.RUNNING : status);
        entity.setCurrentStepId(stepId);
        entity.setCurrentStepName(stepName);
        entity.setState(state != null ? state : entity.getState());
        entity.setResult(result != null ? result : entity.getResult());

        // Increment completed steps if step succeeded
        if (status == PipelineCheckpointStatus.STEP_SUCCESS) {
            entity.setCompletedSteps((entity.getCompletedSteps() != null ? entity.getCompletedSteps() : 0) + 1);
        }

        // If the updated state includes totalSteps, update it on the entity (tests rely on this)
        if (state != null && state.containsKey("totalSteps")) {
            try {
                Object ts = state.get("totalSteps");
                int total = ts instanceof Number ? ((Number) ts).intValue() : Integer.parseInt(ts.toString());
                entity.setTotalSteps(total);
            } catch (Exception e) {
                logger.warn("Unable to parse totalSteps from state: {}", state.get("totalSteps"));
            }
        }

        // Save updated checkpoint
        PipelineCheckpointEntity saved = pipelineRepository.save(entity);
        PipelineCheckpoint checkpoint = saved.toDomainObject();

        logger.debug("Updated checkpoint: instanceId={}, completedSteps={}, status={}", 
                    checkpoint.getInstanceId(), checkpoint.getCompletedSteps(), checkpoint.getStatus());

        return checkpoint;
    }

    public Optional<PipelineCheckpoint> findByInstanceId(String instanceId) {
        logger.debug("Finding checkpoint by instance ID: {}", instanceId);
        
        return pipelineRepository.findById(instanceId)
                .map(PipelineCheckpointEntity::toDomainObject);
    }

    public Optional<PipelineCheckpoint> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        logger.debug("Finding checkpoint by idempotency key: tenantId={}, key={}", tenantId, idempotencyKey);

        return pipelineRepository.findByIdempotencyKey(tenantId, idempotencyKey)
                .map(PipelineCheckpointEntity::toDomainObject);
    }

    public List<PipelineCheckpoint> findByPipelineId(String tenantId, String pipelineId, int limit) {
        logger.debug("Finding checkpoints by pipeline ID: tenantId={}, pipelineId={}, limit: {}", tenantId, pipelineId, limit);

        List<PipelineCheckpointEntity> entities = pipelineRepository.findByPipelineIdOrderByCreatedAtDesc(tenantId, pipelineId);

        return entities.stream()
                .limit(limit)
                .map(PipelineCheckpointEntity::toDomainObject)
                .collect(Collectors.toList());
    }

    public List<PipelineCheckpoint> findActive(int limit) {
        logger.debug("Finding active checkpoints, limit: {}", limit);
        
        List<PipelineCheckpointEntity> entities = pipelineRepository.findActiveExecutions();
        
        return entities.stream()
                .limit(limit)
                .map(PipelineCheckpointEntity::toDomainObject)
                .collect(Collectors.toList());
    }

    public List<PipelineCheckpoint> findStale(Instant staleBefore) {
        logger.debug("Finding stale checkpoints before: {}", staleBefore);
        
        List<PipelineCheckpointEntity> entities = pipelineRepository.findStaleExecutions(staleBefore);
        
        return entities.stream()
                .map(PipelineCheckpointEntity::toDomainObject)
                .collect(Collectors.toList());
    }

    @Override
    public void completeExecution(String instanceId, PipelineCheckpointStatus status, Map<String, Object> finalResult) {
        logger.info("Completing execution: instanceId={}, status={}", instanceId, status);

        Optional<PipelineCheckpointEntity> entityOpt = pipelineRepository.findById(instanceId);
        if (entityOpt.isEmpty()) {
            throw new CheckpointNotFoundException("Pipeline checkpoint not found: " + instanceId);
        }

        PipelineCheckpointEntity entity = entityOpt.get();
        entity.setStatus(status);
        entity.setResult(finalResult != null ? finalResult : entity.getResult());

        pipelineRepository.save(entity);

        logger.info("Completed execution: instanceId={}, status={}", instanceId, status);
    }

    @Override
    public int cleanupOldCheckpoints(Instant completedBefore) {
        logger.info("Cleaning up old checkpoints completed before: {}", completedBefore);

        // Clean up step checkpoints first
        int deletedSteps = stepRepository.deleteForCompletedPipelines(completedBefore);
        
        // Clean up pipeline checkpoints
        int deletedPipelines = pipelineRepository.deleteCompletedBefore(completedBefore);

        logger.info("Cleanup completed: {} pipeline checkpoints, {} step checkpoints deleted", 
                   deletedPipelines, deletedSteps);

        return deletedPipelines;
    }

    public boolean isDuplicate(String tenantId, String idempotencyKey) {
        logger.debug("Checking for duplicate: tenantId={}, key={}", tenantId, idempotencyKey);
        return pipelineRepository.existsByIdempotencyKey(tenantId, idempotencyKey);
    }

    public Optional<StepCheckpoint> getLastSuccessfulStep(String instanceId) {
        logger.debug("Getting last successful step for instance: {}", instanceId);
        
        return stepRepository.findLastSuccessfulStep(instanceId)
                .map(StepCheckpointEntity::toDomainObject);
    }

    @Override
    public void recordStepCheckpoint(String instanceId, StepCheckpoint stepCheckpoint) {
        logger.debug("Recording step checkpoint: instanceId={}, stepId={}, status={}", 
                    instanceId, stepCheckpoint.getStepId(), stepCheckpoint.getStatus());

        // Check if step checkpoint already exists
        Optional<StepCheckpointEntity> existingOpt = stepRepository.findByInstanceIdAndStepId(
            instanceId, stepCheckpoint.getStepId());

        StepCheckpointEntity entity;
        if (existingOpt.isPresent()) {
            // Update existing step checkpoint
            entity = existingOpt.get();
            entity.updateFrom(stepCheckpoint);
        } else {
            // Create new step checkpoint
            entity = new StepCheckpointEntity(
                instanceId, stepCheckpoint.getStepId(), stepCheckpoint.getStepName(),
                stepCheckpoint.getStatus(), stepCheckpoint.getInput());
            entity.updateFrom(stepCheckpoint);
        }

        stepRepository.save(entity);

        logger.debug("Recorded step checkpoint: instanceId={}, stepId={}, status={}", 
                    instanceId, stepCheckpoint.getStepId(), stepCheckpoint.getStatus());
    }

    /**
     * Get execution statistics for monitoring.
     */
    public ExecutionStatistics getExecutionStatistics() {
        long activeCount = pipelineRepository.countActiveExecutions();
        long completedCount = pipelineRepository.countByStatus(PipelineCheckpointStatus.COMPLETED);
        long failedCount = pipelineRepository.countByStatus(PipelineCheckpointStatus.FAILED);
        long cancelledCount = pipelineRepository.countByStatus(PipelineCheckpointStatus.CANCELLED);

        return new ExecutionStatistics(activeCount, completedCount, failedCount, cancelledCount);
    }

    /**
     * Get step execution statistics for a pipeline instance.
     */
    public List<StepCheckpoint> getStepHistory(String instanceId) {
        logger.debug("Getting step history for instance: {}", instanceId);
        
        return stepRepository.findByInstanceIdOrderByStartedAtAsc(instanceId).stream()
                .map(StepCheckpointEntity::toDomainObject)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isExecutionAllowed(String instanceId) {
        logger.debug("Checking execution allowed for instance: {}", instanceId);
        
        Optional<PipelineCheckpointEntity> entity = pipelineRepository.findByInstanceId(instanceId);
        if (entity.isEmpty()) {
            logger.warn("Checkpoint not found for instance: {}", instanceId);
            return false;
        }
        
        return entity.get().toDomainObject().isActive();
    }
}

/**
 * Exception thrown when attempting to create a duplicate execution.
 */
class DuplicateExecutionException extends RuntimeException {
    public DuplicateExecutionException(String message) {
        super(message);
    }
}

/**
 * Exception thrown when a checkpoint is not found.
 */
class CheckpointNotFoundException extends RuntimeException {
    public CheckpointNotFoundException(String message) {
        super(message);
    }
}

/**
 * Execution statistics for monitoring.
 */
class ExecutionStatistics {
    private final long activeCount;
    private final long completedCount;
    private final long failedCount;
    private final long cancelledCount;

    public ExecutionStatistics(long activeCount, long completedCount, long failedCount, long cancelledCount)
    {
        this.activeCount = activeCount;
        this.completedCount = completedCount;
        this.failedCount = failedCount;
        this.cancelledCount = cancelledCount;
    }

    public long getActiveCount()
    {
        return activeCount;
    }

    public long getCompletedCount()
    {
        return completedCount;
    }

    public long getFailedCount()
    {
        return failedCount;
    }

    public long getCancelledCount()
    {
        return cancelledCount;
    }

    public long getTotalCount()
    {
        return activeCount + completedCount + failedCount + cancelledCount;
    }
}
