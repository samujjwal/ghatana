package com.ghatana.aep.pattern.lifecycle;

import java.time.Instant;
import java.util.Map;

/**
 * Auditable event emitted by pattern lifecycle transitions.
 *
 * @doc.type record
 * @doc.purpose Captures PatternSpec lifecycle transition evidence for governance and replay
 * @doc.layer product
 * @doc.pattern Event
 */
public record PatternLifecycleEvent(
        String eventId,
        String patternId,
        String tenantId,
        PatternLifecycleState from,
        PatternLifecycleState to,
        PatternLifecycleEventType eventType,
        String actor,
        Instant occurredAt,
        Map<String, Object> evidence
) {

    public PatternLifecycleEvent {
        eventId = requireText(eventId, "eventId");
        patternId = requireText(patternId, "patternId");
        tenantId = requireText(tenantId, "tenantId");
        if (from == null) {
            throw new IllegalArgumentException("from must not be null");
        }
        if (to == null) {
            throw new IllegalArgumentException("to must not be null");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("eventType must not be null");
        }
        actor = requireText(actor, "actor");
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
        evidence = Map.copyOf(evidence != null ? evidence : Map.of());
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
