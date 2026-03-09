package com.ghatana.recommendation.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a pattern recommendation decision with scoring context.
 */
public class PatternRecommendation {
    private final String patternId;
    private final String patternType;
    private final RecommendationDecision recommendationDecision;
    private final double currentScore;
    private final double threshold;
    private final String reason;
    private final Instant timestamp;
    private final Map<String, Object> context;

    public PatternRecommendation(String patternId, String patternType, 
                                RecommendationDecision recommendationDecision, double currentScore, 
                                double threshold, String reason, Instant timestamp, 
                                Map<String, Object> context) {
        this.patternId = Objects.requireNonNull(patternId, "patternId cannot be null");
        this.patternType = Objects.requireNonNull(patternType, "patternType cannot be null");
        this.recommendationDecision = Objects.requireNonNull(recommendationDecision, "recommendationDecision cannot be null");
        this.currentScore = currentScore;
        this.threshold = threshold;
        this.reason = Objects.requireNonNull(reason, "reason cannot be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
        this.context = Map.copyOf(Objects.requireNonNull(context, "context cannot be null"));
    }

    public String getPatternId() {
        return patternId;
    }

    public String getPatternType() {
        return patternType;
    }

    public RecommendationDecision getRecommendationDecision() {
        return recommendationDecision;
    }

    public double getCurrentScore() {
        return currentScore;
    }

    public double getThreshold() {
        return threshold;
    }

    public String getReason() {
        return reason;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    /**
     * Checks if this recommendation is a promotion.
     */
    public boolean isPromotion() {
        if (recommendationDecision == RecommendationDecision.PROMOTE) {
            return true;
        }
        return false;
    }

    /**
     * Checks if this recommendation is a demotion.
     */
    public boolean isDemotion() {
        if (recommendationDecision == RecommendationDecision.DEMOTE) {
            return true;
        }
        return false;
    }

    /**
     * Gets the score margin above/below the threshold.
     */
    public double getScoreMargin() {
        return currentScore - threshold;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PatternRecommendation that = (PatternRecommendation) o;
        return Objects.equals(patternId, that.patternId) &&
               Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(patternId, timestamp);
    }

    @Override
    public String toString() {
        return "PatternRecommendation{" +
               "patternId='" + patternId + '\'' +
               ", type=" + recommendationDecision +
               ", currentScore=" + currentScore +
               ", threshold=" + threshold +
               ", margin=" + getScoreMargin() +
               '}';
    }

    /**
     * Types of pattern recommendations.
     
 *
 * @doc.type enum
 * @doc.purpose Recommendation decision
 * @doc.layer core
 * @doc.pattern Enumeration
*/
    public enum RecommendationDecision {
        PROMOTE,    // Pattern should be promoted to active use
        DEMOTE,     // Pattern should be demoted from active use
        MAINTAIN,   // Pattern should maintain current status
        EVALUATE    // Pattern needs further evaluation
    }
}
