package com.ghatana.yappc.ai.quality;

/**
 * Represents an AI response's confidence level as a normalized score.
 *
 * <p>The {@link #value()} is in the range {@code [0.0, 1.0]} where:
 * <ul>
 *   <li>{@code >= 0.8} → {@link Label#HIGH}</li>
 *   <li>{@code >= 0.5} → {@link Label#MEDIUM}</li>
 *   <li>{@code < 0.5}  → {@link Label#LOW}</li>
 * </ul>
 *
 * @param value  normalized confidence between 0.0 and 1.0 (inclusive)
 * @param label  human-readable tier derived from the value
 * @param raw    raw string extracted from the LLM JSON payload, for debugging
 *
 * @doc.type class
 * @doc.purpose Immutable confidence score emitted per AI response
 * @doc.layer product
 * @doc.pattern DTO
 */
public record ConfidenceScore(double value, Label label, String raw) {

    /** Enumerated confidence tiers. */
    public enum Label {
        HIGH, MEDIUM, LOW
    }

    /**
     * Returns a fallback score when the LLM did not provide a confidence value.
     */
    public static ConfidenceScore absent() {
        return new ConfidenceScore(0.0, Label.LOW, "absent");
    }

    /**
     * Returns {@code true} if this score is at or above the given threshold.
     *
     * @param threshold value in [0.0, 1.0]
     */
    public boolean meetsThreshold(double threshold) {
        return value >= threshold;
    }

    /**
     * Canonical factory — shared by {@link ConfidenceScorer}.
     *
     * @param value raw score in [0.0, 1.0]
     * @param raw   original parsed string
     * @throws IllegalArgumentException if value is out of range
     */
    static ConfidenceScore of(double value, String raw) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                "Confidence value must be in [0.0, 1.0], got: " + value);
        }
        Label label = value >= 0.8 ? Label.HIGH : value >= 0.5 ? Label.MEDIUM : Label.LOW;
        return new ConfidenceScore(value, label, raw);
    }
}
