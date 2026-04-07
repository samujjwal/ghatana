/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai;

import com.ghatana.kernel.ai.AgentOrchestrator;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Component for FinanceAgentOrchestratorImpl
 *
 * @doc.type class
 * @doc.purpose Component for FinanceAgentOrchestratorImpl
 * @doc.layer product
 * @doc.pattern Agent
 */
public class FinanceAgentOrchestratorImpl implements AgentOrchestrator {

    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final Map<String, KernelAgent> agents = new ConcurrentHashMap<>();

    public FinanceAgentOrchestratorImpl() {
    }
    
    @Override
    public AgentResponse executeAgent(KernelAgent agent, AgentRequest request) {
        Objects.requireNonNull(agent, "agent must not be null");
        Objects.requireNonNull(request, "request must not be null");

        RuntimeException lastException = null;
        AgentResponse lastFailureResponse = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                AgentResponse response = agent.execute(request);
                if (response.isSuccess()) {
                    return response;
                }

                lastFailureResponse = response;
                log.warn("Agent '{}' returned unsuccessful response on attempt {}/{} for request '{}'",
                    agent.getAgentId(), attempt, MAX_RETRY_ATTEMPTS, request.getRequestId());
            } catch (RuntimeException exception) {
                lastException = exception;
                log.warn("Agent '{}' threw on attempt {}/{} for request '{}'",
                    agent.getAgentId(), attempt, MAX_RETRY_ATTEMPTS, request.getRequestId(), exception);
            }
        }

        if (lastFailureResponse != null) {
            throw new IllegalStateException(
                "Agent execution failed after " + MAX_RETRY_ATTEMPTS + " attempts: " + lastFailureResponse.getError()
            );
        }

        throw new IllegalStateException(
            "Agent execution failed after " + MAX_RETRY_ATTEMPTS + " attempts",
            lastException
        );
    }

    @Override
    public void registerAgent(KernelAgent agent) {
        agents.put(agent.getAgentId(), agent);
    }

    @Override
    public void unregisterAgent(String agentId) {
        agents.remove(agentId);
    }

    @Override
    public List<KernelAgent> getAvailableAgents() {
        return new ArrayList<>(agents.values());
    }

    @Override
    public WorkflowResult executeAgentWorkflow(List<KernelAgent> agents, AgentRequest request) {
        Objects.requireNonNull(agents,  "agents must not be null");
        Objects.requireNonNull(request, "request must not be null");

        List<AgentResponse> responses = new ArrayList<>();
        Throwable firstFailure = null;

        for (KernelAgent agent : agents) {
            try {
                AgentResponse response = executeAgent(agent, request);
                responses.add(response);
                if (!response.isSuccess()) {
                    log.warn("Agent '{}' reported failure in workflow", agent.getAgentId());
                }
            } catch (Exception ex) {
                log.error("Agent '{}' threw exception during workflow execution", agent.getAgentId(), ex);
                if (firstFailure == null) firstFailure = ex;
            }
        }

        boolean overallSuccess = firstFailure == null
            && responses.stream().allMatch(AgentResponse::isSuccess);
        return new WorkflowResult(
            overallSuccess,
            responses,
            firstFailure == null ? null : firstFailure.getMessage()
        );
    }

    @Override
    public KernelAgent getAgent(String agentId) {
        return agents.get(agentId);
    }

    @Override
    public Promise<AgentResponse> executeAgentAsync(KernelAgent agent, AgentRequest request) {
        Objects.requireNonNull(agent,   "agent must not be null");
        Objects.requireNonNull(request, "request must not be null");

        try {
            return Promise.of(executeAgent(agent, request));
        } catch (RuntimeException exception) {
            log.error("Async execution failed for agent '{}'", agent.getAgentId(), exception);
            return Promise.ofException(exception);
        }
    }

    // =========================================================================
    // Private
    // =========================================================================

    private static final org.slf4j.Logger log =
        org.slf4j.LoggerFactory.getLogger(FinanceAgentOrchestratorImpl.class);
}
