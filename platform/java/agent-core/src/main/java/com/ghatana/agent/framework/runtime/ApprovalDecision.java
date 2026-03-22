/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.runtime;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Decision made by a human reviewer in response to an {@link ApprovalRequest}.
 *
 * @doc.type record
 * @doc.purpose Records an approval or rejection decision with audit metadata
 * @doc.layer framework
 * @doc.pattern ValueObject
 */
public record ApprovalDecision(
        /** The approval request this decision responds to. */
        @NotNull String requestId,

        /** Distributed trace ID for correlation. */
        @NotNull String traceId,

        /** The decision made. */
        @NotNull ApprovalStatus decision,

        /** Identity of the approver (e.g., email or role-scoped principal). */
        @NotNull String approver,

        /** Optional rationale for the decision. */
        @Nullable String rationale,

        /** When the decision was made. */
        @NotNull Instant decidedAt
) {

    public ApprovalDecision {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(decision, "decision must not be null");
        Objects.requireNonNull(approver, "approver must not be null");
        Objects.requireNonNull(decidedAt, "decidedAt must not be null");
        if (decision != ApprovalStatus.APPROVED && decision != ApprovalStatus.REJECTED) {
            throw new IllegalArgumentException(
                    "ApprovalDecision decision must be APPROVED or REJECTED, got: " + decision);
        }
    }

    /** Returns {@code true} if the request was approved. */
    public boolean isApproved() {
        return decision == ApprovalStatus.APPROVED;
    }
}
