/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.spi;

import com.ghatana.agent.api.AgentConfig;
import com.ghatana.agent.api.TypedAgent;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Contract for agent registration and discovery.
 *
 * <p>Defines the contract for storing, resolving, and querying agent
 * registrations. Implementations handle persistence, caching, and
 * capability indexing.
 *
 * @doc.type interface
 * @doc.purpose Agent registry SPI for pluggable registration and discovery
 * @doc.layer platform
 * @doc.pattern SPI
 */
public interface AgentRegistry {

    /**
     * Registers an agent with its configuration.
     *
     * @param agent  the typed agent to register
     * @param config the agent configuration
     * @return a Promise completing when registration is persisted
     */
    Promise<Void> register(TypedAgent<?, ?> agent, AgentConfig config);

    /**
     * Deregisters an agent by ID.
     *
     * @param agentId the agent identifier
     * @return a Promise completing when deregistration is persisted
     */
    Promise<Void> deregister(String agentId);

    /**
     * Resolves a registered agent by ID.
     *
     * @param agentId the agent identifier
     * @return a Promise of an Optional containing the agent if found
     */
    <I, O> Promise<Optional<TypedAgent<I, O>>> resolve(String agentId);

    /**
     * Returns all registered agent IDs.
     */
    Promise<Set<String>> listAgentIds();

    /**
     * Searches for agents by capability.
     */
    Promise<List<String>> findByCapability(String capability);

    /**
     * Returns registry statistics.
     */
    Promise<Map<String, Object>> getStats();
}
