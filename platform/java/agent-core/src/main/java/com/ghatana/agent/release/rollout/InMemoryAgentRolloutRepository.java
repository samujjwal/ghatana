/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.release.rollout;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link AgentRolloutRepository} for use in tests.
 *
 * <p>Uses a {@link ConcurrentHashMap} for thread-safe storage, but does <em>not</em>
 * provide transactional guarantees. Suitable only for unit and contract tests.
 *
 * @doc.type class
 * @doc.purpose In-memory test double for AgentRolloutRepository
 * @doc.layer platform
 * @doc.pattern TestDouble
 */
public class InMemoryAgentRolloutRepository implements AgentRolloutRepository {

    private final ConcurrentHashMap<String, AgentRolloutRecord> store = new ConcurrentHashMap<>();

    @Override
    public Promise<AgentRolloutRecord> save(AgentRolloutRecord record) {
        store.put(record.rolloutId(), record);
        return Promise.of(record);
    }

    @Override
    public Promise<Optional<AgentRolloutRecord>> findById(String rolloutId) {
        return Promise.of(Optional.ofNullable(store.get(rolloutId)));
    }

    @Override
    public Promise<List<AgentRolloutRecord>> findByReleaseId(String agentReleaseId) {
        List<AgentRolloutRecord> result = store.values().stream()
                .filter(r -> agentReleaseId.equals(r.agentReleaseId()))
                .collect(Collectors.toList());
        return Promise.of(result);
    }

    @Override
    public Promise<List<AgentRolloutRecord>> findByTenantAndEnvironment(String tenantId, String targetEnvironment) {
        List<AgentRolloutRecord> result = store.values().stream()
                .filter(r -> tenantId.equals(r.tenantId()) && targetEnvironment.equals(r.targetEnvironment()))
                .collect(Collectors.toList());
        return Promise.of(result);
    }

    @Override
    public Promise<AgentRolloutRecord> approve(String rolloutId, String approvedBy) {
        AgentRolloutRecord existing = store.get(rolloutId);
        if (existing == null) {
            return Promise.ofException(new IllegalStateException("RolloutRecord not found: " + rolloutId));
        }
        if (!existing.approvalState().isPending()) {
            return Promise.ofException(new IllegalStateException(
                    "Cannot approve rollout in state " + existing.approvalState()
                    + "; must be PENDING. rolloutId=" + rolloutId));
        }
        AgentRolloutRecord updated = existing.withApproved(approvedBy, Instant.now());
        store.put(rolloutId, updated);
        return Promise.of(updated);
    }

    @Override
    public Promise<AgentRolloutRecord> reject(String rolloutId, String rejectedBy, String reason) {
        AgentRolloutRecord existing = store.get(rolloutId);
        if (existing == null) {
            return Promise.ofException(new IllegalStateException("RolloutRecord not found: " + rolloutId));
        }
        if (!existing.approvalState().isPending()) {
            return Promise.ofException(new IllegalStateException(
                    "Cannot reject rollout in state " + existing.approvalState()
                    + "; must be PENDING. rolloutId=" + rolloutId));
        }
        AgentRolloutRecord updated = existing.withRejected(rejectedBy, reason, Instant.now());
        store.put(rolloutId, updated);
        return Promise.of(updated);
    }

    @Override
    public Promise<AgentRolloutRecord> rollback(String rolloutId, String rolledBackBy) {
        AgentRolloutRecord existing = store.get(rolloutId);
        if (existing == null) {
            return Promise.ofException(new IllegalStateException("RolloutRecord not found: " + rolloutId));
        }
        if (existing.approvalState() != AgentRolloutApprovalState.APPROVED) {
            return Promise.ofException(new IllegalStateException(
                    "Cannot rollback rollout in state " + existing.approvalState()
                    + "; must be APPROVED. rolloutId=" + rolloutId));
        }
        AgentRolloutRecord updated = existing.withRolledBack(rolledBackBy, Instant.now());
        store.put(rolloutId, updated);
        return Promise.of(updated);
    }

    /**
     * Clears all stored records. For test isolation in {@code @BeforeEach}.
     */
    public void clear() {
        store.clear();
    }

    /**
     * Returns the number of stored records.
     */
    public int size() {
        return store.size();
    }
}
