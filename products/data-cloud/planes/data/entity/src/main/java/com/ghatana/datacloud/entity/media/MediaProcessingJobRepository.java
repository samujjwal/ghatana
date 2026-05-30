package com.ghatana.datacloud.entity.media;
import com.ghatana.platform.core.common.pagination.Page;
import com.ghatana.platform.core.common.pagination.PageRequest;
import com.ghatana.core.database.repository.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
/**
 * Repository interface for MediaProcessingJob entities.
 *
 * <p>Provides comprehensive query methods for media processing job management
 * including filtering by job type, status, priority, and performance metrics.
 *
 * @see MediaProcessingJob
 * @doc.type interface
 * @doc.purpose Data access layer for media processing jobs
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface MediaProcessingJobRepository extends Repository<MediaProcessingJob, UUID> {
    /**
     * Finds processing jobs by tenant and job ID.
     */
    Optional<MediaProcessingJob> findByTenantIdAndJobId(String tenantId, String jobId);
    /**
     * Finds processing jobs by tenant and media artifact ID.
     */
    List<MediaProcessingJob> findByTenantIdAndMediaArtifactId(String tenantId, UUID mediaArtifactId);
    /**
     * Finds processing jobs by tenant and job type.
     */
    List<MediaProcessingJob> findByTenantIdAndJobType(String tenantId, MediaProcessingJob.JobType jobType);
    /**
     * Finds processing jobs by tenant and status.
     */
    List<MediaProcessingJob> findByTenantIdAndStatus(String tenantId, MediaProcessingJob.JobStatus status);
    /**
     * Finds processing jobs by tenant and priority.
     */
    List<MediaProcessingJob> findByTenantIdAndPriority(String tenantId, MediaProcessingJob.JobPriority priority);
    /**
     * Finds processing jobs by tenant and requested by.
     */
    List<MediaProcessingJob> findByTenantIdAndRequestedBy(String tenantId, String requestedBy);
    /**
     * Finds processing jobs by tenant and worker node.
     */
    List<MediaProcessingJob> findByTenantIdAndWorkerNode(String tenantId, String workerNode);
    /**
     * Finds active processing jobs (not in terminal state).
     */
    List<MediaProcessingJob> findActiveJobs(String tenantId);
    /**
     * Finds processing jobs that can be retried.
     */
    List<MediaProcessingJob> findRetryableJobs(String tenantId);
    /**
     * Finds processing jobs that have timed out.
     */
    List<MediaProcessingJob> findTimedOutJobs(String tenantId, Instant now);
    /**
     * Finds processing jobs created within date range.
     */
    List<MediaProcessingJob> findByCreatedAtBetween(String tenantId, 
                                                   Instant startDate, 
                                                   Instant endDate);
    /**
     * Finds processing jobs started within date range.
     */
    List<MediaProcessingJob> findByStartedAtBetween(String tenantId, 
                                                   Instant startDate, 
                                                   Instant endDate);
    /**
     * Finds processing jobs completed within date range.
     */
    List<MediaProcessingJob> findByCompletedAtBetween(String tenantId, 
                                                     Instant startDate, 
                                                     Instant endDate);
    /**
     * Finds processing jobs by duration range.
     */
    List<MediaProcessingJob> findByProcessingTimeRange(String tenantId, 
                                                      Long minDuration, 
                                                      Long maxDuration);
    /**
     * Finds processing jobs by progress range.
     */
    List<MediaProcessingJob> findByProgressRange(String tenantId, 
                                                 Integer minProgress, 
                                                 Integer maxProgress);
    /**
     * Finds processing jobs by retry count range.
     */
    List<MediaProcessingJob> findByRetryCountRange(String tenantId, 
                                                   Integer minRetries, 
                                                   Integer maxRetries);
    /**
     * Finds processing jobs with errors.
     */
    List<MediaProcessingJob> findJobsWithErrors(String tenantId);
    /**
     * Finds processing jobs by error code.
     */
    List<MediaProcessingJob> findByErrorCode(String tenantId, 
                                             String errorCode);
    /**
     * Finds processing jobs with results.
     */
    List<MediaProcessingJob> findJobsWithResults(String tenantId);
    /**
     * Finds long-running jobs.
     */
    List<MediaProcessingJob> findLongRunningJobs(String tenantId, 
                                                 Instant now, 
                                                 Long thresholdMs);
    /**
     * Finds queued jobs by priority order.
     */
    List<MediaProcessingJob> findQueuedJobsByPriority(String tenantId);
    /**
     * Counts processing jobs by tenant and status.
     */
    long countByTenantIdAndStatus(String tenantId, MediaProcessingJob.JobStatus status);
    /**
     * Counts processing jobs by tenant and job type.
     */
    long countByTenantIdAndJobType(String tenantId, MediaProcessingJob.JobType jobType);
    /**
     * Counts processing jobs by tenant and priority.
     */
    long countByTenantIdAndPriority(String tenantId, MediaProcessingJob.JobPriority priority);
    /**
     * Counts active processing jobs by tenant.
     */
    long countActiveJobs(String tenantId);
    /**
     * Calculates average processing time by job type.
     */
    Double calculateAverageProcessingTime(String tenantId, 
                                         MediaProcessingJob.JobType jobType);
    /**
     * Calculates success rate by job type.
     */
    Double calculateSuccessRate(String tenantId, 
                               MediaProcessingJob.JobType jobType);
    /**
     * Finds jobs with pagination support.
     */
    Page<MediaProcessingJob> findByTenantId(String tenantId, PageRequest pageRequest);
    /**
     * Finds jobs by tenant and status with pagination.
     */
    Page<MediaProcessingJob> findByTenantIdAndStatus(String tenantId, 
                                                     MediaProcessingJob.JobStatus status, 
                                                     PageRequest pageRequest);
    /**
     * Finds jobs by tenant and job type with pagination.
     */
    Page<MediaProcessingJob> findByTenantIdAndJobType(String tenantId, 
                                                       MediaProcessingJob.JobType jobType, 
                                                       PageRequest pageRequest);
    /**
     * Searches jobs by text in job ID or status message.
     */
    List<MediaProcessingJob> searchByText(String tenantId, String searchText);
    /**
     * Finds jobs for specific artifact by type and status.
     */
    List<MediaProcessingJob> findByArtifactTypeAndStatus(String tenantId, 
                                                         UUID mediaArtifactId,
                                                         MediaProcessingJob.JobType jobType, 
                                                         MediaProcessingJob.JobStatus status);
    /**
     * Finds jobs that need attention (failed, timed out, or stuck).
     */
    List<MediaProcessingJob> findJobsNeedingAttention(String tenantId, Instant now);
    /**
     * Gets job statistics by type for tenant.
     */
    List<Object[]> getJobStatisticsByType(String tenantId);
    /**
     * Gets performance metrics by job type.
     */
    List<Object[]> getPerformanceMetricsByType(String tenantId);
}
