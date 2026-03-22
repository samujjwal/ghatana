/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.governance;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable record of a policy evaluation decision with full audit metadata.
 *
 * @doc.type record
 * @doc.purpose Audit record of a policy evaluation outcome
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record PolicyDecisionRecord(
        /** Unique decision identifier. */
        @NotNull String decisionId,

        /** Decision outcome: ALLOW, DENY, ESCALATE, ALLOW_WITH_APPROVAL, etc. */
        @NotNull String decision,

        /** IDs of policies that were evaluated. */
        @NotNull List<String> policyRefsApplied,

        /** Rules that matched the evaluation context. */
        @NotNull List<String> matchedRules,

        /** Human-readable reasons for the decision. */
        @NotNull List<String> reasons,

        /** Roles required for approval (if applicable). */
        @NotNull List<String> requiredApprovals,

        /** Obligations imposed by the decision. */
        @NotNull List<String> obligations,

        /** The context that was evaluated. */
        @NotNull PolicyEvaluationContext context,

        /** When this decision was made. */
        @NotNull Instant decidedAt,

        /** When this decision expires. */
        @Nullable Instant expiresAt
) {
    public PolicyDecisionRecord {
        Objects.requireNonNull(decisionId);
        Objects.requireNonNull(decision);
        policyRefsApplied = List.copyOf(policyRefsApplied);
        matchedRules = List.copyOf(matchedRules);
        reasons = List.copyOf(reasons);
        requiredApprovals = List.copyOf(requiredApprovals);
        obligations = List.copyOf(obligations);
        Objects.requireNonNull(context);
        Objects.requireNonNull(decidedAt);
    }
}
