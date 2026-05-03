package com.ghatana.digitalmarketing.application.agency;

import com.ghatana.digitalmarketing.domain.agency.AgencyClient;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import io.activej.promise.Promise;

/**
 * Service for managing agency clients (DMOS-P3-003).
 *
 * @doc.type interface
 * @doc.purpose Service interface for agency client operations
 * @doc.layer application
 * @doc.pattern Service
 */
public interface AgencyClientService {

    /**
     * Create a new agency client.
     */
    Promise<AgencyClient> createClient(DmTenantId tenantId, String clientName, String contactEmail, String contactPhone, String brandingTheme);

    /**
     * Get an agency client by ID.
     */
    Promise<AgencyClient> getClient(String clientId);

    /**
     * Get all agency clients for a tenant (multi-client dashboard).
     */
    Promise<java.util.List<AgencyClient>> getClientsForTenant(DmTenantId tenantId);

    /**
     * Get active agency clients for a tenant.
     */
    Promise<java.util.List<AgencyClient>> getActiveClientsForTenant(DmTenantId tenantId);

    /**
     * Update an agency client.
     */
    Promise<AgencyClient> updateClient(String clientId, String clientName, String contactEmail, String contactPhone, String brandingTheme, boolean active);

    /**
     * Deactivate an agency client.
     */
    Promise<Void> deactivateClient(String clientId);

    /**
     * Get agency client by workspace (for client isolation).
     */
    Promise<AgencyClient> getClientByWorkspace(DmWorkspaceId workspaceId);
}
