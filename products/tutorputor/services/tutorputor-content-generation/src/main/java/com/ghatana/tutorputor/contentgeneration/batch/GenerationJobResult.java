package com.ghatana.tutorputor.contentgeneration.batch;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable result of a generation job execution.
 *
 * @doc.type record
 * @doc.purpose Output result of a generation job execution
 * @doc.layer domain
 * @doc.pattern ValueObject
 */
public record GenerationJobResult(
        String jobId,
        GenerationJobType jobType,
        Status status,
        Map<String, Object> outputData,
        Map<String, Object> diagnostics,
        String errorMessage,
        long durationMs
) {
    /**
     * Job completion status.
     */
    public enum Status {
        COMPLETED,
        FAILED
    }

    public GenerationJobResult {
        Objects.requireNonNull(jobId, "jobId cannot be null");
        Objects.requireNonNull(jobType, "jobType cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        outputData = outputData != null
                ? Collections.unmodifiableMap(outputData)
                : Collections.emptyMap();
        diagnostics = diagnostics != null
                ? Collections.unmodifiableMap(diagnostics)
                : Collections.emptyMap();
    }

    /**
     * Create a successful result.
     */
    public static GenerationJobResult success(
            String jobId,
            GenerationJobType jobType,
            Map<String, Object> outputData,
            Map<String, Object> diagnostics,
            long durationMs) {
        return new GenerationJobResult(
                jobId, jobType, Status.COMPLETED, outputData, diagnostics, null, durationMs);
    }

    /**
     * Create a failed result.
     */
    public static GenerationJobResult failure(
            String jobId,
            GenerationJobType jobType,
            String errorMessage,
            long durationMs) {
        return new GenerationJobResult(
                jobId, jobType, Status.FAILED, Map.of(), Map.of(), errorMessage, durationMs);
    }

    public boolean isSuccess() {
        return status == Status.COMPLETED;
    }
}
