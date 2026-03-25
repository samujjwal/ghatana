/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.learning.review;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;

/**
 * Represents an item pending human review.
 *
 * <p>A review item is a first-class entity that tracks the full lifecycle
 * of a review request: creation, assignment, decision, and completion.
 *
 * @doc.type class
 * @doc.purpose Review queue item entity
 * @doc.layer agent-learning
 * @doc.pattern Entity
 *
 * @since 2.4.0
 */
public final class ReviewItem {

    private final String reviewId;
    private final String tenantId;
    private final String skillId;
    private final String proposedVersion;
    private final ReviewItemType itemType;
    private final double confidenceScore;
    private final String evaluationSummary;
    private final Map<String, Object> context;
    private final Instant createdAt;

    private volatile ReviewStatus status;
    private volatile ReviewDecision decision;
    private volatile Instant decidedAt;
    private volatile String assignedTo;

    private ReviewItem(Builder builder) {
        this.reviewId = builder.reviewId != null ? builder.reviewId : UUID.randomUUID().toString();
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
        this.skillId = Objects.requireNonNull(builder.skillId, "skillId must not be null");
        this.proposedVersion = Objects.requireNonNull(builder.proposedVersion, "proposedVersion must not be null");
        this.itemType = builder.itemType != null ? builder.itemType : ReviewItemType.POLICY;
        this.confidenceScore = builder.confidenceScore;
        this.evaluationSummary = builder.evaluationSummary;
        this.context = builder.context != null ? Map.copyOf(builder.context) : Map.of();
        this.createdAt = Instant.now();
        this.status = ReviewStatus.PENDING;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Accessors
    // ═══════════════════════════════════════════════════════════════════════════

    @NotNull public String getReviewId() { return reviewId; }
    @NotNull public String getTenantId() { return tenantId; }
    @NotNull public String getSkillId() { return skillId; }
    @NotNull public String getProposedVersion() { return proposedVersion; }
    @NotNull public ReviewItemType getItemType() { return itemType; }
    public double getConfidenceScore() { return confidenceScore; }
    @Nullable public String getEvaluationSummary() { return evaluationSummary; }
    @NotNull public Map<String, Object> getContext() { return context; }
    @NotNull public Instant getCreatedAt() { return createdAt; }
    @NotNull public ReviewStatus getStatus() { return status; }
    @Nullable public ReviewDecision getDecision() { return decision; }
    @Nullable public Instant getDecidedAt() { return decidedAt; }
    @Nullable public String getAssignedTo() { return assignedTo; }

    // ═══════════════════════════════════════════════════════════════════════════
    // State Transitions
    // ═══════════════════════════════════════════════════════════════════════════

    /** Marks this item as approved with the given decision. */
    void markApproved(@NotNull ReviewDecision decision) {
        this.status = ReviewStatus.APPROVED;
        this.decision = decision;
        this.decidedAt = Instant.now();
    }

    /** Marks this item as rejected with the given decision. */
    void markRejected(@NotNull ReviewDecision decision) {
        this.status = ReviewStatus.REJECTED;
        this.decision = decision;
        this.decidedAt = Instant.now();
    }

    /** Assigns this item to a reviewer. */
    void assignTo(@NotNull String reviewer) {
        this.assignedTo = reviewer;
        this.status = ReviewStatus.IN_REVIEW;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Builder
    // ═══════════════════════════════════════════════════════════════════════════

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String reviewId;
        private String tenantId;
        private String skillId;
        private String proposedVersion;
        private ReviewItemType itemType;
        private double confidenceScore;
        private String evaluationSummary;
        private Map<String, Object> context;

        private Builder() {}

        public Builder reviewId(String reviewId) { this.reviewId = reviewId; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder skillId(String skillId) { this.skillId = skillId; return this; }
        public Builder proposedVersion(String proposedVersion) { this.proposedVersion = proposedVersion; return this; }
        public Builder itemType(ReviewItemType itemType) { this.itemType = itemType; return this; }
        public Builder confidenceScore(double score) { this.confidenceScore = score; return this; }
        public Builder evaluationSummary(String summary) { this.evaluationSummary = summary; return this; }
        public Builder context(Map<String, Object> context) { this.context = context; return this; }

        public ReviewItem build() { return new ReviewItem(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReviewItem that)) return false;
        return reviewId.equals(that.reviewId);
    }

    @Override
    public int hashCode() { return reviewId.hashCode(); }

    @Override
    public String toString() {
        return "ReviewItem{id=" + reviewId + ", skill=" + skillId
                + ", confidence=" + confidenceScore + ", status=" + status + '}';
    }
}
