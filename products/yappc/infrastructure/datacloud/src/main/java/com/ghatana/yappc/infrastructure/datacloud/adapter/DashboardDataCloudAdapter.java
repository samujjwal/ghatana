package com.ghatana.yappc.infrastructure.datacloud.adapter;

import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.products.yappc.domain.model.Dashboard;
import com.ghatana.products.yappc.domain.repository.DashboardRepository;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-Cloud adapter for Dashboard repository.
 * 
 * <p>Implements YAPPC DashboardRepository using data-cloud as backend.
 * All methods return ActiveJ Promises for non-blocking async execution.
 * 
 * @doc.type class
 * @doc.purpose Dashboard repository data-cloud adapter
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public class DashboardDataCloudAdapter implements DashboardRepository {
    
    private static final String COLLECTION = "dashboard";
    
    private final YappcDataCloudRepository<Dashboard> repository;
    
    public DashboardDataCloudAdapter(
        @NotNull EntityRepository entityRepository,
        @NotNull YappcEntityMapper mapper
    ) {
        this.repository = new YappcDataCloudRepository<>(
            entityRepository,
            mapper,
            COLLECTION,
            Dashboard.class
        );
    }
    
    @Override
    public Promise<List<Dashboard>> findByWorkspaceIdPaged(
            @NotNull UUID workspaceId, int offset, int limit) {
        Map<String, Object> filter = new HashMap<>();
        filter.put("workspaceId", workspaceId.toString());
        return repository.findByFilter(filter, null, offset + limit, 0)
            .map(all -> all.stream()
                .skip(offset)
                .limit(limit)
                .toList());
    }
    
    @Override
    public Promise<Long> countByWorkspaceId(@NotNull UUID workspaceId) {
        return findByWorkspaceId(workspaceId).map(list -> (long) list.size());
    }
    
    @Override
    public Promise<Optional<Dashboard>> findDefaultByWorkspaceId(@NotNull UUID workspaceId) {
        Map<String, Object> filter = new HashMap<>();
        filter.put("workspaceId", workspaceId.toString());
        filter.put("isDefault", true);
        return repository.findByFilter(filter, null, 1, 0)
            .map(results -> results.isEmpty() ? Optional.empty() : Optional.of(results.get(0)));
    }
    
    @Override
    public Promise<Optional<Dashboard>> findByWorkspaceIdAndName(
            @NotNull UUID workspaceId, @NotNull String name) {
        Map<String, Object> filter = new HashMap<>();
        filter.put("workspaceId", workspaceId.toString());
        filter.put("name", name);
        return repository.findByFilter(filter, null, 1, 0)
            .map(results -> results.isEmpty() ? Optional.empty() : Optional.of(results.get(0)));
    }
    
    @Override
    public Promise<Optional<Dashboard>> findByIdAndWorkspaceId(
            @NotNull UUID id, @NotNull UUID workspaceId) {
        return repository.findById(id)
            .map(opt -> opt.filter(d -> workspaceId.equals(d.getWorkspaceId())));
    }
    
    @Override
    public Promise<List<Dashboard>> findByWorkspaceId(@NotNull UUID workspaceId) {
        Map<String, Object> filter = new HashMap<>();
        filter.put("workspaceId", workspaceId.toString());
        return repository.findByFilter(filter, null, 1000, 0);
    }
    
    @Override
    public Promise<Void> deleteByIdAndWorkspaceId(@NotNull UUID id, @NotNull UUID workspaceId) {
        return findByIdAndWorkspaceId(id, workspaceId)
            .then(opt -> opt.isPresent()
                ? repository.deleteById(id)
                : Promise.complete());
    }
    
    @Override
    public Promise<Boolean> existsByIdAndWorkspaceId(@NotNull UUID id, @NotNull UUID workspaceId) {
        return findByIdAndWorkspaceId(id, workspaceId).map(Optional::isPresent);
    }
}
