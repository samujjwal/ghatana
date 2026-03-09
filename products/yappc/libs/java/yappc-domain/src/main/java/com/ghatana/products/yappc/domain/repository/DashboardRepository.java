package com.ghatana.products.yappc.domain.repository;

import com.ghatana.products.yappc.domain.model.Dashboard;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Dashboard entity operations.
 *
 * <p>Provides data access operations for user-configurable dashboards,
 * including workspace-scoped queries and default dashboard management.</p>
 *
 * @doc.type interface
 * @doc.purpose Defines data access contract for Dashboard entities
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DashboardRepository extends TenantAwareRepository<Dashboard, UUID> {

    /**
     * Finds all dashboards for a workspace with pagination.
     *
     * @param workspaceId the workspace ID
     * @param offset      zero-based offset
     * @param limit       maximum results per page
     * @return promise of paginated dashboards
     */
    Promise<List<Dashboard>> findByWorkspaceIdPaged(UUID workspaceId, int offset, int limit);

    /**
     * Finds the default dashboard for a workspace.
     *
     * @param workspaceId the workspace ID
     * @return promise of the default dashboard if one exists
     */
    Promise<Optional<Dashboard>> findDefaultByWorkspaceId(UUID workspaceId);

    /**
     * Finds a dashboard by name within a workspace.
     *
     * @param workspaceId the workspace ID
     * @param name        the dashboard name
     * @return promise of the dashboard if found
     */
    Promise<Optional<Dashboard>> findByWorkspaceIdAndName(UUID workspaceId, String name);
}
