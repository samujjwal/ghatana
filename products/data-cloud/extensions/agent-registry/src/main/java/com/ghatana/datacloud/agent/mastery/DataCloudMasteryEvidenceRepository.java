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
        String tenantId = evidence.tenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("tenantId is required for evidence save"));
        }
        Map<String, Object> dataMap = MasteryEvidenceMapper.toDataMap(evidence);

        Entity entity = Entity.builder()
                .tenantId(tenantId)
                .collectionName(COLLECTION_MASTERY_EVIDENCE)
                .data(dataMap)
                .createdBy(evidence.createdBy())
                .build();

        return entityRepository.save(tenantId, entity)
                .map(savedEntity -> MasteryEvidenceMapper.fromDataMap(savedEntity.getData()));
    }

    @Override
    @NotNull
    public Promise<Optional<MasteryEvidence>> findById(@NotNull String evidenceId) {
        return Promise.ofException(new UnsupportedOperationException(
                "findById(evidenceId) is deprecated. Use findById(tenantId, evidenceId) for tenant-scoped queries."));
    }

    /**
     * Finds evidence by ID for a specific tenant.
     */
    @NotNull
    public Promise<Optional<MasteryEvidence>> findById(@NotNull String tenantId, @NotNull String evidenceId) {
        if (tenantId == null || tenantId.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("tenantId is required for findById"));
        }
        return entityRepository.findAll(tenantId, COLLECTION_MASTERY_EVIDENCE, 
                Map.of("evidenceId", evidenceId), null, 0, 1)
                .then(entities -> entities.isEmpty() 
                        ? Promise.of(Optional.empty()) 
                        : Promise.of(Optional.of(MasteryEvidenceMapper.fromDataMap(entities.get(0).getData()))));
    }

    @Override
    @NotNull
    public Promise<List<MasteryEvidence>> findByMasteryId(@NotNull String masteryId) {
        return Promise.ofException(new UnsupportedOperationException(
                "findByMasteryId(masteryId) is deprecated. Use findByMasteryId(tenantId, masteryId) for tenant-scoped queries."));
    }

    /**
     * Finds evidence by mastery ID for a specific tenant.
     */
    @NotNull
    public Promise<List<MasteryEvidence>> findByMasteryId(@NotNull String tenantId, @NotNull String masteryId) {
        if (tenantId == null || tenantId.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("tenantId is required for findByMasteryId"));
        }
        return entityRepository.findAll(tenantId, COLLECTION_MASTERY_EVIDENCE, 
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
        return Promise.ofException(new UnsupportedOperationException(
                "findByType(type) is deprecated. Use findByType(tenantId, type) for tenant-scoped queries."));
    }

    /**
     * Finds evidence by type for a specific tenant.
     */
    @NotNull
    public Promise<List<MasteryEvidence>> findByType(@NotNull String tenantId, @NotNull MasteryEvidenceType type) {
        if (tenantId == null || tenantId.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("tenantId is required for findByType"));
        }
        return entityRepository.findAll(tenantId, COLLECTION_MASTERY_EVIDENCE, 
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
        return Promise.ofException(new UnsupportedOperationException(
                "findByRef(ref) is deprecated. Use findByRef(tenantId, ref) for tenant-scoped queries."));
    }

    /**
     * Finds evidence by reference for a specific tenant.
     */
    @NotNull
    public Promise<List<MasteryEvidence>> findByRef(@NotNull String tenantId, @NotNull String ref) {
        if (tenantId == null || tenantId.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("tenantId is required for findByRef"));
        }
        return entityRepository.findAll(tenantId, COLLECTION_MASTERY_EVIDENCE,
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
        return Promise.ofException(new UnsupportedOperationException(
                "findByCreatedBy(createdBy) is deprecated. Use findByCreatedBy(tenantId, createdBy) for tenant-scoped queries."));
    }

    /**
     * Finds evidence by creator for a specific tenant.
     */
    @NotNull
    public Promise<List<MasteryEvidence>> findByCreatedBy(@NotNull String tenantId, @NotNull String createdBy) {
        if (tenantId == null || tenantId.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("tenantId is required for findByCreatedBy"));
        }
        return entityRepository.findAll(tenantId, COLLECTION_MASTERY_EVIDENCE,
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
        return Promise.ofException(new UnsupportedOperationException(
                "deleteById(evidenceId) is deprecated. Use deleteById(tenantId, evidenceId) for tenant-scoped operations."));
    }

    /**
     * Deletes evidence by ID for a specific tenant.
     */
    @NotNull
    public Promise<Void> deleteById(@NotNull String tenantId, @NotNull String evidenceId) {
        if (tenantId == null || tenantId.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("tenantId is required for deleteById"));
        }
        return entityRepository.findAll(tenantId, COLLECTION_MASTERY_EVIDENCE, 
                Map.of("evidenceId", evidenceId), null, 0, 1)
                .then(entities -> {
                    if (entities.isEmpty()) {
                        return Promise.complete();
                    }
                    UUID entityId = entities.get(0).getId();
                    return entityRepository.delete(tenantId, COLLECTION_MASTERY_EVIDENCE, entityId);
                });
    }
}
