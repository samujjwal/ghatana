package com.ghatana.digitalmarketing.application.command;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Optional;

/**
 * P0-006: Repository for external system ID mappings.
 *
 * <p>Maintains bidirectional mappings between DMOS internal IDs and external system IDs
 * (e.g., DMOS campaign ID ↔ Google Ads campaign ID). This is critical for:</p>
 * <ul>
 *   <li>Rollback operations (need external ID to delete)</li>
 *   <li>Status synchronization (query external system by ID)</li>
 *   <li>Audit trail (trace external execution)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose External ID mapping repository (P0-006)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ExternalIdMappingRepository {

    /**
     * Records a mapping between internal and external IDs.
     *
     * @param ctx the operation context
     * @param mapping the mapping to record
     * @return promise resolving when the mapping is recorded
     */
    Promise<Void> save(DmOperationContext ctx, ExternalIdMapping mapping);

    /**
     * Retrieves the external ID for a given internal ID.
     *
     * @param ctx the operation context
     * @param internalId the internal DMOS ID
     * @param externalSystem the external system name (e.g., "google-ads")
     * @return the external ID if found
     */
    Promise<Optional<String>> findExternalId(DmOperationContext ctx, String internalId, String externalSystem);

    /**
     * Retrieves the internal ID for a given external ID.
     *
     * @param ctx the operation context
     * @param externalId the external system ID
     * @param externalSystem the external system name
     * @return the internal DMOS ID if found
     */
    Promise<Optional<String>> findInternalId(DmOperationContext ctx, String externalId, String externalSystem);

    /**
     * Deletes a mapping (typically after rollback).
     *
     * @param ctx the operation context
     * @param internalId the internal DMOS ID
     * @param externalSystem the external system name
     * @return promise resolving when the mapping is deleted
     */
    Promise<Void> delete(DmOperationContext ctx, String internalId, String externalSystem);

    /**
     * External ID mapping record.
     */
    record ExternalIdMapping(
        String id,
        String internalId,
        String externalId,
        String externalSystem,
        String resourceType,
        String tenantId,
        String workspaceId,
        String correlationId,
        Instant mappedAt,
        String mappedBy
    ) {}
}
