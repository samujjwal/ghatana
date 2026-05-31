/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.pattern.spec;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Recommendation candidate for pattern optimization (P4-02).
 *
 * <p>P4-02: Represents a candidate recommendation for improving a pattern
 * based on learning feedback, performance metrics, and execution history.
 * Recommendations can be automatically applied or require human review.
 *
 * @doc.type record
 * @doc.purpose Recommendation candidate for pattern optimization based on learning feedback
 * @doc.layer product
 * @doc.pattern Model
 */
public record RecommendationCandidate(
    String recommendationId,
    String patternId,
    RecommendationType recommendationType,
    String title,
    String description,
    double expectedImprovement,
    List<String> proposedChanges,
    Map<String, Object> impactAnalysis,
    boolean requiresHumanReview,
    boolean automaticallyApplicable,
    Optional<String> rollbackPlan
) {
    public RecommendationCandidate {
        if (recommendationId == null || recommendationId.isBlank()) {
            throw new IllegalArgumentException("recommendationId is required");
        }
        if (patternId == null || patternId.isBlank()) {
            throw new IllegalArgumentException("patternId is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description is required");
        }
        if (expectedImprovement < 0.0 || expectedImprovement > 1.0) {
            throw new IllegalArgumentException("expectedImprovement must be between 0.0 and 1.0");
        }
        proposedChanges = List.copyOf(proposedChanges != null ? proposedChanges : List.of());
        impactAnalysis = Map.copyOf(impactAnalysis != null ? impactAnalysis : Map.of());
        rollbackPlan = rollbackPlan != null ? rollbackPlan : Optional.empty();
    }

    /**
     * Check if this recommendation is high impact.
     */
    public boolean isHighImpact() {
        return expectedImprovement >= 0.5;
    }

    /**
     * Check if this recommendation can be automatically applied.
     */
    public boolean canAutoApply() {
        return automaticallyApplicable && !requiresHumanReview;
    }

    /**
     * Check if this recommendation has a rollback plan.
     */
    public boolean hasRollbackPlan() {
        return rollbackPlan.isPresent();
    }

    /**
     * Create a simple recommendation candidate.
     */
    public static RecommendationCandidate of(
            String recommendationId,
            String patternId,
            RecommendationType recommendationType,
            String title,
            String description,
            double expectedImprovement) {
        return new RecommendationCandidate(
            recommendationId,
            patternId,
            recommendationType,
            title,
            description,
            expectedImprovement,
            List.of(),
            Map.of(),
            false,
            false,
            Optional.empty()
        );
    }

    /**
     * Type of recommendation.
     */
    public enum RecommendationType {
        /** Optimize pattern structure for better performance */
        PERFORMANCE_OPTIMIZATION,
        /** Improve pattern accuracy through parameter tuning */
        ACCURACY_IMPROVEMENT,
        /** Reduce resource consumption */
        RESOURCE_REDUCTION,
        /** Reduce latency */
        LATENCY_REDUCTION,
        /** Improve error handling */
        ERROR_HANDLING_IMPROVEMENT,
        /** Add missing governance controls */
        GOVERNANCE_ENHANCEMENT,
        /** Improve observability */
        OBSERVABILITY_ENHANCEMENT,
        /** Refactor for better maintainability */
        REFACTORING,
        /** Update to use newer capabilities */
        CAPABILITY_UPGRADE
    }

    /**
     * Builder for constructing RecommendationCandidate.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String recommendationId;
        private String patternId;
        private RecommendationType recommendationType;
        private String title;
        private String description;
        private double expectedImprovement = 0.5;
        private final java.util.ArrayList<String> proposedChanges = new java.util.ArrayList<>();
        private final java.util.LinkedHashMap<String, Object> impactAnalysis = new java.util.LinkedHashMap<>();
        private boolean requiresHumanReview = false;
        private boolean automaticallyApplicable = false;
        private String rollbackPlan;

        public Builder recommendationId(String recommendationId) {
            this.recommendationId = recommendationId;
            return this;
        }

        public Builder patternId(String patternId) {
            this.patternId = patternId;
            return this;
        }

        public Builder recommendationType(RecommendationType recommendationType) {
            this.recommendationType = recommendationType;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder expectedImprovement(double expectedImprovement) {
            this.expectedImprovement = expectedImprovement;
            return this;
        }

        public Builder addProposedChange(String change) {
            this.proposedChanges.add(change);
            return this;
        }

        public Builder addImpactAnalysis(String key, Object value) {
            this.impactAnalysis.put(key, value);
            return this;
        }

        public Builder requiresHumanReview(boolean requires) {
            this.requiresHumanReview = requires;
            return this;
        }

        public Builder automaticallyApplicable(boolean applicable) {
            this.automaticallyApplicable = applicable;
            return this;
        }

        public Builder rollbackPlan(String plan) {
            this.rollbackPlan = plan;
            return this;
        }

        public RecommendationCandidate build() {
            return new RecommendationCandidate(
                recommendationId,
                patternId,
                recommendationType,
                title,
                description,
                expectedImprovement,
                proposedChanges,
                impactAnalysis,
                requiresHumanReview,
                automaticallyApplicable,
                Optional.ofNullable(rollbackPlan)
            );
        }
    }
}
