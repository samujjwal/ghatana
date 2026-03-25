package com.ghatana.products.yappc.domain.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Output from the Prediction Agent.
 *
 * @doc.type record
 * @doc.purpose Prediction agent output
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record PredictionOutput(
        @NotNull List<PhasePrediction> phasePredictions,
        @NotNull String estimatedCompletionDate,
        @NotNull ConfidenceInterval confidenceInterval,
        @NotNull RiskScore riskScore,
        @NotNull List<RiskFactor> riskFactors,
        @NotNull List<Recommendation> recommendations,
        @Nullable List<PredictionInput.SimilarItem> similarHistoricalItems
) {

    /**
     * Prediction for a specific phase.
     */
    public record PhasePrediction(
            @NotNull String phaseId,
            @NotNull String phaseName,
            @NotNull String predictedStartDate,
            @NotNull String predictedEndDate,
            int predictedDurationDays,
            double confidence,
            @Nullable String actualStartDate,
            @Nullable String actualEndDate
    ) {}

    /**
     * Confidence interval for predictions.
     */
    public record ConfidenceInterval(
            double lower,
            double upper,
            double confidence
    ) {
        public static ConfidenceInterval of95(double lower, double upper) {
            return new ConfidenceInterval(lower, upper, 0.95);
        }

        public static ConfidenceInterval of90(double lower, double upper) {
            return new ConfidenceInterval(lower, upper, 0.90);
        }
    }

    /**
     * Risk score with breakdown.
     */
    public record RiskScore(
            double overall,
            @NotNull RiskBreakdown breakdown
    ) {
        public record RiskBreakdown(
                double velocity,
                double complexity,
                double dependencies,
                double historical
        ) {}

        public String getRiskLevel() {
            if (overall >= 0.7) return "HIGH";
            if (overall >= 0.4) return "MEDIUM";
            return "LOW";
        }
    }

    /**
     * A specific risk factor.
     */
    public record RiskFactor(
            @NotNull String id,
            @NotNull String name,
            @NotNull String description,
            double impact,
            double probability,
            @Nullable String mitigation
    ) {
        public double getRiskScore() {
            return impact * probability;
        }
    }

    /**
     * A recommendation for mitigating risks or improving outcomes.
     */
    public record Recommendation(
            @NotNull String id,
            @NotNull String title,
            @NotNull String description,
            @NotNull RecommendationType type,
            @NotNull Priority priority,
            double expectedImpact,
            @Nullable Map<String, Object> actionParameters
    ) {
        public enum RecommendationType {
            RESOURCE,
            PROCESS,
            TIMELINE,
            SCOPE,
            DEPENDENCY
        }

        public enum Priority {
            LOW,
            MEDIUM,
            HIGH,
            CRITICAL
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<PhasePrediction> phasePredictions = List.of();
        private String estimatedCompletionDate;
        private ConfidenceInterval confidenceInterval;
        private RiskScore riskScore;
        private List<RiskFactor> riskFactors = List.of();
        private List<Recommendation> recommendations = List.of();
        private List<PredictionInput.SimilarItem> similarHistoricalItems;

        public Builder phasePredictions(List<PhasePrediction> phasePredictions) {
            this.phasePredictions = phasePredictions;
            return this;
        }

        public Builder estimatedCompletionDate(String estimatedCompletionDate) {
            this.estimatedCompletionDate = estimatedCompletionDate;
            return this;
        }

        public Builder confidenceInterval(ConfidenceInterval confidenceInterval) {
            this.confidenceInterval = confidenceInterval;
            return this;
        }

        public Builder riskScore(RiskScore riskScore) {
            this.riskScore = riskScore;
            return this;
        }

        public Builder riskFactors(List<RiskFactor> riskFactors) {
            this.riskFactors = riskFactors;
            return this;
        }

        public Builder recommendations(List<Recommendation> recommendations) {
            this.recommendations = recommendations;
            return this;
        }

        public Builder similarHistoricalItems(List<PredictionInput.SimilarItem> similarHistoricalItems) {
            this.similarHistoricalItems = similarHistoricalItems;
            return this;
        }

        public PredictionOutput build() {
            return new PredictionOutput(
                    phasePredictions,
                    estimatedCompletionDate,
                    confidenceInterval,
                    riskScore,
                    riskFactors,
                    recommendations,
                    similarHistoricalItems
            );
        }
    }
}
