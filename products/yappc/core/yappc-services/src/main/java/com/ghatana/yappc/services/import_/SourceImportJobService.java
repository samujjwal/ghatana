package com.ghatana.yappc.services.import_;

import com.ghatana.yappc.api.SourceImportJob;
import com.ghatana.yappc.storage.SourceImportJobRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * @doc.type interface
 * @doc.purpose Service interface for managing async source import jobs with progress tracking, audit logging, and cancellation
 * @doc.layer service
 * @doc.pattern Service
 * 
 * P2.6: Async job orchestration service replacing in-memory job storage with durable persistence.
 * Supports job submission, progress updates, cancellation, and status queries.
 */
public interface SourceImportJobService {

    /**
     * Creates a durable SourceImportJobService instance.
     */
    static SourceImportJobService create(SourceImportJobRepository jobRepository) {
        return new SourceImportJobServiceImpl(jobRepository);
    }

    /**
     * Submit a new source import job for execution.
     * @param request Job submission parameters
     * @return Promise with the created job ID
     */
    Promise<String> submitJob(SourceImportJobRequest request);

    /**
     * Get the current status of a job.
     * @param jobId Job identifier
     * @return Promise with the job state
     */
    Promise<SourceImportJob> getJobStatus(String jobId);

    /**
     * Get the current status of a job with scope validation.
     * P0: Added scoped job lookup to prevent cross-tenant/workspace/project data leakage.
     * @param jobId Job identifier
     * @param tenantId Tenant identifier
     * @param workspaceId Workspace identifier
     * @param projectId Project identifier
     * @return Promise with the job state (null if scope doesn't match)
     */
    Promise<SourceImportJob> getJobStatus(String jobId, String tenantId, String workspaceId, String projectId);

    /**
     * Cancel a running job.
     * @param jobId Job identifier
     * @param cancelledBy User requesting cancellation
     * @return Promise indicating if cancellation was successful
     */
    Promise<Boolean> cancelJob(String jobId, String cancelledBy);

    /**
     * List jobs for a tenant and product.
     * @param tenantId Tenant identifier
     * @param projectId Product identifier
     * @param limit Maximum number of jobs to return
     * @return Promise with list of jobs
     */
    Promise<java.util.List<SourceImportJob>> listJobs(String tenantId, String projectId, int limit);

    /**
     * List active jobs by status for a tenant.
     * @param tenantId Tenant identifier
     * @param status Job status to filter by
     * @param limit Maximum number of jobs to return
     * @return Promise with list of jobs
     */
    Promise<java.util.List<SourceImportJob>> listJobsByStatus(String tenantId, SourceImportJob.JobStatus status, int limit);

    /**
     * Update job progress incrementally.
     * @param jobId Job identifier
     * @param currentStep Current step number
     * @param totalSteps Total number of steps
     * @param percentage Completion percentage (0-100)
     * @param currentPhase Current phase description
     * @return Promise indicating success
     */
    Promise<Void> updateProgress(String jobId, int currentStep, int totalSteps, double percentage, String currentPhase);

    /**
     * Update job status.
     * @param jobId Job identifier
     * @param status New status
     * @return Promise indicating success
     */
    Promise<Void> updateStatus(String jobId, SourceImportJob.JobStatus status);

    /**
     * Register a progress callback for a job.
     * @param jobId Job identifier
     * @param callback Callback function invoked on progress updates
     */
    void registerProgressCallback(String jobId, Consumer<SourceImportJob> callback);

    /**
     * Remove a progress callback.
     * @param jobId Job identifier
     */
    void unregisterProgressCallback(String jobId);
}

/**
 * Production implementation of SourceImportJobService with durable persistence and async execution.
 * P2.6: Replaces in-memory job storage with database-backed job management.
 */
final class SourceImportJobServiceImpl implements SourceImportJobService {

    private static final Logger log = LoggerFactory.getLogger(SourceImportJobServiceImpl.class);

    private final SourceImportJobRepository jobRepository;
    private final Map<String, Consumer<SourceImportJob>> progressCallbacks = new ConcurrentHashMap<>();

    public SourceImportJobServiceImpl(SourceImportJobRepository jobRepository) {
        this.jobRepository = Objects.requireNonNull(jobRepository, "jobRepository must not be null");
    }

