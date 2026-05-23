package com.ghatana.kernel.communication;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Producer for Kernel timeline events with trace and evidence linking.
 *
 * <p>This component publishes timeline events to the KernelEventBus with
 * correlation IDs for distributed tracing and evidence references for
 * compliance and audit purposes.</p>
 *
 * @doc.type class
 * @doc.purpose Timeline event production with trace/evidence linking
 * @doc.layer core
 * @doc.pattern EventProducer
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class KernelTimelineEventProducer {

    private final KernelEventBus eventBus;

    public KernelTimelineEventProducer(KernelEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Publishes a timeline event with trace and evidence linking.
     *
     * @param source the source component or service
     * @param eventType the event type
     * @param phase the lifecycle phase (e.g., "Intent", "Shape", "Validate", "Generate", "Run", "Observe", "Learn", "Evolve")
     * @param action the action being performed
     * @param correlationId the correlation ID for distributed tracing
     * @param traceId the trace ID for distributed tracing
     * @param evidenceRefs references to evidence artifacts
     * @param metadata additional metadata
     */
    public void publishTimelineEvent(
            String source,
            String eventType,
            String phase,
            String action,
            String correlationId,
            String traceId,
            Map<String, String> evidenceRefs,
            Map<String, String> metadata) {

        TimelineEvent event = new TimelineEvent(
            UUID.randomUUID().toString(),
            eventType,
            source,
            new TimelinePayload(phase, action, correlationId, traceId, evidenceRefs),
            Instant.now().toEpochMilli(),
            metadata
        );

        eventBus.publishEvent(event);
    }

    /**
     * Publishes a timeline event asynchronously with trace and evidence linking.
     *
     * @param source the source component or service
     * @param eventType the event type
     * @param phase the lifecycle phase
     * @param action the action being performed
     * @param correlationId the correlation ID for distributed tracing
     * @param traceId the trace ID for distributed tracing
     * @param evidenceRefs references to evidence artifacts
     * @param metadata additional metadata
     * @return promise that completes when event is published
     */
    public io.activej.promise.Promise<Void> publishTimelineEventAsync(
            String source,
            String eventType,
            String phase,
            String action,
            String correlationId,
            String traceId,
            Map<String, String> evidenceRefs,
            Map<String, String> metadata) {

        TimelineEvent event = new TimelineEvent(
            UUID.randomUUID().toString(),
            eventType,
            source,
            new TimelinePayload(phase, action, correlationId, traceId, evidenceRefs),
            Instant.now().toEpochMilli(),
            metadata
        );

        return eventBus.publishEventAsync(event);
    }

    /**
     * Timeline event implementation with trace and evidence linking.
     */
    private static class TimelineEvent implements KernelEventBus.Event {
        private final String eventId;
        private final String eventType;
        private final String source;
        private final TimelinePayload payload;
        private final long timestamp;
        private final Map<String, String> metadata;

        TimelineEvent(
                String eventId,
                String eventType,
                String source,
                TimelinePayload payload,
                long timestamp,
                Map<String, String> metadata) {
            this.eventId = eventId;
            this.eventType = eventType;
            this.source = source;
            this.payload = payload;
            this.timestamp = timestamp;
            this.metadata = metadata;
        }

        @Override
        public String getEventId() {
            return eventId;
        }

        @Override
        public KernelEventBus.EventType getEventType() {
            return KernelEventBus.EventType.CUSTOM;
        }

        @Override
        public String getSource() {
            return source;
        }

        @Override
        public Object getPayload() {
            return payload;
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public Map<String, String> getMetadata() {
            return metadata;
        }
    }

    /**
     * Timeline payload with trace and evidence linking.
     */
    private record TimelinePayload(
            String phase,
            String action,
            String correlationId,
            String traceId,
            Map<String, String> evidenceRefs) {
    }
}
