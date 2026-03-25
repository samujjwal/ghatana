package com.ghatana.agent.learning.evaluation;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Context provided to evaluation gates for making promotion decisions.
 * Contains historical performance data and test traces.
 *
 * @doc.type class
 * @doc.purpose Evaluation context for gates
 * @doc.layer agent-learning
 */
@Value
@Builder
public class EvaluationContext {

    /** Agent being evaluated. */
    @NotNull String agentId;

    /** Recent trace IDs for regression testing. */
    @Builder.Default
    @NotNull List<String> recentTraceIds = List.of();

    /** Historical success rate for this skill. */
    double historicalSuccessRate;

    /** Number of executions of the current version. */
    int currentVersionExecutionCount;

    /** Environment metadata. */
    @Builder.Default
    @NotNull Map<String, Object> environmentMetadata = Map.of();

    /** Previous evaluation results if this is a re-evaluation. */
    @Builder.Default
    @NotNull List<EvaluationGate.GateResult> previousResults = List.of();
}
