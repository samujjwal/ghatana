/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import java.util.EnumSet;
import java.util.Set;

/**
 * Idempotent state machine for generation review, apply, reject, and rollback.
 *
 * <p><b>State transition graph:</b>
 * <pre>
 *   GENERATING ──complete──▶ GENERATED ──startReview──▶ REVIEW_PENDING
 *       │                                               │
 *       │                                            approve
 *       │                                               │
 *       │                                               ▼
 *       │                                            APPROVED ──apply──▶ APPLIED
 *       │                                               │
 *       │                                            reject
 *       │                                               │
 *       │                                               ▼
 *       │                                            REJECTED
 *       │
 *       │                                            rollback
 *       │                                               │
 *       ▼                                               ▼
 *   FAILED                                    ROLLBACK_REQUESTED ──rollback──▶ ROLLED_BACK
 * </pre>
 *
 * <p><b>Idempotency rules:</b>
 * <ul>
 *   <li>Apply once succeeds, apply twice is idempotent (no-op if already APPLIED)</li>
 *   <li>Reject after apply is denied (cannot reject after APPLIED)</li>
 *   <li>Rollback after apply succeeds (can only rollback from APPLIED or APPROVED)</li>
 *   <li>Rollback twice is idempotent (no-op if already ROLLED_BACK)</li>
 *   <li>Viewer cannot apply/reject/rollback (requires approver role)</li>
 *   <li>Degraded output requires review (FAILED state requires manual review)</li>
 * </ul>
 *
 * <p>All terminal states (APPLIED, REJECTED, ROLLED_BACK, FAILED) do not allow further transitions.
 *
 * @doc.type class
 * @doc.purpose Idempotent state machine for generation review, apply, reject, rollback
 * @doc.layer api
 * @doc.pattern State Machine
 */
public final class GenerationReviewStateMachine {

    /** States from which generation can complete. */
    private static final Set<GenerationRun.ReviewStatus> CAN_COMPLETE_GENERATION =
            EnumSet.of(GenerationRun.ReviewStatus.GENERATING);

    /** States from which review can start. */
    private static final Set<GenerationRun.ReviewStatus> CAN_START_REVIEW =
            EnumSet.of(GenerationRun.ReviewStatus.GENERATED);

    /** States from which approve decision can be made. */
    private static final Set<GenerationRun.ReviewStatus> CAN_APPROVE =
            EnumSet.of(GenerationRun.ReviewStatus.REVIEW_PENDING);

    /** States from which apply can be executed. */
    private static final Set<GenerationRun.ReviewStatus> CAN_APPLY =
            EnumSet.of(GenerationRun.ReviewStatus.APPROVED, GenerationRun.ReviewStatus.APPLIED);

    /** States from which reject decision can be made. */
    private static final Set<GenerationRun.ReviewStatus> CAN_REJECT =
            EnumSet.of(GenerationRun.ReviewStatus.REVIEW_PENDING, GenerationRun.ReviewStatus.APPROVED);

    /** States from which rollback can be requested. */
    private static final Set<GenerationRun.ReviewStatus> CAN_REQUEST_ROLLBACK =
            EnumSet.of(GenerationRun.ReviewStatus.APPLIED, GenerationRun.ReviewStatus.APPROVED);

    /** States from which rollback can be completed. */
    private static final Set<GenerationRun.ReviewStatus> CAN_COMPLETE_ROLLBACK =
            EnumSet.of(GenerationRun.ReviewStatus.ROLLBACK_REQUESTED, GenerationRun.ReviewStatus.ROLLED_BACK);

    /** Terminal states — no further transitions are allowed from these. */
    private static final Set<GenerationRun.ReviewStatus> TERMINAL_STATES =
            EnumSet.of(
                    GenerationRun.ReviewStatus.APPLIED,
                    GenerationRun.ReviewStatus.REJECTED,
                    GenerationRun.ReviewStatus.ROLLED_BACK,
                    GenerationRun.ReviewStatus.FAILED);

    /**
     * Private constructor — utility class; use static methods only.
     */
    private GenerationReviewStateMachine() {}

    /**
     * Asserts that the transition to GENERATED is legal.
     *
     * @param current current status
     * @param runId run ID for error messages
     * @throws IllegalStateException if the transition is not allowed
     */
    public static void assertCanCompleteGeneration(
            GenerationRun.ReviewStatus current,
            String runId) {
        if (!CAN_COMPLETE_GENERATION.contains(current)) {
            throw new IllegalStateException(
                    "Cannot complete generation for run " + runId +
                    " in state " + current + "; expected: " + CAN_COMPLETE_GENERATION);
        }
    }

