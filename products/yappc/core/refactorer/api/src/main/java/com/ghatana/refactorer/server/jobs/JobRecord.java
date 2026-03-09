package com.ghatana.refactorer.server.jobs;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable job record persisted by the refactorer service.
 *
 * @doc.type record
 * @doc.purpose Capture job identifiers, tenant metadata, and run state for persistence and transport.
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record JobRecord(
        String jobId,
        JobState state,
        int currentPass,
        long createdAt,
        long updatedAt,
        String tenantId,
        Map<String, String> attributes,
        String errorMessage) {

    public JobRecord {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(attributes, "attributes must not be null");
    }

    public static JobRecord newQueued(
            String jobId, String tenantId, Map<String, String> attributes) {
        long now = Instant.now().toEpochMilli();
        return new JobRecord(
                jobId, JobState.QUEUED, 0, now, now, tenantId, Map.copyOf(attributes), null);
    }

    public JobRecord transition(JobState newState, int newPass, String error) {
        return new JobRecord(
                jobId,
                newState,
                newPass,
                createdAt,
                Instant.now().toEpochMilli(),
                tenantId,
                attributes,
                error);
    }

    public Optional<String> error() {
        return Optional.ofNullable(errorMessage);
    }
}
