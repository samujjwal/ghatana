/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — ApprovalStateMachine Tests
 */
package com.ghatana.yappc.services.lifecycle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.ghatana.yappc.services.lifecycle.ApprovalRequest.ApprovalStatus.APPROVED;
import static com.ghatana.yappc.services.lifecycle.ApprovalRequest.ApprovalStatus.EXPIRED;
import static com.ghatana.yappc.services.lifecycle.ApprovalRequest.ApprovalStatus.PENDING;
import static com.ghatana.yappc.services.lifecycle.ApprovalRequest.ApprovalStatus.REJECTED;
import static com.ghatana.yappc.services.lifecycle.ApprovalRequest.ApprovalStatus.REVIEWING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Verifies ApprovalStateMachine enforces valid transitions and rejects invalid ones
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ApprovalStateMachine")
class ApprovalStateMachineTest {

    // ─── assertCanStartReview ─────────────────────────────────────────────────

    @Test
    @DisplayName("assertCanStartReview succeeds for PENDING state")
    void startReviewAllowedFromPending() {
        ApprovalStateMachine.assertCanStartReview(PENDING, "req-001");
        // no exception
    }

    @Test
    @DisplayName("assertCanStartReview rejects REVIEWING state")
    void startReviewRejectedFromReviewing() {
        assertThatThrownBy(() -> ApprovalStateMachine.assertCanStartReview(REVIEWING, "req-001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("req-001");
    }

    @Test
    @DisplayName("assertCanStartReview rejects APPROVED state")
    void startReviewRejectedFromApproved() {
        assertThatThrownBy(() -> ApprovalStateMachine.assertCanStartReview(APPROVED, "req-002"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("assertCanStartReview rejects REJECTED state")
    void startReviewRejectedFromRejected() {
        assertThatThrownBy(() -> ApprovalStateMachine.assertCanStartReview(REJECTED, "req-003"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("assertCanStartReview rejects EXPIRED state")
    void startReviewRejectedFromExpired() {
        assertThatThrownBy(() -> ApprovalStateMachine.assertCanStartReview(EXPIRED, "req-004"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ─── assertCanDecide ──────────────────────────────────────────────────────

    @Test
    @DisplayName("assertCanDecide succeeds for PENDING state")
    void decideAllowedFromPending() {
        ApprovalStateMachine.assertCanDecide(PENDING, "req-001");
        // no exception
    }

    @Test
    @DisplayName("assertCanDecide succeeds for REVIEWING state")
    void decideAllowedFromReviewing() {
        ApprovalStateMachine.assertCanDecide(REVIEWING, "req-001");
        // no exception
    }

    @Test
    @DisplayName("assertCanDecide rejects APPROVED state")
    void decideRejectedFromApproved() {
        assertThatThrownBy(() -> ApprovalStateMachine.assertCanDecide(APPROVED, "req-002"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED");
    }

    @Test
    @DisplayName("assertCanDecide rejects REJECTED state")
    void decideRejectedFromRejected() {
        assertThatThrownBy(() -> ApprovalStateMachine.assertCanDecide(REJECTED, "req-003"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("assertCanDecide rejects EXPIRED state")
    void decideRejectedFromExpired() {
        assertThatThrownBy(() -> ApprovalStateMachine.assertCanDecide(EXPIRED, "req-004"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ─── assertCanExpire ──────────────────────────────────────────────────────

    @Test
    @DisplayName("assertCanExpire succeeds for PENDING state")
    void expireAllowedFromPending() {
        ApprovalStateMachine.assertCanExpire(PENDING, "req-001");
    }

    @Test
    @DisplayName("assertCanExpire succeeds for REVIEWING state")
    void expireAllowedFromReviewing() {
        ApprovalStateMachine.assertCanExpire(REVIEWING, "req-001");
    }

    @Test
    @DisplayName("assertCanExpire rejects APPROVED state")
    void expireRejectedFromApproved() {
        assertThatThrownBy(() -> ApprovalStateMachine.assertCanExpire(APPROVED, "req-002"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ─── isTerminal ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("isTerminal returns true for APPROVED, REJECTED, EXPIRED")
    void isTerminalForFinalStates() {
        assertThat(ApprovalStateMachine.isTerminal(APPROVED)).isTrue();
        assertThat(ApprovalStateMachine.isTerminal(REJECTED)).isTrue();
        assertThat(ApprovalStateMachine.isTerminal(EXPIRED)).isTrue();
    }

    @Test
    @DisplayName("isTerminal returns false for PENDING and REVIEWING")
    void isNotTerminalForActiveStates() {
        assertThat(ApprovalStateMachine.isTerminal(PENDING)).isFalse();
        assertThat(ApprovalStateMachine.isTerminal(REVIEWING)).isFalse();
    }

    // ─── canDecide ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("canDecide returns true for PENDING and REVIEWING")
    void canDecideForActiveStates() {
        assertThat(ApprovalStateMachine.canDecide(PENDING)).isTrue();
        assertThat(ApprovalStateMachine.canDecide(REVIEWING)).isTrue();
    }

    @Test
    @DisplayName("canDecide returns false for terminal states")
    void cannotDecideFromTerminalStates() {
        assertThat(ApprovalStateMachine.canDecide(APPROVED)).isFalse();
        assertThat(ApprovalStateMachine.canDecide(REJECTED)).isFalse();
        assertThat(ApprovalStateMachine.canDecide(EXPIRED)).isFalse();
    }
}
