package com.ghatana.orchestrator.queue;

import io.activej.promise.Promise;
import java.util.List;

/**
 * Interface for pipeline execution queue.
 * 
 * <p>Provides asynchronous queue operations for scheduling and managing pipeline execution jobs.
 * Supports idempotency, job polling with visibility timeout, and completion tracking.</p>
 * 
 * @doc.type interface
 * @doc.purpose Defines contract for async pipeline execution job queue management
 * @doc.layer product
 * @doc.pattern Repository
 * @since 2.0.0
 */
public interface ExecutionQueue {

    /**
     * Enqueue a pipeline execution job.
     * 
     * @param tenantId Tenant identifier
     * @param pipelineId The ID of the pipeline to execute
     * @param triggerData The data that triggered the pipeline execution
     * @param idempotencyKey Unique key to prevent duplicate executions
     * @return Promise that completes when the job is enqueued
     */
    Promise<Void> enqueue(String tenantId, String pipelineId, Object triggerData, String idempotencyKey);

    /**
     * Poll for jobs to execute.
     *
     * @param maxJobs Maximum number of jobs to claim
     * @param visibilityTimeoutSeconds How long to lock the jobs
     * @return List of claimed jobs
     */
    Promise<List<ExecutionJob>> poll(int maxJobs, int visibilityTimeoutSeconds);

    /**
     * Mark a job as completed.
     *
     * @param jobId The job identifier
     * @param status Final status
     * @param result Execution result
     */
    Promise<Void> complete(String jobId, String status, Object result);

    /**
     * Get the current size of the queue.
     */
    int size();

    /**
     * Check if the queue is empty.
     */
    boolean isEmpty();

    /**
     * Clear all pending jobs from the queue.
     */
    Promise<Void> clear();
}
