/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.framework.planner;

import com.ghatana.agent.framework.runtime.BaseAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registry for managing agent instances created by {@link PlannerAgentFactory}.
 *
 * <p>Provides lifecycle management including registration, lookup, and listing
 * of agent instances.
 *
 * @doc.type class
 * @doc.purpose Agent instance registry for planner agents
 * @doc.layer framework
 * @doc.pattern Registry
 */
public class AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistry.class);

    private final PlannerAgentFactory factory;
    private final Map<String, BaseAgent<?, ?>> agents = new HashMap<>();

    public AgentRegistry(PlannerAgentFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory must not be null");
    }

    /**
     * Register an agent instance.
     *
     * @param agentId the unique agent identifier
     * @param agent   the agent instance
     */
    public void register(String agentId, BaseAgent<?, ?> agent) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(agent, "agent must not be null");
        agents.put(agentId, agent);
        log.info("Registered agent: {}", agentId);
    }

    /**
     * Look up an agent by ID.
     *
     * @param agentId the agent identifier
     * @return optional containing the agent if found
     */
    public Optional<BaseAgent<?, ?>> lookup(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    /**
     * Get the underlying factory.
     *
     * @return the PlannerAgentFactory
     */
    public PlannerAgentFactory getFactory() {
        return factory;
    }

    /**
     * Get the number of registered agents.
     *
     * @return agent count
     */
    public int getAgentCount() {
        return agents.size();
    }

    /**
     * Get all registered agent IDs.
     *
     * @return map of agent IDs to agents
     */
    public Map<String, BaseAgent<?, ?>> getAgents() {
        return Map.copyOf(agents);
    }
}
