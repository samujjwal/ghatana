package com.ghatana.refactorer.local.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Local helper event model used only inside the refactorer product module.
 * Moved here to avoid collisions with the canonical domain Event types.
 
 * @doc.type class
 * @doc.purpose Handles local event operations
 * @doc.layer core
 * @doc.pattern Event
*/
public class LocalEvent {

    private final String eventId;
    private final String eventType;
    private final String aggregateId;
    private final String aggregateType;
    private final long timestamp;
    private final Map<String, Object> payload;
    private final Map<String, String> metadata;

    public LocalEvent(String eventType, String aggregateId, String aggregateType, Map<String, Object> payload) {
        this(UUID.randomUUID().toString(), eventType, aggregateId, aggregateType, System.currentTimeMillis(), payload, Map.of());
    }

    public LocalEvent(String eventId, String eventType, String aggregateId, String aggregateType,
            long timestamp, Map<String, Object> payload, Map<String, String> metadata) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.timestamp = timestamp;
        this.payload = Map.copyOf(payload);
        this.metadata = Map.copyOf(metadata);
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public Instant getTimestampAsInstant() {
        return Instant.ofEpochMilli(timestamp);
    }

    public <T> T getPayload(String key, Class<T> type) {
        Object value = payload.get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    public String getMetadata(String key) {
        return metadata.get(key);
    }
}
