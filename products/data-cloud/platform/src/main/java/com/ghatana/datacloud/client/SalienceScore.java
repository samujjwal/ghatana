package com.ghatana.datacloud.attention;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable salience score representing the priority/importance of an event or record.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates the computed salience of a data item with detailed breakdown of
 * contributing factors. Used by the attention system to prioritize processing
 * and route items to appropriate handlers.
 *
 * <p><b>Score Range</b><br>
 * All scores are normalized to [0.0, 1.0] where:
 * <ul>
 *   <li>0.0 - No salience (can be ignored/archived)</li>
 *   <li>0.3 - Low salience (background processing)</li>
 *   <li>0.5 - Medium salience (standard priority)</li>
 *   <li>0.7 - High salience (elevated attention)</li>
 *   <li>0.9+ - Critical salience (immediate attention)</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SalienceScore score = SalienceScore.builder()
 *     .score(0.85)
 *     .noveltyScore(0.9)
 *     .deviationScore(0.7)
 *     .relevanceScore(0.8)
 *     .urgencyScore(0.95)
 *     .breakdown(Map.of(
 *         "anomalyDetected", true,
 *         "patternMatch", "fraud-detection-001"
 *     ))
 *     .build();
 *
 * if (score.isCritical()) {
 *     // Route to emergency handler
 * }
 * }</pre>
 *
 * @see SalienceScorer
 * @see AttentionManager
 * @doc.type record
 * @doc.purpose Immutable salience score
 * @doc.layer core
 * @doc.pattern Value Object
 */
@Value
@Builder(toBuilder = true)
public class SalienceScore {

    /**
     * Threshold for high salience requiring elevated attention.
     */
    public static final double HIGH_THRESHOLD = 0.7;

    /**
     * Threshold for critical salience requiring immediate attention.
     */
    public static final double CRITICAL_THRESHOLD = 0.9;

    /**
     * Threshold for emergency salience requiring broadcast.
     */
    public static final double EMERGENCY_THRESHOLD = 0.95;

    /**
     * Combined salience score [0.0, 1.0].
     */
    double score;

    /**
     * Novelty component - how unusual is this item.
     */
    @Builder.Default
    double noveltyScore = 0.0;

    /**
     * Deviation component - how far from baseline.
     */
    @Builder.Default
    double deviationScore = 0.0;

    /**
     * Relevance component - how aligned with goals.
     */
    @Builder.Default
    double relevanceScore = 0.0;

    /**
     * Urgency component - time sensitivity.
     */
    @Builder.Default
    double urgencyScore = 0.0;

    /**
     * Detailed breakdown of scoring factors.
     */
    @Builder.Default
    Map<String, Object> breakdown = Collections.emptyMap();

    /**
     * Timestamp when score was computed.
     */
    @Builder.Default
    Instant computedAt = Instant.now();

    /**
     * ID of the scorer that computed this score.
     */
    @Builder.Default
    String scorerId = "default";

    /**
     * Model version used for ML-based scoring.
     */
    @Builder.Default
    String modelVersion = "1.0.0";

    /**
     * Confidence in the score accuracy [0.0, 1.0].
     */
    @Builder.Default
    double confidence = 1.0;

    /**
     * Check if this score indicates high salience.
     *
     * @return true if score >= HIGH_THRESHOLD
     */
    public boolean isHigh() {
        return score >= HIGH_THRESHOLD;
    }

    /**
     * Check if this score indicates critical salience.
     *
     * @return true if score >= CRITICAL_THRESHOLD
     */
    public boolean isCritical() {
        return score >= CRITICAL_THRESHOLD;
    }

    /**
     * Check if this score indicates emergency salience.
     *
     * @return true if score >= EMERGENCY_THRESHOLD
     */
    public boolean isEmergency() {
        return score >= EMERGENCY_THRESHOLD;
    }

    /**
     * Create a zero salience score.
     *
     * @return SalienceScore with score = 0.0
     */
    public static SalienceScore zero() {
        return SalienceScore.builder().score(0.0).build();
    }