    @Override
    public Promise<String> submitJob(SourceImportJobRequest request) {
        String jobId = UUID.randomUUID().toString();
        log.info("Submitting source import job {} for tenant {}, project {}, source: {}", 
            jobId, request.tenantId(), request.projectId(), request.sourceUrl());

        SourceImportJob job = SourceImportJob.builder()
            .jobId(jobId)
            .projectId(request.projectId())
            .workspaceId(request.workspaceId())
            .tenantId(request.tenantId())
            .sourceUrl(request.sourceUrl())
            .sourceType(request.sourceType())
            .status(SourceImportJob.JobStatus.SUBMITTED)
            .progress(new SourceImportJob.JobProgress(0, 5, 0, "SUBMITTED"))
            .submittedAt(Instant.now())
            .submittedBy(request.submittedBy())
            .metadata(request.metadata() != null ? request.metadata() : Map.of())
            .build();

        return jobRepository.saveJob(job)
            .map(saved -> {
                log.info("Job {} saved successfully with status {}", saved.jobId(), saved.status());
                return saved.jobId();
            })
            .whenException(e -> log.error("Failed to submit job {}", jobId, e));
    }

    @Override
    public Promise<SourceImportJob> getJobStatus(String jobId) {
        return jobRepository.findJobById(jobId)
            .whenComplete((job, e) -> {
                if (job != null) {
                    notifyProgressCallbacks(job);
                }
            });
    }

    @Override
    public Promise<SourceImportJob> getJobStatus(String jobId, String tenantId, String workspaceId, String projectId) {
        return jobRepository.findJobById(jobId, tenantId, workspaceId, projectId)
            .whenComplete((job, e) -> {
                if (job != null) {
                    notifyProgressCallbacks(job);
                }
            });
    }

    @Override
    public Promise<Boolean> cancelJob(String jobId, String cancelledBy) {
        log.info("Cancellation requested for job {} by {}", jobId, cancelledBy);
        return jobRepository.cancelJob(jobId, cancelledBy)
            .then(cancelled -> {
                if (cancelled) {
                    log.info("Job {} successfully cancelled", jobId);
                } else {
                    log.warn("Job {} could not be cancelled (not found or not in cancellable state)", jobId);
                }
                return Promise.of(cancelled);
            });
    }

    @Override
    public Promise<java.util.List<SourceImportJob>> listJobs(String tenantId, String projectId, int limit) {
        return jobRepository.findJobsByTenantAndProduct(tenantId, projectId, limit);
    }

    @Override
    public Promise<java.util.List<SourceImportJob>> listJobsByStatus(String tenantId, SourceImportJob.JobStatus status, int limit) {
        return jobRepository.findJobsByStatus(tenantId, status, limit);
    }

    @Override
    public Promise<Void> updateProgress(String jobId, int currentStep, int totalSteps, double percentage, String currentPhase) {
        return jobRepository.updateProgress(jobId, currentStep, totalSteps, percentage, currentPhase)
            .then(v -> jobRepository.findJobById(jobId))
            .whenComplete((job, e) -> {
                if (job != null) {
                    notifyProgressCallbacks(job);
                }
            })
            .map(v -> null);
    }

    @Override
    public Promise<Void> updateStatus(String jobId, SourceImportJob.JobStatus status) {
        return jobRepository.updateStatus(jobId, status)
            .then(v -> jobRepository.findJobById(jobId))
            .whenComplete((job, e) -> {
                if (job != null) {
                    notifyProgressCallbacks(job);
                }
            })
            .map(v -> null);
    }

    @Override
    public void registerProgressCallback(String jobId, Consumer<SourceImportJob> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        progressCallbacks.put(jobId, callback);
        log.debug("Registered progress callback for job {}", jobId);
    }

    @Override
    public void unregisterProgressCallback(String jobId) {
        progressCallbacks.remove(jobId);
        log.debug("Unregistered progress callback for job {}", jobId);
    }

    private void notifyProgressCallbacks(SourceImportJob job) {
        Consumer<SourceImportJob> callback = progressCallbacks.get(job.jobId());
        if (callback != null) {
            try {
                callback.accept(job);
            } catch (Exception e) {
                log.error("Error in progress callback for job {}", job.jobId(), e);
            }
        }
    }
}
