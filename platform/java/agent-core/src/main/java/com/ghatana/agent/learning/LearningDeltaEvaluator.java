/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Evaluator for learning deltas.
 *
 * <p>Evaluates proposed learning deltas to determine if they should be promoted.
 * Evaluation includes checking evidence, safety, compatibility, and regression risks.
 *
 * @doc.type interface
 * @doc.purpose Evaluator for learning deltas
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public interface LearningDeltaEvaluator {

    /**
     * Evaluates a learning delta.
     *
     * @param delta learning delta to evaluate
     * @return promise of evaluation result
     */
    @NotNull
    Promise<EvaluationResult> evaluate(@NotNull LearningDelta delta);

    /**
     * Evaluation result for a learning delta.
     *
     * @doc.type record
     * @doc.purpose Evaluation result for learning delta
     * @doc.layer agent-core
     * @doc.pattern Record
     */
    record EvaluationResult(
            @NotNull String deltaId,
            boolean approved,
            double confidence,
            @NotNull String reason,
            @NotNull String recommendation
    ) {
        /**
         * Creates an approved evaluation result.
         *
         * @param deltaId delta identifier
         * @param confidence confidence score
         * @param reason evaluation reason
         * @return approved evaluation result
         */
        @NotNull
        public static EvaluationResult approved(
                @NotNull String deltaId,
                double confidence,
                @NotNull String reason
        ) {
            return new EvaluationResult(deltaId, true, confidence, reason, "Approve for promotion");
        }

        /**
         * Creates a rejected evaluation result.
         *
         * @param deltaId delta identifier
         * @param confidence confidence score
         * @param reason evaluation reason
         * @return rejected evaluation result
         */
        @NotNull
        public static EvaluationResult rejected(
                @NotNull String deltaId,
                double confidence,
                @NotNull String reason
        ) {
            return new EvaluationResult(deltaId, false, confidence, reason, "Reject: " + reason);
        }

        /**
         * Creates a pending-human-review evaluation result for deltas that need manual sign-off
         * before promotion (e.g. procedural skills with sufficient evidence but low confidence).
         *
         * @param deltaId    delta identifier
         * @param confidence confidence score
         * @param reason     evaluation reason
         * @return pending-human-review evaluation result
         */
        @NotNull
        public static EvaluationResult pendingHumanReview(
                @NotNull String deltaId,
                double confidence,
                @NotNull String reason
        ) {
            return new EvaluationResult(deltaId, false, confidence, reason, "Pending human review: " + reason);
        }
    }
}