    /**
     * Create a maximum salience score.
     *
     * @return SalienceScore with score = 1.0
     */
    public static SalienceScore max() {
        return SalienceScore.builder().score(1.0).build();
    }

    /**
     * Create a score from a simple value.
     *
     * @param score The score value [0.0, 1.0]
     * @return SalienceScore with the given score
     * @throws IllegalArgumentException if score is out of range
     */
    public static SalienceScore of(double score) {
        validateScore(score, "score");
        return SalienceScore.builder().score(score).build();
    }

    /**
     * Combine multiple salience scores using weighted average.
     *
     * @param scores Map of score to weight
     * @return Combined SalienceScore
     */
    public static SalienceScore combine(Map<SalienceScore, Double> scores) {
        Objects.requireNonNull(scores, "scores cannot be null");
        if (scores.isEmpty()) {
            return zero();
        }

        double totalWeight = scores.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalWeight == 0) {
            return zero();
        }

        double weightedSum = scores.entrySet().stream()
                .mapToDouble(e -> e.getKey().getScore() * e.getValue())
                .sum();

        double combinedScore = weightedSum / totalWeight;

        // Average component scores
        double avgNovelty = scores.keySet().stream()
                .mapToDouble(SalienceScore::getNoveltyScore).average().orElse(0);
        double avgDeviation = scores.keySet().stream()
                .mapToDouble(SalienceScore::getDeviationScore).average().orElse(0);
        double avgRelevance = scores.keySet().stream()
                .mapToDouble(SalienceScore::getRelevanceScore).average().orElse(0);
        double avgUrgency = scores.keySet().stream()
                .mapToDouble(SalienceScore::getUrgencyScore).average().orElse(0);

        return SalienceScore.builder()
                .score(combinedScore)
                .noveltyScore(avgNovelty)
                .deviationScore(avgDeviation)
                .relevanceScore(avgRelevance)
                .urgencyScore(avgUrgency)
                .breakdown(Map.of("combinedFrom", scores.size()))
                .build();
    }

    private static void validateScore(double score, String name) {
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException(
                    name + " must be in range [0.0, 1.0], got " + score);
        }
        if (Double.isNaN(score) || Double.isInfinite(score)) {
            throw new IllegalArgumentException(
                    name + " cannot be NaN or infinite");
        }
    }

    /**
     * Custom builder to validate scores.
     */
    public static class SalienceScoreBuilder {
        public SalienceScore build() {
            // Validate all scores are in range
            if (score < 0.0 || score > 1.0) {
                throw new IllegalArgumentException("score must be in range [0.0, 1.0]");
            }
            if (noveltyScore$value < 0.0 || noveltyScore$value > 1.0) {
                throw new IllegalArgumentException("noveltyScore must be in range [0.0, 1.0]");
            }
            if (deviationScore$value < 0.0 || deviationScore$value > 1.0) {
                throw new IllegalArgumentException("deviationScore must be in range [0.0, 1.0]");
            }
            if (relevanceScore$value < 0.0 || relevanceScore$value > 1.0) {
                throw new IllegalArgumentException("relevanceScore must be in range [0.0, 1.0]");
            }
            if (urgencyScore$value < 0.0 || urgencyScore$value > 1.0) {
                throw new IllegalArgumentException("urgencyScore must be in range [0.0, 1.0]");
            }
            if (confidence$value < 0.0 || confidence$value > 1.0) {
                throw new IllegalArgumentException("confidence must be in range [0.0, 1.0]");
            }

            return new SalienceScore(
                    score,
                    noveltyScore$set ? noveltyScore$value : 0.0,
                    deviationScore$set ? deviationScore$value : 0.0,
                    relevanceScore$set ? relevanceScore$value : 0.0,
                    urgencyScore$set ? urgencyScore$value : 0.0,
                    breakdown$set ? breakdown$value : Collections.emptyMap(),
                    computedAt$set ? computedAt$value : Instant.now(),
                    scorerId$set ? scorerId$value : "default",
                    modelVersion$set ? modelVersion$value : "1.0.0",
                    confidence$set ? confidence$value : 1.0
            );
        }
    }
}
