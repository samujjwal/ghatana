/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.release;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory implementation of {@link AgentReleaseRepository}.
 *
 * <p>Intended exclusively for unit tests and contract tests. Do not use in
 * production code.
 *
 * @doc.type class
 * @doc.purpose In-memory AgentReleaseRepository for testing
 * @doc.layer platform
 * @doc.pattern Repository
 */
public final class InMemoryAgentReleaseRepository implements AgentReleaseRepository {

    private final Map<String, AgentRelease> store = new ConcurrentHashMap<>();

    @Override
    public Promise<AgentRelease> save(AgentRelease release) {
        store.put(release.agentReleaseId(), release);
        return Promise.of(release);
    }

    @Override
    public Promise<Optional<AgentRelease>> findById(String agentReleaseId) {
        return Promise.of(Optional.ofNullable(store.get(agentReleaseId)));
    }

    @Override
    public Promise<List<AgentRelease>> findByAgentId(String agentId) {
        List<AgentRelease> result = store.values().stream()
                .filter(r -> agentId.equals(r.agentId()))
                .collect(Collectors.toList());
        return Promise.of(result);
    }

    @Override
    public Promise<Optional<AgentRelease>> findActiveRelease(String agentId, String tenantId) {
        // In-memory: finds ANY ACTIVE release for the agent (ignoring tenant isolation for test simplicity)
        Optional<AgentRelease> found = store.values().stream()
                .filter(r -> agentId.equals(r.agentId()) && r.state() == AgentReleaseState.ACTIVE)
                .findFirst();
        return Promise.of(found);
    }

    @Override
    public Promise<AgentRelease> transition(String agentReleaseId, AgentReleaseState targetState, String principalId) {
        AgentRelease existing = store.get(agentReleaseId);
        if (existing == null) {
            return Promise.ofException(new IllegalStateException(
                    "AgentRelease not found: " + agentReleaseId));
        }
        AgentRelease updated = existing.withState(targetState, Instant.now());
        store.put(agentReleaseId, updated);
        return Promise.of(updated);
    }

    @Override
    public Promise<List<AgentRelease>> findByState(AgentReleaseState state) {
        List<AgentRelease> result = store.values().stream()
                .filter(r -> r.state() == state)
                .collect(Collectors.toList());
        return Promise.of(result);
    }

    @Override
    public Promise<Optional<AgentRelease>> findGoverningRelease(String agentId, String tenantId) {
        // Governing states: live dispatching + emergency block
        java.util.Set<AgentReleaseState> governingStates = java.util.Set.of(
                AgentReleaseState.ACTIVE, AgentReleaseState.CANARY,
                AgentReleaseState.SHADOW, AgentReleaseState.BLOCKED);
        Optional<AgentRelease> found = store.values().stream()
                .filter(r -> agentId.equals(r.agentId()) && governingStates.contains(r.state()))
                .max(java.util.Comparator.comparing(AgentRelease::updatedAt));
        return Promise.of(found);
    }

    /**
     * Clears all stored releases. Useful for test isolation in {@code @BeforeEach}.
     */
    public void clear() {
        store.clear();
    }

    /**
     * Returns the number of stored releases.
     *
     * @return size of the in-memory store
     */
    public int size() {
        return store.size();
    }
}
