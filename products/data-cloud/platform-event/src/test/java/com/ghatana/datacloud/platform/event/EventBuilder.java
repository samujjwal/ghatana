/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.platform.event;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class EventBuilder {

    private EventBuilder() {} // GH-90000

    static Builder create(String type) { // GH-90000
        return new Builder(type); // GH-90000
    }

    static Builder entityCreated(String collection, String entityId) { // GH-90000
        return create("entity.created")
            .withCollection(collection) // GH-90000
            .withEntityId(entityId) // GH-90000
            .withPayload("action", "create"); // GH-90000
    }

    static Builder entityUpdated(String collection, String entityId) { // GH-90000
        return create("entity.updated")
            .withCollection(collection) // GH-90000
            .withEntityId(entityId) // GH-90000
            .withPayload("action", "update"); // GH-90000
    }

    static Builder entityDeleted(String collection, String entityId) { // GH-90000
        return create("entity.deleted")
            .withCollection(collection) // GH-90000
            .withEntityId(entityId) // GH-90000
            .withPayload("action", "delete"); // GH-90000
    }

    static Builder pipelineCompleted(String entityId) { // GH-90000
        return create("pipeline.completed").withEntityId(entityId);
    }

    static Builder featureIngested(String entityId) { // GH-90000
        return create("feature.ingested").withEntityId(entityId);
    }

    static final class Builder {
        private final Map<String, Object> event = new LinkedHashMap<>(); // GH-90000
        private final Map<String, Object> payload = new LinkedHashMap<>(); // GH-90000

        private Builder(String type) { // GH-90000
            event.put("id", "evt-" + UUID.randomUUID()); // GH-90000
            event.put("type", type); // GH-90000
            event.put("timestamp", Instant.now().toString()); // GH-90000
            event.put("offset", 0L); // GH-90000
            event.put("payload", payload); // GH-90000
        }

        Builder withId(String id) { // GH-90000
            event.put("id", id); // GH-90000
            return this;
        }

        Builder withEntityId(String entityId) { // GH-90000
            event.put("entityId", entityId); // GH-90000
            return this;
        }

        Builder withCollection(String collection) { // GH-90000
            event.put("collection", collection); // GH-90000
            return this;
        }

        Builder withTenant(String tenantId) { // GH-90000
            event.put("tenantId", tenantId); // GH-90000
            return this;
        }

        Builder withOffset(long offset) { // GH-90000
            event.put("offset", offset); // GH-90000
            return this;
        }

        Builder withTimestamp(Instant timestamp) { // GH-90000
            event.put("timestamp", timestamp.toString()); // GH-90000
            return this;
        }

        Builder withPayload(String key, Object value) { // GH-90000
            payload.put(key, value); // GH-90000
            return this;
        }

        Builder withCorrelationId(String correlationId) { // GH-90000
            event.put("correlationId", correlationId); // GH-90000
            return this;
        }

        Map<String, Object> build() { // GH-90000
            return Map.copyOf(event); // GH-90000
        }
    }
}
