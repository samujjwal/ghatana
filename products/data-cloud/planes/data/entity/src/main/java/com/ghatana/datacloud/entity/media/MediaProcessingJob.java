package com.ghatana.datacloud.entity.media;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

/**
 * Represents a processing job for media artifacts in Data Cloud.
 *
 * <p><b>Purpose</b><br>
 * Tracks the lifecycle of media processing operations including transcription,
 * vision analysis, frame extraction, and other media transformations. Provides
 * comprehensive job tracking with status, progress, error handling, and results.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MediaProcessingJob job = MediaProcessingJob.builder()
 *     .mediaArtifact(artifact)
 *     .jobType(JobType.TRANSCRIPTION)
 *     .status(JobStatus.PENDING)
 *     .parameters(Map.of("languageCode", "en-US"))
 *     .build();
 * 
 * // Start processing
 * job.startProcessing();
 * 
 * // Update progress
 * job.updateProgress(50, "Processing audio frames...");
 * 
 * // Complete with results
 * job.complete(Map.of("transcript", "Hello world"));
 * }</pre>
 *
 * @see MediaArtifact
 * @see Transcript
 * @see FrameIndex
 * @doc.type class
 * @doc.purpose Media processing job with lifecycle and result tracking
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Entity)
 */
@Entity
@Table(name = "media_processing_jobs", indexes = {
    @Index(name = "idx_media_job_tenant", columnList = "tenant_id"),
    @Index(name = "idx_media_job_artifact", columnList = "media_artifact_id"),
    @Index(name = "idx_media_job_type", columnList = "job_type"),
    @Index(name = "idx_media_job_status", columnList = "status"),
    @Index(name = "idx_media_job_priority", columnList = "priority"),
    @Index(name = "idx_media_job_created", columnList = "created_at"),
    @Index(name = "idx_media_job_started", columnList = "started_at"),
    @Index(name = "idx_media_job_completed", columnList = "completed_at")
})
public class MediaProcessingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "job_id", nullable = false, unique = true, length = 255)
    private String jobId;

    @NotNull
    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @NotNull
    @Column(name = "media_artifact_id", nullable = false)
    private UUID mediaArtifactId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_artifact_id", insertable = false, updatable = false)
    private MediaArtifact mediaArtifact;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "job_type", nullable = false, length = 50)
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "status", nullable = false, length = 50)
    private JobStatus status = JobStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private JobPriority priority = JobPriority.NORMAL;

    @Column(name = "progress_percentage")
    private Integer progressPercentage = 0;

    @Column(name = "status_message", length = 1000)
    private String statusMessage;

    /**
     * Processing parameters (language code, analysis type, etc.).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private Map<String, Object> parameters = new HashMap<>();

    /**
     * Processing results (transcript text, frame indices, etc.).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "results", columnDefinition = "jsonb")
    private Map<String, Object> results = new HashMap<>();

    /**
     * Error information if the job failed.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_info", columnDefinition = "jsonb")
    private ErrorInfo errorInfo;

    @Column(name = "queue_time_ms")
    private Long queueTimeMs;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "max_retries")
    private Integer maxRetries = 3;

    @Column(name = "timeout_ms")
    private Long timeoutMs = 300000L; // 5 minutes default

    @Column(name = "requested_by", length = 255)
    private String requestedBy;

    @Column(name = "worker_node", length = 255)
    private String workerNode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Types of media processing jobs.
     */
    public enum JobType {
        TRANSCRIPTION,        // Speech-to-text transcription
        VISION_ANALYSIS,     // Computer vision analysis
        FRAME_EXTRACTION,    // Video frame extraction
        AUDIO_ANALYSIS,      // Audio feature analysis
        VIDEO_ANALYSIS,      // Video content analysis
        FORMAT_CONVERSION,   // Media format conversion
        QUALITY_ANALYSIS,    // Media quality assessment
        CONTENT_MODERATION,  // Content safety moderation
        CUSTOM_PROCESSING    // Custom processing logic
    }

    /**
     * Job status states.
     */
    public enum JobStatus {
        PENDING,             // Waiting in queue
        QUEUED,              // Queued for processing
        RUNNING,             // Currently processing
        COMPLETED,           // Successfully completed
        FAILED,              // Failed with error
        CANCELLED,           // Cancelled by user
        TIMEOUT,             // Timed out
        RETRYING,            // Scheduled for retry
        PAUSED               // Paused by admin
    }

    /**
     * Job priority levels.
     */
    public enum JobPriority {
        LOW,                 // Low priority
        NORMAL,              // Normal priority
        HIGH,                // High priority
        URGENT               // Urgent priority
    }

    /**
     * Error information structure.
     */
    public record ErrorInfo(
        String errorCode,
        String errorMessage,
        String errorDetails,
        String stackTrace,
        Instant occurredAt,
        Map<String, Object> context
    ) {
        public ErrorInfo {
            if (occurredAt == null) occurredAt = Instant.now();
            if (context == null) context = Map.of();
        }
    }

    // ============ Getters & Setters ============

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getMediaArtifactId() {
        return mediaArtifactId;
    }

    public void setMediaArtifactId(UUID mediaArtifactId) {
        this.mediaArtifactId = mediaArtifactId;
    }

    public MediaArtifact getMediaArtifact() {
        return mediaArtifact;
    }

    public void setMediaArtifact(MediaArtifact mediaArtifact) {
        this.mediaArtifact = mediaArtifact;
        if (mediaArtifact != null) {
            this.mediaArtifactId = mediaArtifact.getId();
        }
    }

    public JobType getJobType() {
        return jobType;
    }

    public void setJobType(JobType jobType) {
        this.jobType = jobType;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public JobPriority getPriority() {
        return priority;
    }

    public void setPriority(JobPriority priority) {
        this.priority = priority;
    }

    public Integer getProgressPercentage() {
        return progressPercentage;
    }

    public void setProgressPercentage(Integer progressPercentage) {
        this.progressPercentage = progressPercentage;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Map<String, Object> getResults() {
        return results;
    }

    public void setResults(Map<String, Object> results) {
        this.results = results;
    }

    public ErrorInfo getErrorInfo() {
        return errorInfo;
    }

    public void setErrorInfo(ErrorInfo errorInfo) {
        this.errorInfo = errorInfo;
    }

    public Long getQueueTimeMs() {
        return queueTimeMs;
    }

    public void setQueueTimeMs(Long queueTimeMs) {
        this.queueTimeMs = queueTimeMs;
    }

    public Long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(Long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public String getWorkerNode() {
        return workerNode;
    }

    public void setWorkerNode(String workerNode) {
        this.workerNode = workerNode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ============ Business Methods ============

    /**
     * Starts the job processing.
     */
    public void startProcessing(String workerNode) {
        if (status != JobStatus.PENDING && status != JobStatus.QUEUED && status != JobStatus.RETRYING) {
            throw new IllegalStateException("Job cannot be started from status: " + status);
        }
        
        this.status = JobStatus.RUNNING;
        this.startedAt = Instant.now();
        this.workerNode = workerNode;
        this.progressPercentage = 0;
        this.statusMessage = "Processing started";
        
        // Calculate queue time
        if (createdAt != null) {
            this.queueTimeMs = java.time.Duration.between(createdAt, startedAt).toMillis();
        }
    }

    /**
     * Updates the job progress.
     */
    public void updateProgress(int percentage, String message) {
        if (status != JobStatus.RUNNING) {
            throw new IllegalStateException("Cannot update progress for non-running job");
        }
        
        this.progressPercentage = Math.max(0, Math.min(100, percentage));
        this.statusMessage = message;
    }

    /**
     * Completes the job with results.
     */
    public void complete(Map<String, Object> results) {
        if (status != JobStatus.RUNNING) {
            throw new IllegalStateException("Job cannot be completed from status: " + status);
        }
        
        this.status = JobStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.progressPercentage = 100;
        this.statusMessage = "Processing completed successfully";
        
        if (results != null) {
            this.results.putAll(results);
        }
        
        // Calculate processing time
        if (startedAt != null) {
            this.processingTimeMs = java.time.Duration.between(startedAt, completedAt).toMillis();
        }
    }

    /**
     * Fails the job with error information.
     */
    public void fail(String errorCode, String errorMessage, String errorDetails, String stackTrace) {
        this.status = JobStatus.FAILED;
        this.completedAt = Instant.now();
        this.statusMessage = "Processing failed: " + errorMessage;
        this.errorInfo = new ErrorInfo(errorCode, errorMessage, errorDetails, stackTrace, Instant.now(), Map.of());
        
        if (startedAt != null) {
            this.processingTimeMs = java.time.Duration.between(startedAt, completedAt).toMillis();
        }
    }

    /**
     * Cancels the job.
     */
    public void cancel(String reason) {
        if (status == JobStatus.COMPLETED || status == JobStatus.FAILED || status == JobStatus.CANCELLED) {
            throw new IllegalStateException("Job cannot be cancelled from status: " + status);
        }
        
        this.status = JobStatus.CANCELLED;
        this.completedAt = Instant.now();
        this.statusMessage = "Job cancelled: " + reason;
        
        if (startedAt != null) {
            this.processingTimeMs = java.time.Duration.between(startedAt, completedAt).toMillis();
        }
    }

    /**
     * Times out the job.
     */
    public void timeout() {
        if (status != JobStatus.RUNNING) {
            throw new IllegalStateException("Job cannot timeout from status: " + status);
        }
        
        this.status = JobStatus.TIMEOUT;
        this.completedAt = Instant.now();
        this.statusMessage = "Job timed out after " + timeoutMs + "ms";
        
        if (startedAt != null) {
            this.processingTimeMs = java.time.Duration.between(startedAt, completedAt).toMillis();
        }
    }

    /**
     * Schedules the job for retry.
     */
    public void scheduleRetry() {
        if (retryCount >= maxRetries) {
            throw new IllegalStateException("Maximum retries exceeded");
        }
        
        this.status = JobStatus.RETRYING;
        this.retryCount++;
        this.statusMessage = "Scheduled for retry " + retryCount + " of " + maxRetries;
        this.startedAt = null;
        this.workerNode = null;
    }

    /**
     * Pauses the job.
     */
    public void pause(String reason) {
        if (status != JobStatus.RUNNING) {
            throw new IllegalStateException("Job cannot be paused from status: " + status);
        }
        
        this.status = JobStatus.PAUSED;
        this.statusMessage = "Job paused: " + reason;
    }

    /**
     * Resumes a paused job.
     */
    public void resume() {
        if (status != JobStatus.PAUSED) {
            throw new IllegalStateException("Job cannot be resumed from status: " + status);
        }
        
        this.status = JobStatus.RUNNING;
        this.statusMessage = "Processing resumed";
    }

    /**
     * Checks if the job can be retried.
     */
    public boolean canRetry() {
        return status == JobStatus.FAILED && retryCount < maxRetries;
    }

    /**
     * Checks if the job is in a terminal state.
     */
    public boolean isTerminal() {
        return status == JobStatus.COMPLETED || 
               status == JobStatus.FAILED || 
               status == JobStatus.CANCELLED || 
               status == JobStatus.TIMEOUT;
    }

    /**
     * Checks if the job is currently active.
     */
    public boolean isActive() {
        return status == JobStatus.PENDING || 
               status == JobStatus.QUEUED || 
               status == JobStatus.RUNNING || 
               status == JobStatus.RETRYING;
    }

    /**
     * Gets the total duration of the job in milliseconds.
     */
    public Long getTotalDurationMs() {
        if (createdAt == null) return null;
        Instant end = completedAt != null ? completedAt : Instant.now();
        return java.time.Duration.between(createdAt, end).toMillis();
    }

    /**
     * Gets the human-readable duration.
     */
    public String getHumanReadableDuration() {
        Long duration = getTotalDurationMs();
        if (duration == null) return "Unknown";
        
        long seconds = duration / 1000;
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return String.format("%d:%02d", seconds / 60, seconds % 60);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return String.format("%d:%02d:%02d", hours, minutes, seconds % 60);
    }

    // ============ Lifecycle Callbacks ============

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        
        // Generate job ID if not provided
        if (jobId == null) {
            jobId = jobType.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // ============ Builder Pattern ============

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private String jobId;
        private String tenantId;
        private UUID mediaArtifactId;
        private MediaArtifact mediaArtifact;
        private JobType jobType;
        private JobStatus status = JobStatus.PENDING;
        private JobPriority priority = JobPriority.NORMAL;
        private Integer progressPercentage = 0;
        private String statusMessage;
        private Map<String, Object> parameters = new HashMap<>();
        private Map<String, Object> results = new HashMap<>();
        private ErrorInfo errorInfo;
        private Long queueTimeMs;
        private Long processingTimeMs;
        private Integer retryCount = 0;
        private Integer maxRetries = 3;
        private Long timeoutMs = 300000L;
        private String requestedBy;
        private String workerNode;
        private Instant createdAt;
        private Instant startedAt;
        private Instant completedAt;
        private Instant updatedAt;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder jobId(String jobId) {
            this.jobId = jobId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder mediaArtifactId(UUID mediaArtifactId) {
            this.mediaArtifactId = mediaArtifactId;
            return this;
        }

        public Builder mediaArtifact(MediaArtifact mediaArtifact) {
            this.mediaArtifact = mediaArtifact;
            if (mediaArtifact != null) {
                this.mediaArtifactId = mediaArtifact.getId();
            }
            return this;
        }

        public Builder jobType(JobType jobType) {
            this.jobType = jobType;
            return this;
        }

        public Builder status(JobStatus status) {
            this.status = status;
            return this;
        }

        public Builder priority(JobPriority priority) {
            this.priority = priority;
            return this;
        }

        public Builder progressPercentage(Integer progressPercentage) {
            this.progressPercentage = progressPercentage;
            return this;
        }

        public Builder statusMessage(String statusMessage) {
            this.statusMessage = statusMessage;
            return this;
        }

        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder results(Map<String, Object> results) {
            this.results = results;
            return this;
        }

        public Builder errorInfo(ErrorInfo errorInfo) {
            this.errorInfo = errorInfo;
            return this;
        }

        public Builder queueTimeMs(Long queueTimeMs) {
            this.queueTimeMs = queueTimeMs;
            return this;
        }

        public Builder processingTimeMs(Long processingTimeMs) {
            this.processingTimeMs = processingTimeMs;
            return this;
        }

        public Builder retryCount(Integer retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder timeoutMs(Long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder requestedBy(String requestedBy) {
            this.requestedBy = requestedBy;
            return this;
        }

        public Builder workerNode(String workerNode) {
            this.workerNode = workerNode;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public MediaProcessingJob build() {
            MediaProcessingJob job = new MediaProcessingJob();
            job.id = this.id;
            job.jobId = this.jobId;
            job.tenantId = this.tenantId;
            job.mediaArtifactId = this.mediaArtifactId;
            job.mediaArtifact = this.mediaArtifact;
            job.jobType = this.jobType;
            job.status = this.status;
            job.priority = this.priority;
            job.progressPercentage = this.progressPercentage;
            job.statusMessage = this.statusMessage;
            job.parameters = this.parameters;
            job.results = this.results;
            job.errorInfo = this.errorInfo;
            job.queueTimeMs = this.queueTimeMs;
            job.processingTimeMs = this.processingTimeMs;
            job.retryCount = this.retryCount;
            job.maxRetries = this.maxRetries;
            job.timeoutMs = this.timeoutMs;
            job.requestedBy = this.requestedBy;
            job.workerNode = this.workerNode;
            job.createdAt = this.createdAt;
            job.startedAt = this.startedAt;
            job.completedAt = this.completedAt;
            job.updatedAt = this.updatedAt;
            return job;
        }
    }

    @Override
    public String toString() {
        return "MediaProcessingJob{" +
                "jobId='" + jobId + '\'' +
                ", jobType=" + jobType +
                ", status=" + status +
                ", progress=" + progressPercentage + "%" +
                '}';
    }
}
