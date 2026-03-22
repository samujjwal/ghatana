/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Snapshot of the current agent execution state for invariant evaluation.
 *
 * @doc.type record
 * @doc.purpose Execution context snapshot for invariant monitoring
 * @doc.layer agent-runtime
 * @doc.pattern ValueObject
 */
public record InvariantContext(
        /** Agent being monitored. */
        @NotNull String agentId,

        /** Tenant scope. */
        @NotNull String tenantId,

        /** Current trace identifier. */
        @NotNull String traceId,

        /** Accumulated cost for this turn (USD). */
        double accumulatedCostUsd,

        /** Maximum allowed cost for this turn (USD). */
        double costCapUsd,

        /** Current delegation depth (0 = root agent). */
        int delegationDepth,

        /** Maximum allowed delegation depth. */
        int maxDelegationDepth,

        /** Number of actions executed in this turn. */
        int actionsExecuted,

        /** Maximum allowed actions per turn. */
        int maxActionsPerTurn,

        /** When this agent turn started. */
        @NotNull Instant turnStartedAt,

        /** Maximum allowed turn duration in seconds. */
        long maxTurnDurationSeconds,

        /** Additional context attributes. */
        @NotNull Map<String, Object> attributes
) {

    /**
     * Compact constructor with validation and defensive copy.
     */
    public InvariantContext {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(turnStartedAt, "turnStartedAt");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
