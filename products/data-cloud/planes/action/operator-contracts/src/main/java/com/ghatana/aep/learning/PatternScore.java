package com.ghatana.aep.learning;

import java.util.Map;

/**
 * Explainable score for a learned or synthesized candidate pattern.
 *
 * @doc.type record
 * @doc.purpose Captures dissertation-aligned pattern scoring dimensions before lifecycle recommendation
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PatternScore(
        double support,
        double confidence,
        double lift,
        double predictiveValue,
        double novelty,
        double explainability,
        double expertFeedback,
        double agentReviewFeedback,
        double runtimeCost,
        double falsePositiveRisk,
        double falseNegativeRisk,
        double tenantRiskPolicy,
        Map<String, Object> explanation
) {

    public PatternScore {
        requireProbability(support, "support");
        requireProbability(confidence, "confidence");
        requireProbability(predictiveValue, "predictiveValue");
        requireProbability(novelty, "novelty");
        requireProbability(explainability, "explainability");
        requireProbability(expertFeedback, "expertFeedback");
        requireProbability(agentReviewFeedback, "agentReviewFeedback");
        requireProbability(runtimeCost, "runtimeCost");
        requireProbability(falsePositiveRisk, "falsePositiveRisk");
        requireProbability(falseNegativeRisk, "falseNegativeRisk");
        requireProbability(tenantRiskPolicy, "tenantRiskPolicy");
        if (Double.isNaN(lift) || lift < 0.0) {
            throw new IllegalArgumentException("lift must not be negative");
        }
        explanation = Map.copyOf(explanation != null ? explanation : Map.of());
    }

    public double recommendationScore() {
        double positive = support + confidence + predictiveValue + novelty + explainability
            + expertFeedback + agentReviewFeedback + tenantRiskPolicy;
        double risk = falsePositiveRisk + falseNegativeRisk + runtimeCost;
        return clamp((positive / 8.0) * 0.8 + Math.min(lift, 2.0) / 2.0 * 0.2 - risk / 3.0 * 0.35);
    }

    private static void requireProbability(double value, String fieldName) {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
