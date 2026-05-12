/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.mastery;

import com.ghatana.agent.mastery.MasteryEvidence;
import com.ghatana.agent.mastery.MasteryEvidenceRepository;
import com.ghatana.agent.mastery.MasteryEvidenceType;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Data Cloud-backed implementation of MasteryEvidenceRepository.
 *
 * <p>TODO: Replace in-memory storage with actual Data Cloud persistence.
 *
 * @doc.type class
 * @doc.purpose Data Cloud implementation of MasteryEvidenceRepository
 * @doc.layer data-cloud
 * @doc.pattern Repository
 */
public final class DataCloudMasteryEvidenceRepository implements MasteryEvidenceRepository {

    private final ConcurrentHashMap<String, MasteryEvidence> evidenceStore = new ConcurrentHashMap<>();

    @Override
    @NotNull
    public Promise<MasteryEvidence> save(@NotNull MasteryEvidence evidence) {
        evidenceStore.put(evidence.evidenceId(), evidence);
        return Promise.of(evidence);
    }

    @Override
    @NotNull
    public Promise<Optional<MasteryEvidence>> findById(@NotNull String evidenceId) {
        return Promise.of(Optional.ofNullable(evidenceStore.get(evidenceId)));
    }

    @Override
    @NotNull
    public Promise<List<MasteryEvidence>> findByMasteryId(@NotNull String masteryId) {
        // For now, return empty since evidence doesn't directly reference masteryId
        // In a real implementation, evidence would be linked to mastery items through transitions
        return Promise.of(List.of());
    }

    @Override
    @NotNull
    public Promise<List<MasteryEvidence>> findByType(@NotNull MasteryEvidenceType type) {
        return Promise.of(evidenceStore.values().stream()
                .filter(e -> e.type() == type)
                .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<List<MasteryEvidence>> findByRef(@NotNull String ref) {
        return Promise.of(evidenceStore.values().stream()
                .filter(e -> e.ref().equals(ref))
                .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<List<MasteryEvidence>> findByCreatedBy(@NotNull String createdBy) {
        return Promise.of(evidenceStore.values().stream()
                .filter(e -> e.createdBy().equals(createdBy))
                .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<Void> deleteById(@NotNull String evidenceId) {
        evidenceStore.remove(evidenceId);
        return Promise.complete();
    }
}
