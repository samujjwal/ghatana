package com.ghatana.agent.learning;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Result of a skill promotion attempt.
 *
 * @doc.type class
 * @doc.purpose Skill promotion result
 * @doc.layer agent-learning
 */
@Value
@Builder
public class PromotionResult {

    /** The skill that was promoted/rejected. */
    @NotNull String skillId;

    /** The target version. */
    @NotNull String targetVersion;

    /** Whether the promotion succeeded. */
    boolean success;

    /** Previous version (pre-promotion). */
    @Nullable String previousVersion;

    /** Per-gate evaluation results. */
    @Builder.Default
    @NotNull List<GateResult> gateResults = List.of();

    /** Human-readable explanation of the promotion decision. */
    @Nullable String explanation;

    /** Timestamp of the promotion decision. */
    @Builder.Default
    @NotNull Instant decidedAt = Instant.now();

    @Value
    @Builder
    public static class GateResult {
        @NotNull String gateName;
        boolean passed;
        @Nullable String reason;
        double score;
    }
}
