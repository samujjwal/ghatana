/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval.mastery;

import org.jetbrains.annotations.NotNull;

/**
 * Policy governing memory retrieval behavior.
 *
 * <p>Controls how memory items are filtered, ranked, and presented
 * during mastery-aware retrieval.
 *
 * @doc.type record
 * @doc.purpose Policy governing memory retrieval behavior
 * @doc.layer agent-runtime
 * @doc.pattern Record
 */
public record RetrievalPolicy(
        @NotNull String policyId,
        boolean includeObsolete,
        boolean includeMaintenanceOnly,
        boolean includeNegativeKnowledge,
        boolean requireFreshness,
        boolean requireVersionCompatibility,
        boolean prioritizeByMasteryState,
        int maxAgeDays,
        int maxResults,
        double minConfidence
) {
    public RetrievalPolicy {
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("policyId must not be blank");
        }
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be positive");
        }
        if (minConfidence < 0.0 || minConfidence > 1.0) {
            throw new IllegalArgumentException("minConfidence must be in [0.0, 1.0]");
        }
        if (maxAgeDays < 0) {
            throw new IllegalArgumentException("maxAgeDays must be non-negative");
        }
    }

    /**
     * Creates a default retrieval policy for production use.
     *
     * @return default retrieval policy
     */
    @NotNull
    public static RetrievalPolicy defaultPolicy() {
        return new RetrievalPolicy(
                "default",
                false,
                false,
                true,
                true,
                true,
                true,
                90,
                50,
                0.5
        );
    }

    /**
     * Creates a strict retrieval policy that enforces all constraints.
     *
     * @return strict retrieval policy
     */
    @NotNull
    public static RetrievalPolicy strictPolicy() {
        return new RetrievalPolicy(
                "strict",
                false,
                false,
                true,
                true,
                true,
                true,
                30,
                20,
                0.7
        );
    }

    /**
     * Creates a permissive retrieval policy for exploration.
     *
     * @return permissive retrieval policy
     */
    @NotNull
    public static RetrievalPolicy permissivePolicy() {
        return new RetrievalPolicy(
                "permissive",
                true,
                true,
                true,
                false,
                false,
                false,
                365,
                100,
                0.3
        );
    }
}
