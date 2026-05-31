/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.media;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Record of a media processing job for audio/video artifacts.
 *
 * <p>Pass 6 - Audio-video first-class modality: Tracks transcription and
 * vision analysis jobs with their lifecycle, results, and error states.
 *
 * @param jobId        globally unique job identifier
 * @param artifactId   associated media artifact ID
 * @param tenantId     tenant scope for isolation
 * @param jobType      type of processing (TRANSCRIPTION, VISION_ANALYSIS)
 * @param status       job lifecycle status (QUEUED, PROCESSING, COMPLETED, FAILED, CANCELLED)
 * @param parameters   job-specific parameters (language, analysis type, etc.)
 * @param resultId     ID of the result (transcript ID or frame index ID)
 * @param errorMessage error message if job failed
 * @param progress     progress percentage (0-100)
 * @param createdAt    job creation timestamp
 * @param startedAt    job start timestamp (null until started)
 * @param completedAt  job completion timestamp (null until completed)
 * @param createdBy    user ID who initiated the job
 *
 * @doc.type record
 * @doc.purpose Media processing job lifecycle tracking for Pass 6
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record MediaProcessingJob(
        String jobId,
        String artifactId,
        String tenantId,
        JobType jobType,
        JobStatus status,
        Map<String, String> parameters,
        String resultId,
        String errorMessage,
        int progress,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        String createdBy) {

    public MediaProcessingJob {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(jobType, "jobType must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        if (jobId.isBlank()) throw new IllegalArgumentException("jobId must not be blank");
        if (artifactId.isBlank()) throw new IllegalArgumentException("artifactId must not be blank");
        if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        if (progress < 0 || progress > 100) throw new IllegalArgumentException("progress must be between 0 and 100");

        parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
    }

    /**
     * Job types for media processing.
     */
    public enum JobType {
        TRANSCRIPTION,
        VISION_ANALYSIS
    }

    /**
     * Job lifecycle statuses.
     */
    public enum JobStatus {
        QUEUED,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    /**
     * Creates a new processing job.
     *
     * @param artifactId  associated artifact ID
     * @param tenantId    tenant scope
     * @param jobType     type of processing
     * @param parameters  job parameters
     * @param createdBy   user ID who initiated the job
     * @return a new MediaProcessingJob with generated ID
     */
    public static MediaProcessingJob create(
            String artifactId,
            String tenantId,
            JobType jobType,
            Map<String, String> parameters,
            String createdBy) {
        Instant now = Instant.now();
        return new MediaProcessingJob(
                UUID.randomUUID().toString(),
                artifactId,
                tenantId,
                jobType,
                JobStatus.QUEUED,
                parameters,
                null,
                null,
                0,
                now,
                null,
                null,
                createdBy);
    }

    /**
     * Returns true if the job is in a terminal state.
     */
    public boolean isTerminal() {
        return status == JobStatus.COMPLETED || status == JobStatus.FAILED || status == JobStatus.CANCELLED;
    }

    /**
     * Returns true if the job was successful.
     */
    public boolean isSuccessful() {
        return status == JobStatus.COMPLETED && resultId != null;
    }

    /**
     * Returns a copy with updated status and timestamps.
     */
    public MediaProcessingJob withStatus(JobStatus newStatus) {
        Instant now = Instant.now();
        Instant newStartedAt = this.startedAt;
        Instant newCompletedAt = this.completedAt;

        if (newStatus == JobStatus.PROCESSING && this.startedAt == null) {
            newStartedAt = now;
        }
        if ((newStatus == JobStatus.COMPLETED || newStatus == JobStatus.FAILED || newStatus == JobStatus.CANCELLED)
                && this.completedAt == null) {
            newCompletedAt = now;
        }

        return new MediaProcessingJob(
                jobId, artifactId, tenantId, jobType, newStatus, parameters,
                resultId, errorMessage, progress, createdAt, newStartedAt, newCompletedAt, createdBy);
    }

    /**
     * Returns a copy with updated progress.
     */
    public MediaProcessingJob withProgress(int newProgress) {
        return new MediaProcessingJob(
                jobId, artifactId, tenantId, jobType, status, parameters,
                resultId, errorMessage, Math.max(0, Math.min(100, newProgress)),
                createdAt, startedAt, completedAt, createdBy);
    }

    /**
     * Returns a copy with updated result.
     */
    public MediaProcessingJob withResult(String newResultId) {
        return new MediaProcessingJob(
                jobId, artifactId, tenantId, jobType, status, parameters,
                newResultId, errorMessage, progress, createdAt, startedAt, completedAt, createdBy);
    }

    /**
     * Returns a copy with updated error.
     */
    public MediaProcessingJob withError(String newErrorMessage) {
        return new MediaProcessingJob(
                jobId, artifactId, tenantId, jobType, JobStatus.FAILED, parameters,
                resultId, newErrorMessage, progress, createdAt, startedAt, completedAt, createdBy);
    }
}
