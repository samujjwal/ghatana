/**
 * Review Decision
 * 
 * Canonical schema for review decisions on generated content.
 * Defines the structure for review decisions including apply/reject/rollback/request-changes.
 * 
 * @doc.type class
 * @doc.purpose Review decision schema
 * @doc.layer product
 * @doc.pattern DTO
 */

package com.ghatana.yappc.api;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Canonical review decision schema.
 */
public final class ReviewDecision {

    private final String decisionId;
    private final String generationPlanId;
    private final String diffId;
    private final String projectId;
    private final DecisionType decisionType;
    private final ReviewDecisionMetadata metadata;
    private final DecisionContext context;
    private final Instant createdAt;
    private final String reviewerId;
    private final String reviewerName;

    public ReviewDecision(
            @NotNull String decisionId,
            @NotNull String generationPlanId,
            String diffId,
            @NotNull String projectId,
            @NotNull DecisionType decisionType,
            @NotNull ReviewDecisionMetadata metadata,
            @NotNull DecisionContext context,
            @NotNull Instant createdAt,
            @NotNull String reviewerId,
            @NotNull String reviewerName
    ) {
        this.decisionId = decisionId;
        this.generationPlanId = generationPlanId;
        this.diffId = diffId;
        this.projectId = projectId;
        this.decisionType = decisionType;
        this.metadata = metadata;
        this.context = context;
        this.createdAt = createdAt;
        this.reviewerId = reviewerId;
        this.reviewerName = reviewerName;
    }

    public String decisionId() {
        return decisionId;
    }

    public String generationPlanId() {
        return generationPlanId;
    }

    public String diffId() {
        return diffId;
    }

    public String projectId() {
        return projectId;
    }

    public DecisionType decisionType() {
        return decisionType;
    }

    public ReviewDecisionMetadata metadata() {
        return metadata;
    }

    public DecisionContext context() {
        return context;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String reviewerId() {
        return reviewerId;
    }

    public String reviewerName() {
        return reviewerName;
    }

    /**
     * Decision type enum.
     */
    public enum DecisionType {
        APPLY,
        REJECT,
        ROLLBACK,
        REQUEST_CHANGES
    }

    /**
     * Review decision metadata.
     * For REJECT and ROLLBACK decisions, the reason field is required.
     */
    public record ReviewDecisionMetadata(
            String reason,
            String comments,
            List<String> regionIds,
            List<String> fileIds,
            Map<String, String> additionalMetadata
    ) {
        public ReviewDecisionMetadata {
            // For REJECT and ROLLBACK, reason is required
            // This validation should be enforced at construction time
        }
    }

    /**
     * Decision context.
     */
    public record DecisionContext(
            String sessionId,
            String traceId,
            String phase,
            boolean isAutoApply,
            boolean isDegraded,
            String degradationReason,
            Map<String, String> contextData
    ) {}
}
