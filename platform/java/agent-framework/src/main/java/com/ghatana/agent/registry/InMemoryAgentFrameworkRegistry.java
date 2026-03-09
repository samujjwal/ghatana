/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.registry;

import com.ghatana.agent.*;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link AgentFrameworkRegistry}.
 *
 * <p>Thread-safe: backed by {@link ConcurrentHashMap}.
 *
 * @since 2.0.0
 *
 * @doc.type class
 * @doc.purpose In-memory implementation of the in-process agent framework registry
 * @doc.layer platform
 * @doc.pattern Registry
 */
public class InMemoryAgentFrameworkRegistry implements AgentFrameworkRegistry {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAgentFrameworkRegistry.class);

    private record Entry(TypedAgent<?, ?> agent, AgentConfig config) {}

    private final ConcurrentHashMap<String, Entry> agents = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // Registration
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @NotNull
    public Promise<Void> register(@NotNull TypedAgent<?, ?> agent, @NotNull AgentConfig config) {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(config, "config");

        String id = agent.descriptor().getAgentId();
        Entry prev = agents.putIfAbsent(id, new Entry(agent, config));
        if (prev != null) {
            return Promise.ofException(new IllegalArgumentException(
                    "Agent already registered: " + id));
        }

        log.info("Registered agent: {} type={}", id, agent.descriptor().getType());
        return Promise.complete();
    }

    @Override
    @NotNull
    public Promise<Void> unregister(@NotNull String agentId) {
        Entry entry = agents.remove(agentId);
        if (entry == null) {
            return Promise.ofException(new NoSuchElementException("Agent not found: " + agentId));
        }

        log.info("Unregistering agent: {}", agentId);
        return entry.agent().shutdown()
                .whenResult($ -> log.info("Agent unregistered: {}", agentId));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Discovery
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @NotNull
    @SuppressWarnings("unchecked")
    public <I, O> Promise<TypedAgent<I, O>> resolve(@NotNull String agentId) {
        Entry entry = agents.get(agentId);
        if (entry == null) {
            return Promise.ofException(new NoSuchElementException("Agent not found: " + agentId));
        }
        return Promise.of((TypedAgent<I, O>) entry.agent());
    }

    @Override
    @NotNull
    public Promise<List<AgentDescriptor>> findByType(@NotNull AgentType type) {
        List<AgentDescriptor> result = agents.values().stream()
                .map(e -> e.agent().descriptor())
                .filter(d -> d.getType() == type)
                .collect(Collectors.toList());
        return Promise.of(result);
    }

    @Override
    @NotNull
    public Promise<List<AgentDescriptor>> findByCapability(@NotNull String capability) {
        List<AgentDescriptor> result = agents.values().stream()
                .map(e -> e.agent().descriptor())
                .filter(d -> d.hasCapability(capability))
                .collect(Collectors.toList());
        return Promise.of(result);
    }

    @Override
    @NotNull
    public Promise<List<AgentDescriptor>> findByCustomType(@NotNull String customTypeName) {
        String normalized = customTypeName.trim().toUpperCase();
        List<AgentDescriptor> result = agents.values().stream()
                .map(e -> e.agent().descriptor())
                .filter(d -> d.getType() == AgentType.CUSTOM)
                .filter(d -> normalized.equals(
                        d.getSubtype() != null ? d.getSubtype().toUpperCase() : null))
                .collect(Collectors.toList());
        return Promise.of(result);
    }

    @Override
    @NotNull
    public Promise<List<AgentDescriptor>> listAll() {
        List<AgentDescriptor> result = agents.values().stream()
                .map(e -> e.agent().descriptor())
                .collect(Collectors.toList());
        return Promise.of(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @NotNull
    public Promise<Void> initialize(@NotNull String agentId) {
        Entry entry = agents.get(agentId);
        if (entry == null) {
            return Promise.ofException(new NoSuchElementException("Agent not found: " + agentId));
        }
        return entry.agent().initialize(entry.config());
    }

    @Override
    @NotNull
    public Promise<Void> shutdown(@NotNull String agentId) {
        Entry entry = agents.get(agentId);
        if (entry == null) {
            return Promise.ofException(new NoSuchElementException("Agent not found: " + agentId));
        }
        return entry.agent().shutdown();
    }

    @Override
    @NotNull
    public Promise<HealthStatus> healthCheck(@NotNull String agentId) {
        Entry entry = agents.get(agentId);
        if (entry == null) {
            return Promise.of(HealthStatus.UNKNOWN);
        }
        return entry.agent().healthCheck();
    }

    @Override
    @NotNull
    public Promise<Void> initializeAll() {
        // Chain sequentially to avoid Promises.toList() NPE with Void results
        Promise<Void> chain = Promise.complete();
        for (Entry e : agents.values()) {
            chain = chain.then(() -> e.agent().initialize(e.config()));
        }
        return chain;
    }

    @Override
    @NotNull
    public Promise<Void> shutdownAll() {
        // Chain sequentially to avoid Promises.toList() NPE with Void results
        Promise<Void> chain = Promise.complete();
        for (Entry e : agents.values()) {
            chain = chain.then(() -> e.agent().shutdown());
        }
        return chain;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Hot-Reload
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @NotNull
    public Promise<Void> reload(@NotNull String agentId, @NotNull AgentConfig newConfig) {
        Entry entry = agents.get(agentId);
        if (entry == null) {
            return Promise.ofException(new NoSuchElementException("Agent not found: " + agentId));
        }

        // Update stored config
        agents.put(agentId, new Entry(entry.agent(), newConfig));

        // Re-initialize with new config
        log.info("Hot-reloading agent: {}", agentId);
        return entry.agent().reconfigure(newConfig)
                .whenResult($ -> log.info("Agent reloaded: {}", agentId));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Metadata
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public int size() { return agents.size(); }

    @Override
    public boolean contains(@NotNull String agentId) { return agents.containsKey(agentId); }
}
