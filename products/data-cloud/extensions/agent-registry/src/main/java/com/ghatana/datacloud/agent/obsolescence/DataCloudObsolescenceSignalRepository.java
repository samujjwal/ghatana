/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.obsolescence;

import com.ghatana.agent.obsolescence.ObsolescenceSignal;
import com.ghatana.agent.obsolescence.ObsolescenceSignalRepository;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data Cloud-backed repository for obsolescence signals.
 *
 * <p>This implementation uses EntityRepository for durable persistence with tenant isolation.
 *
 * @doc.type class
 * @doc.purpose Data Cloud repository for obsolescence signals
 * @doc.layer data-cloud
 * @doc.pattern Repository Implementation
 */
public final class DataCloudObsolescenceSignalRepository implements ObsolescenceSignalRepository {

    private static final String COLLECTION_OBSOLESCENCE_SIGNALS = "agent-obsolescence-signals";

    private final EntityRepository entityRepository;

    /**
     * Creates a new DataCloudObsolescenceSignalRepository.
     *
     * @param entityRepository Data Cloud entity repository
     */
    public DataCloudObsolescenceSignalRepository(@NotNull EntityRepository entityRepository) {
        this.entityRepository = entityRepository;
    }

    @Override
    @NotNull
    public Promise<Void> save(@NotNull ObsolescenceSignal signal) {
        // Extract tenantId from metadata or use default
        String tenantId = signal.metadata().getOrDefault("tenantId", "default");
        
        Map<String, Object> dataMap = ObsolescenceSignalMapper.toDataMap(signal);

        Entity entity = Entity.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .collectionName(COLLECTION_OBSOLESCENCE_SIGNALS)
                .data(dataMap)
                .createdBy(signal.source())
                .build();

        return entityRepository.save(tenantId, entity).map(saved -> null);
    }

    @Override
    @NotNull
    public Promise<List<ObsolescenceSignal>> findByMasteryItem(
            @NotNull String tenantId,
            @NotNull String masteryItemId) {
        return entityRepository.findAll(tenantId, COLLECTION_OBSOLESCENCE_SIGNALS,
                Map.of("masteryItemId", masteryItemId), "detectedAt:DESC", 0, 100)
                .map(entities -> entities.stream()
                        .map(e -> ObsolescenceSignalMapper.fromDataMap(e.getData()))
                        .toList());
    }

    @Override
    @NotNull
    public Promise<List<ObsolescenceSignal>> findByDetectedAfter(
            @NotNull String tenantId,
            @NotNull Instant detectedAfter) {
        // For now, fetch all and filter in-memory
        // A production implementation would use a proper date range query
        return entityRepository.findAll(tenantId, COLLECTION_OBSOLESCENCE_SIGNALS,
                Map.of(), "detectedAt:DESC", 0, 1000)
                .map(entities -> entities.stream()
                        .map(e -> ObsolescenceSignalMapper.fromDataMap(e.getData()))
                        .filter(s -> s.detectedAt().isAfter(detectedAfter))
                        .toList());
    }

    @Override
    @NotNull
    public Promise<List<ObsolescenceSignal>> findHighSeverity(@NotNull String tenantId) {
        return entityRepository.findAll(tenantId, COLLECTION_OBSOLESCENCE_SIGNALS,
                Map.of(), "detectedAt:DESC", 0, 100)
                .map(entities -> entities.stream()
                        .map(e -> ObsolescenceSignalMapper.fromDataMap(e.getData()))
                        .filter(ObsolescenceSignal::isHighSeverity)
                        .toList());
    }

    @Override
    @NotNull
    public Promise<Void> deleteByMasteryItem(
            @NotNull String tenantId,
            @NotNull String masteryItemId) {
        return entityRepository.findAll(tenantId, COLLECTION_OBSOLESCENCE_SIGNALS,
                Map.of("masteryItemId", masteryItemId), null, 0, 100)
                .then(entities -> {
                    if (entities.isEmpty()) {
                        return Promise.of(null);
                    }
                    // Delete all matching entities
                    Promise<Void> result = Promise.of(null);
                    for (Entity entity : entities) {
                        result = result.then(v -> entityRepository.delete(tenantId, COLLECTION_OBSOLESCENCE_SIGNALS, entity.getId()));
                    }
                    return result;
                });
    }
}
