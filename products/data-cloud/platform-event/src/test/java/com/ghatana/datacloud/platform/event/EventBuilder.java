/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.platform.event;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class EventBuilder {

    private EventBuilder() {} 

    static Builder create(String type) { 
        return new Builder(type); 
    }

    static Builder entityCreated(String collection, String entityId) { 
        return create("entity.created")
            .withCollection(collection) 
            .withEntityId(entityId) 
            .withPayload("action", "create"); 
    }

    static Builder entityUpdated(String collection, String entityId) { 
        return create("entity.updated")
            .withCollection(collection) 
            .withEntityId(entityId) 
            .withPayload("action", "update"); 
    }

    static Builder entityDeleted(String collection, String entityId) { 
        return create("entity.deleted")
            .withCollection(collection) 
            .withEntityId(entityId) 
            .withPayload("action", "delete"); 
    }

    static Builder pipelineCompleted(String entityId) { 
        return create("pipeline.completed").withEntityId(entityId);
    }

    static Builder featureIngested(String entityId) { 
        return create("feature.ingested").withEntityId(entityId);
    }

    static final class Builder {
        private final Map<String, Object> event = new LinkedHashMap<>(); 
        private final Map<String, Object> payload = new LinkedHashMap<>(); 

        private Builder(String type) { 
            event.put("id", "evt-" + UUID.randomUUID()); 
            event.put("type", type); 
            event.put("timestamp", Instant.now().toString()); 
            event.put("offset", 0L); 
            event.put("payload", payload); 
        }

        Builder withId(String id) { 
            event.put("id", id); 
            return this;
        }

        Builder withEntityId(String entityId) { 
            event.put("entityId", entityId); 
            return this;
        }

        Builder withCollection(String collection) { 
            event.put("collection", collection); 
            return this;
        }

        Builder withTenant(String tenantId) { 
            event.put("tenantId", tenantId); 
            return this;
        }

        Builder withOffset(long offset) { 
            event.put("offset", offset); 
            return this;
        }

        Builder withTimestamp(Instant timestamp) { 
            event.put("timestamp", timestamp.toString()); 
            return this;
        }

        Builder withPayload(String key, Object value) { 
            payload.put(key, value); 
            return this;
        }

        Builder withCorrelationId(String correlationId) { 
            event.put("correlationId", correlationId); 
            return this;
        }

        Map<String, Object> build() { 
            return Map.copyOf(event); 
        }
    }
}
