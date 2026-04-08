/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.composition;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrates a scatter-gather composition: fans out to all member agents in
 * parallel and aggregates results using the configured {@link AggregationStrategy}.
 *
 * @doc.type class
 * @doc.purpose Scatter-gather multi-agent orchestration
 * @doc.layer platform
 * @doc.pattern Orchestrator
 */
public final class ScatterGatherOrchestration {

    private final AgentInvoker invoker;

    public ScatterGatherOrchestration(@NotNull AgentInvoker invoker) {
        this.invoker = Objects.requireNonNull(invoker, "invoker");
    }

    /**
     * Dispatches to all member agents and aggregates results.
     *
     * @param policy composition policy (must have pattern SCATTER_GATHER and non-null aggregation)
     * @param input  common input passed to every member
     * @param ctx    agent context
     * @param <I>    input type
     * @param <O>    output type
     * @return aggregated result
     */
    @SuppressWarnings("unchecked")
    @NotNull
    public <I, O> Promise<AgentResult<O>> execute(
            @NotNull CompositionPolicy policy, @NotNull I input, @NotNull AgentContext ctx) {
        if (policy.pattern() != CompositionPattern.SCATTER_GATHER || policy.aggregation() == null) {
            throw new IllegalArgumentException(
                    "ScatterGatherOrchestration requires a SCATTER_GATHER CompositionPolicy with aggregation");
        }
        List<Promise<AgentResult<O>>> dispatches = policy.memberAgentIds().stream()
                .map(agentId -> (Promise<AgentResult<O>>) (Promise<?>) invoker.invoke(agentId, input, ctx))
                .toList();

        return Promises.toList(dispatches)
                .map(results -> aggregate(results, policy.aggregation()));
    }

    @SuppressWarnings("unchecked")
    private <O> AgentResult<O> aggregate(List<AgentResult<O>> results, AggregationStrategy strategy) {
        return switch (strategy) {
            case COLLECT_ALL -> (AgentResult<O>) AgentResult.builder()
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("scatter-gather")
                    .output(results)
                    .confidence(1.0)
                    .processingTime(Duration.ZERO)
                    .build();
            case FIRST_SUCCESS -> results.stream()
                    .filter(r -> r.getStatus() == AgentResultStatus.SUCCESS)
                    .findFirst()
                    .orElseGet(() -> (AgentResult<O>) AgentResult.builder()
                            .status(AgentResultStatus.FAILED)
                            .agentId("scatter-gather")
                            .explanation("No successful results")
                            .confidence(0.0)
                            .processingTime(Duration.ZERO)
                            .build());
            case HIGHEST_CONFIDENCE -> results.stream()
                    .filter(r -> r.getStatus() == AgentResultStatus.SUCCESS)
                    .max((a, b) -> Double.compare(a.getConfidence(), b.getConfidence()))
                    .orElseGet(() -> (AgentResult<O>) AgentResult.builder()
                            .status(AgentResultStatus.FAILED)
                            .agentId("scatter-gather")
                            .explanation("No successful results for highest-confidence aggregation")
                            .confidence(0.0)
                            .processingTime(Duration.ZERO)
                            .build());
            case CUSTOM_MERGE -> throw new UnsupportedOperationException(
                    "CUSTOM_MERGE requires a domain-specific merge function; use subclass or delegate");
        };
    }
}
