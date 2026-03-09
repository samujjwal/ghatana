/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 5.3 — Event Schema Registry: schema versioning with compatibility checks.
 */
package com.ghatana.datacloud.schema;

import java.time.Instant;
import java.util.*;

/**
 * Schema definition for events and entities in the Data-Cloud.
 *
 * <p>Schemas define the structure of data flowing through the platform, enabling
 * validation, evolution, and compatibility checking.
 *
 * @param id unique schema identifier
 * @param subject schema subject (typically event type or collection name)
 * @param version schema version (monotonically increasing)
 * @param format the schema format (JSON_SCHEMA, AVRO, PROTOBUF)
 * @param definition the schema definition string (format depends on {@link SchemaFormat})
 * @param fields declared field metadata for compatibility checking
 * @param metadata arbitrary metadata
 * @param createdAt creation timestamp
 */
public record EventSchema(
        String id,
        String subject,
        int version,
        SchemaFormat format,
        String definition,
        List<SchemaField> fields,
        Map<String, String> metadata,
        Instant createdAt
) {
    public EventSchema {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(subject, "subject required");
        Objects.requireNonNull(format, "format required");
        Objects.requireNonNull(definition, "definition required");
        if (version < 1) throw new IllegalArgumentException("version must be >= 1");
        fields = fields != null ? List.copyOf(fields) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    /**
     * Creates a V1 schema.
     */
    public static EventSchema create(String subject, SchemaFormat format,
                                     String definition, List<SchemaField> fields) {
        return new EventSchema(
                UUID.randomUUID().toString(), subject, 1, format,
                definition, fields, Map.of(), Instant.now());
    }

    /**
     * Creates the next version of this schema.
     */
    public EventSchema nextVersion(String newDefinition, List<SchemaField> newFields) {
        return new EventSchema(
                UUID.randomUUID().toString(), subject, version + 1, format,
                newDefinition, newFields, metadata, Instant.now());
    }

    /**
     * Schema field metadata for compatibility checking.
     */
    public record SchemaField(
            String name,
            String type,
            boolean required,
            Object defaultValue
    ) {
        public SchemaField {
            Objects.requireNonNull(name, "field name required");
            Objects.requireNonNull(type, "field type required");
        }

        public static SchemaField required(String name, String type) {
            return new SchemaField(name, type, true, null);
        }

        public static SchemaField required(String name, String type, Object defaultValue) {
            return new SchemaField(name, type, true, defaultValue);
        }

        public static SchemaField optional(String name, String type) {
            return new SchemaField(name, type, false, null);
        }

        public static SchemaField optional(String name, String type, Object defaultValue) {
            return new SchemaField(name, type, false, defaultValue);
        }
    }
}
