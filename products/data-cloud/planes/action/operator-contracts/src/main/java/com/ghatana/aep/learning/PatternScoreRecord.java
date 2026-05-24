package com.ghatana.aep.learning;

import java.time.Instant;
import java.util.Map;

/**
 * Historical score snapshot for a candidate pattern.
 *
 * @doc.type record
 * @doc.purpose Stores explainable scoring history before recommendation, review, or promotion
 * @doc.layer product
 * @doc.pattern Event
 */
public record PatternScoreRecord(
        String recordId,
        String candidateId,
        String tenantId,
        PatternScore score,
        String source,
        Instant occurredAt,
        Map<String, Object> explanation) {

    public PatternScoreRecord {
        recordId = requireText(recordId, "recordId");
        candidateId = requireText(candidateId, "candidateId");
        tenantId = requireText(tenantId, "tenantId");
        if (score == null) {
            throw new IllegalArgumentException("score must not be null");
        }
        source = requireText(source, "source");
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
        explanation = Map.copyOf(explanation != null ? explanation : Map.of());
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
