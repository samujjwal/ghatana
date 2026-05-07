/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Scoped grant authorizing one agent to delegate work to another.
 *
 * <p>Delegation grants cap the child agent's permissions to a subset of the
 * parent's and track the delegation chain depth. The chain is broken if
 * the depth limit is exceeded.
 *
 * @doc.type record
 * @doc.purpose Scoped delegation grant for agent-to-agent delegation
 * @doc.layer agent-runtime
 * @doc.pattern ValueObject
 */
public record DelegationGrant(
        /** Unique delegation grant identifier. */
        @NotNull String grantId,

        /** Parent agent delegating work. */
        @NotNull String parentAgentId,

        /** Child agent receiving the delegation. */
        @NotNull String childAgentId,

        /** Tenant scope. */
        @NotNull String tenantId,

        /** Trace identifier. */
        @NotNull String traceId,

        /** Current depth in the delegation chain (parent depth + 1). */
        int depth,

        /** Maximum allowed depth for the delegation chain. */
        int maxDepth,

        /** Action classes the child is permitted to use. */
        @NotNull Set<String> allowedActionClasses,

        /** Maximum cost (USD) the child can accrue under this delegation. */
        double maxCostUsd,

        /** When this grant was issued. */
        @NotNull Instant issuedAt,

        /** When this grant expires. */
        @NotNull Instant expiresAt
) {

    /**
     * Compact constructor with validation.
     */
    public DelegationGrant {
        Objects.requireNonNull(grantId, "grantId");
        Objects.requireNonNull(parentAgentId, "parentAgentId");
        Objects.requireNonNull(childAgentId, "childAgentId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        allowedActionClasses = allowedActionClasses == null
                ? Set.of() : Set.copyOf(allowedActionClasses);
        if (depth < 0) throw new IllegalArgumentException("depth must be >= 0");
        if (depth > maxDepth) {
            throw new IllegalArgumentException(String.format(
                    "Delegation depth %d exceeds max %d", depth, maxDepth));
        }
    }

    /**
     * Returns true if this grant allows further delegation (depth < maxDepth).
     */
    public boolean canDelegateForward() {
        return depth < maxDepth;
    }

    /**
     * Returns true if this grant is still valid (not expired).
     */
    public boolean isValid() {
        return Instant.now().isBefore(expiresAt);
    }

    /**
     * Creates a child delegation grant scoped to a subset of this grant's permissions.
     *
     * @param childGrantId       new grant ID
     * @param nextChildAgentId   the agent receiving the sub-delegation
     * @param childActionClasses action classes (must be a subset of this grant's)
     * @param childMaxCostUsd    cost cap (must be ≤ this grant's)
     * @param ttl                time-to-live for the child grant
     * @return the new delegation grant
     * @throws IllegalStateException if delegation depth would be exceeded
     */
    @NotNull
    public DelegationGrant delegateTo(
            @NotNull String childGrantId,
            @NotNull String nextChildAgentId,
            @NotNull Set<String> childActionClasses,
            double childMaxCostUsd,
            @NotNull Duration ttl) {
        if (!canDelegateForward()) {
            throw new IllegalStateException(String.format(
                    "Cannot delegate forward: depth=%d already at max=%d", depth, maxDepth));
        }
        // Ensure child permissions are a subset
        for (String action : childActionClasses) {
            if (!allowedActionClasses.contains(action)) {
                throw new IllegalArgumentException(
                        "Child action class '" + action + "' not permitted by parent grant");
            }
        }
        if (childMaxCostUsd > maxCostUsd) {
            throw new IllegalArgumentException(String.format(
                    "Child cost cap $%.4f exceeds parent cap $%.4f", childMaxCostUsd, maxCostUsd));
        }

        Instant now = Instant.now();
        Instant childExpiry = now.plus(ttl);
        // Child cannot outlive parent
        if (childExpiry.isAfter(expiresAt)) {
            childExpiry = expiresAt;
        }

        return new DelegationGrant(
                childGrantId, childAgentId, nextChildAgentId,
                tenantId, traceId,
                depth + 1, maxDepth,
                childActionClasses, childMaxCostUsd,
                now, childExpiry);
    }
}
