/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
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
@DisplayName("ApprovalStateMachine [GH-90000]")
class ApprovalStateMachineTest {

    // ─── assertCanStartReview ─────────────────────────────────────────────────

    @Test
    @DisplayName("assertCanStartReview succeeds for PENDING state [GH-90000]")
    void startReviewAllowedFromPending() { // GH-90000
        ApprovalStateMachine.assertCanStartReview(PENDING, "req-001"); // GH-90000
        // no exception
    }

    @Test
    @DisplayName("assertCanStartReview rejects REVIEWING state [GH-90000]")
    void startReviewRejectedFromReviewing() { // GH-90000
        assertThatThrownBy(() -> ApprovalStateMachine.assertCanStartReview(REVIEWING, "req-001")) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("req-001 [GH-90000]");
    }

    @Test
    @DisplayName("assertCanStartReview rejects APPROVED state [GH-90000]")
    void startReviewRejectedFromApproved() { // GH-90000
        assertThatThrownBy(() -> ApprovalStateMachine.assertCanStartReview(APPROVED, "req-002")) // GH-90000
                .isInstanceOf(IllegalStateException.class); // GH-90000
    }

    @Test
    @DisplayName("assertCanStartReview rejects REJECTED state [GH-90000]")
    void startReviewRejectedFromRejected() { // GH-90000
        assertThatThrownBy(() -> ApprovalStateMachine.assertCanStartReview(REJECTED, "req-003")) // GH-90000
                .isInstanceOf(IllegalStateException.class); // GH-90000
    }

    @Test
    @DisplayName("assertCanStartReview rejects EXPIRED state [GH-90000]")
    void startReviewRejectedFromExpired() { // GH-90000
        assertThatThrownBy(() -> ApprovalStateMachine.assertCanStartReview(EXPIRED, "req-004")) // GH-90000
                .isInstanceOf(IllegalStateException.class); // GH-90000
    }

    // ─── assertCanDecide ──────────────────────────────────────────────────────

    @Test
    @DisplayName("assertCanDecide succeeds for PENDING state [GH-90000]")
    void decideAllowedFromPending() { // GH-90000
        ApprovalStateMachine.assertCanDecide(PENDING, "req-001"); // GH-90000
        // no exception
    }

    @Test
    @DisplayName("assertCanDecide succeeds for REVIEWING state [GH-90000]")
    void decideAllowedFromReviewing() { // GH-90000
        ApprovalStateMachine.assertCanDecide(REVIEWING, "req-001"); // GH-90000
        // no exception
    }

    @Test
    @DisplayName("assertCanDecide rejects APPROVED state [GH-90000]")
    void decideRejectedFromApproved() { // GH-90000
        assertThatThrownBy(() -> ApprovalStateMachine.assertCanDecide(APPROVED, "req-002")) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("APPROVED [GH-90000]");
    }

    @Test
    @DisplayName("assertCanDecide rejects REJECTED state [GH-90000]")
    void decideRejectedFromRejected() { // GH-90000
        assertThatThrownBy(() -> ApprovalStateMachine.assertCanDecide(REJECTED, "req-003")) // GH-90000
                .isInstanceOf(IllegalStateException.class); // GH-90000
    }

    @Test
    @DisplayName("assertCanDecide rejects EXPIRED state [GH-90000]")
    void decideRejectedFromExpired() { // GH-90000
        assertThatThrownBy(() -> ApprovalStateMachine.assertCanDecide(EXPIRED, "req-004")) // GH-90000
                .isInstanceOf(IllegalStateException.class); // GH-90000
    }

    // ─── assertCanExpire ──────────────────────────────────────────────────────

    @Test
    @DisplayName("assertCanExpire succeeds for PENDING state [GH-90000]")
    void expireAllowedFromPending() { // GH-90000
        ApprovalStateMachine.assertCanExpire(PENDING, "req-001"); // GH-90000
    }

    @Test
    @DisplayName("assertCanExpire succeeds for REVIEWING state [GH-90000]")
    void expireAllowedFromReviewing() { // GH-90000
        ApprovalStateMachine.assertCanExpire(REVIEWING, "req-001"); // GH-90000
    }

    @Test
    @DisplayName("assertCanExpire rejects APPROVED state [GH-90000]")
    void expireRejectedFromApproved() { // GH-90000
        assertThatThrownBy(() -> ApprovalStateMachine.assertCanExpire(APPROVED, "req-002")) // GH-90000
                .isInstanceOf(IllegalStateException.class); // GH-90000
    }

    // ─── isTerminal ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("isTerminal returns true for APPROVED, REJECTED, EXPIRED [GH-90000]")
    void isTerminalForFinalStates() { // GH-90000
        assertThat(ApprovalStateMachine.isTerminal(APPROVED)).isTrue(); // GH-90000
        assertThat(ApprovalStateMachine.isTerminal(REJECTED)).isTrue(); // GH-90000
        assertThat(ApprovalStateMachine.isTerminal(EXPIRED)).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("isTerminal returns false for PENDING and REVIEWING [GH-90000]")
    void isNotTerminalForActiveStates() { // GH-90000
        assertThat(ApprovalStateMachine.isTerminal(PENDING)).isFalse(); // GH-90000
        assertThat(ApprovalStateMachine.isTerminal(REVIEWING)).isFalse(); // GH-90000
    }

    // ─── canDecide ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("canDecide returns true for PENDING and REVIEWING [GH-90000]")
    void canDecideForActiveStates() { // GH-90000
        assertThat(ApprovalStateMachine.canDecide(PENDING)).isTrue(); // GH-90000
        assertThat(ApprovalStateMachine.canDecide(REVIEWING)).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("canDecide returns false for terminal states [GH-90000]")
    void cannotDecideFromTerminalStates() { // GH-90000
        assertThat(ApprovalStateMachine.canDecide(APPROVED)).isFalse(); // GH-90000
        assertThat(ApprovalStateMachine.canDecide(REJECTED)).isFalse(); // GH-90000
        assertThat(ApprovalStateMachine.canDecide(EXPIRED)).isFalse(); // GH-90000
    }
}
