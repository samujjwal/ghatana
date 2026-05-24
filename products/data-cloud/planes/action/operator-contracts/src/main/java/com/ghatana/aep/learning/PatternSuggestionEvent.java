package com.ghatana.aep.learning;

import java.time.Instant;
import java.util.Map;

/**
 * Auditable event emitted when learning recommends a candidate pattern.
 *
 * @doc.type record
 * @doc.purpose Emits pattern.suggested without mutating active pattern rules
 * @doc.layer product
 * @doc.pattern Event
 */
public record PatternSuggestionEvent(
        String eventId,
        String eventType,
        String tenantId,
        String candidateId,
        double recommendationScore,
        Instant occurredAt,
        Map<String, Object> reviewPacket
) {

    public PatternSuggestionEvent {
        eventId = requireText(eventId, "eventId");
        eventType = requireText(eventType, "eventType");
        if (!"pattern.suggested".equals(eventType)) {
            throw new IllegalArgumentException("learning output must emit pattern.suggested");
        }
        tenantId = requireText(tenantId, "tenantId");
        candidateId = requireText(candidateId, "candidateId");
        if (Double.isNaN(recommendationScore) || recommendationScore < 0.0 || recommendationScore > 1.0) {
            throw new IllegalArgumentException("recommendationScore must be between 0.0 and 1.0");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
        reviewPacket = Map.copyOf(reviewPacket != null ? reviewPacket : Map.of());
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
