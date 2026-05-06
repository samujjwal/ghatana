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
 * Time-bounded, scope-limited grant authorizing an agent to execute actions.
 *
 * <p>Grants are issued after governance evaluation and encapsulate the exact
 * permissions for a single agent turn. They expire after a configured TTL
 * or when explicitly revoked.
 *
 * @doc.type record
 * @doc.purpose Time-bounded execution grant for governed agent dispatch
 * @doc.layer agent-runtime
 * @doc.pattern ValueObject
 */
public record AgentExecutionGrant(
        /** Unique grant identifier. */
        @NotNull String grantId,

        /** Agent authorized by this grant. */
        @NotNull String agentId,

        /** Tenant scope. */
        @NotNull String tenantId,

        /** Trace identifier for the current turn. */
        @NotNull String traceId,

        /** Action classes this grant permits. */
        @NotNull Set<String> allowedActionClasses,

        /** Maximum cost (USD) this grant allows. */
        double maxCostUsd,

        /** Maximum delegation depth permitted. */
        int maxDelegationDepth,

        /** Maximum number of actions this grant allows. */
        int maxActions,

        /** When this grant was issued. */
        @NotNull Instant issuedAt,

        /** When this grant expires. */
        @NotNull Instant expiresAt,

        /** Whether this grant has been revoked. */
        boolean revoked
) {

    /**
     * Compact constructor with validation.
     */
    public AgentExecutionGrant {
        Objects.requireNonNull(grantId, "grantId");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        allowedActionClasses = allowedActionClasses == null
                ? Set.of() : Set.copyOf(allowedActionClasses);
    }

    /**
     * Returns true if this grant is currently valid (not expired, not revoked).
     */
    public boolean isValid() {
        return !revoked && Instant.now().isBefore(expiresAt);
    }

    /**
     * Returns true if the given action class is permitted by this grant.
     */
    public boolean permitsAction(String actionClass) {
        if (actionClass == null) {
            return false;
        }
        return allowedActionClasses.contains("*") || allowedActionClasses.contains(actionClass);
    }

    /**
     * Creates a standard grant with a TTL from now.
     */
    @NotNull
    public static AgentExecutionGrant create(
            @NotNull String grantId,
            @NotNull String agentId,
            @NotNull String tenantId,
            @NotNull String traceId,
            @NotNull Set<String> allowedActionClasses,
            double maxCostUsd,
            int maxDelegationDepth,
            int maxActions,
            @NotNull Duration ttl) {
        Instant now = Instant.now();
        return new AgentExecutionGrant(
                grantId, agentId, tenantId, traceId,
                allowedActionClasses, maxCostUsd,
                maxDelegationDepth, maxActions,
                now, now.plus(ttl), false);
    }
}
