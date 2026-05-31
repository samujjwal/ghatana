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
 * Persistent record of a media processing result (transcription text, vision analysis data, etc.).
 *
 * <p>A {@code MediaProcessingResultRecord} stores the output of completed media processing
 * operations. Results are linked to a processing job and contain the processed data
 * (e.g., transcription text, detected objects, embeddings) along with metadata.
 *
 * @param resultId      globally unique identifier for this result
 * @param tenantId      tenant scope for isolation
 * @param jobId         the processing job that produced this result
 * @param artifactId    the media artifact that was processed
 * @param operation     the operation type (transcription, vision-analysis, etc.)
 * @param operationType specific operation subtype
 * @param status        result status (SUCCESS, PARTIAL, FAILED)
 * @param resultData    the processed result data (transcript, analysis, etc.)
 * @param resultUri     URI to the stored result (if large data)
 * @param confidence    confidence score for the result (0.0 to 1.0)
 * @param metadata      additional result metadata
 * @param createdAt     when the result record was created
 *
 * @doc.type record
 * @doc.purpose Metadata record for a media processing result
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record MediaProcessingResultRecord(
        String resultId,
        String tenantId,
        String jobId,
        String artifactId,
        String operation,
        String operationType,
        String status,
        String resultData,
        String resultUri,
        Double confidence,
        Map<String, String> metadata,
        Instant createdAt) {

    public MediaProcessingResultRecord {
        Objects.requireNonNull(resultId, "resultId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        if (resultId.isBlank()) throw new IllegalArgumentException("resultId must not be blank");
        if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        if (jobId.isBlank()) throw new IllegalArgumentException("jobId must not be blank");
        if (artifactId.isBlank()) throw new IllegalArgumentException("artifactId must not be blank");
        if (operation.isBlank()) throw new IllegalArgumentException("operation must not be blank");
        if (status.isBlank()) throw new IllegalArgumentException("status must not be blank");

        if (confidence != null && (confidence < 0.0 || confidence > 1.0)) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }

        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Creates a new result record with a generated UUID, stamped at {@code Instant.now()}.
     *
     * @param tenantId      tenant scope
     * @param jobId         the processing job that produced this result
     * @param artifactId    the media artifact that was processed
     * @param operation     the operation type
     * @param operationType specific operation subtype
     * @param status        result status (defaults to SUCCESS)
     * @param resultData    the processed result data
     * @param resultUri     URI to the stored result
     * @param confidence    confidence score
     * @param metadata      additional result metadata
     * @return a new MediaProcessingResultRecord with a generated resultId and current timestamp
     */
    public static MediaProcessingResultRecord create(
            String tenantId,
            String jobId,
            String artifactId,
            String operation,
            String operationType,
            String status,
            String resultData,
            String resultUri,
            Double confidence,
            Map<String, String> metadata) {
        return new MediaProcessingResultRecord(
                UUID.randomUUID().toString(),
                tenantId,
                jobId,
                artifactId,
                operation,
                operationType,
                status != null ? status : "SUCCESS",
                resultData,
                resultUri,
                confidence,
                metadata,
                Instant.now());
    }

    /**
     * Creates a new result record with minimal required fields.
     *
     * @param tenantId   tenant scope
     * @param jobId      the processing job that produced this result
     * @param artifactId the media artifact that was processed
     * @param operation  the operation type
     * @param resultData the processed result data
     * @return a new MediaProcessingResultRecord with default values
     */
    public static MediaProcessingResultRecord create(
            String tenantId,
            String jobId,
            String artifactId,
            String operation,
            String resultData) {
        return create(tenantId, jobId, artifactId, operation, null, "SUCCESS", resultData, null, null, Map.of());
    }
}
