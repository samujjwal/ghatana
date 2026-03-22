/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.runtime;

import com.ghatana.agent.framework.governance.ActionIntent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Request for human approval before an agent action proceeds.
 *
 * <p>Created by the {@link AutonomyRouter} or {@link AgentApprovalRouter}
 * when an action requires human review. Contains all information needed
 * for a reviewer to make an informed decision.
 *
 * @doc.type record
 * @doc.purpose Human approval request for governance-gated agent actions
 * @doc.layer framework
 * @doc.pattern ValueObject
 */
public record ApprovalRequest(
        /** Unique request identifier. */
        @NotNull String requestId,

        /** Distributed trace ID for correlation. */
        @NotNull String traceId,

        /** The action intent being evaluated. */
        @NotNull ActionIntent actionIntent,

        /** Plain-language summary of what the agent wants to do. */
        @NotNull String actionSummary,

        /** Human-readable risk assessment. */
        @NotNull String riskSummary,

        /** Plain-language explanation of why approval is required. */
        @NotNull String explanation,

        /** Roles that must approve this request. */
        @NotNull List<String> approvingRoles,

        /** Deadline for the approval decision. */
        @Nullable Instant deadline,

        /** Current status. */
        @NotNull ApprovalStatus status,

        /** When this request was created. */
        @NotNull Instant createdAt
) {

    public ApprovalRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(actionIntent, "actionIntent must not be null");
        Objects.requireNonNull(actionSummary, "actionSummary must not be null");
        Objects.requireNonNull(riskSummary, "riskSummary must not be null");
        Objects.requireNonNull(explanation, "explanation must not be null");
        approvingRoles = List.copyOf(approvingRoles);
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /** Returns {@code true} if this request is still awaiting a decision. */
    public boolean isPending() {
        return status == ApprovalStatus.PENDING;
    }

    /** Returns {@code true} if the deadline has passed. */
    public boolean isExpired() {
        return deadline != null && Instant.now().isAfter(deadline);
    }
}
