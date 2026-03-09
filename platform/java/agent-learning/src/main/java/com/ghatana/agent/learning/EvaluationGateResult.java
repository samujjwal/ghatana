package com.ghatana.agent.learning;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of evaluating an update candidate through evaluation gates.
 *
 * @doc.type class
 * @doc.purpose Evaluation gate aggregate result
 * @doc.layer agent-learning
 */
@Value
@Builder
public class EvaluationGateResult {

    /** The candidate that was evaluated. */
    @NotNull String skillId;

    /** The proposed version. */
    @NotNull String proposedVersion;

    /** Whether all gates passed. */
    boolean allGatesPassed;

    /** Individual gate results. */
    @Builder.Default
    @NotNull List<GateOutcome> outcomes = List.of();

    /** Overall recommendation (PROMOTE, REJECT, REVIEW). */
    @NotNull String recommendation;

    /** When the evaluation completed. */
    @Builder.Default
    @NotNull Instant evaluatedAt = Instant.now();

    @Value
    @Builder
    public static class GateOutcome {
        @NotNull String gateName;
        boolean passed;
        double score;
        double threshold;
        @Nullable String reason;
        @Builder.Default
        @NotNull Map<String, Object> details = Map.of();
    }
}
