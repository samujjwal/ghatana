package com.ghatana.datacloud.launcher.connectors;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * P0-08: Service for managing durable connector sync jobs.
 * 
 * <p>This service provides durable job management for connector operations (sync, test, schema discovery).
 * Jobs are persisted and can be tracked, retried, and cancelled.
 *
 * @doc.type interface
 * @doc.purpose Service for managing durable connector sync jobs
 * @doc.layer product
 * @doc.pattern Service
 */
public interface ConnectorSyncJobService {
    
    /**
     * Creates a new connector sync job.
     *
     * @param tenantId the tenant ID
     * @param connectionId the connection ID
     * @param jobType the job type (sync, test, schema-discovery)
     * @param jobConfig the job configuration
     * @param correlationId the correlation ID for tracing
     * @return promise that completes with the created job
     */
    Promise<ConnectorSyncJob> createJob(String tenantId, String connectionId, String jobType,
                                       Map<String, Object> jobConfig, String correlationId);
    
    /**
     * Gets a job by ID.
     *
     * @param jobId the job ID
     * @return promise that completes with the job, or null if not found
     */
    Promise<ConnectorSyncJob> getJob(String jobId);
    
    /**
     * Lists jobs for a connection.
     *
     * @param tenantId the tenant ID
     * @param connectionId the connection ID
     * @param limit maximum number of jobs to return
     * @return promise that completes with the list of jobs
     */
    Promise<List<ConnectorSyncJob>> listJobs(String tenantId, String connectionId, int limit);
    
    /**
     * Updates a job's state.
     *
     * @param jobId the job ID
     * @param newState the new state
     * @param evidence additional evidence to attach to the job
     * @return promise that completes when the update is done
     */
    Promise<Void> updateJobState(String jobId, ConnectorJobState newState, Map<String, Object> evidence);
    
    /**
     * Marks a job as failed.
     *
     * @param jobId the job ID
     * @param errorMessage the error message
     * @param evidence additional evidence to attach to the job
     * @return promise that completes when the update is done
     */
    Promise<Void> markJobFailed(String jobId, String errorMessage, Map<String, Object> evidence);
    
    /**
     * Cancels a job.
     *
     * @param jobId the job ID
     * @return promise that completes when the cancellation is done
     */
    Promise<Void> cancelJob(String jobId);
    
    /**
     * Retries a failed job.
     *
     * @param jobId the job ID
     * @return promise that completes with the updated job
     */
    Promise<ConnectorSyncJob> retryJob(String jobId);
    
    /**
     * Polls for pending jobs and processes them.
     *
     * @param tenantId the tenant ID (optional, if null polls all tenants)
     * @param limit maximum number of jobs to process
     * @return promise that completes with the number of jobs processed
     */
    Promise<Integer> pollAndProcess(String tenantId, int limit);
}
