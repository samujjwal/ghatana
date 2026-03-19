/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Core Agents
 */
package com.ghatana.yappc.agent;

import java.time.Instant;

/**
 * The final decision made by a human reviewer on an {@code ApprovalRequest}.
 *
 * @param requestId  the approval request this decision resolves
 * @param approved   {@code true} when the request was approved, {@code false} when rejected
 * @param decidedBy  user ID or name of the reviewer
 * @param decidedAt  timestamp of the decision
 * @param comment    optional free-text reason or note left by the reviewer
 *
 * @doc.type record
 * @doc.purpose Value object representing the outcome of a human-approval gate
 * @doc.layer product
 * @doc.pattern ValueObject
 * @doc.gaa.lifecycle act
 */
public record ApprovalDecision(
        String requestId,
        boolean approved,
        String decidedBy,
        Instant decidedAt,
        String comment
) {
    /** Returns {@code true} when the gate was approved. */
    public boolean isApproved() {
        return approved;
    }

    /** Returns {@code true} when the gate was rejected. */
    public boolean isRejected() {
        return !approved;
    }

    /** Factory: creates an approved decision at the current instant. */
    public static ApprovalDecision approved(String requestId, String decidedBy, String comment) {
        return new ApprovalDecision(requestId, true, decidedBy, Instant.now(), comment);
    }

    /** Factory: creates a rejected decision at the current instant. */
    public static ApprovalDecision rejected(String requestId, String decidedBy, String comment) {
        return new ApprovalDecision(requestId, false, decidedBy, Instant.now(), comment);
    }
}
