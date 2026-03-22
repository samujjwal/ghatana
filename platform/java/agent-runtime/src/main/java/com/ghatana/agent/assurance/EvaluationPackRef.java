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
 * Reference to an evaluation pack used for agent validation and promotion.
 *
 * <p>Evaluation packs contain test scenarios, expected behaviors, safety
 * boundary tests, and regression tests. They are versioned and bound to
 * specific agent versions.
 *
 * @doc.type record
 * @doc.purpose Evaluation pack reference for promotion gates
 * @doc.layer agent-runtime
 * @doc.pattern ValueObject
 */
public record EvaluationPackRef(
        /** Unique evaluation pack identifier. */
        @NotNull String packId,

        /** Semantic version of the pack. */
        @NotNull String version,

        /** Agent ID this evaluation pack targets. */
        @NotNull String agentId,

        /** Minimum agent version this pack applies to. */
        @NotNull String minAgentVersion,

        /** Categories of tests included. */
        @NotNull List<String> categories,

        /** Number of test scenarios in this pack. */
        int scenarioCount,

        /** Required minimum pass rate (0.0 - 1.0) for promotion. */
        double requiredPassRate,

        /** When this evaluation pack was last updated. */
        @NotNull Instant lastUpdatedAt
) {

    /**
     * Compact constructor with validation.
     */
    public EvaluationPackRef {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(minAgentVersion, "minAgentVersion");
        Objects.requireNonNull(lastUpdatedAt, "lastUpdatedAt");
        categories = categories == null ? List.of() : List.copyOf(categories);
        if (requiredPassRate < 0.0 || requiredPassRate > 1.0) {
            throw new IllegalArgumentException(
                    "requiredPassRate must be between 0.0 and 1.0, got " + requiredPassRate);
        }
    }
}
