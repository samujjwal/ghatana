/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.assurance;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of running an evaluation pack against an agent version.
 *
 * <p>Records the pass/fail outcome, individual scenario results, and
 * determines whether the agent meets promotion criteria.
 *
 * @doc.type record
 * @doc.purpose Evaluation run result for promotion gate decisions
 * @doc.layer agent-runtime
 * @doc.pattern ValueObject
 */
public record EvaluationResult(
        /** Unique run identifier. */
        @NotNull String runId,

        /** Reference to the evaluation pack used. */
        @NotNull String packId,

        /** Agent version evaluated. */
        @NotNull String agentVersion,

        /** Overall pass rate (0.0 - 1.0). */
        double passRate,

        /** Total scenarios executed. */
        int totalScenarios,

        /** Scenarios that passed. */
        int passedScenarios,

        /** Scenarios that failed. */
        int failedScenarios,

        /** Whether the agent meets the required pass rate for promotion. */
        boolean promotionEligible,

        /** Dimension-specific scores (e.g., safety, accuracy, latency). */
        @NotNull Map<String, Double> dimensionScores,

        /** Summary of failures (scenario IDs and reasons). */
        @NotNull List<String> failureSummaries,

        /** When this evaluation was executed. */
        @NotNull Instant evaluatedAt,

        /** Duration of the evaluation run in milliseconds. */
        long durationMs,

        // DC-33: Stale audit/TODO target references that should block release
        @NotNull List<String> staleAuditTargetRefs,

        @NotNull List<String> staleTodoTargetRefs
) {

    /**
     * Compact constructor with validation.
     */
    public EvaluationResult {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(agentVersion, "agentVersion");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        dimensionScores = dimensionScores == null ? Map.of() : Map.copyOf(dimensionScores);
        failureSummaries = failureSummaries == null ? List.of() : List.copyOf(failureSummaries);
        staleAuditTargetRefs = staleAuditTargetRefs == null ? List.of() : List.copyOf(staleAuditTargetRefs);
        staleTodoTargetRefs = staleTodoTargetRefs == null ? List.of() : List.copyOf(staleTodoTargetRefs);
    }

    /**
     * DC-33: Returns true if this result has any stale audit or TODO references.
     */
    public boolean hasStaleRefs() {
        return !staleAuditTargetRefs.isEmpty() || !staleTodoTargetRefs.isEmpty();
    }

    /**
     * DC-33: Returns total count of stale references (audit + TODO).
     */
    public int totalStaleRefCount() {
        return staleAuditTargetRefs.size() + staleTodoTargetRefs.size();
    }
}
