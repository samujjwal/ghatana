/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.mastery;

import com.ghatana.agent.mastery.MasteryEvidence;
import com.ghatana.agent.mastery.MasteryEvidenceRepository;
import com.ghatana.agent.mastery.MasteryEvidenceType;
import com.ghatana.agent.mastery.MasteryEvidenceMapper;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Data Cloud-backed implementation of MasteryEvidenceRepository.
 *
 * <p>Uses EntityRepository for durable persistence with tenant isolation.
 *
 * @doc.type class
 * @doc.purpose Data Cloud implementation of MasteryEvidenceRepository
 * @doc.layer data-cloud
 * @doc.pattern Repository
 */
public final class DataCloudMasteryEvidenceRepository implements MasteryEvidenceRepository {

    private static final String COLLECTION_MASTERY_EVIDENCE = "agent-mastery-evidence";

    private final EntityRepository entityRepository;

    /**
     * Creates a new DataCloudMasteryEvidenceRepository.
     *
     * @param entityRepository Data Cloud entity repository
     */
    public DataCloudMasteryEvidenceRepository(@NotNull EntityRepository entityRepository) {
        this.entityRepository = entityRepository;
    }

    @Override
    @NotNull
    public Promise<MasteryEvidence> save(@NotNull MasteryEvidence evidence) {
        // MasteryEvidence doesn't have tenantId, use default
        // TODO: Add tenantId to MasteryEvidence when governance is fully implemented
        String tenantId = "default";
        Map<String, Object> dataMap = MasteryEvidenceMapper.toDataMap(evidence);

        Entity entity = Entity.builder()
                .tenantId(tenantId)
                .collectionName(COLLECTION_MASTERY_EVIDENCE)
                .data(dataMap)
                .createdBy(evidence.createdBy())
                .build();

        return entityRepository.save(tenantId, entity)
                .map(savedEntity -> Promise.of(MasteryEvidenceMapper.fromDataMap(savedEntity.getData())))
                .then(p -> p);
    }

    @Override
    @NotNull
    public Promise<Optional<MasteryEvidence>> findById(@NotNull String evidenceId) {
        return entityRepository.findAll("default", COLLECTION_MASTERY_EVIDENCE, 
                Map.of("evidenceId", evidenceId), null, 0, 1)
                .then(entities -> entities.isEmpty() 
                        ? Promise.of(Optional.empty()) 
                        : Promise.of(Optional.of(MasteryEvidenceMapper.fromDataMap(entities.get(0).getData()))));
    }

    @Override
    @NotNull
    public Promise<List<MasteryEvidence>> findByMasteryId(@NotNull String masteryId) {
        return entityRepository.findAll("default", COLLECTION_MASTERY_EVIDENCE, 
                Map.of("masteryId", masteryId), null, 0, 100)
                .then(entities -> {
                    List<MasteryEvidence> evidenceList = entities.stream()
                            .map(e -> MasteryEvidenceMapper.fromDataMap(e.getData()))
                            .collect(Collectors.toList());
                    return Promise.of(evidenceList);
                });
    }

    @Override
    @NotNull
    public Promise<List<MasteryEvidence>> findByType(@NotNull MasteryEvidenceType type) {
        return entityRepository.findAll("default", COLLECTION_MASTERY_EVIDENCE, 
                Map.of("type", type.name()), null, 0, 100)
                .then(entities -> {
                    List<MasteryEvidence> evidenceList = entities.stream()
                            .map(e -> MasteryEvidenceMapper.fromDataMap(e.getData()))
                            .collect(Collectors.toList());
                    return Promise.of(evidenceList);
                });
    }

    @Override
    @NotNull
    public Promise<List<MasteryEvidence>> findByRef(@NotNull String ref) {
        return entityRepository.findAll("default", COLLECTION_MASTERY_EVIDENCE, 
                Map.of("ref", ref), null, 0, 100)
                .then(entities -> {
                    List<MasteryEvidence> evidenceList = entities.stream()
                            .map(e -> MasteryEvidenceMapper.fromDataMap(e.getData()))
                            .collect(Collectors.toList());
                    return Promise.of(evidenceList);
                });
    }

    @Override
    @NotNull
    public Promise<List<MasteryEvidence>> findByCreatedBy(@NotNull String createdBy) {
        return entityRepository.findAll("default", COLLECTION_MASTERY_EVIDENCE, 
                Map.of("createdBy", createdBy), null, 0, 100)
                .then(entities -> {
                    List<MasteryEvidence> evidenceList = entities.stream()
                            .map(e -> MasteryEvidenceMapper.fromDataMap(e.getData()))
                            .collect(Collectors.toList());
                    return Promise.of(evidenceList);
                });
    }

    @Override
    @NotNull
    public Promise<Void> deleteById(@NotNull String evidenceId) {
        return entityRepository.findAll("default", COLLECTION_MASTERY_EVIDENCE, 
                Map.of("evidenceId", evidenceId), null, 0, 1)
                .then(entities -> {
                    if (entities.isEmpty()) {
                        return Promise.complete();
                    }
                    UUID entityId = entities.get(0).getId();
                    return entityRepository.delete("default", COLLECTION_MASTERY_EVIDENCE, entityId);
                });
    }
}
