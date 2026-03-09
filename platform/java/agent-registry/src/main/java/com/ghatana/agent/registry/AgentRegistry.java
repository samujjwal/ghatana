/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.registry;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.TypedAgent;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Service Provider Interface for agent registration and discovery.
 *
 * <p>Defines the contract for storing, resolving, and querying agent
 * registrations. Implementations are responsible for persistence,
 * caching, and capability indexing.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li><b>Registration</b>: Store agent descriptors and configs</li>
 *   <li><b>Deregistration</b>: Remove agents from the registry</li>
 *   <li><b>Discovery</b>: Resolve agents by ID or capability</li>
 *   <li><b>Statistics</b>: Expose registry health and population info</li>
 * </ul>
 *
 * <h2>Implementations</h2>
 * <ul>
 *   <li>{@code DataCloudAgentRegistry} — Data-Cloud-backed with write-through cache</li>
 * </ul>
 *
 * <h2>Storage Constraint</h2>
 * <p>Per the Agentic Framework Hardening Plan, agent registry storage
 * is <b>exclusively</b> backed by Data-Cloud. No separate registry
 * database or storage module should be created.</p>
 *
 * @doc.type interface
 * @doc.purpose Agent registry SPI for pluggable registration and discovery
 * @doc.layer registry
 * @doc.pattern SPI, Repository
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public interface AgentRegistry {

    /**
     * Registers an agent with its descriptor and configuration.
     *
     * <p>The agent's {@link com.ghatana.agent.AgentDescriptor} is persisted
     * along with its configuration. Capability indices are updated for
     * discovery queries.
     *
     * @param agent  the typed agent to register
     * @param config the agent configuration
     * @return a Promise completing when registration is persisted
     */
    @NotNull
    Promise<Void> register(@NotNull TypedAgent<?, ?> agent,
                           @NotNull AgentConfig config);

    /**
     * Deregisters an agent by ID.
     *
     * @param agentId the agent identifier
     * @return a Promise completing when deregistration is persisted
     */
    @NotNull
    Promise<Void> deregister(@NotNull String agentId);

    /**
     * Resolves a registered agent by ID.
     *
     * @param agentId the agent identifier
     * @param <I>     input type
     * @param <O>     output type
     * @return a Promise of an Optional containing the agent if found
     */
    @NotNull
    <I, O> Promise<Optional<TypedAgent<I, O>>> resolve(@NotNull String agentId);

    /**
     * Returns all registered agent IDs.
     *
     * @return a Promise of the set of registered agent IDs
     */
    @NotNull
    Promise<Set<String>> listAgentIds();

    /**
     * Searches for agents by capability.
     *
     * @param capability the capability to search for
     * @return a Promise of agent IDs that have the given capability
     */
    @NotNull
    Promise<List<String>> findByCapability(@NotNull String capability);

    /**
     * Returns registry statistics (total registered, cache size, etc.).
     *
     * @return a Promise of key-value statistics
     */
    @NotNull
    Promise<Map<String, Object>> getStats();
}
