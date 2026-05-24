package com.ghatana.aep.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type record
 * @doc.purpose Represents a completed PatternSpec match with evidence and confidence
 * @doc.layer product
 * @doc.pattern Contract
 */
public record PatternMatch(
        String matchId,
        String patternId,
        String tenantId,
        Instant matchedAt,
        double confidence,
        List<CanonicalEvent> events,
        Map<String, Object> evidence) {

    public PatternMatch {
        matchId = requireText(matchId, "matchId");
        patternId = requireText(patternId, "patternId");
        tenantId = requireText(tenantId, "tenantId");
        matchedAt = Objects.requireNonNull(matchedAt, "matchedAt must not be null");
        if (Double.isNaN(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        events = List.copyOf(events != null ? events : List.of());
        evidence = Map.copyOf(evidence != null ? evidence : Map.of());
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
