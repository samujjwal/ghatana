/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.dispatch.tier;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import com.ghatana.agent.dispatch.AgentDispatcher;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Default {@link ServiceOrchestrationPlan} that reads the delegation chain
 * from the catalog entry and dispatches to sub-agents sequentially.
 *
 * <p>For each delegatee in {@code delegation.can_delegate_to[]}, the plan
 * dispatches via the parent {@link AgentDispatcher} (which recursively resolves
 * the tier for each sub-agent). Results are aggregated into a single
 * {@link AgentResult}.
 *
 * @doc.type class
 * @doc.purpose Default Tier-S execution via sequential delegation
 * @doc.layer framework
 * @doc.pattern Strategy, Chain of Responsibility
 *
 * @author Ghatana AI Platform
 * @since 2.2.0
 */
public class DefaultServiceOrchestrationPlan implements ServiceOrchestrationPlan {

    private static final Logger log = LoggerFactory.getLogger(DefaultServiceOrchestrationPlan.class);

    private static final int MAX_DELEGATION_DEPTH = 10;

    @Override
    @NotNull
    @SuppressWarnings("unchecked")
    public Promise<AgentResult<Object>> execute(
            @NotNull CatalogAgentEntry entry,
            @NotNull Object input,
            @NotNull AgentContext ctx,
            @NotNull AgentDispatcher dispatcher) {

        Instant start = Instant.now();
        String agentId = entry.getId();

        List<String> delegatees = extractDelegatees(entry);
        if (delegatees.isEmpty()) {
            log.warn("Tier-S agent '{}' has no delegatees — returning delegation failure", agentId);
            return Promise.of(AgentResult.builder()
                    .status(AgentResultStatus.FAILED)
                    .confidence(0.0)
                    .agentId(agentId)
                    .explanation("No delegatees configured for service-orchestrated agent: " + agentId)
                    .processingTime(Duration.between(start, Instant.now()))
                    .build());
        }

        log.debug("Tier-S executing agent '{}' — delegating to {} sub-agents: {}",
                agentId, delegatees.size(), delegatees);

        // Execute delegation chain sequentially, passing each output as next input
        return executeChain(delegatees, input, ctx, dispatcher, agentId, start, 0);
    }

    private Promise<AgentResult<Object>> executeChain(
            List<String> delegatees,
            Object currentInput,
            AgentContext ctx,
            AgentDispatcher dispatcher,
            String parentAgentId,
            Instant start,
            int depth) {

        if (depth >= MAX_DELEGATION_DEPTH) {
            return Promise.of(AgentResult.builder()
                    .status(AgentResultStatus.FAILED)
                    .confidence(0.0)
                    .agentId(parentAgentId)
                    .explanation("Maximum delegation depth (" + MAX_DELEGATION_DEPTH + ") exceeded")
                    .processingTime(Duration.between(start, Instant.now()))
                    .build());
        }

        if (delegatees.isEmpty()) {
            return Promise.of(AgentResult.builder()
                    .output(currentInput)
                    .status(AgentResultStatus.SUCCESS)
                    .confidence(1.0)
                    .agentId(parentAgentId)
                    .explanation("Service orchestration completed")
                    .processingTime(Duration.between(start, Instant.now()))
                    .metrics(Map.of("tier", "SERVICE_ORCHESTRATED", "delegationDepth", depth))
                    .build());
        }

        String nextAgent = delegatees.getFirst();
        List<String> remaining = delegatees.subList(1, delegatees.size());

        return dispatcher.<Object, Object>dispatch(nextAgent, currentInput, ctx)
                .then(result -> {
                    if (result.isFailed()) {
                        log.warn("Delegation to '{}' failed: {}", nextAgent, result.getExplanation());
                        return Promise.of(AgentResult.<Object>builder()
                                .status(AgentResultStatus.FAILED)
                                .confidence(0.0)
                                .agentId(parentAgentId)
                                .explanation("Delegation failed at agent '" + nextAgent + "': " + result.getExplanation())
                                .processingTime(Duration.between(start, Instant.now()))
                                .build());
                    }

                    Object nextInput = result.getOutput() != null ? result.getOutput() : currentInput;
                    return executeChain(remaining, nextInput, ctx, dispatcher, parentAgentId, start, depth + 1);
                });
    }

    @SuppressWarnings("unchecked")
    private List<String> extractDelegatees(CatalogAgentEntry entry) {
        Map<String, Object> delegation = entry.getDelegation();
        if (delegation == null) return List.of();

        Object delegateTo = delegation.get("can_delegate_to");
        if (delegateTo instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .toList();
        }
        return List.of();
    }
}
