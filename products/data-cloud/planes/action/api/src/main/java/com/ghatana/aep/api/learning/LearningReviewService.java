/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.api.learning;

import java.time.Instant;
import java.util.Map;

/**
 * Service for handling learning review decisions and wiring them to policy promotion and audit.
 * 
 * P7.4: Wires learning review approve/reject into policy promotion and audit.
 * When a learning delta is reviewed (approved or rejected), this service:
 * - Promotes approved learning to the policy system
 * - Records audit events for all review decisions
 * - Triggers policy re-evaluation for affected agents
 * 
 * @doc.type class
 * @doc.purpose Wire learning review decisions to policy promotion and audit
 * @doc.layer product
 * @doc.pattern Service
 */
public final class LearningReviewService {

    private final PolicyPromotionService policyPromotionService;
    private final AuditService auditService;

    public LearningReviewService(
            PolicyPromotionService policyPromotionService,
            AuditService auditService) {
        this.policyPromotionService = policyPromotionService;
        this.auditService = auditService;
    }

    /**
     * Reviews a learning delta and processes the decision.
     *
     * @param review the learning review
     * @return the review result
     */
    public ReviewResult reviewLearning(LearningReview review) {
        // Record audit event for the review decision
        auditService.recordEvent(new AuditEvent(
            review.tenantId(),
            "LEARNING_REVIEW",
            review.decision().name(),
            Map.of(
                "learningDeltaId", review.learningDeltaId(),
                "reviewer", review.reviewer(),
                "reason", review.reason(),
                "timestamp", review.timestamp().toString()
            )
        ));

        // Process based on decision
        if (review.decision() == ReviewDecision.APPROVE) {
            return processApproval(review);
        } else {
            return processRejection(review);
        }
    }

    /**
     * Processes an approved learning review.
     *
     * @param review the learning review
     * @return the review result
     */
    private ReviewResult processApproval(LearningReview review) {
        // Promote learning to policy system
        PromotionResult promotionResult = policyPromotionService.promoteLearning(
            review.learningDeltaId(),
            review.tenantId(),
            review.reviewer()
        );

        if (promotionResult.success()) {
            // Record promotion success in audit
            auditService.recordEvent(new AuditEvent(
                review.tenantId(),
                "LEARNING_PROMOTION",
                "SUCCESS",
                Map.of(
                    "learningDeltaId", review.learningDeltaId(),
                    "policyId", promotionResult.policyId(),
                    "reviewer", review.reviewer()
                )
            ));

            return ReviewResult.approved(promotionResult.policyId());
        } else {
            // Record promotion failure in audit
            auditService.recordEvent(new AuditEvent(
                review.tenantId(),
                "LEARNING_PROMOTION",
                "FAILED",
                Map.of(
                    "learningDeltaId", review.learningDeltaId(),
                    "error", promotionResult.error(),
                    "reviewer", review.reviewer()
                )
            ));

            return ReviewResult.failed(promotionResult.error());
        }
    }

    /**
     * Processes a rejected learning review.
     *
     * @param review the learning review
     * @return the review result
     */
    private ReviewResult processRejection(LearningReview review) {
        // Record rejection in audit (already done above)
        // Optionally trigger policy re-evaluation if rejection affects current policies
        policyPromotionService.triggerReEvaluation(
            review.affectedPolicyId(),
            review.tenantId(),
            "Learning delta rejected: " + review.reason()
        );

        return ReviewResult.rejected(review.reason());
    }

    /**
     * Learning review decision.
     *
     * @param learningDeltaId the learning delta ID
     * @param tenantId the tenant ID
     * @param reviewer the reviewer
     * @param decision the review decision
     * @param reason the reason for the decision
     * @param affectedPolicyId the affected policy ID (if any)
     * @param timestamp when the review was made
     */
    public record LearningReview(
            String learningDeltaId,
            String tenantId,
            String reviewer,
            ReviewDecision decision,
            String reason,
            String affectedPolicyId,
            Instant timestamp) {

        public LearningReview(
                String learningDeltaId,
                String tenantId,
                String reviewer,
                ReviewDecision decision,
                String reason,
                String affectedPolicyId) {
            this(learningDeltaId, tenantId, reviewer, decision, reason, affectedPolicyId, Instant.now());
        }
    }

    /**
     * Review decision.
     */
    public enum ReviewDecision {
        APPROVE,
        REJECT
    }

    /**
     * Review result.
     *
     * @param success whether the review was processed successfully
     * @param policyId the promoted policy ID (if approved)
     * @param error error message (if failed)
     */
    public record ReviewResult(
            boolean success,
            String policyId,
            String error) {

        public static ReviewResult approved(String policyId) {
            return new ReviewResult(true, policyId, null);
        }

        public static ReviewResult rejected(String reason) {
            return new ReviewResult(true, null, reason);
        }

        public static ReviewResult failed(String error) {
            return new ReviewResult(false, null, error);
        }
    }

    /**
     * Policy promotion service interface.
     */
    public interface PolicyPromotionService {
        PromotionResult promoteLearning(String learningDeltaId, String tenantId, String reviewer);
        void triggerReEvaluation(String policyId, String tenantId, String reason);
    }

    /**
     * Promotion result.
     *
     * @param success whether promotion succeeded
     * @param policyId the promoted policy ID
     * @param error error message (if failed)
     */
    public record PromotionResult(
            boolean success,
            String policyId,
            String error) {

        public static PromotionResult success(String policyId) {
            return new PromotionResult(true, policyId, null);
        }

        public static PromotionResult failed(String error) {
            return new PromotionResult(false, null, error);
        }
    }

    /**
     * Audit service interface.
     */
    public interface AuditService {
        void recordEvent(AuditEvent event);
    }

    /**
     * Audit event.
     *
     * @param tenantId the tenant ID
     * @param eventType the event type
     * @param outcome the event outcome
     * @param metadata event metadata
     */
    public record AuditEvent(
            String tenantId,
            String eventType,
            String outcome,
            Map<String, Object> metadata) {

        public AuditEvent(
                String tenantId,
                String eventType,
                String outcome,
                Map<String, Object> metadata,
                Instant timestamp) {
            this(tenantId, eventType, outcome, metadata);
        }
    }
}
