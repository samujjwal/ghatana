package com.ghatana.digitalmarketing.application.agency;

import com.ghatana.digitalmarketing.domain.agency.AgencyClient;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Repository interface for AgencyClient entities (DMOS-P3-003).
 *
 * @doc.type interface
 * @doc.purpose Repository interface for agency client operations
 * @doc.layer application
 * @doc.pattern Repository
 */
public interface AgencyClientRepository {

    /**
     * Save an agency client.
     */
    Promise<AgencyClient> save(AgencyClient client);

    /**
     * Find an agency client by ID.
     */
    Promise<Optional<AgencyClient>> findById(String clientId);

    /**
     * Find all agency clients for a tenant.
     */
    Promise<java.util.List<AgencyClient>> findByTenant(DmTenantId tenantId);

    /**
     * Find all active agency clients for a tenant.
     */
    Promise<java.util.List<AgencyClient>> findActiveByTenant(DmTenantId tenantId);

    /**
     * Find agency client by workspace.
     */
    Promise<Optional<AgencyClient>> findByWorkspace(DmWorkspaceId workspaceId);

    /**
     * Update an agency client.
     */
    Promise<AgencyClient> update(AgencyClient client);

    /**
     * Delete an agency client.
     */
    Promise<Void> delete(String clientId);
}
