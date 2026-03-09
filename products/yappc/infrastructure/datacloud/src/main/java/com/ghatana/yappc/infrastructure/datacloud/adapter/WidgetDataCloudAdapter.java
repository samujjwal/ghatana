package com.ghatana.yappc.infrastructure.datacloud.adapter;

import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.products.yappc.domain.model.Widget;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-Cloud adapter for Widget storage.
 * 
 * <p>Provides Widget CRUD operations using data-cloud as backend.
 * All methods return ActiveJ Promises for non-blocking async execution.
 * 
 * @doc.type class
 * @doc.purpose Widget data-cloud adapter
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public class WidgetDataCloudAdapter {
    
    private static final String COLLECTION = "widget";
    
    private final YappcDataCloudRepository<Widget> repository;
    
    public WidgetDataCloudAdapter(
        @NotNull EntityRepository entityRepository,
        @NotNull YappcEntityMapper mapper
    ) {
        this.repository = new YappcDataCloudRepository<>(
            entityRepository,
            mapper,
            COLLECTION,
            Widget.class
        );
    }
    
    /**
     * Finds widgets by dashboard ID.
     *
     * @param dashboardId dashboard identifier
     * @return promise of matching widgets
     */
    @NotNull
    public Promise<List<Widget>> findByDashboardId(@NotNull UUID dashboardId) {
        Map<String, Object> filter = new HashMap<>();
        filter.put("dashboardId", dashboardId.toString());
        return repository.findByFilter(filter, null, 1000, 0);
    }
    
    /**
     * Finds a widget by ID within a workspace.
     *
     * @param id          widget identifier
     * @param workspaceId workspace identifier
     * @return promise of optional widget
     */
    @NotNull
    public Promise<Optional<Widget>> findByIdAndWorkspaceId(@NotNull UUID id, @NotNull UUID workspaceId) {
        return repository.findById(id)
            .map(opt -> opt.filter(w -> workspaceId.equals(w.getWorkspaceId())));
    }
    
    /**
     * Finds all widgets for a workspace.
     *
     * @param workspaceId workspace identifier
     * @return promise of matching widgets
     */
    @NotNull
    public Promise<List<Widget>> findByWorkspaceId(@NotNull UUID workspaceId) {
        Map<String, Object> filter = new HashMap<>();
        filter.put("workspaceId", workspaceId.toString());
        return repository.findByFilter(filter, null, 1000, 0);
    }
    
    /**
     * Saves a widget.
     *
     * @param widget widget to save
     * @return promise of saved widget
     */
    @NotNull
    public Promise<Widget> save(@NotNull Widget widget) {
        return repository.save(widget);
    }
    
    /**
     * Deletes a widget by ID.
     *
     * @param id widget identifier
     * @return promise completing when deleted
     */
    public Promise<Void> deleteById(@NotNull UUID id) {
        return repository.deleteById(id);
    }
}
