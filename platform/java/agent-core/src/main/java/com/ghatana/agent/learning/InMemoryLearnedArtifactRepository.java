/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @doc.type class
 * @doc.purpose In-memory learned artifact repository for tests and local execution
 * @doc.layer agent-core
 * @doc.pattern Repository
 */
/**
 * In-memory learned artifact repository for tests and local execution.
 */
public final class InMemoryLearnedArtifactRepository implements LearnedArtifactRepository {

    private final ConcurrentHashMap<String, LearnedArtifact> artifacts = new ConcurrentHashMap<>();

    @Override
    public @NotNull Promise<LearnedArtifact> save(@NotNull LearnedArtifact artifact) {
        artifacts.put(artifact.artifactId(), artifact);
        return Promise.of(artifact);
    }

    @Override
    public @NotNull Promise<Optional<LearnedArtifact>> findById(@NotNull String artifactId) {
        return Promise.of(Optional.ofNullable(artifacts.get(artifactId)));
    }

    @Override
    public @NotNull Promise<List<LearnedArtifact>> findByAgent(@NotNull String agentId) {
        return Promise.of(artifacts.values().stream()
                .filter(a -> agentId.equals(a.agentId()))
                .sorted(Comparator.comparing(LearnedArtifact::createdAt).reversed())
                .toList());
    }

    @Override
    public @NotNull Promise<List<LearnedArtifact>> findActiveByAgentAndTarget(
            @NotNull String agentId,
            @NotNull LearningTarget target) {
        return Promise.of(artifacts.values().stream()
                .filter(a -> agentId.equals(a.agentId()))
                .filter(a -> target == a.target())
                .filter(a -> PromotionState.ACTIVE == a.state())
                .sorted(Comparator.comparing(LearnedArtifact::createdAt).reversed())
                .toList());
    }

    @Override
    public @NotNull Promise<List<LearnedArtifact>> findByCandidateId(@NotNull String candidateId) {
        // Note: LearnedArtifact doesn't have a candidateId field directly
        // This method returns empty list since artifacts are not linked to candidates in the current schema
        // For proper idempotency, the artifact schema should include candidateId or promotionEvidenceId should be used
        return Promise.of(List.of());
    }
}
