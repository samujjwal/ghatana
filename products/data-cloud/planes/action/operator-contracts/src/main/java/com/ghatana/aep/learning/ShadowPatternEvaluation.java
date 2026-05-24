package com.ghatana.aep.learning;

import java.util.List;
import java.util.Map;

/**
 * Shadow evaluation result for a candidate pattern that was run without side effects.
 *
 * @doc.type record
 * @doc.purpose Captures shadow metrics and review packet data before governed promotion
 * @doc.layer product
 * @doc.pattern Event
 */
public record ShadowPatternEvaluation(
        String candidateId,
        String tenantId,
        int matchCount,
        int truePositiveCount,
        int falsePositiveCount,
        int falseNegativeCount,
        double precision,
        double recall,
        boolean sideEffectsEnabled,
        List<String> matchedOutcomeIds,
        Map<String, Object> reviewPacket) {

    public ShadowPatternEvaluation {
        candidateId = requireText(candidateId, "candidateId");
        tenantId = requireText(tenantId, "tenantId");
        requireNonNegative(matchCount, "matchCount");
        requireNonNegative(truePositiveCount, "truePositiveCount");
        requireNonNegative(falsePositiveCount, "falsePositiveCount");
        requireNonNegative(falseNegativeCount, "falseNegativeCount");
        requireProbability(precision, "precision");
        requireProbability(recall, "recall");
        if (sideEffectsEnabled) {
            throw new IllegalArgumentException("shadow evaluation must not enable side effects");
        }
        matchedOutcomeIds = List.copyOf(matchedOutcomeIds != null ? matchedOutcomeIds : List.of());
        reviewPacket = Map.copyOf(reviewPacket != null ? reviewPacket : Map.of());
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }

    private static void requireProbability(double value, String fieldName) {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
