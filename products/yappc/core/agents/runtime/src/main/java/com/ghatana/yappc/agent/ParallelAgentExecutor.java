/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Core Agents — Parallel Agent Executor
 */
package com.ghatana.yappc.agent;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Executes multiple typed agents in parallel using {@code Promises.all()} and provides
 * configurable result aggregation strategies.
 *
 * <p>All agents receive the same input concurrently. No agent failure blocks the others;
 * each result is captured in its own {@link AgentResult} wrapper.
 *
 * <h2>Aggregation strategies</h2>
 * <ul>
 *   <li>{@link AggregationStrategy#FIRST_WINS} — return the first successful result</li>
 *   <li>{@link AggregationStrategy#MAJORITY_VOTE} — return the most common output</li>
 *   <li>{@link AggregationStrategy#HIGHEST_CONFIDENCE} — return the result with the
 *       highest confidence score</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Executes multiple agents in parallel and aggregates results
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle act
 */
public class ParallelAgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(ParallelAgentExecutor.class);

    /**
     * Strategy used to select a single winner from parallel agent results.
     */
    public enum AggregationStrategy {
        /** Return the first result whose status is SUCCESS. */
        FIRST_WINS,
        /** Return the result with the highest confidence score. */
        HIGHEST_CONFIDENCE,
        /** Return the output that appears most frequently across agents. */
        MAJORITY_VOTE
    }

    /**
     * Executes all agents in parallel and returns all results.
     *
     * <p>Individual agent failures are captured in the respective {@link AgentResult}
     * rather than propagating as promise rejections.
     *
     * @param agents  the agents to execute concurrently
     * @param context shared agent context
     * @param input   input forwarded to all agents
     * @param <I>     input type
     * @param <O>     output type
     * @return {@link Promise} of a list of all results (in invocation order)
     */
    @NotNull
    public <I, O> Promise<List<AgentResult<O>>> executeAll(
            @NotNull List<TypedAgent<I, O>> agents,
            @NotNull AgentContext context,
            @NotNull I input) {
        Objects.requireNonNull(agents, "agents must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(input, "input must not be null");

        log.debug("ParallelAgentExecutor: dispatching {} agents", agents.size());

        List<Promise<AgentResult<O>>> promises = agents.stream()
                .map(agent -> agent.process(context, input)
                        .then(result -> Promise.of(result),
                              ex -> {
                            log.warn("Agent {} failed: {}", agent.descriptor().getAgentId(), ex.getMessage());
                            return Promise.of(AgentResult.<O>failure(
                                    ex, agent.descriptor().getAgentId(), Duration.ZERO));
                        }))
                .collect(Collectors.toList());

        return Promises.toList(promises);
    }

    /**
     * Executes all agents in parallel and returns a single aggregated result.
     *
     * @param agents   the agents to execute
     * @param context  shared agent context
     * @param input    input forwarded to all agents
     * @param strategy how to pick the winning result
     * @param <I>      input type
     * @param <O>      output type
     * @return {@link Promise} of the aggregated result, or a failure result if all fail
     */
    @NotNull
    public <I, O> Promise<AgentResult<O>> executeAndAggregate(
            @NotNull List<TypedAgent<I, O>> agents,
            @NotNull AgentContext context,
            @NotNull I input,
            @NotNull AggregationStrategy strategy) {
        return executeAll(agents, context, input)
                .map(results -> aggregate(results, strategy));
    }

    // ─── Aggregation ─────────────────────────────────────────────────────────

    private <O> AgentResult<O> aggregate(
            List<AgentResult<O>> results, AggregationStrategy strategy) {
        List<AgentResult<O>> successes = results.stream()
                .filter(r -> !r.isFailed())
                .collect(Collectors.toList());

        if (successes.isEmpty()) {
            log.warn("ParallelAgentExecutor: all {} agents failed", results.size());
            return AgentResult.failure(
                    new RuntimeException("All parallel agents failed"),
                    "parallel-executor", Duration.ZERO);
        }

        log.debug("ParallelAgentExecutor: {} of {} agents succeeded, strategy={}",
                successes.size(), results.size(), strategy);

        return switch (strategy) {
            case FIRST_WINS -> successes.get(0);

            case HIGHEST_CONFIDENCE -> successes.stream()
                    .max(Comparator.comparingDouble(AgentResult::getConfidence))
                    .orElse(successes.get(0));

            case MAJORITY_VOTE -> {
                // Count how many times each output appears (using toString for equality)
                Map<String, List<AgentResult<O>>> grouped = successes.stream()
                        .collect(Collectors.groupingBy(r ->
                                r.getOutput() != null ? r.getOutput().toString() : "null"));
                yield grouped.values().stream()
                        .max(Comparator.comparingInt(List::size))
                        .flatMap(group -> group.stream()
                                .max(Comparator.comparingDouble(AgentResult::getConfidence)))
                        .orElse(successes.get(0));
            }
        };
    }
}
