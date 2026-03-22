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

import com.ghatana.agent.*;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * In-process registry for {@link TypedAgent} instances within the agent-framework.
 *
 * <p>Provides registration, discovery, lifecycle management (initialize / shutdown /
 * health-check), hot-reload, and metadata access for typed agents running inside
 * a single JVM process.
 *
 * <p><b>Relationship to platform AgentRegistry SPI</b><br>
 * {@code com.ghatana.agent.registry.AgentRegistry} (platform agent-registry module)
 * is the persistent, distributed SPI backed by Data-Cloud. This interface is the
 * lightweight, in-process counterpart used by the agent-framework runtime and
 * operator pipeline. Implementations include {@link InMemoryAgentFrameworkRegistry}
 * for tests and single-node deployments.
 *
 * @since 2.0.0
 *
 * @doc.type interface
 * @doc.purpose In-process typed-agent registry for the agent-framework runtime
 * @doc.layer platform
 * @doc.pattern Registry
 */
public interface AgentFrameworkRegistry {

    // ═══════════════════════════════════════════════════════════════════════════
    // Registration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Registers a typed agent with the given configuration.
     *
     * @param agent  the agent to register
     * @param config the agent's configuration
     * @return a Promise completing when registration is done
     * @throws IllegalArgumentException if an agent with the same ID is already registered
     */
    @NotNull
    Promise<Void> register(@NotNull TypedAgent<?, ?> agent, @NotNull AgentConfig config);

    /**
     * Unregisters and shuts down the agent with the given ID.
     *
     * @param agentId the agent ID to unregister
     * @return a Promise completing when the agent is shut down and removed
     */
    @NotNull
    Promise<Void> unregister(@NotNull String agentId);

    // ═══════════════════════════════════════════════════════════════════════════
    // Discovery
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Resolves an agent by ID.
     *
     * @param agentId the agent ID
     * @return the agent, or a failed Promise if not found
     */
    @NotNull
    <I, O> Promise<TypedAgent<I, O>> resolve(@NotNull String agentId);

    /**
     * Finds all registered agents of a given type.
     */
    @NotNull
    Promise<List<AgentDescriptor>> findByType(@NotNull AgentType type);

    /**
     * Finds all registered agents that have a given capability.
     */
    @NotNull
    Promise<List<AgentDescriptor>> findByCapability(@NotNull String capability);

    /**
     * Finds all {@link AgentType#CUSTOM CUSTOM}-typed agents whose subtype
     * matches the given custom type name (case-insensitive).
     *
     * @param customTypeName the custom type name (e.g., "RAG_RETRIEVER")
     * @return list of matching agent descriptors
     */
    @NotNull
    Promise<List<AgentDescriptor>> findByCustomType(@NotNull String customTypeName);

    /**
     * Lists descriptors of all registered agents.
     */
    @NotNull
    Promise<List<AgentDescriptor>> listAll();

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Initializes an already-registered agent.
     */
    @NotNull
    Promise<Void> initialize(@NotNull String agentId);

    /**
     * Shuts down an agent (without unregistering).
     */
    @NotNull
    Promise<Void> shutdown(@NotNull String agentId);

    /**
     * Checks the health of an agent.
     */
    @NotNull
    Promise<HealthStatus> healthCheck(@NotNull String agentId);

    /**
     * Initializes all registered agents.
     */
    @NotNull
    Promise<Void> initializeAll();

    /**
     * Shuts down all registered agents.
     */
    @NotNull
    Promise<Void> shutdownAll();

    // ═══════════════════════════════════════════════════════════════════════════
    // Hot-Reload
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Reconfigures an agent with a new configuration.
     * The agent is re-initialized with the new config.
     *
     * @param agentId   the agent ID
     * @param newConfig the new configuration
     * @return a Promise completing when reconfiguration is done
     */
    @NotNull
    Promise<Void> reload(@NotNull String agentId, @NotNull AgentConfig newConfig);

    // ═══════════════════════════════════════════════════════════════════════════
    // Metadata
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the number of registered agents.
     */
    int size();

    /**
     * Checks if an agent with the given ID is registered.
     */
    boolean contains(@NotNull String agentId);
}
