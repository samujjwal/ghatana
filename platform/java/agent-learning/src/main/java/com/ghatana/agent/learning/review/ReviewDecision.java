/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.learning.review;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Captures a human reviewer's decision on a review item.
 *
 * @doc.type record
 * @doc.purpose Human review decision value object
 * @doc.layer agent-learning
 * @doc.pattern ValueObject
 *
 * @param reviewer   identifier of the human reviewer (e.g., email or user ID)
 * @param rationale  explanation for the decision
 * @param decidedAt  when the decision was made
 * @param notes      optional additional notes
 *
 * @since 2.4.0
 */
public record ReviewDecision(
        @NotNull String reviewer,
        @NotNull String rationale,
        @NotNull Instant decidedAt,
        @Nullable String notes
) {
    public ReviewDecision {
        Objects.requireNonNull(reviewer, "reviewer must not be null");
        Objects.requireNonNull(rationale, "rationale must not be null");
        if (decidedAt == null) {
            decidedAt = Instant.now();
        }
    }

    /**
     * Convenience factory for a quick approval.
     */
    public static ReviewDecision approve(String reviewer, String rationale) {
        return new ReviewDecision(reviewer, rationale, Instant.now(), null);
    }

    /**
     * Convenience factory for a quick rejection.
     */
    public static ReviewDecision reject(String reviewer, String rationale) {
        return new ReviewDecision(reviewer, rationale, Instant.now(), null);
    }
}
