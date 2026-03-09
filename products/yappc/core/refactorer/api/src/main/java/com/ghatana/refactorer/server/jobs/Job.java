package com.ghatana.refactorer.server.jobs;

import java.util.Map;

/**
 * Represents a job that can be executed by the job queue.
 
 * @doc.type interface
 * @doc.purpose Defines the contract for job
 * @doc.layer core
 * @doc.pattern Enum
*/
public interface Job {
    /**
     * Gets the unique identifier for this job.
     *
     * @return The job ID
     */
    String getId();

    /**
     * Gets the type of this job.
     *
     * @return The job type
     */
    String getType();

    /**
     * Gets the current status of this job.
     *
     * @return The job status
     */
    JobStatus getStatus();

    /**
     * Executes the job.
     *
     * @return The result of the job execution
     */
    JobResult execute();

    /**
     * Cancels the job.
     */
    void cancel();

    /**
     * Gets the metadata for this job.
     *
     * @return The job metadata
     */
    JobMetadata getMetadata();

    /**
     * Updates the status of this job.
     *
     * @param status The new status
     * @param context Additional context for the status update
     */
    void updateStatus(JobStatus status, Map<String, Object> context);

    /**
     * Job status enum.
     */
    enum JobStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED,
        RETRYING
    }

    /**
     * Job result container.
     */
    class JobResult {
        private final boolean success;
        private final String message;
        private final Throwable error;

        private JobResult(boolean success, String message, Throwable error) {
            this.success = success;
            this.message = message;
            this.error = error;
        }

        public static JobResult success(String message) {
            return new JobResult(true, message, null);
        }

        public static JobResult failure(String message, Throwable error) {
            return new JobResult(false, message, error);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Throwable getError() {
            return error;
        }
    }

    /**
     * Job metadata container.
     */
    class JobMetadata {
        private final long createdAt;
        private Long startedAt;
        private Long completedAt;
        private int retryCount;
        private final int maxRetries;
        private final Map<String, Object> context;

        public JobMetadata() {
            this(System.currentTimeMillis(), null, null, 0, 3, Map.of());
        }

        public JobMetadata(long createdAt, Long startedAt, Long completedAt, int retryCount, int maxRetries, Map<String, Object> context) {
            this.createdAt = createdAt;
            this.startedAt = startedAt;
            this.completedAt = completedAt;
            this.retryCount = retryCount;
            this.maxRetries = maxRetries;
            this.context = context != null ? Map.copyOf(context) : Map.of();
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public Long getStartedAt() {
            return startedAt;
        }

        public void setStartedAt(Long startedAt) {
            this.startedAt = startedAt;
        }

        public Long getCompletedAt() {
            return completedAt;
        }

        public void setCompletedAt(Long completedAt) {
            this.completedAt = completedAt;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public void incrementRetryCount() {
            this.retryCount++;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public Map<String, Object> getContext() {
            return context;
        }
    }
}
