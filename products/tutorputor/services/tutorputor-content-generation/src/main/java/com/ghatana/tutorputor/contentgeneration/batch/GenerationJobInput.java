package com.ghatana.tutorputor.contentgeneration.batch;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable input for a single batch generation job.
 *
 * @doc.type record
 * @doc.purpose Input descriptor for a generation job execution
 * @doc.layer domain
 * @doc.pattern ValueObject
 */
public record GenerationJobInput(
        String jobId,
        String requestId,
        String tenantId,
        GenerationJobType jobType,
        String topic,
        String domain,
        String gradeLevel,
        String targetRef,
        Map<String, Object> parameters
) {
    public GenerationJobInput {
        Objects.requireNonNull(jobId, "jobId cannot be null");
        Objects.requireNonNull(requestId, "requestId cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(jobType, "jobType cannot be null");
        Objects.requireNonNull(topic, "topic cannot be null");
        Objects.requireNonNull(domain, "domain cannot be null");
        parameters = parameters != null
                ? Collections.unmodifiableMap(parameters)
                : Collections.emptyMap();
    }
}
