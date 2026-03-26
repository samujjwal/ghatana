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

    private final Map<String, KernelAgent> agents = new ConcurrentHashMap<>();

    public FinanceAgentOrchestratorImpl() {
    }
    
    @Override
    public AgentResponse executeAgent(KernelAgent agent, AgentRequest request) {
        return agent.execute(request);
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
        // TODO: Implement workflow execution
        return new WorkflowResult(true, new ArrayList<>(), null);
    }

    @Override
    public KernelAgent getAgent(String agentId) {
        return agents.get(agentId);
    }

    @Override
    public Promise<AgentResponse> executeAgentAsync(KernelAgent agent, AgentRequest request) {
        // TODO: Implement async execution
        return Promise.of(agent.execute(request));
    }
}
