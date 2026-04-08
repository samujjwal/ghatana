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
import java.util.Map;
import java.util.Objects;

/**
 * Orchestrates a voting composition: dispatches all member agents in parallel,
 * then applies the configured {@link VotingPolicy} to pick a winner.
 *
 * <p>Current implementation supports {@link VotingPolicy#MAJORITY}, {@link VotingPolicy#UNANIMOUS},
 * and {@link VotingPolicy#ANY_ONE}. {@link VotingPolicy#WEIGHTED_MAJORITY} is treated the same as
 * {@link VotingPolicy#MAJORITY} until weight configuration is introduced.
 *
 * @doc.type class
 * @doc.purpose Voting-based multi-agent orchestration
 * @doc.layer platform
 * @doc.pattern Orchestrator
 */
public final class VotingOrchestration {

    private final AgentInvoker invoker;

    public VotingOrchestration(@NotNull AgentInvoker invoker) {
        this.invoker = Objects.requireNonNull(invoker, "invoker");
    }

    /**
     * Runs all member agents in parallel and selects a winner using the voting policy.
     *
     * @param policy composition policy (must have pattern VOTING and non-null votingPolicy)
     * @param input  the common input passed to every member agent
     * @param ctx    shared agent context
     * @param <I>    input type
     * @param <O>    output type
     * @return the winning result, or an ERROR result when no winner can be established
     */
    @SuppressWarnings("unchecked")
    @NotNull
    public <I, O> Promise<AgentResult<O>> execute(
            @NotNull CompositionPolicy policy, @NotNull I input, @NotNull AgentContext ctx) {
        if (policy.pattern() != CompositionPattern.VOTING || policy.votingPolicy() == null) {
            throw new IllegalArgumentException("VotingOrchestration requires a VOTING CompositionPolicy");
        }
        List<Promise<AgentResult<O>>> dispatches = policy.memberAgentIds().stream()
                .map(agentId -> (Promise<AgentResult<O>>) (Promise<?>) invoker.invoke(agentId, input, ctx))
                .toList();

        return Promises.toList(dispatches)
                .map(results -> applyVoting(results, policy.votingPolicy()));
    }

    @SuppressWarnings("unchecked")
    private <O> AgentResult<O> applyVoting(List<AgentResult<O>> results, VotingPolicy votingPolicy) {
        List<AgentResult<O>> successes = results.stream()
                .filter(r -> r.getStatus() == AgentResultStatus.SUCCESS)
                .toList();

        boolean quorum = switch (votingPolicy) {
            case MAJORITY, WEIGHTED_MAJORITY -> successes.size() > results.size() / 2;
            case UNANIMOUS -> successes.size() == results.size();
            case ANY_ONE -> !successes.isEmpty();
        };

        if (!quorum) {
            return (AgentResult<O>) AgentResult.builder()
                    .status(AgentResultStatus.FAILED)
                    .agentId("voting-orchestration")
                    .explanation("Voting quorum not met: " + successes.size() + "/" + results.size() + " succeeded")
                    .confidence(0.0)
                    .processingTime(Duration.ZERO)
                    .build();
        }

        // Return the highest-confidence winning result
        return successes.stream()
                .max((a, b) -> Double.compare(a.getConfidence(), b.getConfidence()))
                .orElseThrow();
    }
}
