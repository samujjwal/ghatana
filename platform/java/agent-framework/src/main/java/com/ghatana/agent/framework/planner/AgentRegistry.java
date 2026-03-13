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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-tenant registry for managing agent instances created by {@link PlannerAgentFactory}.
 *
 * <p>Agents are isolated per tenant: agent IDs are scoped to a {@code tenantId} so an
 * agent registered for tenant A is never visible to tenant B.
 *
 * <p>All public methods that accept {@code agentId} also require a {@code tenantId}.
 * Single-argument overloads that omit {@code tenantId} default to {@code "default"},
 * preserved for backward-compatibility with tests and legacy callers.
 *
 * @doc.type class
 * @doc.purpose Multi-tenant agent instance registry for planner agents
 * @doc.layer framework
 * @doc.pattern Registry
 */
public class AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistry.class);
    private static final String DEFAULT_TENANT = "default";

    private final PlannerAgentFactory factory;

    /**
     * Outer key: tenantId. Inner key: agentId. ConcurrentHashMap for thread safety on
     * the outer level; inner maps are guarded by the outer map's compute operations.
     */
    private final ConcurrentHashMap<String, Map<String, BaseAgent<?, ?>>> tenantAgents =
            new ConcurrentHashMap<>();

    public AgentRegistry(PlannerAgentFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory must not be null");
    }

    // =========================================================================
    // Registration
    // =========================================================================

    /**
     * Register an agent instance scoped to {@code tenantId}.
     *
     * @param tenantId the tenant identifier (never blank)
     * @param agentId  the unique agent identifier within the tenant
     * @param agent    the agent instance
     */
    public void register(String tenantId, String agentId, BaseAgent<?, ?> agent) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(agent, "agent must not be null");
        tenantAgents.computeIfAbsent(tenantId, t -> new HashMap<>()).put(agentId, agent);
        log.info("Registered agent: tenantId={} agentId={}", tenantId, agentId);
    }

    /**
     * Register an agent using the default tenant.
     *
     * @param agentId the unique agent identifier
     * @param agent   the agent instance
     * @deprecated Use {@link #register(String, String, BaseAgent)} with an explicit tenantId.
     */
    @Deprecated
    public void register(String agentId, BaseAgent<?, ?> agent) {
        register(DEFAULT_TENANT, agentId, agent);
    }

    // =========================================================================
    // Lookup
    // =========================================================================

    /**
     * Look up an agent by tenant and agent ID.
     *
     * @param tenantId the tenant identifier
     * @param agentId  the agent identifier
     * @return optional containing the agent if found within this tenant
     */
    public Optional<BaseAgent<?, ?>> lookup(String tenantId, String agentId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        Map<String, BaseAgent<?, ?>> agents = tenantAgents.get(tenantId);
        return agents == null ? Optional.empty() : Optional.ofNullable(agents.get(agentId));
    }

    /**
     * Look up an agent in the default tenant.
     *
     * @param agentId the agent identifier
     * @return optional containing the agent if found
     * @deprecated Use {@link #lookup(String, String)} with an explicit tenantId.
     */
    @Deprecated
    public Optional<BaseAgent<?, ?>> lookup(String agentId) {
        return lookup(DEFAULT_TENANT, agentId);
    }

    // =========================================================================
    // Queries
    // =========================================================================

    /**
     * Get the underlying factory.
     *
     * @return the PlannerAgentFactory
     */
    public PlannerAgentFactory getFactory() {
        return factory;
    }

    /**
     * Get the number of agents registered for a specific tenant.
     *
     * @param tenantId the tenant identifier
     * @return agent count for this tenant
     */
    public int getAgentCount(String tenantId) {
        Map<String, BaseAgent<?, ?>> agents = tenantAgents.get(tenantId);
        return agents == null ? 0 : agents.size();
    }

    /**
     * Get the total number of agents across all tenants.
     *
     * @return total agent count
     */
    public int getAgentCount() {
        return tenantAgents.values().stream().mapToInt(Map::size).sum();
    }

    /**
     * Get all agents registered for a specific tenant.
     *
     * @param tenantId the tenant identifier
     * @return immutable copy of the agent map for this tenant; empty if no agents registered
     */
    public Map<String, BaseAgent<?, ?>> getAgents(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Map<String, BaseAgent<?, ?>> agents = tenantAgents.get(tenantId);
        return agents == null ? Map.of() : Map.copyOf(agents);
    }

    /**
     * Get all agents for the default tenant.
     *
     * @return immutable copy of the default-tenant agent map
     * @deprecated Use {@link #getAgents(String)} with an explicit tenantId.
     */
    @Deprecated
    public Map<String, BaseAgent<?, ?>> getAgents() {
        return getAgents(DEFAULT_TENANT);
    }
}
