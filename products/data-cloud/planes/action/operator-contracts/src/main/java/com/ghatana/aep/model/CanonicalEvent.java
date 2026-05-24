package com.ghatana.aep.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @doc.type record
 * @doc.purpose Defines the canonical AEP event envelope shared by events, matches, and agent outputs
 * @doc.layer product
 * @doc.pattern Contract
 */
public record CanonicalEvent(
        String eventId,
        String tenantId,
        String eventType,
        String schemaVersion,
        Instant eventTime,
        Optional<Instant> processingTime,
        Optional<Instant> detectionTime,
        Optional<EventInterval> interval,
        Map<String, Object> source,
        List<String> entityRefs,
        String correlationId,
        Optional<String> causationId,
        Map<String, Object> payload,
        Map<String, Object> confidence,
        Map<String, Object> provenance,
        List<String> policyTags,
        String idempotencyKey) {

    public CanonicalEvent {
        eventId = requireText(eventId, "eventId");
        tenantId = requireText(tenantId, "tenantId");
        eventType = requireText(eventType, "eventType");
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        eventTime = Objects.requireNonNull(eventTime, "eventTime must not be null");
        processingTime = processingTime != null ? processingTime : Optional.empty();
        detectionTime = detectionTime != null ? detectionTime : Optional.empty();
        interval = interval != null ? interval : Optional.empty();
        source = Map.copyOf(source != null ? source : Map.of());
        entityRefs = List.copyOf(entityRefs != null ? entityRefs : List.of());
        correlationId = requireText(correlationId, "correlationId");
        causationId = causationId != null ? causationId : Optional.empty();
        payload = Map.copyOf(payload != null ? payload : Map.of());
        confidence = Map.copyOf(confidence != null ? confidence : Map.of());
        provenance = Map.copyOf(provenance != null ? provenance : Map.of());
        policyTags = List.copyOf(policyTags != null ? policyTags : List.of());
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
