/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.pipeline.registry.repository;

import com.ghatana.pipeline.registry.model.PipelineRegistration;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of PipelineRepository.
 * Simplified implementation without external dependencies.
 *
 * @doc.type class
 * @doc.purpose In-memory pipeline repository for testing and development
 * @doc.layer product
 * @doc.pattern Repository
 */
public class InMemoryPipelineRepository implements PipelineRepository {

    private final Map<String, PipelineRegistration> pipelines = new ConcurrentHashMap<>();

    /**
     * Version history keyed by pipelineId. Each list contains immutable
     * snapshots in the order they were saved. Used for AEP-07 versioning.
     */
    private final Map<String, List<PipelineRegistration>> versionHistory = new ConcurrentHashMap<>();

    @Override
    public Promise<PipelineRegistration> save(PipelineRegistration pipeline) {
        pipelines.put(pipeline.getId(), pipeline);
        return Promise.of(pipeline);
    }

    @Override
    public Promise<Optional<PipelineRegistration>> findById(String id, String tenantId) {
        PipelineRegistration pipeline = pipelines.get(id);
        if (pipeline != null && pipeline.getTenantId() != null
                && pipeline.getTenantId().value().equals(tenantId)) {
            return Promise.of(Optional.of(pipeline));
        }
        return Promise.of(Optional.empty());
    }

    @Override
    public Promise<List<PipelineRegistration>> findByTenantId(String tenantId) {
        List<PipelineRegistration> result = pipelines.values().stream()
            .filter(p -> p.getTenantId() != null && p.getTenantId().value().equals(tenantId))
            .collect(Collectors.toList());
        return Promise.of(result);
    }

    @Override
    public Promise<Void> delete(String id, String tenantId) {
        PipelineRegistration pipeline = pipelines.get(id);
        if (pipeline != null && pipeline.getTenantId() != null
                && pipeline.getTenantId().value().equals(tenantId)) {
            pipelines.remove(id);
        }
        return Promise.complete();
    }

    @Override
    public Promise<Long> countByTenantId(String tenantId) {
        long count = pipelines.values().stream()
            .filter(p -> p.getTenantId() != null && p.getTenantId().value().equals(tenantId))
            .count();
        return Promise.of(count);
    }

    // ==================== Versioning (AEP-07) ====================

    @Override
    public Promise<Void> saveVersionSnapshot(String pipelineId, PipelineRegistration snapshot) {
        versionHistory.computeIfAbsent(pipelineId, k -> new ArrayList<>()).add(snapshot);
        return Promise.complete();
    }

    @Override
    public Promise<List<PipelineRegistration>> findVersionHistory(String pipelineId, String tenantId) {
        List<PipelineRegistration> history = versionHistory
            .getOrDefault(pipelineId, List.of())
            .stream()
            .filter(p -> p.getTenantId() != null && tenantId.equals(p.getTenantId().value()))
            .sorted(Comparator.comparingInt(PipelineRegistration::getVersion))
            .collect(Collectors.toList());
        return Promise.of(history);
    }

    @Override
    public Promise<Optional<PipelineRegistration>> findVersionSnapshot(String pipelineId, int version, String tenantId) {
        Optional<PipelineRegistration> found = versionHistory
            .getOrDefault(pipelineId, List.of())
            .stream()
            .filter(p -> p.getVersion() == version
                && p.getTenantId() != null && tenantId.equals(p.getTenantId().value()))
            .findFirst();
        return Promise.of(found);
    }
}
