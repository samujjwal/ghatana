/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.mastery;

import com.ghatana.agent.mastery.MasteryTransition;
import com.ghatana.agent.mastery.MasteryTransitionRepository;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Data Cloud-backed implementation of MasteryTransitionRepository.
 *
 * <p>Enforces append-only semantics - transitions can only be added, never modified or deleted.
 *
 * <p>TODO: Replace in-memory storage with actual Data Cloud persistence.
 *
 * @doc.type class
 * @doc.purpose Data Cloud implementation of MasteryTransitionRepository (append-only)
 * @doc.layer data-cloud
 * @doc.pattern Repository
 */
public final class DataCloudMasteryTransitionRepository implements MasteryTransitionRepository {

    private final ConcurrentHashMap<String, MasteryTransition> transitionStore = new ConcurrentHashMap<>();

    @Override
    @NotNull
    public Promise<MasteryTransition> append(@NotNull MasteryTransition transition) {
        // Append-only: store the transition (no update/delete operations allowed)
        transitionStore.put(transition.transitionId(), transition);
        return Promise.of(transition);
    }

    @Override
    @NotNull
    public Promise<Optional<MasteryTransition>> findById(@NotNull String transitionId) {
        return Promise.of(Optional.ofNullable(transitionStore.get(transitionId)));
    }

    @Override
    @NotNull
    public Promise<List<MasteryTransition>> findByMasteryId(@NotNull String masteryId) {
        return Promise.of(transitionStore.values().stream()
                .filter(t -> t.masteryId().equals(masteryId))
                .sorted((t1, t2) -> t1.transitionedAt().compareTo(t2.transitionedAt()))
                .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<List<MasteryTransition>> findByInitiatedBy(@NotNull String initiatedBy) {
        return Promise.of(transitionStore.values().stream()
                .filter(t -> t.initiatedBy().equals(initiatedBy))
                .sorted((t1, t2) -> t2.transitionedAt().compareTo(t1.transitionedAt()))
                .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<List<MasteryTransition>> findByTimeRange(@NotNull Instant from, @NotNull Instant to) {
        return Promise.of(transitionStore.values().stream()
                .filter(t -> !t.transitionedAt().isBefore(from) && !t.transitionedAt().isAfter(to))
                .sorted((t1, t2) -> t1.transitionedAt().compareTo(t2.transitionedAt()))
                .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<Optional<MasteryTransition>> findLatestByMasteryId(@NotNull String masteryId) {
        return Promise.of(transitionStore.values().stream()
                .filter(t -> t.masteryId().equals(masteryId))
                .max((t1, t2) -> t1.transitionedAt().compareTo(t2.transitionedAt())));
    }
}
