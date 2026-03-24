package com.ghatana.products.yappc.domain.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Output from the Anomaly Detector Agent.
 *
 * @doc.type record
 * @doc.purpose Anomaly detector agent output
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record AnomalyOutput(
        int anomaliesDetected,
        @NotNull List<DetectedAnomaly> anomalies,
        @NotNull Baseline baseline,
        @NotNull List<MitigationStep> recommendations
) {

    /**
     * A detected anomaly.
     */
    public record DetectedAnomaly(
            @NotNull String id,
            @NotNull AnomalyType type,
            @NotNull Severity severity,
            @NotNull String title,
            @NotNull String description,
            @NotNull String detectedAt,
            @NotNull List<String> affectedItems,
            double baselineValue,
            double currentValue,
            double deviationPercent,
            double confidence,
            @NotNull String modelVersion,
            @Nullable RootCauseAnalysis rootCause
    ) {

        /**
         * Types of anomalies.
         */
        public enum AnomalyType {
            VELOCITY_DROP,
            VELOCITY_SPIKE,
            PATTERN_BREAK,
            SECURITY_THREAT,
            QUALITY_DEGRADATION,
            RESOURCE_EXHAUSTION,
            UNUSUAL_ACCESS,
            CUSTOM
        }

        /**
         * Severity levels.
         */
        public enum Severity {
            INFO(0.3),
            WARNING(0.5),
            CRITICAL(0.8);

            private final double threshold;

            Severity(double threshold) {
                this.threshold = threshold;
            }

            public double getThreshold() {
                return threshold;
            }

            public static Severity fromScore(double score) {
                if (score >= CRITICAL.threshold) return CRITICAL;
                if (score >= WARNING.threshold) return WARNING;
                return INFO;
            }
        }

        public double getSeverityScore() {
            return Math.abs(deviationPercent) * confidence;
        }
    }

    /**
     * Root cause analysis for an anomaly.
     */
    public record RootCauseAnalysis(
            @NotNull String category,
            @NotNull String description,
            double confidence,
            @NotNull List<String> contributingFactors
    ) {}

    /**
     * Baseline metrics for comparison.
     */
    public record Baseline(
            double mean,
            double stdDev,
            double min,
            double max,
            int dataPoints,
            @NotNull String timeWindow
    ) {}

    /**
     * A mitigation step recommendation.
     */
    public record MitigationStep(
            @NotNull String id,
            @NotNull String title,
            @NotNull String description,
            @NotNull MitigationPriority priority,
            @NotNull MitigationType type,
            @Nullable Map<String, Object> parameters
    ) {
        public enum MitigationType {
            IMMEDIATE,
            SHORT_TERM,
            LONG_TERM
        }

        public enum MitigationPriority {
            LOW,
            MEDIUM,
            HIGH,
            URGENT
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int anomaliesDetected;
        private List<DetectedAnomaly> anomalies = List.of();
        private Baseline baseline;
        private List<MitigationStep> recommendations = List.of();

        public Builder anomaliesDetected(int anomaliesDetected) {
            this.anomaliesDetected = anomaliesDetected;
            return this;
        }

        public Builder anomalies(List<DetectedAnomaly> anomalies) {
            this.anomalies = anomalies;
            return this;
        }

        public Builder baseline(Baseline baseline) {
            this.baseline = baseline;
            return this;
        }

        public Builder recommendations(List<MitigationStep> recommendations) {
            this.recommendations = recommendations;
            return this;
        }

        public AnomalyOutput build() {
            return new AnomalyOutput(anomaliesDetected, anomalies, baseline, recommendations);
        }
    }
}
