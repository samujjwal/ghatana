package com.ghatana.pipeline.registry.repository;

import com.ghatana.platform.core.common.pagination.Page;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.pipeline.registry.model.Pipeline;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of the PipelineRepository interface.
 *
 * <p>Purpose: Provides a thread-safe, non-persistent storage implementation
 * for pipeline entities using ConcurrentHashMap. Suitable for development,
 * testing, and single-instance deployments where persistence is not required.</p>
 *
 * @doc.type class
 * @doc.purpose In-memory storage implementation for pipeline entities with tenant isolation
 * @doc.layer product
 * @doc.pattern Repository
 * @since 2.0.0
 */
public class InMemoryPipelineRepository implements PipelineRepository {

    private final Map<String, Pipeline> pipelinesById = new ConcurrentHashMap<>();

    @Override
    public Promise<Optional<Pipeline>> findById(String id, TenantId tenantId) {
        Pipeline pipeline = pipelinesById.get(id);
        if (pipeline == null || pipeline.getTenantId() == null || !pipeline.getTenantId().equals(tenantId)) {
            return Promise.of(Optional.empty());
        }
        return Promise.of(Optional.of(pipeline));
    }

    @Override
    public Promise<Optional<Pipeline>> findLatestVersion(String name, TenantId tenantId) {
        return Promise.of(
                pipelinesById.values().stream()
                        .filter(p -> tenantId.equals(p.getTenantId()))
                        .filter(p -> name.equals(p.getName()))
                        .max(Comparator.comparingInt(Pipeline::getVersion))
        );
    }

    @Override
    public Promise<Optional<Pipeline>> findByNameAndVersion(String name, int version, TenantId tenantId) {
        return Promise.of(
                pipelinesById.values().stream()
                        .filter(p -> tenantId.equals(p.getTenantId()))
                        .filter(p -> name.equals(p.getName()))
                        .filter(p -> p.getVersion() == version)
                        .findFirst()
        );
    }

    @Override
    public Promise<List<Pipeline>> findAllVersions(String name, TenantId tenantId) {
        List<Pipeline> versions = pipelinesById.values().stream()
                .filter(p -> tenantId.equals(p.getTenantId()))
                .filter(p -> name.equals(p.getName()))
                .sorted(Comparator.comparingInt(Pipeline::getVersion).reversed())
                .toList();
        return Promise.of(versions);
    }

    @Override
    public Promise<Page<Pipeline>> findAll(TenantId tenantId, String nameFilter, Boolean activeOnly, int page, int size) {
        int pageSize = Math.max(1, size);
        int pageNumber = Math.max(1, page);

        List<Pipeline> filtered = pipelinesById.values().stream()
                .filter(p -> tenantId.equals(p.getTenantId()))
                .filter(p -> nameFilter == null || nameFilter.isBlank() || (p.getName() != null && p.getName().contains(nameFilter)))
                .filter(p -> activeOnly == null || !activeOnly || p.isActive())
                .sorted(Comparator.comparing(Pipeline::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();

        int startIndex = (pageNumber - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, filtered.size());
        List<Pipeline> pageItems = startIndex < filtered.size() ? filtered.subList(startIndex, endIndex) : new ArrayList<>();

        return Promise.of(Page.of(pageItems, pageSize, pageNumber - 1, filtered.size()));
    }

    @Override
    public Promise<Pipeline> save(Pipeline pipeline) {
        Pipeline toSave = pipeline;

        if (toSave.getId() == null || toSave.getId().isBlank()) {
            toSave = Pipeline.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(toSave.getTenantId())
                    .name(toSave.getName())
                    .description(toSave.getDescription())
                    .version(toSave.getVersion())
                    .active(toSave.isActive())
                    .config(toSave.getConfig())
                    .updatedBy(toSave.getUpdatedBy())
                    .tags(toSave.getTags() != null ? new ArrayList<>(toSave.getTags()) : new ArrayList<>())
                    .createdAt(toSave.getCreatedAt() != null ? toSave.getCreatedAt() : Instant.now())
                    .updatedAt(toSave.getUpdatedAt() != null ? toSave.getUpdatedAt() : Instant.now())
                    .createdBy(toSave.getCreatedBy())
                    .versionControl(toSave.getVersionControl())
                    .build();
        } else {
            if (toSave.getCreatedAt() == null) {
                toSave.setCreatedAt(Instant.now());
            }
            toSave.setUpdatedAt(Instant.now());
        }

        pipelinesById.put(toSave.getId(), toSave);
        return Promise.of(toSave);
    }

    @Override
    public Promise<Void> delete(String id, TenantId tenantId, boolean softDelete, String deletedBy) {
        Pipeline pipeline = pipelinesById.get(id);
        if (pipeline == null || pipeline.getTenantId() == null || !pipeline.getTenantId().equals(tenantId)) {
            return Promise.of(null);
        }

        if (softDelete) {
            pipeline.setActive(false);
            pipeline.setUpdatedBy(deletedBy);
            pipeline.setUpdatedAt(Instant.now());
            pipelinesById.put(id, pipeline);
        } else {
            pipelinesById.remove(id);
        }

        return Promise.of(null);
    }

    @Override
    public Promise<Integer> nextVersion(String name, TenantId tenantId) {
        int next = pipelinesById.values().stream()
                .filter(p -> tenantId.equals(p.getTenantId()))
                .filter(p -> name.equals(p.getName()))
                .mapToInt(Pipeline::getVersion)
                .max()
                .orElse(0) + 1;
        return Promise.of(next);
    }

    @Override
    public Promise<Boolean> exists(String id, TenantId tenantId) {
        return findById(id, tenantId).map(Optional::isPresent);
    }

    @Override
    public Promise<Long> countStructuredConfigPipelines(TenantId tenantId) {
        long count = pipelinesById.values().stream()
                .filter(p -> tenantId.equals(p.getTenantId()))
                .filter(p -> p.getStructuredConfig() != null)
                .count();
        return Promise.of(count);
    }

    @Override
    public Promise<Long> countLegacyConfigPipelines(TenantId tenantId) {
        long count = pipelinesById.values().stream()
                .filter(p -> tenantId.equals(p.getTenantId()))
                .filter(p -> p.getStructuredConfig() == null && p.getConfig() != null)
                .count();
        return Promise.of(count);
    }
}
