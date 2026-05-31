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
 * Persistent record of a media processing job (transcription, vision analysis, etc.).
 *
 * <p>A {@code MediaProcessingJobRecord} tracks the lifecycle of async processing
 * operations on media artifacts. Jobs are created when processing is requested
 * and updated as the job progresses through its lifecycle.
 *
 * @param jobId         globally unique identifier for this job
 * @param tenantId      tenant scope for isolation
 * @param artifactId    the media artifact being processed
 * @param agentId       the agent that initiated the job
 * @param operation     the operation type (transcription, vision-analysis, etc.)
 * @param operationType specific operation subtype (e.g., OBJECT_DETECTION, en-US)
 * @param status        job status (PENDING, QUEUED, PROCESSING, COMPLETED, FAILED, CANCELLED)
 * @param startedAt     when the job started processing
 * @param completedAt   when the job completed (null if not completed)
 * @param errorMessage  error message if the job failed
 * @param metadata      additional job metadata
 * @param createdAt     when the job record was created
 *
 * @doc.type record
 * @doc.purpose Metadata record for a media processing job
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record MediaProcessingJobRecord(
        String jobId,
        String tenantId,
        String artifactId,
        String agentId,
        String operation,
        String operationType,
        String status,
        Instant startedAt,
        Instant completedAt,
        String errorMessage,
        Map<String, String> metadata,
        Instant createdAt) {

    public MediaProcessingJobRecord {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        if (jobId.isBlank()) throw new IllegalArgumentException("jobId must not be blank");
        if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        if (artifactId.isBlank()) throw new IllegalArgumentException("artifactId must not be blank");
        if (operation.isBlank()) throw new IllegalArgumentException("operation must not be blank");
        if (status.isBlank()) throw new IllegalArgumentException("status must not be blank");

        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Creates a new job record with a generated UUID, stamped at {@code Instant.now()}.
     *
     * @param tenantId      tenant scope
     * @param artifactId    the media artifact being processed
     * @param agentId       the agent that initiated the job
     * @param operation     the operation type
     * @param operationType specific operation subtype
     * @param status        job status (defaults to PENDING)
     * @param metadata      additional job metadata
     * @return a new MediaProcessingJobRecord with a generated jobId and current timestamp
     */
    public static MediaProcessingJobRecord create(
            String tenantId,
            String artifactId,
            String agentId,
            String operation,
            String operationType,
            String status,
            Map<String, String> metadata) {
        return new MediaProcessingJobRecord(
                UUID.randomUUID().toString(),
                tenantId,
                artifactId,
                agentId,
                operation,
                operationType,
                status != null ? status : "PENDING",
                null, // startedAt
                null, // completedAt
                null, // errorMessage
                metadata,
                Instant.now());
    }

    /**
     * Creates a new job record with minimal required fields.
     *
     * @param tenantId   tenant scope
     * @param artifactId the media artifact being processed
     * @param agentId    the agent that initiated the job
     * @param operation  the operation type
     * @return a new MediaProcessingJobRecord with default values
     */
    public static MediaProcessingJobRecord create(
            String tenantId,
            String artifactId,
            String agentId,
            String operation) {
        return create(tenantId, artifactId, agentId, operation, null, "PENDING", Map.of());
    }
}
