package com.ghatana.agent.learning.evaluation;

import com.ghatana.agent.learning.UpdateCandidate;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * An evaluation gate that a skill update candidate must pass before promotion.
 * Gates are composable and run sequentially or in parallel.
 *
 * @doc.type interface
 * @doc.purpose Evaluation gate SPI
 * @doc.layer agent-learning
 */
public interface EvaluationGate {

    /** Unique name of this gate (e.g., "regression", "safety"). */
    @NotNull String getName();

    /**
     * Evaluates the candidate against this gate's criteria.
     *
     * @param candidate The proposed skill update
     * @param context Contextual information for evaluation
     * @return Gate result with pass/fail and score
     */
    @NotNull Promise<GateResult> evaluate(
            @NotNull UpdateCandidate candidate,
            @NotNull EvaluationContext context);

    /**
     * Individual gate result.
     */
    record GateResult(
            @NotNull String gateName,
            boolean passed,
            double score,
            double threshold,
            @NotNull String reason) {}
}
