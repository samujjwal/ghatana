package com.ghatana.digitalmarketing.application.funnel;

import com.ghatana.digitalmarketing.domain.funnel.DemoWorkspace;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for DemoWorkspace persistence.
 *
 * @doc.type interface
 * @doc.purpose Persistence operations for demo workspaces (P3-001)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DemoWorkspaceRepository {

    /**
     * Saves a demo workspace.
     *
     * @param workspace the workspace to save
     * @return the saved workspace
     */
    Promise<DemoWorkspace> save(DemoWorkspace workspace);

    /**
     * Finds a demo workspace by ID.
     *
     * @param id the workspace ID
     * @return the workspace if found
     */
    Promise<Optional<DemoWorkspace>> findById(String id);

    /**
     * Finds demo workspaces by lead ID.
     *
     * @param leadId the lead ID
     * @return list of demo workspaces for the lead
     */
    Promise<List<DemoWorkspace>> findByLeadId(String leadId);

    /**
     * Finds demo workspaces by tenant ID.
     *
     * @param tenantId the tenant ID
     * @return list of demo workspaces for the tenant
     */
    Promise<List<DemoWorkspace>> findByTenantId(String tenantId);

    /**
     * Lists all demo workspaces for a tenant.
     *
     * @param tenantId the tenant ID
     * @return list of demo workspaces
     */
    Promise<List<DemoWorkspace>> listByTenant(String tenantId);

    /**
     * Deletes a demo workspace.
     *
     * @param id the workspace ID
     * @return void
     */
    Promise<Void> delete(String id);
}
