package com.ghatana.refactorer.server.jobs;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.EventId;
import com.ghatana.platform.domain.domain.event.GEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Factory for job-related events.
 *
 *
 *
 * <p>
 * This factory creates strongly-typed events for job lifecycle transitions
 *
 * that are emitted to the EventBus for system-wide visibility and event-driven
 *
 * architecture compliance.</p>
 *
 *
 *
 * <p>
 * All events include tenant context for multi-tenant isolation and follow
 *
 * the EventCloud v4 specification from core/domain-models.</p>
 *
 *
 *
 * @doc.type class
 *
 * @doc.purpose Emit structured events whenever jobs change state so listeners
 * can react.
 *
 * @doc.layer product
 *
 * @doc.pattern Event Emitter
 *
 */
public final class JobEvents {

    private static final String EVENT_VERSION = "1.0.0";

    private JobEvents() {
        // Factory class
    }

    /**
     * Create a job.started event.
     *
     * @param jobId the job identifier
     * @param tenantId the tenant context
     * @param jobType the type of job
     * @return the event
     */
    public static GEvent jobStarted(String jobId, String tenantId, String jobType) {
        return createJobEvent(
                "job.started",
                tenantId,
                jobId,
                jobType,
                "startedAt", Instant.now().toString()
        );
    }

    /**
     * Create a job.completed event.
     *
     * @param jobId the job identifier
     * @param tenantId the tenant context
     * @param jobType the type of job
     * @param durationMs duration in milliseconds
     * @return the event
     */
    public static GEvent jobCompleted(String jobId, String tenantId, String jobType, long durationMs) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("jobId", jobId);
        payload.put("tenantId", tenantId);
        payload.put("jobType", jobType);
        payload.put("durationMs", durationMs);
        payload.put("completedAt", Instant.now().toString());

        return buildEvent("job.completed", tenantId, jobId, payload);
    }

    /**
     * Create a job.failed event.
     *
     * @param jobId the job identifier
     * @param tenantId the tenant context
     * @param jobType the type of job
     * @param errorMessage the error message
     * @return the event
     */
    public static GEvent jobFailed(String jobId, String tenantId, String jobType, String errorMessage) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("jobId", jobId);
        payload.put("tenantId", tenantId);
        payload.put("jobType", jobType);
        payload.put("errorMessage", errorMessage);
        payload.put("failedAt", Instant.now().toString());

        return buildEvent("job.failed", tenantId, jobId, payload);
    }

    /**
     * Create a job.cancelled event.
     *
     * @param jobId the job identifier
     * @param tenantId the tenant context
     * @param jobType the type of job
     * @return the event
     */
    public static GEvent jobCancelled(String jobId, String tenantId, String jobType) {
        return createJobEvent(
                "job.cancelled",
                tenantId,
                jobId,
                jobType,
                "cancelledAt", Instant.now().toString()
        );
    }

    /**
     * Helper to create a simple job event.
     */
    private static GEvent createJobEvent(
            String eventType,
            String tenantId,
            String jobId,
            String jobType,
            String additionalKey,
            String additionalValue) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("jobId", jobId);
        payload.put("tenantId", tenantId);
        payload.put("jobType", jobType);
        payload.put(additionalKey, additionalValue);

        return buildEvent(eventType, tenantId, jobId, payload);
    }

    /**
     * Build a GEvent with proper headers and payload.
     */
    private static GEvent buildEvent(
            String eventType,
            String tenantId,
            String jobId,
            Map<String, Object> payload) {

        // Create event ID with tenant context
        EventId eventId = EventId.create(
                eventType,
                EVENT_VERSION,
                tenantId,
                UUID.randomUUID().toString()
        );

        // Create headers with correlation ID
        Map<String, String> headers = new HashMap<>();
        headers.put("correlationId", jobId);
        headers.put("source", "refactorer-job-service");

        // Build and return the concrete GEvent instance
        return GEvent.builder()
                .id(eventId)
                .headers(headers)
                .payload(payload)
                .intervalBased(false)
                .build();
    }
}
