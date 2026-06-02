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
 * <p>WS3-5: Canonical durable media job model with comprehensive lifecycle tracking.
 * Tracks transcription and vision analysis jobs with their lifecycle, results,
 * error states, retry logic, and observability.
 *
 * @param jobId            globally unique job identifier
 * @param artifactId       associated media artifact ID
 * @param tenantId         tenant scope for isolation
 * @param jobType          type of processing (TRANSCRIPTION, VISION_ANALYSIS)
 * @param status           job lifecycle status (QUEUED, PROCESSING, COMPLETED, FAILED, CANCELLED)
 * @param parameters       job-specific parameters (language, analysis type, etc.)
 * @param resultId         ID of the result (transcript ID or frame index ID)
 * @param errorMessage     error message if job failed
 * @param progress         progress percentage (0-100)
 * @param queuedAt         job queue timestamp
 * @param startedAt        job start timestamp (null until started)
 * @param completedAt      job completion timestamp (null until completed)
 * @param attempt          current attempt number (1-based)
 * @param maxAttempts      maximum retry attempts
 * @param processorId      processor identifier (e.g., stt-service, vision-service)
 * @param processorVersion processor version for reproducibility
 * @param inputArtifactId  input artifact ID (may differ from artifactId for chained jobs)
 * @param outputArtifactIds output artifact IDs (may be multiple for multi-output jobs)
 * @param traceId          distributed trace ID for observability
 * @param requestId        request ID for correlation
 * @param failureCode      structured failure code for categorization
 * @param failureReason    detailed failure reason
 * @param retryable        whether the job is retryable
 * @param cancelledBy      user ID who cancelled the job (null if not cancelled)
 * @param cancelledAt      cancellation timestamp (null if not cancelled)
 * @param createdBy        user ID who initiated the job
 *
 * @doc.type record
 * @doc.purpose Canonical durable media job model with comprehensive lifecycle tracking
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
        Instant queuedAt,
        Instant startedAt,
        Instant completedAt,
        int attempt,
        int maxAttempts,
        String processorId,
        String processorVersion,
        String inputArtifactId,
        java.util.List<String> outputArtifactIds,
        String traceId,
        String requestId,
        String failureCode,
        String failureReason,
        boolean retryable,
        String cancelledBy,
        Instant cancelledAt,
        String createdBy) {

    public MediaProcessingJob {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(jobType, "jobType must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(queuedAt, "queuedAt must not be null");

        if (jobId.isBlank()) throw new IllegalArgumentException("jobId must not be blank");
        if (artifactId.isBlank()) throw new IllegalArgumentException("artifactId must not be blank");
        if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        if (progress < 0 || progress > 100) throw new IllegalArgumentException("progress must be between 0 and 100");
        if (attempt < 1) throw new IllegalArgumentException("attempt must be at least 1");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be at least 1");
        if (attempt > maxAttempts) throw new IllegalArgumentException("attempt cannot exceed maxAttempts");

        parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
        outputArtifactIds = outputArtifactIds != null ? java.util.List.copyOf(outputArtifactIds) : java.util.List.of();
    }

    /**
     * Job types for media processing.
     */
    public enum JobType {
        TRANSCRIPTION,
        VISION_ANALYSIS,
        MULTIMODAL_INDEXING,
        RETRY
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
     * Creates a new processing job with defaults for optional fields.
     *
     * @param artifactId       associated artifact ID
     * @param tenantId         tenant scope
     * @param jobType          type of processing
     * @param parameters       job parameters
     * @param processorId      processor identifier
     * @param processorVersion processor version
     * @param traceId          trace ID for observability
     * @param requestId        request ID for correlation
     * @param createdBy        user ID who initiated the job
     * @return a new MediaProcessingJob with generated ID and defaults
     */
    public static MediaProcessingJob create(
            String artifactId,
            String tenantId,
            JobType jobType,
            Map<String, String> parameters,
            String processorId,
            String processorVersion,
            String traceId,
            String requestId,
            String createdBy) {
        Instant now = Instant.now();
        return new MediaProcessingJob(
            UUID.randomUUID().toString(),
            artifactId,
            tenantId,
            jobType,
            JobStatus.QUEUED,
            parameters,
            null, // resultId
            null, // errorMessage
            0, // progress
            now, // queuedAt
            null, // startedAt
            null, // completedAt
            1, // attempt
            3, // maxAttempts (default)
            processorId,
            processorVersion,
            artifactId, // inputArtifactId defaults to artifactId
            java.util.List.of(), // outputArtifactIds
            traceId,
            requestId,
            null, // failureCode
            null, // failureReason
            true, // retryable (default)
            null, // cancelledBy
            null, // cancelledAt
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
                resultId, errorMessage, progress, queuedAt, newStartedAt, newCompletedAt,
                attempt, maxAttempts, processorId, processorVersion, inputArtifactId, outputArtifactIds,
                traceId, requestId, failureCode, failureReason, retryable, cancelledBy, cancelledAt, createdBy);
    }

    /**
     * Returns a copy with updated progress.
     */
    public MediaProcessingJob withProgress(int newProgress) {
        return new MediaProcessingJob(
                jobId, artifactId, tenantId, jobType, status, parameters,
                resultId, errorMessage, Math.max(0, Math.min(100, newProgress)),
                queuedAt, startedAt, completedAt, attempt, maxAttempts, processorId, processorVersion,
                inputArtifactId, outputArtifactIds, traceId, requestId, failureCode, failureReason,
                retryable, cancelledBy, cancelledAt, createdBy);
    }

    /**
     * Returns a copy with updated result.
     */
    public MediaProcessingJob withResult(String newResultId) {
        return new MediaProcessingJob(
                jobId, artifactId, tenantId, jobType, status, parameters,
                newResultId, errorMessage, progress, queuedAt, startedAt, completedAt,
                attempt, maxAttempts, processorId, processorVersion, inputArtifactId, outputArtifactIds,
                traceId, requestId, failureCode, failureReason, retryable, cancelledBy, cancelledAt, createdBy);
    }

    /**
     * Returns a copy with updated error.
     */
    public MediaProcessingJob withError(String newErrorMessage) {
        return new MediaProcessingJob(
                jobId, artifactId, tenantId, jobType, JobStatus.FAILED, parameters,
                resultId, newErrorMessage, progress, queuedAt, startedAt, completedAt,
                attempt, maxAttempts, processorId, processorVersion, inputArtifactId, outputArtifactIds,
                traceId, requestId, failureCode, failureReason, retryable, cancelledBy, cancelledAt, createdBy);
    }
}
