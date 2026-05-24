package com.ghatana.aep.learning;

import java.util.List;
import java.util.Map;

/**
 * Candidate pattern produced by exploration, extraction, or synthesis.
 *
 * @doc.type record
 * @doc.purpose Represents a learned candidate PatternSpec before governance review
 * @doc.layer product
 * @doc.pattern Contract
 */
public record PatternCandidate(
        String candidateId,
        String tenantId,
        String source,
        Map<String, Object> patternSpec,
        PatternScore score,
        List<String> evidenceRefs
) {

    public PatternCandidate {
        candidateId = requireText(candidateId, "candidateId");
        tenantId = requireText(tenantId, "tenantId");
        source = requireText(source, "source");
        patternSpec = Map.copyOf(patternSpec != null ? patternSpec : Map.of());
        if (patternSpec.isEmpty()) {
            throw new IllegalArgumentException("patternSpec must not be empty");
        }
        if (score == null) {
            throw new IllegalArgumentException("score must not be null");
        }
        evidenceRefs = List.copyOf(evidenceRefs != null ? evidenceRefs : List.of());
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
