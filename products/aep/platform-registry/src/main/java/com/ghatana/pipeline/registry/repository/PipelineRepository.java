/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.pipeline.registry.repository;

import com.ghatana.pipeline.registry.model.Pipeline;
import com.ghatana.pipeline.registry.model.PipelineRegistration;
import com.ghatana.platform.core.common.pagination.Page;
import com.ghatana.platform.domain.auth.TenantId;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for pipeline persistence.
 * Simplified implementation for platform-registry module without external dependencies.
 */
public interface PipelineRepository {

    /**
     * Save or update a pipeline.
     *
     * @param pipeline the pipeline to save
     * @return promise with saved pipeline
     */
    Promise<PipelineRegistration> save(PipelineRegistration pipeline);

    /**
     * Find pipeline by ID.
     *
     * @param id pipeline ID
     * @param tenantId tenant identifier
     * @return promise with optional pipeline
     */
    Promise<Optional<PipelineRegistration>> findById(String id, String tenantId);

    /**
     * List all pipelines for a tenant.
     *
     * @param tenantId tenant identifier
     * @return promise with list of pipelines
     */
    Promise<List<PipelineRegistration>> findByTenantId(String tenantId);

    /**
     * Delete a pipeline.
     *
     * @param id pipeline ID
     * @param tenantId tenant identifier
     * @return promise of completion
     */
    Promise<Void> delete(String id, String tenantId);

    /**
     * Count pipelines for a tenant.
     *
     * @param tenantId tenant identifier
     * @return promise with count
     */
    Promise<Long> countByTenantId(String tenantId);

    // ==================== TenantId-typed overloads (used by AEP server) ====================

    /**
     * Find pipeline by ID with {@link TenantId}.
     */
    default Promise<Optional<Pipeline>> findById(String id, TenantId tenantId) {
        return findById(id, tenantId.value()).map(opt -> opt.map(PipelineRepository::toPipeline));
    }

    /**
     * Save a {@link Pipeline}.
     */
    default Promise<Pipeline> save(Pipeline pipeline) {
        return save((PipelineRegistration) pipeline).map(saved -> {
            if (saved instanceof Pipeline p) return p;
            Pipeline p = new Pipeline();
            p.setId(saved.getId());
            p.setTenantId(saved.getTenantId());
            p.setName(saved.getName());
            p.setDescription(saved.getDescription());
            p.setVersion(saved.getVersion());
            p.setActive(saved.isActive());
            p.setConfig(saved.getConfig());
            p.setCreatedAt(saved.getCreatedAt());
            p.setUpdatedAt(saved.getUpdatedAt());
            p.setCreatedBy(saved.getCreatedBy());
            p.setUpdatedBy(saved.getUpdatedBy());
            return p;
        });
    }

    /**
     * Paginated search for pipelines.
     */
    default Promise<Page<Pipeline>> findAll(TenantId tenantId, String nameFilter,
                                             Boolean activeOnly, int page, int size) {
        return findByTenantId(tenantId.value()).map(all -> {
            var filtered = all.stream()
                    .filter(p -> nameFilter == null || p.getName().contains(nameFilter))
                    .filter(p -> activeOnly == null || p.isActive() == activeOnly)
                    .toList();
            int start = Math.min((page - 1) * size, filtered.size());
            int end = Math.min(start + size, filtered.size());
            List<Pipeline> content = filtered.subList(start, end).stream()
                    .map(PipelineRepository::toPipeline)
                    .toList();
            return new Page<>(content, size, page - 1, filtered.size());
        });
    }

    /**
     * Check if a pipeline exists.
     */
    default Promise<Boolean> exists(String id, TenantId tenantId) {
        return findById(id, tenantId.value()).map(Optional::isPresent);
    }

    /**
     * Compute next version number for a named pipeline.
     */
    default Promise<Integer> nextVersion(String name, TenantId tenantId) {
        return findByTenantId(tenantId.value()).map(all -> all.stream()
                .filter(p -> name.equals(p.getName()))
                .mapToInt(PipelineRegistration::getVersion)
                .max()
                .orElse(0) + 1);
    }

    /**
     * Delete a pipeline (extended).
     */
    default Promise<Void> delete(String id, TenantId tenantId, boolean hardDelete, String deletedBy) {
        return delete(id, tenantId.value());
    }

    private static Pipeline toPipeline(PipelineRegistration reg) {
        if (reg instanceof Pipeline p) return p;
        Pipeline p = new Pipeline();
        p.setId(reg.getId());
        p.setTenantId(reg.getTenantId());
        p.setName(reg.getName());
        p.setDescription(reg.getDescription());
        p.setVersion(reg.getVersion());
        p.setActive(reg.isActive());
        p.setConfig(reg.getConfig());
        p.setCreatedAt(reg.getCreatedAt());
        p.setUpdatedAt(reg.getUpdatedAt());
        p.setCreatedBy(reg.getCreatedBy());
        p.setUpdatedBy(reg.getUpdatedBy());
        return p;
    }
}
