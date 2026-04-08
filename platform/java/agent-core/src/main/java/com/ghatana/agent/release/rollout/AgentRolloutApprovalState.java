/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.release.rollout;

/**
 * Lifecycle states for an approval gate on an {@link AgentRolloutRecord}.
 *
 * <p>A rollout passes through these states in order:
 * <ol>
 *   <li>{@code PENDING} — the rollout request has been created but not yet reviewed.</li>
 *   <li>{@code APPROVED} — an authorized principal approved the rollout.</li>
 *   <li>{@code REJECTED} — the rollout was explicitly denied with a reason.</li>
 *   <li>{@code EXPIRED} — the rollout window passed before a decision was made.</li>
 *   <li>{@code ROLLED_BACK} — the rollout was previously approved but was manually reverted.</li>
 * </ol>
 *
 * @doc.type enum
 * @doc.purpose Approval lifecycle states for agent rollout records
 * @doc.layer platform
 * @doc.pattern StateEnum
 */
public enum AgentRolloutApprovalState {

    /**
     * The rollout request has been submitted and is awaiting approval.
     */
    PENDING,

    /**
     * The rollout was approved by an authorized principal.
     */
    APPROVED,

    /**
     * The rollout was explicitly rejected. A {@code rejectedReason} must be recorded.
     */
    REJECTED,

    /**
     * The rollout window expired before a decision was made.
     * Treated as a soft rejection; the rollout must be re-submitted to retry.
     */
    EXPIRED,

    /**
     * The rollout was previously approved but has since been reverted by an operator.
     */
    ROLLED_BACK;

    /**
     * Returns {@code true} if the rollout is still awaiting a decision.
     *
     * @return whether the state is {@code PENDING}
     */
    public boolean isPending() {
        return this == PENDING;
    }

    /**
     * Returns {@code true} if the rollout reached a terminal state — one from which
     * further transitions by normal approval flow are not expected.
     *
     * @return whether the state is {@code APPROVED}, {@code REJECTED}, {@code EXPIRED}, or {@code ROLLED_BACK}
     */
    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED || this == EXPIRED || this == ROLLED_BACK;
    }
}
