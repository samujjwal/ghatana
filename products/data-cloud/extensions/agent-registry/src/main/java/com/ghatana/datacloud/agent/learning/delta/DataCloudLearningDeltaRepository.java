/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.learning.delta;

import com.ghatana.agent.learning.delta.LearningDelta;
import com.ghatana.agent.learning.delta.LearningDeltaRepository;
import com.ghatana.agent.learning.delta.LearningDeltaState;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data Cloud-backed implementation of LearningDeltaRepository.
 *
 * <p>TODO: Replace in-memory storage with actual Data Cloud persistence.
 *
 * @doc.type class
 * @doc.purpose Data Cloud implementation of LearningDeltaRepository
 * @doc.layer data-cloud
 * @doc.pattern Repository
 */
public final class DataCloudLearningDeltaRepository implements LearningDeltaRepository {

    private final ConcurrentHashMap<String, LearningDelta> deltas = new ConcurrentHashMap<>();

    @Override
    @NotNull
    public Promise<LearningDelta> save(@NotNull LearningDelta delta) {
        deltas.put(delta.deltaId(), delta);
        return Promise.of(delta);
    }

    @Override
    @NotNull
    public Promise<Optional<LearningDelta>> findById(@NotNull String deltaId) {
        return Promise.of(Optional.ofNullable(deltas.get(deltaId)));
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findPending(@NotNull String agentId) {
        return Promise.of(deltas.values().stream()
                .filter(d -> d.agentId().equals(agentId))
                .filter(d -> d.state() == LearningDeltaState.PROPOSED || d.state() == LearningDeltaState.VALIDATING)
                .toList());
    }

    @Override
    @NotNull
    public Promise<LearningDelta> transition(@NotNull String deltaId, @NotNull LearningDeltaState state) {
        LearningDelta delta = deltas.get(deltaId);
        if (delta == null) {
            return Promise.of(null);
        }

        LearningDelta updated = new LearningDelta(
                delta.deltaId(),
                delta.agentId(),
                delta.agentReleaseId(),
                delta.target(),
                delta.changeType(),
                state,
                delta.targetId(),
                delta.proposedArtifactRef(),
                delta.sourceEpisodeIds(),
                delta.evidenceRefs(),
                delta.evaluationRefs(),
                delta.rollbackRef(),
                delta.confidenceBefore(),
                delta.confidenceAfter(),
                delta.requiresHumanReview(),
                delta.createdAt(),
                delta.labels()
        );

        deltas.put(deltaId, updated);
        return Promise.of(updated);
    }
}
