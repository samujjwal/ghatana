package com.ghatana.aep.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type record
 * @doc.purpose Represents in-flight partial-match state for temporal PatternSpec evaluation
 * @doc.layer product
 * @doc.pattern Contract
 */
public record PatternPartialMatch(
        String partialMatchId,
        String patternId,
        String tenantId,
        Instant startedAt,
        Instant expiresAt,
        List<CanonicalEvent> events,
        Map<String, Object> bindings,
        double confidence) {

    public PatternPartialMatch {
        partialMatchId = requireText(partialMatchId, "partialMatchId");
        patternId = requireText(patternId, "patternId");
        tenantId = requireText(tenantId, "tenantId");
        startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (expiresAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("expiresAt must not be before startedAt");
        }
        events = List.copyOf(events != null ? events : List.of());
        bindings = Map.copyOf(bindings != null ? bindings : Map.of());
        if (Double.isNaN(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
