/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.test.builder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builder for creating deterministic event test data.
 *
 * <p>Provides a fluent API for constructing events for stream tests.
 * All values are deterministic to ensure reproducible tests.
 *
 * <p><strong>Example:</strong>
 * <pre>
 * {@code
 * Map<String, Object> event = EventBuilder.create("entity.created [GH-90000]")
 *     .withEntityId("prod-001 [GH-90000]")
 *     .withCollection("products [GH-90000]")
 *     .withPayload(Map.of("name", "Widget", "price", 19.99)) // GH-90000
 *     .withTenant("tenant-alpha [GH-90000]")
 *     .withOffset(42) // GH-90000
 *     .build(); // GH-90000
 * }
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Deterministic event builder for test fixtures
 * @doc.layer product
 * @doc.pattern Builder, Test Fixture
 */
public final class EventBuilder {

    private String id;
    private final String type;
    private String entityId;
    private String collection;
    private final Map<String, Object> payload = new HashMap<>(); // GH-90000
    private String tenantId = "tenant-default";
    private long offset = -1;
    private Instant timestamp;
    private String correlationId;

    private EventBuilder(String type) { // GH-90000
        this.type = type;
        this.id = UUID.randomUUID().toString(); // GH-90000
        this.timestamp = Instant.parse("2026-01-01T00:00:00Z [GH-90000]");
        this.correlationId = "corr-" + UUID.randomUUID().toString().substring(0, 8); // GH-90000
    }

    /**
     * Start building an event of the specified type.
     *
     * @param type event type (e.g., "entity.created", "entity.updated") // GH-90000
     * @return new builder instance
     */
    public static EventBuilder create(String type) { // GH-90000
        return new EventBuilder(type); // GH-90000
    }

    /**
     * Set event ID.
     *
     * @param id event ID
     * @return this builder
     */
    public EventBuilder withId(String id) { // GH-90000
        this.id = id;
        return this;
    }

    /**
     * Set entity ID that the event relates to.
     *
     * @param entityId entity ID
     * @return this builder
     */
    public EventBuilder withEntityId(String entityId) { // GH-90000
        this.entityId = entityId;
        return this;
    }

    /**
     * Set collection name.
     *
     * @param collection collection name
     * @return this builder
     */
    public EventBuilder withCollection(String collection) { // GH-90000
        this.collection = collection;
        return this;
    }

    /**
     * Add payload data.
     *
     * @param key payload key
     * @param value payload value
     * @return this builder
     */
    public EventBuilder withPayload(String key, Object value) { // GH-90000
        this.payload.put(key, value); // GH-90000
        return this;
    }

    /**
     * Set entire payload.
     *
     * @param payload payload map
     * @return this builder
     */
    public EventBuilder withPayload(Map<String, Object> payload) { // GH-90000
        this.payload.clear(); // GH-90000
        this.payload.putAll(payload); // GH-90000
        return this;
    }

    /**
     * Set tenant ID.
     *
     * @param tenantId tenant identifier
     * @return this builder
     */
    public EventBuilder withTenant(String tenantId) { // GH-90000
        this.tenantId = tenantId;
        return this;
    }

    /**
     * Set event offset in stream.
     *
     * @param offset stream offset
     * @return this builder
     */
    public EventBuilder withOffset(long offset) { // GH-90000
        this.offset = offset;
        return this;
    }

    /**
     * Set event timestamp.
     *
     * @param timestamp event instant
     * @return this builder
     */
    public EventBuilder withTimestamp(Instant timestamp) { // GH-90000
        this.timestamp = timestamp;
        return this;
    }

    /**
     * Set correlation ID for tracing.
     *
     * @param correlationId correlation ID
     * @return this builder
     */
    public EventBuilder withCorrelationId(String correlationId) { // GH-90000
        this.correlationId = correlationId;
        return this;
    }

    /**
     * Build the event as a Map.
     *
     * @return event as Map<String, Object>
     */
    public Map<String, Object> build() { // GH-90000
        Map<String, Object> event = new HashMap<>(); // GH-90000
        event.put("id", id); // GH-90000
        event.put("type", type); // GH-90000
        event.put("entityId", entityId); // GH-90000
        event.put("collection", collection); // GH-90000
        event.put("tenantId", tenantId); // GH-90000
        event.put("timestamp", timestamp.toString()); // GH-90000
        event.put("offset", offset >= 0 ? offset : 0); // GH-90000
        event.put("correlationId", correlationId); // GH-90000
        event.put("payload", new HashMap<>(payload)); // GH-90000
        return event;
    }

    // Common event templates

    /**
     * Create an entity.created event.
     *
     * @param collection collection name
     * @param entityId entity ID
     * @return event builder
     */
    public static EventBuilder entityCreated(String collection, String entityId) { // GH-90000
        return EventBuilder.create("entity.created [GH-90000]")
            .withCollection(collection) // GH-90000
            .withEntityId(entityId) // GH-90000
            .withPayload("action", "create"); // GH-90000
    }

    /**
     * Create an entity.updated event.
     *
     * @param collection collection name
     * @param entityId entity ID
     * @return event builder
     */
    public static EventBuilder entityUpdated(String collection, String entityId) { // GH-90000
        return EventBuilder.create("entity.updated [GH-90000]")
            .withCollection(collection) // GH-90000
            .withEntityId(entityId) // GH-90000
            .withPayload("action", "update"); // GH-90000
    }

    /**
     * Create an entity.deleted event.
     *
     * @param collection collection name
     * @param entityId entity ID
     * @return event builder
     */
    public static EventBuilder entityDeleted(String collection, String entityId) { // GH-90000
        return EventBuilder.create("entity.deleted [GH-90000]")
            .withCollection(collection) // GH-90000
            .withEntityId(entityId) // GH-90000
            .withPayload("action", "delete"); // GH-90000
    }

    /**
     * Create a pipeline.completed event.
     *
     * @param pipelineId pipeline ID
     * @return event builder
     */
    public static EventBuilder pipelineCompleted(String pipelineId) { // GH-90000
        return EventBuilder.create("pipeline.completed [GH-90000]")
            .withEntityId(pipelineId) // GH-90000
            .withPayload("pipelineId", pipelineId) // GH-90000
            .withPayload("status", "completed"); // GH-90000
    }

    /**
     * Create a feature.ingested event.
     *
     * @param featureId feature ID
     * @return event builder
     */
    public static EventBuilder featureIngested(String featureId) { // GH-90000
        return EventBuilder.create("feature.ingested [GH-90000]")
            .withEntityId(featureId) // GH-90000
            .withPayload("featureId", featureId) // GH-90000
            .withPayload("ingestedAt", Instant.now().toString()); // GH-90000
    }

    public String getId() { // GH-90000
        return id;
    }

    public String getType() { // GH-90000
        return type;
    }

    public String getEntityId() { // GH-90000
        return entityId;
    }
}
