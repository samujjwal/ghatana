/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.learning.delta;

import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.learning.LearningDeltaRepository;
import com.ghatana.agent.learning.LearningDeltaState;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
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
    public Promise<List<LearningDelta>> findByAgentId(@NotNull String agentId) {
        return Promise.of(deltas.values().stream()
                .filter(d -> d.agentId().equals(agentId))
                .toList());
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findBySkillId(@NotNull String skillId) {
        return Promise.of(deltas.values().stream()
                .filter(d -> d.skillId().equals(skillId))
                .toList());
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findByState(@NotNull LearningDeltaState state) {
        return Promise.of(deltas.values().stream()
                .filter(d -> d.state() == state)
                .toList());
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findPendingEvaluation() {
        return Promise.of(deltas.values().stream()
                .filter(d -> d.state() == LearningDeltaState.PROPOSED || d.state() == LearningDeltaState.PENDING_EVALUATION)
                .toList());
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findPromotable() {
        return Promise.of(deltas.values().stream()
                .filter(LearningDelta::isPromotable)
                .toList());
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findObsolete(@NotNull Instant before) {
        return Promise.of(deltas.values().stream()
                .filter(d -> d.proposedAt().isBefore(before))
                .filter(d -> d.state() == LearningDeltaState.OBSOLETE)
                .toList());
    }

    @Override
    @NotNull
    public Promise<LearningDelta> updateState(@NotNull String deltaId, @NotNull LearningDeltaState newState) {
        LearningDelta delta = deltas.get(deltaId);
        if (delta == null) {
            return Promise.of(null);
        }

        Instant now = Instant.now();
        LearningDelta updated = new LearningDelta(
                delta.deltaId(),
                delta.type(),
                delta.target(),
                newState,
                delta.agentId(),
                delta.agentReleaseId(),
                delta.skillId(),
                delta.procedureId(),
                delta.semanticFactId(),
                delta.negativeKnowledgeId(),
                delta.contentDigest(),
                delta.proposedContent(),
                delta.evidenceRefs(),
                delta.proposedBy(),
                delta.proposedAt(),
                newState == LearningDeltaState.EVALUATED ? now : delta.evaluatedAt(),
                newState == LearningDeltaState.PROMOTED ? now : delta.promotedAt(),
                newState == LearningDeltaState.REJECTED ? now : delta.rejectedAt(),
                delta.labels(),
                delta.rejectionReason()
        );

        deltas.put(deltaId, updated);
        return Promise.of(updated);
    }

    @Override
    @NotNull
    public Promise<LearningDelta> updateState(@NotNull String deltaId, @NotNull LearningDeltaState newState, @NotNull String rejectionReason) {
        LearningDelta delta = deltas.get(deltaId);
        if (delta == null) {
            return Promise.of(null);
        }

        Instant now = Instant.now();
        LearningDelta updated = new LearningDelta(
                delta.deltaId(),
                delta.type(),
                delta.target(),
                newState,
                delta.agentId(),
                delta.agentReleaseId(),
                delta.skillId(),
                delta.procedureId(),
                delta.semanticFactId(),
                delta.negativeKnowledgeId(),
                delta.contentDigest(),
                delta.proposedContent(),
                delta.evidenceRefs(),
                delta.proposedBy(),
                delta.proposedAt(),
                newState == LearningDeltaState.EVALUATED ? now : delta.evaluatedAt(),
                newState == LearningDeltaState.PROMOTED ? now : delta.promotedAt(),
                newState == LearningDeltaState.REJECTED ? now : delta.rejectedAt(),
                delta.labels(),
                rejectionReason
        );

        deltas.put(deltaId, updated);
        return Promise.of(updated);
    }
}
