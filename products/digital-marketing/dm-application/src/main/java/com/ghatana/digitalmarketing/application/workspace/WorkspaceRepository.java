package com.ghatana.digitalmarketing.application.workspace;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.workspace.Workspace;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for DMOS workspace persistence.
 *
 * <p>All methods are tenant-scoped; implementations must enforce isolation so that
 * cross-tenant data access is structurally impossible.</p>
 *
 * @doc.type interface
 * @doc.purpose Workspace persistence contract for DMOS application services
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface WorkspaceRepository {

    /**
     * Saves a workspace (insert or update by ID within the tenant).
     *
     * @param workspace the workspace to save; must not be null
     * @return promise resolving to the saved workspace
     */
    Promise<Workspace> save(Workspace workspace);

    /**
     * Finds a workspace by its ID within the given tenant.
     *
     * @param tenantId    the owning tenant; must not be null
     * @param workspaceId the workspace ID; must not be null
     * @return promise resolving to an optional workspace
     */
    Promise<Optional<Workspace>> findById(DmTenantId tenantId, DmWorkspaceId workspaceId);

    /**
     * Lists all workspaces belonging to the given tenant.
     *
     * @param tenantId the owning tenant; must not be null
     * @return promise resolving to an unordered list of workspaces; never null
     */
    Promise<List<Workspace>> listByTenant(DmTenantId tenantId);
}
