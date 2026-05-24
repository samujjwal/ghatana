package com.ghatana.core.pattern.learning;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a pattern discovered by the learning engine.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates a learned pattern with its metadata, confidence scores,
 * support metrics, and evolution history. Learned patterns can be
 * converted to executable patterns and continuously improved through
 * feedback and evolution.
 *
 * <p><b>Hardening (AEP-007)</b><br>
 * - Links learned patterns to reusable asset IDs
 * - Tracks pattern promotion status to reusable asset catalog
 * - Maintains evidence references for learning outcomes
 * - Supports commit SHA binding for production truth
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
    // AEP-007: Link learning to reusable assets
    private final Set<String> linkedReusableAssetIds;
    private final String promotedAssetId;
    private final String commitSha;
    private final String environment;

    private LearnedPattern(Builder builder) {
        this.signature = builder.signature;
        this.patternType = builder.patternType;
        this.confidence = builder.confidence;
        this.support = builder.support;
        this.features = Map.copyOf(builder.features);
        this.discoveryTime = builder.discoveryTime;
        this.evolutionHistory = builder.evolutionHistory;
        this.performanceMetrics = builder.performanceMetrics;
        this.linkedReusableAssetIds = Set.copyOf(builder.linkedReusableAssetIds);
        this.promotedAssetId = builder.promotedAssetId;
        this.commitSha = builder.commitSha;
        this.environment = builder.environment;
    }

    public String getSignature() { return signature; }
    public LearnedPatternType getPatternType() { return patternType; }
    public double getConfidence() { return confidence; }
    public long getSupport() { return support; }
    public Map<String, Object> getFeatures() { return features; }
    public Instant getDiscoveryTime() { return discoveryTime; }
    public PatternEvolutionHistory getEvolutionHistory() { return evolutionHistory; }
    public PatternPerformanceMetrics getPerformanceMetrics() { return performanceMetrics; }
    public Set<String> getLinkedReusableAssetIds() { return linkedReusableAssetIds; }
    public String getPromotedAssetId() { return promotedAssetId; }
    public String getCommitSha() { return commitSha; }
    public String getEnvironment() { return environment; }

    public double getFitnessScore() {
        return confidence * Math.log(support + 1) * performanceMetrics.getAccuracy();
    }

    /**
     * Checks if this learned pattern has been promoted to a reusable asset.
     *
     * @return true if promoted
     */
    public boolean isPromoted() {
        return promotedAssetId != null && !promotedAssetId.isEmpty();
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
        return String.format("LearnedPattern{signature='%s', type=%s, confidence=%.3f, support=%d, promoted=%s}",
                signature, patternType, confidence, support, promotedAssetId);
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
        // AEP-007: Link learning to reusable assets
        private Set<String> linkedReusableAssetIds = Set.of();
        private String promotedAssetId;
        private String commitSha;
        private String environment;

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

        // AEP-007: Link learning to reusable assets

        public Builder linkedReusableAssetIds(Set<String> assetIds) {
            this.linkedReusableAssetIds = assetIds;
            return this;
        }

        public Builder promotedAssetId(String assetId) {
            this.promotedAssetId = assetId;
            return this;
        }

        public Builder commitSha(String commitSha) {
            this.commitSha = commitSha;
            return this;
        }

        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public LearnedPattern build() {
            Objects.requireNonNull(signature, "Signature is required");
            Objects.requireNonNull(patternType, "Pattern type is required");
            return new LearnedPattern(this);
        }
    }
}
