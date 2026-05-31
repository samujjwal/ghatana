/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.pattern.spec;

import java.util.Map;
import java.util.Optional;

/**
 * Pattern match result with confidence and uncertainty metadata (P4-03).
 *
 * <p>Represents the result of matching an event against a pattern,
 * including match status, confidence level, and uncertainty metadata.
 *
 * @doc.type record
 * @doc.purpose Represents pattern match results with confidence and uncertainty
 * @doc.layer product
 * @doc.pattern Model
 */
public record PatternMatchResult(
    boolean isMatch,
    boolean isComplete,
    double confidence,
    double uncertainty,
    Map<String, Object> matchData,
    Optional<String> explanation,
    Optional<String> noMatchReason
) {
    public PatternMatchResult {
        matchData = Map.copyOf(matchData != null ? matchData : Map.of());
        explanation = explanation != null ? explanation : Optional.empty();
        noMatchReason = noMatchReason != null ? noMatchReason : Optional.empty();
    }

    /**
     * Create a successful match result.
     */
    public static PatternMatchResult match(double confidence, Map<String, Object> data) {
        return new PatternMatchResult(true, true, confidence, 0.0, data, Optional.empty(), Optional.empty());
    }

    /**
     * Create a partial match result (matching in progress).
     */
    public static PatternMatchResult partial(double confidence, double uncertainty, Map<String, Object> data) {
        return new PatternMatchResult(true, false, confidence, uncertainty, data, Optional.empty(), Optional.empty());
    }

    /**
     * Create a no-match result.
     */
    public static PatternMatchResult noMatch() {
        return new PatternMatchResult(false, false, 0.0, 1.0, Map.of(), Optional.of("No match"), Optional.of("No match"));
    }

    /**
     * Create a no-match result with a specific reason.
     */
    public static PatternMatchResult noMatch(String reason) {
        return new PatternMatchResult(false, false, 0.0, 1.0, Map.of(), Optional.of(reason), Optional.of(reason));
    }

    /**
     * Convert to map representation.
     */
    public Map<String, Object> toMap() {
        return Map.of(
            "isMatch", isMatch,
            "isComplete", isComplete,
            "confidence", confidence,
            "uncertainty", uncertainty,
            "matchData", matchData,
            "explanation", explanation.orElse(""),
            "noMatchReason", noMatchReason.orElse("")
        );
    }
}
