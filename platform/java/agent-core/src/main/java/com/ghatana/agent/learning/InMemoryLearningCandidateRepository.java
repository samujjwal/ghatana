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
 * In-memory learning candidate repository for tests and local execution.
 */
public final class InMemoryLearningCandidateRepository implements LearningCandidateRepository {

    private final ConcurrentHashMap<String, LearningCandidate> candidates = new ConcurrentHashMap<>();

    @Override
    public @NotNull Promise<LearningCandidate> save(@NotNull LearningCandidate candidate) {
        candidates.put(candidate.candidateId(), candidate);
        return Promise.of(candidate);
    }

    @Override
    public @NotNull Promise<Optional<LearningCandidate>> findById(@NotNull String candidateId) {
        return Promise.of(Optional.ofNullable(candidates.get(candidateId)));
    }

    @Override
    public @NotNull Promise<List<LearningCandidate>> findByAgentRelease(@NotNull String agentReleaseId) {
        return Promise.of(candidates.values().stream()
                .filter(c -> agentReleaseId.equals(c.agentReleaseId()))
                .sorted(Comparator.comparing(LearningCandidate::createdAt).reversed())
                .toList());
    }

    @Override
    public @NotNull Promise<List<LearningCandidate>> findByState(@NotNull LearningCandidateState state) {
        return Promise.of(candidates.values().stream()
                .filter(c -> state == c.state())
                .sorted(Comparator.comparing(LearningCandidate::createdAt).reversed())
                .toList());
    }
}
