/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * Map<String, Object> event = EventBuilder.create("entity.created")
 *     .withEntityId("prod-001")
 *     .withCollection("products")
 *     .withPayload(Map.of("name", "Widget", "price", 19.99)) 
 *     .withTenant("tenant-alpha")
 *     .withOffset(42) 
 *     .build(); 
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
    private final Map<String, Object> payload = new HashMap<>(); 
    private String tenantId = "tenant-default";
    private long offset = -1;
    private Instant timestamp;
    private String correlationId;

    private EventBuilder(String type) { 
        this.type = type;
        this.id = UUID.randomUUID().toString(); 
        this.timestamp = Instant.parse("2026-01-01T00:00:00Z");
        this.correlationId = "corr-" + UUID.randomUUID().toString().substring(0, 8); 
    }

    /**
     * Start building an event of the specified type.
     *
     * @param type event type (e.g., "entity.created", "entity.updated") 
     * @return new builder instance
     */
    public static EventBuilder create(String type) { 
        return new EventBuilder(type); 
    }

    /**
     * Set event ID.
     *
     * @param id event ID
     * @return this builder
     */
    public EventBuilder withId(String id) { 
        this.id = id;
        return this;
    }

    /**
     * Set entity ID that the event relates to.
     *
     * @param entityId entity ID
     * @return this builder
     */
    public EventBuilder withEntityId(String entityId) { 
        this.entityId = entityId;
        return this;
    }

    /**
     * Set collection name.
     *
     * @param collection collection name
     * @return this builder
     */
    public EventBuilder withCollection(String collection) { 
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
    public EventBuilder withPayload(String key, Object value) { 
        this.payload.put(key, value); 
        return this;
    }

    /**
     * Set entire payload.
     *
     * @param payload payload map
     * @return this builder
     */
    public EventBuilder withPayload(Map<String, Object> payload) { 
        this.payload.clear(); 
        this.payload.putAll(payload); 
        return this;
    }

    /**
     * Set tenant ID.
     *
     * @param tenantId tenant identifier
     * @return this builder
     */
    public EventBuilder withTenant(String tenantId) { 
        this.tenantId = tenantId;
        return this;
    }

    /**
     * Set event offset in stream.
     *
     * @param offset stream offset
     * @return this builder
     */
    public EventBuilder withOffset(long offset) { 
        this.offset = offset;
        return this;
    }

    /**
     * Set event timestamp.
     *
     * @param timestamp event instant
     * @return this builder
     */
    public EventBuilder withTimestamp(Instant timestamp) { 
        this.timestamp = timestamp;
        return this;
    }

    /**
     * Set correlation ID for tracing.
     *
     * @param correlationId correlation ID
     * @return this builder
     */
    public EventBuilder withCorrelationId(String correlationId) { 
        this.correlationId = correlationId;
        return this;
    }

    /**
     * Build the event as a Map.
     *
     * @return event as Map<String, Object>
     */
    public Map<String, Object> build() { 
        Map<String, Object> event = new HashMap<>(); 
        event.put("id", id); 
        event.put("type", type); 
        event.put("entityId", entityId); 
        event.put("collection", collection); 
        event.put("tenantId", tenantId); 
        event.put("timestamp", timestamp.toString()); 
        event.put("offset", offset >= 0 ? offset : 0); 
        event.put("correlationId", correlationId); 
        event.put("payload", new HashMap<>(payload)); 
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
    public static EventBuilder entityCreated(String collection, String entityId) { 
        return EventBuilder.create("entity.created")
            .withCollection(collection) 
            .withEntityId(entityId) 
            .withPayload("action", "create"); 
    }

    /**
     * Create an entity.updated event.
     *
     * @param collection collection name
     * @param entityId entity ID
     * @return event builder
     */
    public static EventBuilder entityUpdated(String collection, String entityId) { 
        return EventBuilder.create("entity.updated")
            .withCollection(collection) 
            .withEntityId(entityId) 
            .withPayload("action", "update"); 
    }

    /**
     * Create an entity.deleted event.
     *
     * @param collection collection name
     * @param entityId entity ID
     * @return event builder
     */
    public static EventBuilder entityDeleted(String collection, String entityId) { 
        return EventBuilder.create("entity.deleted")
            .withCollection(collection) 
            .withEntityId(entityId) 
            .withPayload("action", "delete"); 
    }

    /**
     * Create a pipeline.completed event.
     *
     * @param pipelineId pipeline ID
     * @return event builder
     */
    public static EventBuilder pipelineCompleted(String pipelineId) { 
        return EventBuilder.create("pipeline.completed")
            .withEntityId(pipelineId) 
            .withPayload("pipelineId", pipelineId) 
            .withPayload("status", "completed"); 
    }

    /**
     * Create a feature.ingested event.
     *
     * @param featureId feature ID
     * @return event builder
     */
    public static EventBuilder featureIngested(String featureId) { 
        return EventBuilder.create("feature.ingested")
            .withEntityId(featureId) 
            .withPayload("featureId", featureId) 
            .withPayload("ingestedAt", Instant.now().toString()); 
    }

    public String getId() { 
        return id;
    }

    public String getType() { 
        return type;
    }

    public String getEntityId() { 
        return entityId;
    }
}
