package com.ghatana.pipeline.registry.model;

import com.ghatana.platform.domain.auth.TenantId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Schema definition model for event type schemas.
 *
 * <p>Purpose: Represents a versioned schema definition bound to an event type.
 * Contains the schema format (JSON Schema, Avro, Protobuf), document content,
 * direction (ingress/egress/both), and metadata for schema management.</p>
 *
 * @doc.type class
 * @doc.purpose Domain model for event type schema definitions
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaDefinition {

    private String id;
    private TenantId tenantId;
    private String eventTypeId;
    private int version;
    private String format;
    private String document;
    private String direction;
    private String description;
    private Instant createdAt;

    public static SchemaDefinition create(
            TenantId tenantId,
            String eventTypeId,
            String format,
            String document,
            String direction,
            String description,
            int version) {
        Instant now = Instant.now();
        return SchemaDefinition.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .eventTypeId(eventTypeId)
                .version(version)
                .format(format)
                .document(document)
                .direction(direction)
                .description(description)
                .createdAt(now)
                .build();
    }
}
