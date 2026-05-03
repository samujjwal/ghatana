package com.ghatana.digitalmarketing.application.attribution;

import com.ghatana.digitalmarketing.domain.attribution.AttributionModel;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Repository interface for AttributionModel entities (DMOS-P3-005).
 *
 * @doc.type interface
 * @doc.purpose Repository interface for attribution model operations
 * @doc.layer application
 */
public interface AttributionModelRepository {

    /**
     * Save an attribution model.
     */
    Promise<AttributionModel> save(AttributionModel model);

    /**
     * Find an attribution model by ID.
     */
    Promise<Optional<AttributionModel>> findById(String modelId);

    /**
     * Find active attribution model for workspace.
     */
    Promise<Optional<AttributionModel>> findActiveByWorkspace(DmWorkspaceId workspaceId);

    /**
     * Find all attribution models for a tenant.
     */
    Promise<java.util.List<AttributionModel>> findByTenant(DmTenantId tenantId);

    /**
     * Update an attribution model.
     */
    Promise<AttributionModel> update(AttributionModel model);

    /**
     * Delete an attribution model.
     */
    Promise<Void> delete(String modelId);
}
