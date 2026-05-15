/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.evaluation;

import com.ghatana.agent.evaluation.pack.EvaluationPack;
import com.ghatana.agent.evaluation.pack.EvaluationPackRepository;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Data Cloud-backed repository for evaluation packs.
 *
 * <p>Evaluation packs contain test cases and benchmarks for evaluating agent skills.
 *
 * <p>This implementation uses EntityRepository for durable persistence with tenant isolation.
 *
 * @doc.type class
 * @doc.purpose Data Cloud repository for evaluation packs
 * @doc.layer data-cloud
 * @doc.pattern Repository Implementation
 */
public final class DataCloudEvaluationPackRepository implements EvaluationPackRepository {

    private static final String COLLECTION_EVALUATION_PACKS = "agent-evaluation-packs";

    private final EntityRepository entityRepository;

    /**
     * Creates a new DataCloudEvaluationPackRepository.
     *
     * @param entityRepository Data Cloud entity repository
     */
    public DataCloudEvaluationPackRepository(@NotNull EntityRepository entityRepository) {
        this.entityRepository = entityRepository;
    }

    @Override
    @NotNull
    public Promise<Void> save(@NotNull EvaluationPack pack) {
        String tenantId = pack.tenantId();
        Map<String, Object> dataMap = EvaluationPackMapper.toDataMap(pack);

        // Check if pack already exists
        return entityRepository.findAll(tenantId, COLLECTION_EVALUATION_PACKS,
                Map.of("evaluationPackId", pack.evaluationPackId()), null, 0, 1)
                .then(entities -> {
                    UUID entityId = entities.isEmpty() ? UUID.randomUUID() : entities.get(0).getId();

                    Entity entity = Entity.builder()
                            .id(entityId)
                            .tenantId(tenantId)
                            .collectionName(COLLECTION_EVALUATION_PACKS)
                            .data(dataMap)
                            .createdBy("system")
                            .build();

                    return entityRepository.save(tenantId, entity).map(saved -> null);
                });
    }

    @Override
    @NotNull
    public Promise<Optional<EvaluationPack>> findById(
            @NotNull String tenantId,
            @NotNull String evaluationPackId) {
        return entityRepository.findAll(tenantId, COLLECTION_EVALUATION_PACKS,
                Map.of("evaluationPackId", evaluationPackId), null, 0, 1)
                .then(entities -> {
                    if (entities.isEmpty()) {
                        return Promise.of(Optional.empty());
                    }
                    EvaluationPack pack = EvaluationPackMapper.fromDataMap(entities.get(0).getData());
                    return Promise.of(Optional.of(pack));
                });
    }

    @Override
    @NotNull
    public Promise<List<EvaluationPack>> findBySkill(
            @NotNull String tenantId,
            @NotNull String skillId) {
        return entityRepository.findAll(tenantId, COLLECTION_EVALUATION_PACKS,
                Map.of("skillId", skillId), null, 0, 100)
                .map(entities -> entities.stream()
                        .map(e -> EvaluationPackMapper.fromDataMap(e.getData()))
                        .toList());
    }

    @Override
    @NotNull
    public Promise<Void> delete(
            @NotNull String tenantId,
            @NotNull String evaluationPackId) {
        return entityRepository.findAll(tenantId, COLLECTION_EVALUATION_PACKS,
                Map.of("evaluationPackId", evaluationPackId), null, 0, 1)
                .then(entities -> {
                    if (entities.isEmpty()) {
                        return Promise.of(null);
                    }
                    return entityRepository.delete(tenantId, COLLECTION_EVALUATION_PACKS, entities.get(0).getId());
                });
    }
}
