package com.ghatana.yappc.services.source;

import com.ghatana.yappc.domain.source.SourceLocator;
import com.ghatana.yappc.domain.source.RepositorySnapshot;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * @doc.type interface
 * @doc.purpose Durable source import job lifecycle service for governed repository acquisition.
 *              Provides APIs to start, query, cancel, and retry source import jobs.
 * @doc.layer service
 * @doc.pattern Service
 *
 * P0: Java service interface for durable source import and snapshot job lifecycle.
 */
public interface SourceImportService {

    /**
     * Start a new source import job for the given locator.
     *
     * @param locator the source locator defining repository and scope
     * @return promise of the created import job
     */
    Promise<SourceImportJob> startImport(SourceLocator locator);

    /**
     * Get an import job by its ID.
     *
     * @param jobId the job identifier
     * @param tenantId the tenant for scope validation
     * @return promise of the job if found
     */
    Promise<Optional<SourceImportJob>> getJob(String jobId, String tenantId);

    /**
     * List import jobs for a given scope.
     *
     * @param tenantId the tenant ID
     * @param workspaceId optional workspace ID filter
     * @param projectId optional project ID filter
     * @return promise of job list
     */
    Promise<List<SourceImportJob>> listJobs(String tenantId, String workspaceId, String projectId);

    /**
     * Cancel a running import job.
     *
     * @param jobId the job identifier
     * @param tenantId the tenant for scope validation
     * @param reason the reason for cancellation
     * @return promise of success
     */
    Promise<Boolean> cancelJob(String jobId, String tenantId, String reason);

    /**
     * Retry a failed import job.
     *
     * @param jobId the job identifier
     * @param tenantId the tenant for scope validation
     * @return promise of the retried job
     */
    Promise<SourceImportJob> retryJob(String jobId, String tenantId);

    /**
     * Get the snapshot created by a completed import job.
     *
     * @param jobId the job identifier
     * @param tenantId the tenant for scope validation
     * @return promise of the snapshot if job completed successfully
     */
    Promise<Optional<RepositorySnapshot>> getJobSnapshot(String jobId, String tenantId);

    /**
     * Run the full compile pipeline: import -> scan -> extract -> graph -> model.
     *
     * @param locator the source locator
     * @return promise of the compiled model result
     */
    Promise<CompilePipelineResult> runCompilePipeline(SourceLocator locator);

    /**
     * Import job statuses.
     */
    enum JobStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED,
        RETRYING
    }

    /**
     * Source import job record.
     */
    record SourceImportJob(
            String jobId,
            String tenantId,
            String workspaceId,
            String projectId,
            SourceLocator locator,
            JobStatus status,
            int progressPercent,
            String currentStep,
            String errorMessage,
            String snapshotId,
            java.time.Instant createdAt,
            java.time.Instant updatedAt,
            java.time.Instant completedAt
    ) {}

    /**
     * Compile pipeline result containing the semantic model and metadata.
     */
    record CompilePipelineResult(
            String jobId,
            String modelId,
            RepositorySnapshot snapshot,
            String status,
            java.util.Map<String, Object> graphSummary,
            java.util.Map<String, Object> modelSummary,
            java.time.Instant completedAt
    ) {}
}
