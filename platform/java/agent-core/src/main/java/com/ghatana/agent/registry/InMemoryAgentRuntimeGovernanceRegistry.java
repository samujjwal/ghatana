/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.registry;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link AgentRuntimeGovernanceRegistry}.
 *
 * <p>Stores governance state in memory for development and testing.
 * Production deployments should use a persistent implementation backed by Data Cloud.
 *
 * @doc.type class
 * @doc.purpose In-memory agent runtime governance registry
 * @doc.layer agent-runtime
 * @doc.pattern Registry, In-Memory Store
 */
public final class InMemoryAgentRuntimeGovernanceRegistry implements AgentRuntimeGovernanceRegistry {

    private final ConcurrentHashMap<String, String> previousVersionsByAgent = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> fallbackAgentsByAgent = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<GovernanceDecisionRecord>> decisionsByAgent = new ConcurrentHashMap<>();

    @Override
    @NotNull
    public Promise<Void> recordDecision(
            @NotNull String agentId,
            @NotNull String tenantId,
            @NotNull GovernanceDecision decision,
            @NotNull String reason,
            @NotNull Map<String, String> metadata) {
        String key = agentKey(agentId, tenantId);
        GovernanceDecisionRecord record = new GovernanceDecisionRecord(
                agentId, tenantId, decision, reason, metadata, Instant.now());
        decisionsByAgent.compute(key, (k, records) -> {
            List<GovernanceDecisionRecord> newRecords = records != null ? new ArrayList<>(records) : new ArrayList<>();
            newRecords.add(record);
            // Keep only last 100 decisions per agent
            if (newRecords.size() > 100) {
                newRecords = newRecords.subList(newRecords.size() - 100, newRecords.size());
            }
            return newRecords;
        });
        return Promise.complete();
    }

    @Override
    @NotNull
    public Promise<Void> recordRollback(
            @NotNull String agentId,
            @NotNull String tenantId,
            @NotNull String fromVersion,
            @NotNull String toVersion,
            @NotNull String reason) {
        String key = agentKey(agentId, tenantId);
        previousVersionsByAgent.put(key, toVersion);
        return Promise.complete();
    }

    @Override
    @NotNull
    public Promise<Optional<String>> getPreviousVersion(@NotNull String agentId, @NotNull String tenantId) {
        String key = agentKey(agentId, tenantId);
        return Promise.of(Optional.ofNullable(previousVersionsByAgent.get(key)));
    }

    @Override
    @NotNull
    public Promise<Void> recordFallback(
            @NotNull String agentId,
            @NotNull String tenantId,
            @NotNull String fallbackAgentId,
            @NotNull String reason) {
        String key = agentKey(agentId, tenantId);
        fallbackAgentsByAgent.put(key, fallbackAgentId);
        return Promise.of(null);
    }

    @Override
    @NotNull
    public Promise<Optional<String>> getFallbackAgent(@NotNull String agentId, @NotNull String tenantId) {
        String key = agentKey(agentId, tenantId);
        return Promise.of(Optional.ofNullable(fallbackAgentsByAgent.get(key)));
    }

    @Override
    @NotNull
    public Promise<List<GovernanceDecisionRecord>> queryDecisions(
            @NotNull String agentId,
            @NotNull String tenantId,
            @Nullable Instant from,
            @Nullable Instant to,
            int limit) {
        String key = agentKey(agentId, tenantId);
        List<GovernanceDecisionRecord> records = decisionsByAgent.getOrDefault(key, List.of());
        List<GovernanceDecisionRecord> filtered = records.stream()
                .filter(record -> from == null || !record.timestamp().isBefore(from))
                .filter(record -> to == null || !record.timestamp().isBefore(to))
                .limit(Math.max(0, limit))
                .toList();
        return Promise.of(filtered);
    }

    private static String agentKey(String agentId, String tenantId) {
        return tenantId + ":" + agentId;
    }
}
