/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
     * Reason codes for evaluation results.
     */
    enum ReasonCode {
        INSUFFICIENT_EVIDENCE,
        LOW_CONFIDENCE,
        MISSING_REQUIRED_FIELD,
        SAFETY_CHECK_FAILED,
        REGRESSION_CHECK_FAILED,
        GOVERNANCE_VIOLATION,
        TARGET_SPECIFIC_CHECK_FAILED,
        EVALUATION_REFS_REQUIRED,
        HUMAN_REVIEW_REQUIRED,
        OFFLINE_ONLY_TARGET,
        APPROVED,
        PENDING_HUMAN_REVIEW
    }

    /**
     * Safety grades for evaluation results.
     */
    enum SafetyGrade {
        SAFE,
        LOW_RISK,
        MEDIUM_RISK,
        HIGH_RISK,
        CRITICAL
    }

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
            @NotNull ReasonCode reasonCode,
            @NotNull String reason,
            @NotNull String recommendation,
            @NotNull SafetyGrade safetyGrade,
            @NotNull List<String> nextActions
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
            return new EvaluationResult(
                    deltaId,
                    true,
                    confidence,
                    ReasonCode.APPROVED,
                    reason,
                    "Approve for promotion",
                    SafetyGrade.SAFE,
                    List.of("Proceed with promotion")
            );
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
            return new EvaluationResult(
                    deltaId,
                    false,
                    confidence,
                    ReasonCode.TARGET_SPECIFIC_CHECK_FAILED,
                    reason,
                    "Reject: " + reason,
                    SafetyGrade.MEDIUM_RISK,
                    List.of("Review and resubmit with fixes")
            );
        }

        /**
         * Creates a rejected evaluation result with specific reason code and safety grade.
         *
         * @param deltaId delta identifier
         * @param confidence confidence score
         * @param reasonCode structured reason code
         * @param reason evaluation reason
         * @param safetyGrade safety grade
         * @param nextActions recommended next actions
         * @return rejected evaluation result
         */
        @NotNull
        public static EvaluationResult rejected(
                @NotNull String deltaId,
                double confidence,
                @NotNull ReasonCode reasonCode,
                @NotNull String reason,
                @NotNull SafetyGrade safetyGrade,
                @NotNull List<String> nextActions
        ) {
            return new EvaluationResult(
                    deltaId,
                    false,
                    confidence,
                    reasonCode,
                    reason,
                    "Reject: " + reason,
                    safetyGrade,
                    nextActions
            );
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
            return new EvaluationResult(
                    deltaId,
                    false,
                    confidence,
                    ReasonCode.HUMAN_REVIEW_REQUIRED,
                    reason,
                    "Pending human review: " + reason,
                    SafetyGrade.MEDIUM_RISK,
                    List.of("Request human review", "Review evidence", "Approve or reject")
            );
        }
    }
}
