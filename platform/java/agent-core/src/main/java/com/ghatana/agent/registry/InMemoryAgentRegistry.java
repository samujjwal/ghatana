/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.registry;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.spi.AgentRegistry;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link AgentRegistry}.
 *
 * <p>Thread-safe: backed by {@link ConcurrentHashMap}. Suitable for testing and
 * single-instance deployments. For durable, cross-instance discovery use
 * {@code DataCloudAgentRegistry} or the AEP central registry.
 *
 * @doc.type class
 * @doc.purpose In-memory AgentRegistry SPI implementation for testing and standalone use
 * @doc.layer platform
 * @doc.pattern Registry
 */
public class InMemoryAgentRegistry implements AgentRegistry {

    private record Entry(TypedAgent<?, ?> agent, AgentConfig config) {}

    private final ConcurrentHashMap<String, Entry> agents = new ConcurrentHashMap<>();

    @Override
    public Promise<Void> register(TypedAgent<?, ?> agent, AgentConfig config) {
        String id = agent.descriptor().getAgentId();
        agents.put(id, new Entry(agent, config));
        return Promise.complete();
    }

    @Override
    public Promise<Void> deregister(String agentId) {
        agents.remove(agentId);
        return Promise.complete();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I, O> Promise<Optional<TypedAgent<I, O>>> resolve(String agentId) {
        Entry entry = agents.get(agentId);
        if (entry == null) {
            return Promise.of(Optional.empty());
        }
        return Promise.of(Optional.of((TypedAgent<I, O>) entry.agent()));
    }

    @Override
    public Promise<Set<String>> listAgentIds() {
        return Promise.of(Set.copyOf(agents.keySet()));
    }

    @Override
    public Promise<List<String>> findByCapability(String capability) {
        List<String> result = agents.values().stream()
                .filter(e -> {
                    AgentConfig cfg = e.config();
                    return cfg != null && cfg.getRequiredCapabilities() != null
                            && cfg.getRequiredCapabilities().contains(capability);
                })
                .map(e -> e.agent().descriptor().getAgentId())
                .collect(Collectors.toCollection(ArrayList::new));
        return Promise.of(result);
    }

    @Override
    public Promise<Map<String, Object>> getStats() {
        return Promise.of(Map.of("registered_count", (Object) agents.size()));
    }
}
