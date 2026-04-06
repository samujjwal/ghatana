/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — Approval State Machine
 */
package com.ghatana.yappc.services.lifecycle;

import java.util.EnumSet;
import java.util.Set;

/**
 * Validates and enforces legal state transitions for {@link ApprovalRequest}.
 *
 * <p><b>State transition graph:</b>
 * <pre>
 *   PENDING ──startReview──▶ REVIEWING ──approve──▶ APPROVED
 *      │                         │
 *      │                      reject
 *      │                         │
 *      ▼                         ▼
 *   expire                    REJECTED
 *      │
 *      ▼
 *   EXPIRED
 * </pre>
 *
 * <p>PENDING also allows direct approve/reject without going through REVIEWING
 * (to support existing flows where a human decides without explicitly starting review).
 *
 * <p>All terminal states (APPROVED, REJECTED, EXPIRED) do not allow further transitions.
 *
 * @doc.type class
 * @doc.purpose Validates lifecycle state transitions for approval requests
 * @doc.layer product
 * @doc.pattern State Machine
 * @doc.gaa.lifecycle act
 */
public final class ApprovalStateMachine {

    /** States from which a review can begin. */
    private static final Set<ApprovalRequest.ApprovalStatus> CAN_START_REVIEW =
            EnumSet.of(ApprovalRequest.ApprovalStatus.PENDING);

    /** States from which an approval decision can be made. */
    private static final Set<ApprovalRequest.ApprovalStatus> CAN_DECIDE =
            EnumSet.of(
                    ApprovalRequest.ApprovalStatus.PENDING,
                    ApprovalRequest.ApprovalStatus.REVIEWING);

    /** States from which a request can expire. */
    private static final Set<ApprovalRequest.ApprovalStatus> CAN_EXPIRE =
            EnumSet.of(
                    ApprovalRequest.ApprovalStatus.PENDING,
                    ApprovalRequest.ApprovalStatus.REVIEWING);

    /** Terminal states — no further transitions are allowed from these. */
    private static final Set<ApprovalRequest.ApprovalStatus> TERMINAL_STATES =
            EnumSet.of(
                    ApprovalRequest.ApprovalStatus.APPROVED,
                    ApprovalRequest.ApprovalStatus.REJECTED,
                    ApprovalRequest.ApprovalStatus.EXPIRED);

    /**
     * Private constructor — utility class; use static methods only.
     */
    private ApprovalStateMachine() {}

    /**
     * Asserts that the transition from {@code current} to {@code REVIEWING} is legal.
     *
     * @param current current status of the approval request
     * @param requestId request ID for error messages
     * @throws IllegalStateException if the transition is not allowed
     */
    public static void assertCanStartReview(
            ApprovalRequest.ApprovalStatus current,
            String requestId) {
        if (!CAN_START_REVIEW.contains(current)) {
            throw new IllegalStateException(
                    "Cannot start review for approval " + requestId +
                    " in state " + current + "; expected: " + CAN_START_REVIEW);
        }
    }

    /**
     * Asserts that an approve/reject decision is legal from the given state.
     *
     * @param current   current status
     * @param requestId request ID for error messages
     * @throws IllegalStateException if the transition is not allowed
     */
    public static void assertCanDecide(
            ApprovalRequest.ApprovalStatus current,
            String requestId) {
        if (!CAN_DECIDE.contains(current)) {
            throw new IllegalStateException(
                    "Cannot decide approval " + requestId +
                    " in state " + current + "; expected one of: " + CAN_DECIDE);
        }
    }

    /**
     * Asserts that the expiry transition is legal from the given state.
     *
     * @param current   current status
     * @param requestId request ID for error messages
     * @throws IllegalStateException if the request is already in a terminal state
     */
    public static void assertCanExpire(
            ApprovalRequest.ApprovalStatus current,
            String requestId) {
        if (!CAN_EXPIRE.contains(current)) {
            throw new IllegalStateException(
                    "Cannot expire approval " + requestId +
                    " in state " + current + "; already terminal or decided");
        }
    }

    /**
     * Returns {@code true} when the given status is a terminal state
     * (no further transitions are possible).
     *
     * @param status the status to check
     * @return true when the status is APPROVED, REJECTED, or EXPIRED
     */
    public static boolean isTerminal(ApprovalRequest.ApprovalStatus status) {
        return TERMINAL_STATES.contains(status);
    }

    /**
     * Returns {@code true} if an approve or reject decision can be made in the current state.
     *
     * @param status the status to check
     * @return true when status is PENDING or REVIEWING
     */
    public static boolean canDecide(ApprovalRequest.ApprovalStatus status) {
        return CAN_DECIDE.contains(status);
    }
}
