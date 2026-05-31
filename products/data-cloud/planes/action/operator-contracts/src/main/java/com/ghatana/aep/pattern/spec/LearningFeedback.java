/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.pattern.spec;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Learning feedback from pattern execution (P4-02).
 *
 * <p>P4-02: Captures feedback from pattern execution that can be used
 * to improve pattern performance, accuracy, and efficiency through
 * machine learning and adaptive optimization.
 *
 * @doc.type record
 * @doc.purpose Learning feedback from pattern execution for adaptive optimization
 * @doc.layer product
 * @doc.pattern Model
 */
public record LearningFeedback(
    String patternId,
    String executionId,
    Instant timestamp,
    FeedbackType feedbackType,
    double confidence,
    Map<String, Object> metrics,
    List<String> suggestions,
    Optional<String> rootCauseAnalysis,
    Optional<String> improvementRecommendation
) {
    public LearningFeedback {
        if (patternId == null || patternId.isBlank()) {
            throw new IllegalArgumentException("patternId is required");
        }
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId is required");
        }
        timestamp = timestamp != null ? timestamp : Instant.now();
        feedbackType = feedbackType != null ? feedbackType : FeedbackType.GENERAL;
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        metrics = Map.copyOf(metrics != null ? metrics : Map.of());
        suggestions = List.copyOf(suggestions != null ? suggestions : List.of());
        rootCauseAnalysis = rootCauseAnalysis != null ? rootCauseAnalysis : Optional.empty();
        improvementRecommendation = improvementRecommendation != null ? improvementRecommendation : Optional.empty();
    }

    /**
     * Check if this feedback has high confidence.
     */
    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }

    /**
     * Check if this feedback has any suggestions.
     */
    public boolean hasSuggestions() {
        return !suggestions.isEmpty();
    }

    /**
     * Check if this feedback has an improvement recommendation.
     */
    public boolean hasImprovementRecommendation() {
        return improvementRecommendation.isPresent();
    }

    /**
     * Create a simple feedback object with just a feedback type and confidence.
     */
    public static LearningFeedback of(String patternId, String executionId, FeedbackType feedbackType, double confidence) {
        return new LearningFeedback(
            patternId,
            executionId,
            Instant.now(),
            feedbackType,
            confidence,
            Map.of(),
            List.of(),
            Optional.empty(),
            Optional.empty()
        );
    }

    /**
     * Type of learning feedback.
     */
    public enum FeedbackType {
        /** General feedback without specific categorization */
        GENERAL,
        /** Feedback on pattern performance */
        PERFORMANCE,
        /** Feedback on pattern accuracy */
        ACCURACY,
        /** Feedback on resource utilization */
        RESOURCE_UTILIZATION,
        /** Feedback on false positives/negatives */
        FALSE_POSITIVE_NEGATIVE,
        /** Feedback on latency */
        LATENCY,
        /** Feedback on error rates */
        ERROR_RATE,
        /** Feedback on user satisfaction */
        USER_SATISFACTION
    }

    /**
     * Builder for constructing LearningFeedback.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String patternId;
        private String executionId;
        private Instant timestamp;
        private FeedbackType feedbackType = FeedbackType.GENERAL;
        private double confidence = 0.5;
        private final java.util.LinkedHashMap<String, Object> metrics = new java.util.LinkedHashMap<>();
        private final java.util.ArrayList<String> suggestions = new java.util.ArrayList<>();
        private String rootCauseAnalysis;
        private String improvementRecommendation;

        public Builder patternId(String patternId) {
            this.patternId = patternId;
            return this;
        }

        public Builder executionId(String executionId) {
            this.executionId = executionId;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder feedbackType(FeedbackType feedbackType) {
            this.feedbackType = feedbackType;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder addMetric(String key, Object value) {
            this.metrics.put(key, value);
            return this;
        }

        public Builder addSuggestion(String suggestion) {
            this.suggestions.add(suggestion);
            return this;
        }

        public Builder rootCauseAnalysis(String analysis) {
            this.rootCauseAnalysis = analysis;
            return this;
        }

        public Builder improvementRecommendation(String recommendation) {
            this.improvementRecommendation = recommendation;
            return this;
        }

        public LearningFeedback build() {
            return new LearningFeedback(
                patternId,
                executionId,
                timestamp,
                feedbackType,
                confidence,
                metrics,
                suggestions,
                Optional.ofNullable(rootCauseAnalysis),
                Optional.ofNullable(improvementRecommendation)
            );
        }
    }
}