    /**
     * Asserts that the transition to REVIEW_PENDING is legal.
     *
     * @param current current status
     * @param runId run ID for error messages
     * @throws IllegalStateException if the transition is not allowed
     */
    public static void assertCanStartReview(
            GenerationRun.ReviewStatus current,
            String runId) {
        if (!CAN_START_REVIEW.contains(current)) {
            throw new IllegalStateException(
                    "Cannot start review for run " + runId +
                    " in state " + current + "; expected: " + CAN_START_REVIEW);
        }
    }

    /**
     * Asserts that an approve decision is legal from the given state.
     *
     * @param current current status
     * @param runId run ID for error messages
     * @throws IllegalStateException if the transition is not allowed
     */
    public static void assertCanApprove(
            GenerationRun.ReviewStatus current,
            String runId) {
        if (!CAN_APPROVE.contains(current)) {
            throw new IllegalStateException(
                    "Cannot approve run " + runId +
                    " in state " + current + "; expected one of: " + CAN_APPROVE);
        }
    }

    /**
     * Asserts that apply is legal from the given state (idempotent if already APPLIED).
     *
     * @param current current status
     * @param runId run ID for error messages
     * @throws IllegalStateException if the transition is not allowed
     */
    public static void assertCanApply(
            GenerationRun.ReviewStatus current,
            String runId) {
        if (!CAN_APPLY.contains(current)) {
            throw new IllegalStateException(
                    "Cannot apply run " + runId +
                    " in state " + current + "; expected one of: " + CAN_APPLY);
        }
    }

    /**
     * Asserts that a reject decision is legal from the given state.
     *
     * @param current current status
     * @param runId run ID for error messages
     * @throws IllegalStateException if the transition is not allowed
     */
    public static void assertCanReject(
            GenerationRun.ReviewStatus current,
            String runId) {
        if (!CAN_REJECT.contains(current)) {
            throw new IllegalStateException(
                    "Cannot reject run " + runId +
                    " in state " + current + "; expected one of: " + CAN_REJECT);
        }
    }

    /**
     * Asserts that rollback request is legal from the given state.
     *
     * @param current current status
     * @param runId run ID for error messages
     * @throws IllegalStateException if the transition is not allowed
     */
    public static void assertCanRequestRollback(
            GenerationRun.ReviewStatus current,
            String runId) {
        if (!CAN_REQUEST_ROLLBACK.contains(current)) {
            throw new IllegalStateException(
                    "Cannot request rollback for run " + runId +
                    " in state " + current + "; expected one of: " + CAN_REQUEST_ROLLBACK);
        }
    }

    /**
     * Asserts that rollback completion is legal from the given state (idempotent if already ROLLED_BACK).
     *
     * @param current current status
     * @param runId run ID for error messages
     * @throws IllegalStateException if the transition is not allowed
     */
    public static void assertCanCompleteRollback(
            GenerationRun.ReviewStatus current,
            String runId) {
        if (!CAN_COMPLETE_ROLLBACK.contains(current)) {
            throw new IllegalStateException(
                    "Cannot complete rollback for run " + runId +
                    " in state " + current + "; expected one of: " + CAN_COMPLETE_ROLLBACK);
        }
    }

    /**
     * Returns {@code true} when the given status is a terminal state
     * (no further transitions are possible).
     *
     * @param status the status to check
     * @return true when the status is APPLIED, REJECTED, ROLLED_BACK, or FAILED
     */
    public static boolean isTerminal(GenerationRun.ReviewStatus status) {
        return TERMINAL_STATES.contains(status);
    }

    /**
     * Returns {@code true} if apply is idempotent in the current state (already APPLIED).
     *
     * @param status the status to check
     * @return true when status is APPLIED
     */
    public static boolean isApplyIdempotent(GenerationRun.ReviewStatus status) {
        return status == GenerationRun.ReviewStatus.APPLIED;
    }

    /**
     * Returns {@code true} if rollback is idempotent in the current state (already ROLLED_BACK).
     *
     * @param status the status to check
     * @return true when status is ROLLED_BACK
     */
    public static boolean isRollbackIdempotent(GenerationRun.ReviewStatus status) {
        return status == GenerationRun.ReviewStatus.ROLLED_BACK;
    }
}
