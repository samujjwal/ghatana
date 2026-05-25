package com.ghatana.core.pattern.learning;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Event emitted when a pattern is suggested by the learning system.
 *
 * <p>This event represents a candidate pattern that has been discovered
 * through learning but has not yet been approved for production use.
 * Learning never mutates active rules directly; it only emits suggestions.
 *
 * @doc.type class
 * @doc.purpose Pattern suggestion event for learning-to-recommendation flow
 * @doc.layer core
 * @doc.pattern Learning
 */
public final class PatternSuggestedEvent {

    private final String suggestionId;
    private final String patternId;
    private final String tenantId;
    private final String candidatePatternSpec;
    private final double confidenceScore;
    private final Map<String, Object> evidence;
    private final Instant timestamp;
    private final String suggestedBy; // "learning", "human", "agent"

    public PatternSuggestedEvent(
            String suggestionId,
            String patternId,
            String tenantId,
            String candidatePatternSpec,
            double confidenceScore,
            Map<String, Object> evidence,
            String suggestedBy) {
        this.suggestionId = Objects.requireNonNull(suggestionId, "suggestionId must not be null");
        this.patternId = Objects.requireNonNull(patternId, "patternId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.candidatePatternSpec = Objects.requireNonNull(candidatePatternSpec, "candidatePatternSpec must not be null");
        this.confidenceScore = confidenceScore;
        this.evidence = Map.copyOf(Objects.requireNonNull(evidence, "evidence must not be null"));
        this.timestamp = Instant.now();
        this.suggestedBy = Objects.requireNonNull(suggestedBy, "suggestedBy must not be null");
    }

    public String suggestionId() {
        return suggestionId;
    }

    public String patternId() {
        return patternId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String candidatePatternSpec() {
        return candidatePatternSpec;
    }

    public double confidenceScore() {
        return confidenceScore;
    }

    public Map<String, Object> evidence() {
        return evidence;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public String suggestedBy() {
        return suggestedBy;
    }

    /**
     * Validates that this suggestion does not mutate active rules directly.
     * Learning systems must only emit suggestions, never modify active patterns.
     */
    public boolean isSuggestionOnly() {
        // Ensure this is a suggestion, not a direct mutation
        return !evidence.containsKey("directMutation") && 
               !evidence.containsKey("activeRuleModified");
    }
}
