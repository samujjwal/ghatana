package com.ghatana.recommendation.config;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for pattern recommendation thresholds and behavior.
 * Includes hysteresis settings to prevent oscillation between promotion/demotion.
 
 *
 * @doc.type class
 * @doc.purpose Recommendation config
 * @doc.layer core
 * @doc.pattern Configuration
*/
public class RecommendationConfig {
    private final double promotionThreshold;
    private final double demotionThreshold;
    private final double hysteresisMargin;
    private final Duration evaluationInterval;
    private final Duration cooldownPeriod;
    private final int minimumObservations;
    private final Map<String, Double> patternTypeThresholds;

    public RecommendationConfig(double promotionThreshold, double demotionThreshold, 
                               double hysteresisMargin, Duration evaluationInterval,
                               Duration cooldownPeriod, int minimumObservations,
                               Map<String, Double> patternTypeThresholds) {
        this.promotionThreshold = promotionThreshold;
        this.demotionThreshold = demotionThreshold;
        this.hysteresisMargin = hysteresisMargin;
        this.evaluationInterval = Objects.requireNonNull(evaluationInterval, "evaluationInterval cannot be null");
        this.cooldownPeriod = Objects.requireNonNull(cooldownPeriod, "cooldownPeriod cannot be null");
        this.minimumObservations = minimumObservations;
        this.patternTypeThresholds = Map.copyOf(Objects.requireNonNull(patternTypeThresholds, "patternTypeThresholds cannot be null"));
    }

    /**
     * Creates a default configuration suitable for most use cases.
     */
    public static RecommendationConfig defaults() {
        return new RecommendationConfig(
            0.8,                                    // promotionThreshold
            0.6,                                    // demotionThreshold
            0.1,                                    // hysteresisMargin
            Duration.ofMinutes(5),                  // evaluationInterval
            Duration.ofMinutes(15),                 // cooldownPeriod
            10,                                     // minimumObservations
            Map.of(                                 // patternTypeThresholds
                "high-frequency", 0.9,
                "critical-path", 0.85,
                "experimental", 0.7
            )
        );
    }

    /**
     * Creates a conservative configuration with higher thresholds.
     */
    public static RecommendationConfig conservative() {
        return new RecommendationConfig(
            0.9,                                    // promotionThreshold
            0.7,                                    // demotionThreshold
            0.15,                                   // hysteresisMargin
            Duration.ofMinutes(10),                 // evaluationInterval
            Duration.ofMinutes(30),                 // cooldownPeriod
            20,                                     // minimumObservations
            Map.of(
                "high-frequency", 0.95,
                "critical-path", 0.92,
                "experimental", 0.8
            )
        );
    }

    /**
     * Creates an aggressive configuration with lower thresholds.
     */
    public static RecommendationConfig aggressive() {
        return new RecommendationConfig(
            0.7,                                    // promotionThreshold
            0.5,                                    // demotionThreshold
            0.05,                                   // hysteresisMargin
            Duration.ofMinutes(2),                  // evaluationInterval
            Duration.ofMinutes(5),                  // cooldownPeriod
            5,                                      // minimumObservations
            Map.of(
                "high-frequency", 0.75,
                "critical-path", 0.8,
                "experimental", 0.6
            )
        );
    }

    /**
     * Gets the promotion threshold for a specific pattern type.
     */
    public double getPromotionThreshold(String patternType) {
        return patternTypeThresholds.getOrDefault(patternType, promotionThreshold);
    }

    /**
     * Gets the demotion threshold for a specific pattern type.
     */
    public double getDemotionThreshold(String patternType) {
        // Use the default demotion threshold for all pattern types
        // Pattern-specific thresholds only apply to promotion
        return demotionThreshold;
    }

    /**
     * Checks if a score is above the promotion threshold with hysteresis.
     */
    public boolean shouldPromote(double score, String patternType) {
        return score >= getPromotionThreshold(patternType) + hysteresisMargin;
    }

    /**
     * Checks if a score is below the demotion threshold with hysteresis.
     */
    public boolean shouldDemote(double score, String patternType) {
        return score <= getDemotionThreshold(patternType) - hysteresisMargin;
    }

    public double getPromotionThreshold() {
        return promotionThreshold;
    }

    public double getDemotionThreshold() {
        return demotionThreshold;
    }

    public double getHysteresisMargin() {
        return hysteresisMargin;
    }

    public Duration getEvaluationInterval() {
        return evaluationInterval;
    }

    public Duration getCooldownPeriod() {
        return cooldownPeriod;
    }

    public int getMinimumObservations() {
        return minimumObservations;
    }

    public Map<String, Double> getPatternTypeThresholds() {
        return patternTypeThresholds;
    }

    @Override
    public String toString() {
        return "RecommendationConfig{" +
               "promotionThreshold=" + promotionThreshold +
               ", demotionThreshold=" + demotionThreshold +
               ", hysteresisMargin=" + hysteresisMargin +
               ", evaluationInterval=" + evaluationInterval +
               ", cooldownPeriod=" + cooldownPeriod +
               ", minimumObservations=" + minimumObservations +
               '}';
    }
}