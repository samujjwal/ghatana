package com.ghatana.datacloud.attention;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable salience score representing the priority/importance of an event or record.
 *
 * @doc.type class
 * @doc.purpose Immutable salience score
 * @doc.layer core
 * @doc.pattern Value Object
 */
public final class SalienceScore {

    public static final double HIGH_THRESHOLD = 0.7;
    public static final double CRITICAL_THRESHOLD = 0.9;
    public static final double EMERGENCY_THRESHOLD = 0.95;

    private final double score;
    private final double noveltyScore;
    private final double deviationScore;
    private final double relevanceScore;
    private final double urgencyScore;
    private final Map<String, Object> breakdown;
    private final Instant computedAt;
    private final String scorerId;
    private final String modelVersion;
    private final double confidence;

    private SalienceScore(SalienceScoreBuilder builder) {
        this.score = builder.score;
        this.noveltyScore = builder.noveltyScore;
        this.deviationScore = builder.deviationScore;
        this.relevanceScore = builder.relevanceScore;
        this.urgencyScore = builder.urgencyScore;
        this.breakdown = Collections.unmodifiableMap(builder.breakdown);
        this.computedAt = builder.computedAt;
        this.scorerId = builder.scorerId;
        this.modelVersion = builder.modelVersion;
        this.confidence = builder.confidence;
    }

    public static SalienceScoreBuilder builder() {
        return new SalienceScoreBuilder();
    }

    public SalienceScoreBuilder toBuilder() {
        return builder()
            .score(score)
            .noveltyScore(noveltyScore)
            .deviationScore(deviationScore)
            .relevanceScore(relevanceScore)
            .urgencyScore(urgencyScore)
            .breakdown(breakdown)
            .computedAt(computedAt)
            .scorerId(scorerId)
            .modelVersion(modelVersion)
            .confidence(confidence);
    }

    public double getScore() {
        return score;
    }

    public double getNoveltyScore() {
        return noveltyScore;
    }

    public double getDeviationScore() {
        return deviationScore;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }

    public double getUrgencyScore() {
        return urgencyScore;
    }

    public Map<String, Object> getBreakdown() {
        return breakdown;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public String getScorerId() {
        return scorerId;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public double getConfidence() {
        return confidence;
    }

    public boolean isHigh() {
        return score >= HIGH_THRESHOLD;
    }

    public boolean isCritical() {
        return score >= CRITICAL_THRESHOLD;
    }

    public boolean isEmergency() {
        return score >= EMERGENCY_THRESHOLD;
    }

    public static SalienceScore zero() {
        return SalienceScore.builder().score(0.0).build();
    }

    public static SalienceScore max() {
        return SalienceScore.builder().score(1.0).build();
    }

    public static SalienceScore of(double score) {
        validateScore(score, "score");
        return SalienceScore.builder().score(score).build();
    }

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

        double avgNovelty = scores.keySet().stream().mapToDouble(SalienceScore::getNoveltyScore).average().orElse(0);
        double avgDeviation = scores.keySet().stream().mapToDouble(SalienceScore::getDeviationScore).average().orElse(0);
        double avgRelevance = scores.keySet().stream().mapToDouble(SalienceScore::getRelevanceScore).average().orElse(0);
        double avgUrgency = scores.keySet().stream().mapToDouble(SalienceScore::getUrgencyScore).average().orElse(0);

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
            throw new IllegalArgumentException(name + " must be in range [0.0, 1.0], got " + score);
        }
        if (Double.isNaN(score) || Double.isInfinite(score)) {
            throw new IllegalArgumentException(name + " cannot be NaN or infinite");
        }
    }

    public static final class SalienceScoreBuilder {
        private double score;
        private double noveltyScore = 0.0;
        private double deviationScore = 0.0;
        private double relevanceScore = 0.0;
        private double urgencyScore = 0.0;
        private Map<String, Object> breakdown = Collections.emptyMap();
        private Instant computedAt = Instant.now();
        private String scorerId = "default";
        private String modelVersion = "1.0.0";
        private double confidence = 1.0;

        private SalienceScoreBuilder() {
        }

        public SalienceScoreBuilder score(double score) {
            this.score = score;
            return this;
        }

        public SalienceScoreBuilder noveltyScore(double noveltyScore) {
            this.noveltyScore = noveltyScore;
            return this;
        }

        public SalienceScoreBuilder deviationScore(double deviationScore) {
            this.deviationScore = deviationScore;
            return this;
        }

        public SalienceScoreBuilder relevanceScore(double relevanceScore) {
            this.relevanceScore = relevanceScore;
            return this;
        }

        public SalienceScoreBuilder urgencyScore(double urgencyScore) {
            this.urgencyScore = urgencyScore;
            return this;
        }

        public SalienceScoreBuilder breakdown(Map<String, Object> breakdown) {
            this.breakdown = breakdown != null ? breakdown : Collections.emptyMap();
            return this;
        }

        public SalienceScoreBuilder computedAt(Instant computedAt) {
            this.computedAt = computedAt != null ? computedAt : Instant.now();
            return this;
        }

        public SalienceScoreBuilder scorerId(String scorerId) {
            this.scorerId = scorerId != null ? scorerId : "default";
            return this;
        }

        public SalienceScoreBuilder modelVersion(String modelVersion) {
            this.modelVersion = modelVersion != null ? modelVersion : "1.0.0";
            return this;
        }

        public SalienceScoreBuilder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public SalienceScore build() {
            validateScore(score, "score");
            validateScore(noveltyScore, "noveltyScore");
            validateScore(deviationScore, "deviationScore");
            validateScore(relevanceScore, "relevanceScore");
            validateScore(urgencyScore, "urgencyScore");
            validateScore(confidence, "confidence");
            return new SalienceScore(this);
        }
    }
}
