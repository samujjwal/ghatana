/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.promotion;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * Orchestrates the 7-step episodic→procedural memory promotion pipeline.
 *
 * <h3>Promotion Pipeline Steps</h3>
 * <ol>
 *   <li><b>EVALUATE</b> — Score the episodic memory entry for promotion readiness</li>
 *   <li><b>ASSESS_QUALITY</b> — Check score against the minimum promotion threshold</li>
 *   <li><b>CHECK_NAMESPACE</b> — Verify the target procedural namespace exists and has promotion enabled</li>
 *   <li><b>CREATE_EVIDENCE</b> — Persist the promotion evidence record for auditability</li>
 *   <li><b>MARK_PROMOTED</b> — Mark the episodic entry's metadata to indicate it has been promoted</li>
 *   <li><b>WRITE_PROCEDURAL</b> — Write the enriched content into the procedural memory namespace</li>
 *   <li><b>EMIT_EVENT</b> — Emit a {@link PromotionResult} domain event for downstream consumers</li>
 * </ol>
 *
 * <p>Each step is gated: if a step fails its threshold or validation, promotion is aborted
 * and a {@link PromotionResult} describing the failure is returned. No partial writes occur when
 * a gate step fails before step 6.
 *
 * @doc.type interface
 * @doc.purpose 7-step memory promotion pipeline from episodic to procedural memory
 * @doc.layer product
 * @doc.pattern Service Interface
 */
public interface MemoryPromotionService {

    /**
     * Minimum promotion quality score that an episodic entry must reach before
     * it is eligible for procedural promotion.
     */
    double DEFAULT_PROMOTION_THRESHOLD = 0.75;

    /**
     * Promotes an episodic memory entry to the procedural namespace for the given agent.
     *
     * <p>Executes all 7 promotion steps in sequence. The returned {@link PromotionResult}
     * reflects whether promotion succeeded or at which step it was rejected.
     *
     * @param request the promotion request
     * @return a {@link PromotionResult} describing the outcome
     */
    Promise<PromotionResult> promote(PromotionRequest request);

    // ─── Request and Result value types ───────────────────────────────────────

    /**
     * Immutable request to promote an episodic memory entry.
     *
     * @param agentId          the owning agent
     * @param tenantId         tenant scope
     * @param sourceMemoryId   ID of the episodic memory entry to promote
     * @param sourceContent    content of the episodic entry
     * @param importanceScore  caller-supplied importance score in [0.0, 1.0]
     * @param promotionThreshold quality threshold to pass (null → use {@link #DEFAULT_PROMOTION_THRESHOLD})
     */
    record PromotionRequest(
            @NotNull String agentId,
            @NotNull String tenantId,
            @NotNull String sourceMemoryId,
            @NotNull String sourceContent,
            double importanceScore,
            @org.jetbrains.annotations.Nullable Double promotionThreshold
    ) {
        /** Creates a request using the default promotion threshold. */
        public static PromotionRequest of(
                String agentId, String tenantId, String sourceMemoryId,
                String sourceContent, double importanceScore) {
            return new PromotionRequest(agentId, tenantId, sourceMemoryId,
                    sourceContent, importanceScore, null);
        }

        /** Returns the effective threshold: caller-supplied value or {@link #DEFAULT_PROMOTION_THRESHOLD}. */
        public double effectiveThreshold() {
            return promotionThreshold != null ? promotionThreshold : DEFAULT_PROMOTION_THRESHOLD;
        }
    }

    /**
     * Immutable result of a promotion attempt.
     *
     * @param succeeded       whether the full 7-step pipeline completed successfully
     * @param targetMemoryId  ID of the written procedural memory entry ({@code null} if failed)
     * @param rejectedAtStep  name of the step that caused failure ({@code null} if succeeded)
     * @param rejectedReason  human-readable rejection reason ({@code null} if succeeded)
     * @param evidence        list of {@link PromotionEvidence} records produced (one per completed step)
     * @param promotedAt      timestamp of the promotion attempt
     */
    record PromotionResult(
            boolean succeeded,
            @org.jetbrains.annotations.Nullable String targetMemoryId,
            @org.jetbrains.annotations.Nullable String rejectedAtStep,
            @org.jetbrains.annotations.Nullable String rejectedReason,
            @NotNull List<PromotionEvidence> evidence,
            @NotNull Instant promotedAt
    ) {
        /** Creates a successful promotion result. */
        public static PromotionResult success(
                String targetMemoryId, List<PromotionEvidence> evidence, Instant promotedAt) {
            return new PromotionResult(true, targetMemoryId, null, null, List.copyOf(evidence), promotedAt);
        }

        /** Creates a failed promotion result. */
        public static PromotionResult failure(
                String rejectedAtStep, String rejectedReason,
                List<PromotionEvidence> evidence, Instant promotedAt) {
            return new PromotionResult(false, null, rejectedAtStep, rejectedReason,
                    List.copyOf(evidence), promotedAt);
        }
    }
}
