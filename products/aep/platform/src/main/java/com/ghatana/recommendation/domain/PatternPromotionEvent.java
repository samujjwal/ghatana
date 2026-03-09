package com.ghatana.recommendation.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Domain event representing a pattern promotion decision.
 * This event is emitted to the EventLog when patterns are promoted or demoted.
 */
public class PatternPromotionEvent {
    private final String eventId;
    private final String patternId;
    private final String patternType;
    private final PromotionAction action;
    private final double scoreAtPromotion;
    private final double threshold;
    private final String reason;
    private final Instant timestamp;
    private final Map<String, Object> metadata;

    public PatternPromotionEvent(String eventId, String patternId, String patternType,
                                PromotionAction action, double scoreAtPromotion, double threshold,
                                String reason, Instant timestamp, Map<String, Object> metadata) {
        this.eventId = Objects.requireNonNull(eventId, "eventId cannot be null");
        this.patternId = Objects.requireNonNull(patternId, "patternId cannot be null");
        this.patternType = Objects.requireNonNull(patternType, "patternType cannot be null");
        this.action = Objects.requireNonNull(action, "action cannot be null");
        this.scoreAtPromotion = scoreAtPromotion;
        this.threshold = threshold;
        this.reason = Objects.requireNonNull(reason, "reason cannot be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
        this.metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata cannot be null"));
    }

    public String getEventId() {
        return eventId;
    }

    public String getPatternId() {
        return patternId;
    }

    public String getPatternType() {
        return patternType;
    }

    public PromotionAction getAction() {
        return action;
    }

    public double getScoreAtPromotion() {
        return scoreAtPromotion;
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

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Gets the score margin above/below the threshold at promotion.
     */
    public double getScoreMargin() {
        return scoreAtPromotion - threshold;
    }

    /**
     * Creates a promotion event.
     */
    public static PatternPromotionEvent promoted(String eventId, String patternId, String patternType,
                                               double score, double threshold, String reason,
                                               Map<String, Object> metadata) {
    return new PatternPromotionEvent(eventId, patternId, patternType, PromotionAction.PROMOTED,
        score, threshold, reason, Instant.now(), metadata);
    }

    /**
     * Creates a demotion event.
     */
    public static PatternPromotionEvent demoted(String eventId, String patternId, String patternType,
                                              double score, double threshold, String reason,
                                              Map<String, Object> metadata) {
    return new PatternPromotionEvent(eventId, patternId, patternType, PromotionAction.DEMOTED,
        score, threshold, reason, Instant.now(), metadata);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PatternPromotionEvent that = (PatternPromotionEvent) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public String toString() {
        return "PatternPromotionEvent{" +
               "eventId='" + eventId + '\'' +
               ", patternId='" + patternId + '\'' +
               ", action=" + action +
               ", score=" + scoreAtPromotion +
               ", threshold=" + threshold +
               '}';
    }

    /**
     * Actions that can be taken for pattern promotion.
     
 *
 * @doc.type enum
 * @doc.purpose Promotion action
 * @doc.layer core
 * @doc.pattern Enumeration
*/
    public enum PromotionAction {
        PROMOTED,   // Pattern promoted to active status
        DEMOTED,    // Pattern demoted from active status
        SUSPENDED,  // Pattern temporarily suspended
        REACTIVATED // Pattern reactivated after suspension
    }
}