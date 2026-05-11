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
 * In-memory promotion evidence repository for tests and local execution.
 */
public final class InMemoryPromotionEvidenceRepository implements PromotionEvidenceRepository {

    private final ConcurrentHashMap<String, PromotionEvidence> evidence = new ConcurrentHashMap<>();

    @Override
    public @NotNull Promise<PromotionEvidence> save(@NotNull PromotionEvidence item) {
        evidence.put(item.evidenceId(), item);
        return Promise.of(item);
    }

    @Override
    public @NotNull Promise<Optional<PromotionEvidence>> findById(@NotNull String evidenceId) {
        return Promise.of(Optional.ofNullable(evidence.get(evidenceId)));
    }

    @Override
    public @NotNull Promise<List<PromotionEvidence>> findByCandidate(@NotNull String candidateId) {
        return Promise.of(evidence.values().stream()
                .filter(e -> candidateId.equals(e.candidateId()))
                .sorted(Comparator.comparing(PromotionEvidence::createdAt).reversed())
                .toList());
    }
}
