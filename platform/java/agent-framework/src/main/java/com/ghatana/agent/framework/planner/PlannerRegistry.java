/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
 * Multi-tenant registry for {@link PlanningAgent} and GAA {@link BaseAgent} instances.
 *
 * <p>This is the canonical in-process registry for <em>planner-created</em> agents.
 * It is intentionally distinct from:
 * <ul>
 *   <li>{@link com.ghatana.agent.registry.AgentFrameworkRegistry} — the in-process
 *       {@link com.ghatana.agent.TypedAgent} lifecycle registry (discovery, init, shutdown)</li>
 *   <li>{@link com.ghatana.agent.registry.AgentRegistry} (in {@code platform:agent-registry})
 *       — the durable platform SPI backed by JDBC or in-memory for distributed use</li>
 * </ul>
 *
 * <p>Tenant isolation: agent IDs are scoped to a {@code tenantId}. An agent registered
 * for tenant A is never visible to tenant B.
 *
 * <p>All public methods accepting {@code agentId} also require a {@code tenantId}.
 * Single-argument overloads default to {@code "default"} for backward compatibility.
 *
 * @doc.type class
 * @doc.purpose Multi-tenant GAA/planner agent instance registry
 * @doc.layer framework
 * @doc.pattern Registry
 *
 * @author Ghatana AI Platform
 * @since 2.1.0
 */
public class PlannerRegistry {

    private static final Logger log = LoggerFactory.getLogger(PlannerRegistry.class);
    private static final String DEFAULT_TENANT = "default";

    private final PlannerAgentFactory factory;

    /**
     * Outer key: tenantId. Inner key: agentId. ConcurrentHashMap for thread safety on
     * the outer level; inner maps are guarded by the outer map's compute operations.
     */
    private final ConcurrentHashMap<String, Map<String, BaseAgent<?, ?>>> tenantAgents =
            new ConcurrentHashMap<>();

    /**
     * Creates a new PlannerRegistry backed by the given factory.
     *
     * @param factory the factory used to create planner agents; must not be null
     */
    public PlannerRegistry(PlannerAgentFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory must not be null");
    }

    // =========================================================================
    // Registration
    // =========================================================================

    /**
     * Registers an agent instance scoped to {@code tenantId}.
     *
     * @param tenantId the tenant identifier (never blank)
     * @param agentId  the unique agent identifier within the tenant
     * @param agent    the agent instance
     * @throws NullPointerException if any argument is null
     */
    public void register(String tenantId, String agentId, BaseAgent<?, ?> agent) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(agent, "agent must not be null");
        tenantAgents.computeIfAbsent(tenantId, t -> new HashMap<>()).put(agentId, agent);
        log.info("Registered planner agent: tenantId={} agentId={}", tenantId, agentId);
    }

    /**
     * Registers an agent under the default tenant.
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
     * Looks up an agent by tenant and agent ID.
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
     * Looks up an agent in the default tenant.
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

    /** Returns the underlying factory. */
    public PlannerAgentFactory getFactory() {
        return factory;
    }

    /**
     * Returns the number of agents registered for a specific tenant.
     *
     * @param tenantId the tenant identifier
     * @return agent count for this tenant
     */
    public int getAgentCount(String tenantId) {
        Map<String, BaseAgent<?, ?>> agents = tenantAgents.get(tenantId);
        return agents == null ? 0 : agents.size();
    }

    /**
     * Returns the total number of agents across all tenants.
     *
     * @return total agent count
     */
    public int getAgentCount() {
        return tenantAgents.values().stream().mapToInt(Map::size).sum();
    }

    /**
     * Returns all agents registered for a specific tenant.
     *
     * @param tenantId the tenant identifier
     * @return immutable copy of the tenant's agent map; empty if none registered
     */
    public Map<String, BaseAgent<?, ?>> getAgents(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Map<String, BaseAgent<?, ?>> agents = tenantAgents.get(tenantId);
        return agents == null ? Map.of() : Map.copyOf(agents);
    }

    /**
     * Returns all agents for the default tenant.
     *
     * @return immutable copy of the default-tenant agent map
     * @deprecated Use {@link #getAgents(String)} with an explicit tenantId.
     */
    @Deprecated
    public Map<String, BaseAgent<?, ?>> getAgents() {
        return getAgents(DEFAULT_TENANT);
    }
}
