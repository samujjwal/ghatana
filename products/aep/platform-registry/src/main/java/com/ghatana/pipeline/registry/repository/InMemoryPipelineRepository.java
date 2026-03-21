/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.pipeline.registry.repository;

import com.ghatana.pipeline.registry.model.PipelineRegistration;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of PipelineRepository.
 * Simplified implementation without external dependencies.
 */
public class InMemoryPipelineRepository implements PipelineRepository {
    
    private final Map<String, PipelineRegistration> pipelines = new ConcurrentHashMap<>();
    
    @Override
    public Promise<PipelineRegistration> save(PipelineRegistration pipeline) {
        pipelines.put(pipeline.getId(), pipeline);
        return Promise.of(pipeline);
    }
    
    @Override
    public Promise<Optional<PipelineRegistration>> findById(String id, String tenantId) {
        PipelineRegistration pipeline = pipelines.get(id);
        if (pipeline != null && pipeline.getTenantId().equals(tenantId)) {
            return Promise.of(Optional.of(pipeline));
        }
        return Promise.of(Optional.empty());
    }
    
    @Override
    public Promise<List<PipelineRegistration>> findByTenantId(String tenantId) {
        List<PipelineRegistration> result = pipelines.values().stream()
            .filter(p -> p.getTenantId().equals(tenantId))
            .collect(Collectors.toList());
        return Promise.of(result);
    }
    
    @Override
    public Promise<Void> delete(String id, String tenantId) {
        PipelineRegistration pipeline = pipelines.get(id);
        if (pipeline != null && pipeline.getTenantId().equals(tenantId)) {
            pipelines.remove(id);
        }
        return Promise.complete();
    }
    
    @Override
    public Promise<Long> countByTenantId(String tenantId) {
        long count = pipelines.values().stream()
            .filter(p -> p.getTenantId().equals(tenantId))
            .count();
        return Promise.of(count);
    }
}
