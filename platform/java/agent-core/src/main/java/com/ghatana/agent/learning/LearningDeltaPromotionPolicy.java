/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Policy for determining when learning deltas should be promoted.
 *
 * <p>The promotion policy evaluates learning deltas against rules to determine
 * if they should be promoted to active knowledge.
 *
 * @doc.type interface
 * @doc.purpose Policy for learning delta promotion
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public interface LearningDeltaPromotionPolicy {

    /**
     * Determines if a learning delta should be promoted.
     *
     * @param delta learning delta to evaluate
     * @return promise of promotion decision
     */
    @NotNull
    Promise<PromotionDecision> shouldPromote(@NotNull LearningDelta delta);

    /**
     * Promotion decision for a learning delta.
     *
     * @doc.type record
     * @doc.purpose Promotion decision for learning delta
     * @doc.layer agent-core
     * @doc.pattern Record
     */
    record PromotionDecision(
            @NotNull String deltaId,
            boolean shouldPromote,
            @NotNull String reason,
            @NotNull PromotionLevel level
    ) {
        /**
         * Creates a promotion decision to promote.
         *
         * @param deltaId delta identifier
         * @param reason promotion reason
         * @param level promotion level
         * @return promotion decision to promote
         */
        @NotNull
        public static PromotionDecision promote(
                @NotNull String deltaId,
                @NotNull String reason,
                @NotNull PromotionLevel level
        ) {
            return new PromotionDecision(deltaId, true, reason, level);
        }

        /**
         * Creates a promotion decision to reject.
         *
         * @param deltaId delta identifier
         * @param reason rejection reason
         * @return promotion decision to reject
         */
        @NotNull
        public static PromotionDecision reject(
                @NotNull String deltaId,
                @NotNull String reason
        ) {
            return new PromotionDecision(deltaId, false, reason, PromotionLevel.NONE);
        }
    }

    /**
     * Promotion level indicating the degree of promotion.
     *
     * @doc.type enum
     * @doc.purpose Promotion level for learning deltas
     * @doc.layer agent-core
     * @doc.pattern Enumeration
     */
    enum PromotionLevel {
        /**
         * No promotion.
         */
        NONE,

        /**
         * Promote to observed state.
         */
        OBSERVED,

        /**
         * Promote to practiced state.
         */
        PRACTICED,

        /**
         * Promote to competent state.
         */
        COMPETENT,

        /**
         * Promote to mastered state.
         */
        MASTERED
    }
}
