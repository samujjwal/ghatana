/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.governance;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Result of a governance policy evaluation for an {@link ActionIntent}.
 *
 * <p>A {@code PolicyDecision} carries the outcome, the policies and rules that
 * were applied, the reasons for the decision, any obligations the caller must
 * fulfill, and an optional expiry for time-bounded approvals.
 *
 * @doc.type record
 * @doc.purpose Immutable result of policy evaluation with obligations and evidence
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record PolicyDecision(
        /** The governance outcome. */
        @NotNull PolicyDecisionType decision,

        /** IDs of the policies that were evaluated. */
        @NotNull List<String> policyRefsApplied,

        /** Specific rules within policies that matched this intent. */
        @NotNull List<String> matchedRules,

        /** Human-readable reasons for the decision. */
        @NotNull List<String> reasons,

        /** Roles required to approve (if decision requires approval). */
        @NotNull List<String> requiredApprovals,

        /** Obligations the caller must fulfill before or after execution. */
        @NotNull List<PolicyObligation> obligations,

        /** When this decision expires (for time-bounded approvals). */
        @Nullable Instant expiresAt,

        /** When this decision was made. */
        @NotNull Instant decidedAt
) {

    public PolicyDecision {
        Objects.requireNonNull(decision, "decision must not be null");
        policyRefsApplied = List.copyOf(policyRefsApplied);
        matchedRules = List.copyOf(matchedRules);
        reasons = List.copyOf(reasons);
        requiredApprovals = List.copyOf(requiredApprovals);
        obligations = List.copyOf(obligations);
        Objects.requireNonNull(decidedAt, "decidedAt must not be null");
    }

    /** Creates a simple ALLOW decision with no obligations. */
    @NotNull
    public static PolicyDecision allow(@NotNull List<String> policyRefs, @NotNull String reason) {
        return new PolicyDecision(
                PolicyDecisionType.ALLOW,
                policyRefs,
                List.of(),
                List.of(reason),
                List.of(),
                List.of(),
                null,
                Instant.now());
    }

    /** Creates a DENY decision. */
    @NotNull
    public static PolicyDecision deny(
            @NotNull List<String> policyRefs,
            @NotNull List<String> matchedRules,
            @NotNull String reason) {
        return new PolicyDecision(
                PolicyDecisionType.DENY,
                policyRefs,
                matchedRules,
                List.of(reason),
                List.of(),
                List.of(),
                null,
                Instant.now());
    }

    /** Creates an ALLOW_WITH_APPROVAL decision. */
    @NotNull
    public static PolicyDecision requireApproval(
            @NotNull List<String> policyRefs,
            @NotNull List<String> requiredRoles,
            @NotNull String reason) {
        return new PolicyDecision(
                PolicyDecisionType.ALLOW_WITH_APPROVAL,
                policyRefs,
                List.of(),
                List.of(reason),
                requiredRoles,
                List.of(),
                null,
                Instant.now());
    }

    /** Returns {@code true} if the action may proceed. */
    public boolean isPermitted() {
        return decision.isPermitted();
    }
}
