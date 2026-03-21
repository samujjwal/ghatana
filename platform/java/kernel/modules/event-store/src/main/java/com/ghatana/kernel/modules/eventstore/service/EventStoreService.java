/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.eventstore.service;

import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.core.event.cloud.EventRecord;
import com.ghatana.core.event.cloud.EventTypeRef;
import com.ghatana.core.event.cloud.Version;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.ContentType;
import com.ghatana.platform.types.identity.EventId;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Generic event store service.
 *
 * <p>Wraps the EventCloud platform library and provides kernel-specific
 * functionality including simplified publishing methods and tenant management.
 * This service contains NO finance-specific logic.</p>
 *
 * @doc.type class
 * @doc.purpose Generic event store service - simplified publishing, tenant management
 * @doc.layer kernel
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class EventStoreService {

    private static final Logger log = LoggerFactory.getLogger(EventStoreService.class);

    private final KernelContext context;
    private final EventCloud eventCloud;
    private final Map<String, Object> eventCache;
    private final Executor executor;
    private volatile boolean started = false;

    /**
     * Creates a new event store service.
     *
     * @param context the kernel context
     * @param eventCloud the EventCloud instance
     */
    public EventStoreService(KernelContext context, EventCloud eventCloud) {
        this.context = context;
        this.eventCloud = eventCloud;
        this.eventCache = new ConcurrentHashMap<>();
        this.executor = context.getExecutor("event-store");
    }

    /**
     * Starts the event store service.
     */
    public void start() {
        log.info("Starting event store service");
        started = true;
        log.info("Event store service started");
    }

    /**
     * Stops the event store service.
     */
    public void stop() {
        log.info("Stopping event store service");
        eventCache.clear();
        started = false;
        log.info("Event store service stopped");
    }

    /**
     * Checks if the service is healthy.
     *
     * @return true if healthy
     */
    public boolean isHealthy() {
        return started;
    }

    /**
     * Publishes a simple event with topic and payload.
     *
     * @param topic the event topic/type
     * @param payload the event payload
     * @return Promise completing when event is published
     */
    public Promise<Void> publish(String topic, Map<String, Object> payload) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Event store service not started"));
        }

        log.debug("Publishing event to topic: {}", topic);

        // Create event record
        EventRecord event = createEventRecord(topic, null, payload);
        
        // Append to event cloud
        return eventCloud.append(new EventCloud.AppendRequest(event, EventCloud.AppendOptions.defaults()))
            .map(result -> {
                log.debug("Event published to partition {} offset {}", result.partitionId(), result.offset());
                return null;
            });
    }

    /**
     * Publishes an event with tenant ID.
     *
     * @param topic the event topic/type
     * @param tenantId the tenant identifier
     * @param payload the event payload
     * @return Promise completing when event is published
     */
    public Promise<Void> publish(String topic, String tenantId, Map<String, Object> payload) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Event store service not started"));
        }

        log.debug("Publishing event to topic: {} for tenant: {}", topic, tenantId);

        // Create event record with tenant
        EventRecord event = createEventRecord(topic, tenantId, payload);
        
        // Append to event cloud
        return eventCloud.append(new EventCloud.AppendRequest(event, EventCloud.AppendOptions.defaults()))
            .map(result -> {
                log.debug("Event published to partition {} offset {}", result.partitionId(), result.offset());
                return null;
            });
    }

    /**
     * Publishes a structured event with full metadata.
     *
     * @param topic the event topic/type
     * @param tenantId the tenant identifier
     * @param runId the workflow run identifier
     * @param category the event category
     * @param stepName the step name
     * @param data the event data
     * @param timestamp the event timestamp
     * @return Promise completing when event is published
     */
    public Promise<Void> publish(String topic, String tenantId, String runId, 
                                String category, String stepName, Map<String, Object> data, 
                                Instant timestamp) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Event store service not started"));
        }

        log.debug("Publishing structured event: {} category: {}", topic, category);

        // Create enriched payload
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("runId", runId);
        payload.put("category", category);
        payload.put("stepName", stepName);
        payload.put("data", data);
        payload.put("timestamp", timestamp.toString());

        // Create event record
        EventRecord event = createEventRecord(topic, tenantId, payload);
        
        // Append to event cloud
        return eventCloud.append(new EventCloud.AppendRequest(event, EventCloud.AppendOptions.defaults()))
            .map(result -> {
                log.debug("Structured event published to partition {} offset {}", result.partitionId(), result.offset());
                return null;
            });
    }

    /**
     * Publishes multiple events atomically.
     *
     * @param events list of events to publish
     * @return Promise completing when all events are published
     */
    public Promise<Void> publishBatch(List<EventData> events) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Event store service not started"));
        }

        log.debug("Publishing batch of {} events", events.size());

        // Convert to EventRecords
        List<EventCloud.AppendRequest> requests = events.stream()
            .map(eventData -> {
                EventRecord event = createEventRecord(eventData);
                return new EventCloud.AppendRequest(event, EventCloud.AppendOptions.defaults());
            })
            .toList();

        // Batch append
        return eventCloud.appendBatch(requests)
            .map(results -> {
                log.debug("Batch published {} events", results.size());
                return null;
            });
    }

    /**
     * Gets the underlying EventCloud instance.
     *
     * @return the EventCloud instance
     */
    public EventCloud getEventCloud() {
        return eventCloud;
    }

    // ==================== Private Methods ====================

    private EventRecord createEventRecord(String topic, String tenantId, Map<String, Object> payload) {
        return EventRecord.builder()
            .tenantId(tenantId != null ? TenantId.of(tenantId) : TenantId.of("default"))
            .typeRef(EventTypeRef.of(topic, 1, 0))
            .eventId(EventId.random())
            .occurrenceTime(Instant.now())
            .detectionTime(Instant.now())
            .payload(ByteBuffer.wrap(serializePayload(payload)))
            .contentType(ContentType.JSON)
            .schemaUri("schema://kernel/event-store/" + topic + "/v1")
            .build();
    }

    private EventRecord createEventRecord(EventData eventData) {
        return EventRecord.builder()
            .tenantId(eventData.tenantId() != null ? TenantId.of(eventData.tenantId()) : TenantId.of("default"))
            .typeRef(EventTypeRef.of(eventData.topic(), 1, 0))
            .eventId(EventId.random())
            .occurrenceTime(eventData.timestamp() != null ? eventData.timestamp() : Instant.now())
            .detectionTime(Instant.now())
            .payload(ByteBuffer.wrap(serializePayload(eventData.payload())))
            .contentType(ContentType.JSON)
                .schemaUri("schema://kernel/event-store/" + eventData.topic() + "/v1")
            .build();
    }

    private byte[] serializePayload(Map<String, Object> payload) {
        try {
            // Simple JSON serialization - in production would use proper JSON library
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                if (!first) {
                    json.append(",");
                }
                json.append("\"").append(entry.getKey()).append("\":");
                if (entry.getValue() instanceof String) {
                    json.append("\"").append(entry.getValue()).append("\"");
                } else {
                    json.append(entry.getValue());
                }
                first = false;
            }
            json.append("}");
            return json.toString().getBytes();
        } catch (Exception e) {
            log.error("Failed to serialize payload", e);
            return "{}".getBytes();
        }
    }

    /**
     * Simple event data structure.
     */
    public record EventData(
        String topic,
        String tenantId,
        Map<String, Object> payload,
        Instant timestamp
    ) {
        public EventData {
            Objects.requireNonNull(topic, "topic required");
            Objects.requireNonNull(payload, "payload required");
        }

        public static EventData of(String topic, Map<String, Object> payload) {
            return new EventData(topic, null, payload, null);
        }

        public static EventData of(String topic, String tenantId, Map<String, Object> payload) {
            return new EventData(topic, tenantId, payload, null);
        }
    }
}
