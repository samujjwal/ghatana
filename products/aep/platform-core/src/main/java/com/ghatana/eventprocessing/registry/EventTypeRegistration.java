/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.eventprocessing.registry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a registered event type specification with versioning and metadata.
 *
 * <p>
 * <b>Purpose</b><br>
 * Immutable record of event type registration including type definition, schema
 * information, metadata, and producer/consumer relationships.
 * Forms the domain model for event type lifecycle tracking within the registry.
 *
 * @doc.type record
 * @doc.purpose Immutable event type registration domain model with versioning
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class EventTypeRegistration {

    private final UUID eventTypeId;
    private final String tenantId;
    private final String name;
    private final String description;
    private final String schema;
    private final String schemaVersion;
    private final String createdBy;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Map<String, Object> metadata;
    private final List<String> producers;
    private final List<String> consumers;
    private final boolean active;

    private EventTypeRegistration(
            UUID eventTypeId,
            String tenantId,
            String name,
            String description,
            String schema,
            String schemaVersion,
            String createdBy,
            Instant createdAt,
            Instant updatedAt,
            Map<String, Object> metadata,
            List<String> producers,
            List<String> consumers,
            boolean active) {
        this.eventTypeId = eventTypeId;
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
        this.schema = schema;
        this.schemaVersion = schemaVersion;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.metadata = metadata;
        this.producers = producers;
        this.consumers = consumers;
        this.active = active;
    }

    public static EventTypeRegistrationBuilder builder() {
        return new EventTypeRegistrationBuilder();
    }

    public UUID getEventTypeId() {
        return eventTypeId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getEventTypeName() {
        return name;
    }

    public String getSchemaJson() {
        return schema;
    }

    public String getSourceHint() {
        return null;
    }

    public String getConsumerHint() {
        return null;
    }

    public List<String> getTags() {
        return null;
    }

    public String getDescription() {
        return description;
    }

    public String getSchema() {
        return schema;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public List<String> getProducers() {
        return producers;
    }

    public List<String> getConsumers() {
        return consumers;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isValid() {
        return eventTypeId != null && name != null && schema != null;
    }

    public static class EventTypeRegistrationBuilder {
        private UUID eventTypeId;
        private String tenantId;
        private String name;
        private String description;
        private String schema;
        private String schemaVersion;
        private String createdBy;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();
        private Map<String, Object> metadata;
        private List<String> producers;
        private List<String> consumers;
        private boolean active = true;

        public EventTypeRegistrationBuilder eventTypeId(UUID eventTypeId) {
            this.eventTypeId = eventTypeId;
            return this;
        }

        public EventTypeRegistrationBuilder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public EventTypeRegistrationBuilder eventTypeName(String name) {
            this.name = name;
            return this;
        }

        public EventTypeRegistrationBuilder schemaJson(String schema) {
            this.schema = schema;
            return this;
        }

        public EventTypeRegistrationBuilder sourceHint(String sourceHint) {
            return this;
        }

        public EventTypeRegistrationBuilder consumerHint(String consumerHint) {
            return this;
        }

        public EventTypeRegistrationBuilder tags(List<String> tags) {
            return this;
        }

        public EventTypeRegistrationBuilder description(String description) {
            this.description = description;
            return this;
        }

        public EventTypeRegistrationBuilder schema(String schema) {
            this.schema = schema;
            return this;
        }

        public EventTypeRegistrationBuilder schemaVersion(String schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public EventTypeRegistrationBuilder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public EventTypeRegistrationBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public EventTypeRegistrationBuilder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public EventTypeRegistrationBuilder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public EventTypeRegistrationBuilder producers(List<String> producers) {
            this.producers = producers;
            return this;
        }

        public EventTypeRegistrationBuilder consumers(List<String> consumers) {
            this.consumers = consumers;
            return this;
        }

        public EventTypeRegistrationBuilder active(boolean active) {
            this.active = active;
            return this;
        }

        public EventTypeRegistration build() {
            return new EventTypeRegistration(
                    eventTypeId, tenantId, name, description, schema,
                    schemaVersion, createdBy, createdAt, updatedAt,
                    metadata, producers, consumers, active);
        }
    }
}
