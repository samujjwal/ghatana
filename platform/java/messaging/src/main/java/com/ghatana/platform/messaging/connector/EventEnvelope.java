package com.ghatana.core.connectors;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.ContentType;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical event envelope for cross-product event contracts.
 *
 * @doc.type record
 * @doc.purpose Shared event envelope contract for cross-product publication, persistence, and routing
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record EventEnvelope(
        String eventId,
        TenantId tenantId,
        String eventTypeName,
        String eventTypeVersion,
        Instant occurrenceTime,
        ContentType contentType,
        String schemaUri,
        ByteBuffer payload,
        EventMetadata metadata
) {
    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(eventTypeName, "eventTypeName required");
        Objects.requireNonNull(eventTypeVersion, "eventTypeVersion required");
        Objects.requireNonNull(occurrenceTime, "occurrenceTime required");
        Objects.requireNonNull(contentType, "contentType required");
        Objects.requireNonNull(schemaUri, "schemaUri required");
        payload = Objects.requireNonNull(payload, "payload required").asReadOnlyBuffer();
        Objects.requireNonNull(metadata, "metadata required");
    }

    public static EventEnvelope fromIngestEvent(String eventId, IngestEvent event) {
        Objects.requireNonNull(event, "event required");
        return new EventEnvelope(
                Objects.requireNonNull(eventId, "eventId required"),
                event.tenantId(),
                event.eventTypeName(),
                event.eventTypeVersion(),
                event.occurrenceTime(),
                event.contentType(),
                event.schemaUri(),
                event.payload(),
                new EventMetadata(
                        event.headers(),
                        event.correlationId(),
                        event.idempotencyKey(),
                        event.partitionKey())
        );
    }

    public static EventEnvelope fromIngestEvent(IngestEvent event) {
        return fromIngestEvent(UUID.randomUUID().toString(), event);
    }
}
