package com.ghatana.core.pattern.learning;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a pattern discovered by the learning engine.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates a learned pattern with its metadata, confidence scores,
 * support metrics, and evolution history. Learned patterns can be
 * converted to executable patterns and continuously improved through
 * feedback and evolution.
 *
 * @see RealTimePatternLearningEngine
 * @doc.type class
 * @doc.purpose Discovered pattern representation
 * @doc.layer core
 * @doc.pattern Learning
 */
public class LearnedPattern {

    private final String signature;
    private final LearnedPatternType patternType;
    private final double confidence;
    private final long support;
    private final Map<String, Object> features;
    private final Instant discoveryTime;
    private final PatternEvolutionHistory evolutionHistory;
    private final PatternPerformanceMetrics performanceMetrics;

    private LearnedPattern(Builder builder) {
        this.signature = builder.signature;
        this.patternType = builder.patternType;
        this.confidence = builder.confidence;
        this.support = builder.support;
        this.features = Map.copyOf(builder.features);
        this.discoveryTime = builder.discoveryTime;
        this.evolutionHistory = builder.evolutionHistory;
        this.performanceMetrics = builder.performanceMetrics;
    }

    public String getSignature() { return signature; }
    public LearnedPatternType getPatternType() { return patternType; }
    public double getConfidence() { return confidence; }
    public long getSupport() { return support; }
    public Map<String, Object> getFeatures() { return features; }
    public Instant getDiscoveryTime() { return discoveryTime; }
    public PatternEvolutionHistory getEvolutionHistory() { return evolutionHistory; }
    public PatternPerformanceMetrics getPerformanceMetrics() { return performanceMetrics; }

    public double getFitnessScore() {
        return confidence * Math.log(support + 1) * performanceMetrics.getAccuracy();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LearnedPattern that = (LearnedPattern) o;
        return Objects.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(signature);
    }

    @Override
    public String toString() {
        return String.format("LearnedPattern{signature='%s', type=%s, confidence=%.3f, support=%d}",
                signature, patternType, confidence, support);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String signature;
        private LearnedPatternType patternType;
        private double confidence;
        private long support;
        private Map<String, Object> features = Map.of();
        private Instant discoveryTime = Instant.now();
        private PatternEvolutionHistory evolutionHistory = new PatternEvolutionHistory();
        private PatternPerformanceMetrics performanceMetrics = new PatternPerformanceMetrics();

        public Builder signature(String signature) {
            this.signature = signature;
            return this;
        }

        public Builder patternType(LearnedPatternType patternType) {
            this.patternType = patternType;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder support(long support) {
            this.support = support;
            return this;
        }

        public Builder features(Map<String, Object> features) {
            this.features = features;
            return this;
        }

        public Builder discoveryTime(Instant discoveryTime) {
            this.discoveryTime = discoveryTime;
            return this;
        }

        public Builder evolutionHistory(PatternEvolutionHistory evolutionHistory) {
            this.evolutionHistory = evolutionHistory;
            return this;
        }

        public Builder performanceMetrics(PatternPerformanceMetrics performanceMetrics) {
            this.performanceMetrics = performanceMetrics;
            return this;
        }

        public LearnedPattern build() {
            Objects.requireNonNull(signature, "Signature is required");
            Objects.requireNonNull(patternType, "Pattern type is required");
            return new LearnedPattern(this);
        }
    }
}
