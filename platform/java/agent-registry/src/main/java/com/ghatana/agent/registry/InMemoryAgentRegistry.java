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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Lightweight, in-process implementation of {@link AgentRegistry} backed by
 * {@link ConcurrentHashMap}s.
 *
 * <p>All operations complete synchronously (wrapped in {@link Promise#of})
 * and are therefore safe to call from within an ActiveJ eventloop without
 * {@code Promise.ofBlocking}. This makes the class ideal for:
 * <ul>
 *   <li>Unit and integration tests that need a real registry without I/O</li>
 *   <li>Single-node development deployments where durability is not required</li>
 *   <li>AEP workers that manage a small, fixed set of operator agents</li>
 * </ul>
 *
 * <p>Registered agents survive only for the lifetime of the JVM. For a
 * persistent, multi-instance registry use
 * {@code com.ghatana.datacloud.agent.registry.DataCloudAgentRegistry}.
 *
 * @doc.type class
 * @doc.purpose In-memory agent registry for testing and single-node deployments
 * @doc.layer registry
 * @doc.pattern Repository
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public final class InMemoryAgentRegistry implements AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAgentRegistry.class);

    private final ConcurrentHashMap<String, TypedAgent<?, ?>> agents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentConfig> configs = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // AgentRegistry implementation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Registers the agent in the in-memory map. If an agent with the same
     * ID is already present it is silently replaced.
     */
    @NotNull
    @Override
    public Promise<Void> register(@NotNull TypedAgent<?, ?> agent,
                                  @NotNull AgentConfig config) {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(config, "config");

        String agentId = agent.descriptor().getAgentId();
        agents.put(agentId, agent);
        configs.put(agentId, config);
        log.debug("Registered agent [{}] in InMemoryAgentRegistry", agentId);
        return Promise.complete();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Silently succeeds if the agent ID is not registered.
     */
    @NotNull
    @Override
    public Promise<Void> deregister(@NotNull String agentId) {
        Objects.requireNonNull(agentId, "agentId");
        agents.remove(agentId);
        configs.remove(agentId);
        log.debug("Deregistered agent [{}] from InMemoryAgentRegistry", agentId);
        return Promise.complete();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Performs an unchecked cast — the caller is responsible for providing
     * the correct type parameters consistent with how the agent was registered.
     */
    @NotNull
    @Override
    @SuppressWarnings("unchecked")
    public <I, O> Promise<Optional<TypedAgent<I, O>>> resolve(@NotNull String agentId) {
        Objects.requireNonNull(agentId, "agentId");
        TypedAgent<I, O> agent = (TypedAgent<I, O>) agents.get(agentId);
        return Promise.of(Optional.ofNullable(agent));
    }

    /** {@inheritDoc} */
    @NotNull
    @Override
    public Promise<Set<String>> listAgentIds() {
        return Promise.of(Set.copyOf(agents.keySet()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Filters the in-memory registry by calling
     * {@link com.ghatana.agent.AgentDescriptor#hasCapability(String)} on each
     * registered agent's descriptor.
     */
    @NotNull
    @Override
    public Promise<List<String>> findByCapability(@NotNull String capability) {
        Objects.requireNonNull(capability, "capability");
        List<String> matched = agents.entrySet().stream()
                .filter(e -> e.getValue().descriptor().hasCapability(capability))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        return Promise.of(matched);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns a snapshot of in-memory registry statistics.
     */
    @NotNull
    @Override
    public Promise<Map<String, Object>> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("registeredAgents", agents.size());
        stats.put("registryType", "InMemory");
        return Promise.of(Collections.unmodifiableMap(stats));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Additional convenience (not in SPI, used by tests)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the {@link AgentConfig} that was registered alongside the given
     * agent, or {@link Optional#empty()} if the agent is not registered.
     *
     * @param agentId the agent ID
     * @return optional config
     */
    public Optional<AgentConfig> getConfig(String agentId) {
        return Optional.ofNullable(configs.get(agentId));
    }

    /** Removes all registrations. Intended for test teardown. */
    public void clear() {
        agents.clear();
        configs.clear();
    }
}
