/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.mastery;

import com.ghatana.agent.mastery.MasteryTransition;
import com.ghatana.agent.mastery.MasteryTransitionMapper;
import com.ghatana.agent.mastery.MasteryTransitionRepository;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Data Cloud-backed implementation of MasteryTransitionRepository.
 *
 * <p>Enforces append-only semantics - transitions can only be added, never modified or deleted.
 *
 * <p>Uses EntityRepository for durable persistence with tenant isolation.
 *
 * @doc.type class
 * @doc.purpose Data Cloud implementation of MasteryTransitionRepository (append-only)
 * @doc.layer data-cloud
 * @doc.pattern Repository
 */
public final class DataCloudMasteryTransitionRepository implements MasteryTransitionRepository {

    private static final String COLLECTION_MASTERY_TRANSITIONS = "agent-mastery-transitions";

    private final EntityRepository entityRepository;

    /**
     * Creates a new DataCloudMasteryTransitionRepository.
     *
     * @param entityRepository Data Cloud entity repository
     */
    public DataCloudMasteryTransitionRepository(@NotNull EntityRepository entityRepository) {
        this.entityRepository = entityRepository;
    }

    @Override
    @NotNull
    public Promise<MasteryTransition> append(@NotNull MasteryTransition transition) {
        // MasteryTransition doesn't have tenantId, use default
        // TODO: Add tenantId to MasteryTransition when governance is fully implemented
        String tenantId = "default";
        Map<String, Object> dataMap = MasteryTransitionMapper.toDataMap(transition);

        Entity entity = Entity.builder()
                .tenantId(tenantId)
                .collectionName(COLLECTION_MASTERY_TRANSITIONS)
                .data(dataMap)
                .createdBy(transition.initiatedBy())
                .build();

        return entityRepository.save(tenantId, entity)
                .map(savedEntity -> Promise.of(MasteryTransitionMapper.fromDataMap(savedEntity.getData())))
                .then(p -> p);
    }

    @Override
    @NotNull
    public Promise<Optional<MasteryTransition>> findById(@NotNull String transitionId) {
        return entityRepository.findAll("default", COLLECTION_MASTERY_TRANSITIONS, 
                Map.of("transitionId", transitionId), null, 0, 1)
                .then(entities -> entities.isEmpty() 
                        ? Promise.of(Optional.empty()) 
                        : Promise.of(Optional.of(MasteryTransitionMapper.fromDataMap(entities.get(0).getData()))));
    }

    @Override
    @NotNull
    public Promise<List<MasteryTransition>> findByMasteryId(@NotNull String masteryId) {
        return entityRepository.findAll("default", COLLECTION_MASTERY_TRANSITIONS, 
                Map.of("masteryId", masteryId), "transitionedAt:ASC", 0, 100)
                .then(entities -> {
                    List<MasteryTransition> transitions = entities.stream()
                            .map(e -> MasteryTransitionMapper.fromDataMap(e.getData()))
                            .collect(Collectors.toList());
                    return Promise.of(transitions);
                });
    }

    @Override
    @NotNull
    public Promise<List<MasteryTransition>> findByInitiatedBy(@NotNull String initiatedBy) {
        return entityRepository.findAll("default", COLLECTION_MASTERY_TRANSITIONS, 
                Map.of("initiatedBy", initiatedBy), "transitionedAt:DESC", 0, 100)
                .then(entities -> {
                    List<MasteryTransition> transitions = entities.stream()
                            .map(e -> MasteryTransitionMapper.fromDataMap(e.getData()))
                            .collect(Collectors.toList());
                    return Promise.of(transitions);
                });
    }

    @Override
    @NotNull
    public Promise<List<MasteryTransition>> findByTimeRange(@NotNull Instant from, @NotNull Instant to) {
        return entityRepository.count("default", COLLECTION_MASTERY_TRANSITIONS)
                .then(count -> {
                    int limit = count > 1000 ? 1000 : (int) count.longValue();
                    return entityRepository.findAll("default", COLLECTION_MASTERY_TRANSITIONS, 
                            Map.of(), "transitionedAt:ASC", 0, limit);
                })
                .then(entities -> {
                    List<MasteryTransition> transitions = entities.stream()
                            .map(e -> MasteryTransitionMapper.fromDataMap(e.getData()))
                            .filter(t -> !t.transitionedAt().isBefore(from) && !t.transitionedAt().isAfter(to))
                            .collect(Collectors.toList());
                    return Promise.of(transitions);
                });
    }

    @Override
    @NotNull
    public Promise<Optional<MasteryTransition>> findLatestByMasteryId(@NotNull String masteryId) {
        return entityRepository.findAll("default", COLLECTION_MASTERY_TRANSITIONS, 
                Map.of("masteryId", masteryId), "transitionedAt:DESC", 0, 1)
                .then(entities -> entities.isEmpty() 
                        ? Promise.of(Optional.empty()) 
                        : Promise.of(Optional.of(MasteryTransitionMapper.fromDataMap(entities.get(0).getData()))));
    }
}
