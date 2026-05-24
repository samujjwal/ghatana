package com.ghatana.aep.learning;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Converts scored candidate patterns into governed recommendation events.
 *
 * @doc.type class
 * @doc.purpose Ensures learning emits pattern.suggested events instead of activating rules directly
 * @doc.layer product
 * @doc.pattern Policy
 */
public final class PatternRecommendationPolicy {

    private final Clock clock;
    private final double recommendationThreshold;

    public PatternRecommendationPolicy(Clock clock, double recommendationThreshold) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (Double.isNaN(recommendationThreshold) || recommendationThreshold < 0.0 || recommendationThreshold > 1.0) {
            throw new IllegalArgumentException("recommendationThreshold must be between 0.0 and 1.0");
        }
        this.recommendationThreshold = recommendationThreshold;
    }

    public boolean shouldRecommend(PatternCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return candidate.score().recommendationScore() >= recommendationThreshold;
    }

    public PatternSuggestionEvent suggest(PatternCandidate candidate) {
        if (!shouldRecommend(candidate)) {
            throw new IllegalArgumentException("candidate score is below recommendation threshold");
        }
        return new PatternSuggestionEvent(
            UUID.randomUUID().toString(),
            "pattern.suggested",
            candidate.tenantId(),
            candidate.candidateId(),
            candidate.score().recommendationScore(),
            clock.instant(),
            Map.of(
                "source", candidate.source(),
                "evidenceRefs", candidate.evidenceRefs(),
                "scoreExplanation", candidate.score().explanation()));
    }
}
