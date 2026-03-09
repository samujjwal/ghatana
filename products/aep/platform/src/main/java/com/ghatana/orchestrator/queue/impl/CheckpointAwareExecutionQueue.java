/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.queue.impl;

import com.ghatana.orchestrator.queue.ExecutionJob;
import com.ghatana.orchestrator.queue.ExecutionQueue;
import com.ghatana.orchestrator.store.CheckpointStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Day 39: Enhanced execution queue with checkpoint store integration.
 * Provides exactly-once semantics through idempotency guards and persistent checkpoints.
 */
public class CheckpointAwareExecutionQueue implements ExecutionQueue {

    private static final Logger logger = LoggerFactory.getLogger(CheckpointAwareExecutionQueue.class);

    private final ConcurrentLinkedQueue<ExecutionJob> queue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, ExecutionJob> idempotencyKeys = new ConcurrentHashMap<>();
    private final AtomicInteger size = new AtomicInteger(0);
    private final CheckpointStore checkpointStore;

    public CheckpointAwareExecutionQueue(CheckpointStore checkpointStore) {
        this.checkpointStore = checkpointStore;
    }

    @Override
    public Promise<Void> enqueue(String tenantId, String pipelineId, Object triggerData, String idempotencyKey) {
        logger.debug("Enqueuing pipeline execution: tenantId={}, pipelineId={}, idempotencyKey={}", tenantId, pipelineId, idempotencyKey);

        // Day 39: Check for duplicate using checkpoint store
        if (checkpointStore.isDuplicate(tenantId, idempotencyKey)) {
            logger.info("Duplicate execution prevented: tenantId={}, pipelineId={}, idempotencyKey={}", tenantId, pipelineId, idempotencyKey);
            recordIdempotencyViolation(pipelineId, idempotencyKey);
            return Promise.of(null); // Silently ignore duplicate
        }

        // Check in-memory queue for recent duplicates
        if (idempotencyKeys.containsKey(idempotencyKey)) {
            logger.debug("Duplicate found in memory queue: tenantId={}, pipelineId={}, idempotencyKey={}", tenantId, pipelineId, idempotencyKey);
            return Promise.of(null); // Already queued, ignore
        }

        // Generate unique instance ID for this execution
        String instanceId = generateInstanceId(pipelineId);

        // Create execution job
        ExecutionJob job = new ExecutionJob(tenantId, pipelineId, triggerData, idempotencyKey, UUID.randomUUID().toString(), instanceId);

        try {
            // Day 39: Create checkpoint to establish exactly-once semantics
            checkpointStore.createExecution(tenantId, pipelineId, instanceId, idempotencyKey,
                Map.of("triggerData", triggerData, "queuedAt", System.currentTimeMillis()));

            // Add to in-memory queue
            queue.offer(job);
            idempotencyKeys.put(idempotencyKey, job);
            size.incrementAndGet();

            logger.info("Enqueued pipeline execution: tenantId={}, pipelineId={}, instanceId={}, idempotencyKey={}",
                       tenantId, pipelineId, instanceId, idempotencyKey);

            return Promise.of(null);

        } catch (Exception e) {
            logger.error("Failed to enqueue pipeline execution: tenantId={}, pipelineId={}, idempotencyKey={}",
                        tenantId, pipelineId, idempotencyKey, e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<List<ExecutionJob>> poll(int maxJobs, int visibilityTimeoutSeconds) {
        List<ExecutionJob> jobs = new ArrayList<>();
        for (int i = 0; i < maxJobs; i++) {
            ExecutionJob job = queue.poll();
            if (job == null) break;

            // Validate checkpoint still exists and is in correct state
            if (validateJobCheckpoint(job)) {
                jobs.add(job);
                size.decrementAndGet();
                idempotencyKeys.remove(job.getIdempotencyKey());
                logger.debug("Polled job for execution: instanceId={}, pipelineId={}",
                        job.getInstanceId(), job.getPipelineId());
            } else {
                logger.warn("Job checkpoint validation failed, skipping: instanceId={}, pipelineId={}",
                        job.getInstanceId(), job.getPipelineId());
                // Skip and try next (loop continues)
                i--; // Decrement counter to try to fill the batch
            }
        }
        return Promise.of(jobs);
    }

    @Override
    public Promise<Void> complete(String jobId, String status, Object result) {
        // CheckpointAware queue relies on CheckpointStore for completion status
        // This method is a no-op for the in-memory queue part
        return Promise.of(null);
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public Promise<Void> clear() {
        logger.info("Clearing execution queue: size={}", size.get());
        
        queue.clear();
        idempotencyKeys.clear();
        size.set(0);
        
        return Promise.of(null);
    }

    /**
     * Day 39: Validate that a job's checkpoint is in the correct state for execution.
     */
    private boolean validateJobCheckpoint(ExecutionJob job) {
        try {
            // Check if checkpoint allows execution
            boolean isExecutable = checkpointStore.isExecutionAllowed(job.getInstanceId());
            
            if (!isExecutable) {
                logger.debug("Job checkpoint not executable: instanceId={}", job.getInstanceId());
            }

            return isExecutable;

        } catch (Exception e) {
            logger.error("Error validating job checkpoint: instanceId={}", job.getInstanceId(), e);
            return false;
        }
    }

    /**
     * Generate unique instance ID for pipeline execution.
     */
    private String generateInstanceId(String pipelineId) {
        return pipelineId + "-" + UUID.randomUUID().toString().substring(0, 8) + "-" + System.currentTimeMillis();
    }

    /**
     * Record idempotency violation for monitoring.
     */
    private void recordIdempotencyViolation(String pipelineId, String idempotencyKey) {
        // TODO: Emit metrics for idempotency violations
        logger.debug("Recorded idempotency violation: pipelineId={}, idempotencyKey={}", pipelineId, idempotencyKey);
    }

    /**
     * Get queue statistics for monitoring.
     */
    public QueueStatistics getStatistics() {
        return new QueueStatistics(size.get(), idempotencyKeys.size());
    }

    /**
     * Queue statistics for monitoring.
     */
    public static class QueueStatistics {
        private final int queueSize;
        private final int idempotencyKeyCount;

        public QueueStatistics(int queueSize, int idempotencyKeyCount) {
            this.queueSize = queueSize;
            this.idempotencyKeyCount = idempotencyKeyCount;
        }

        public int getQueueSize() {
            return queueSize;
        }

        public int getIdempotencyKeyCount() {
            return idempotencyKeyCount;
        }
    }
}

