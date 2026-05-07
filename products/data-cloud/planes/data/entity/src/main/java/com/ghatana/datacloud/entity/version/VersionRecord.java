package com.ghatana.datacloud.entity.version;

import com.ghatana.datacloud.entity.Entity;
import io.activej.promise.Promise;
import java.util.List;
import java.util.UUID;

/**
 * Port interface for entity version storage and retrieval.
 *
 * <p><b>Purpose</b><br>
 * Abstract version persistence from domain layer. Enables different storage
 * backends (database, event store, etc.) without coupling domain logic.
 *
 * <p><b>Responsibilities</b><br>
 * - Store new entity versions
 * - Retrieve version history
 * - Compare and compute diffs between versions
 * - Support rollback operations
 * - Track version metadata (author, timestamp, reason)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Promise<EntityVersion> stored = versionRepository.saveVersion(
 *     "tenant-123",
 *     entity,
 *     new VersionMetadata("user-456", Instant.now(), "Updated contact info")
 * );
 * }</pre>
 *
 * <p><b>Multi-Tenancy</b><br>
 * All operations are tenant-scoped and return Promise for async execution.
 *
 * @doc.type interface
 * @doc.purpose Version repository port interface
 * @doc.layer domain
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface VersionRecord {

    /**
     * Saves a new version of an entity.
     *
     * @param tenantId the tenant ID
     * @param entity the entity to version
     * @param metadata version metadata (author, timestamp, reason)
     * @return Promise of stored EntityVersion
     */
    Promise<EntityVersion> saveVersion(String tenantId, Entity entity, VersionMetadata metadata);

    /**
     * Retrieves all versions of an entity in chronological order.
     *
     * @param tenantId the tenant ID
     * @param entityId the entity ID
     * @return Promise of list of versions (oldest first)
     */
    Promise<List<EntityVersion>> getVersionHistory(String tenantId, UUID entityId);

    /**
     * Retrieves a specific version by version number.
     *
     * @param tenantId the tenant ID
     * @param entityId the entity ID
     * @param versionNumber the version number (1-based)
     * @return Promise of EntityVersion or empty if not found
     */
    Promise<EntityVersion> getVersion(String tenantId, UUID entityId, Integer versionNumber);

    /**
     * Gets the latest version of an entity.
     *
     * @param tenantId the tenant ID
     * @param entityId the entity ID
     * @return Promise of latest EntityVersion or empty if no versions
     */
    Promise<EntityVersion> getLatestVersion(String tenantId, UUID entityId);

    /**
     * Computes diff between two versions.
     *
     * @param tenantId the tenant ID
     * @param entityId the entity ID
     * @param versionNumberFrom starting version number (inclusive)
     * @param versionNumberTo ending version number (inclusive)
     * @return Promise of VersionDiff showing changes
     */
    Promise<VersionDiff> computeDiff(
            String tenantId,
            UUID entityId,
            Integer versionNumberFrom,
            Integer versionNumberTo);

    /**
     * Counts total versions for an entity.
     *
     * @param tenantId the tenant ID
     * @param entityId the entity ID
     * @return Promise of version count
     */
    Promise<Integer> countVersions(String tenantId, UUID entityId);

    /**
     * Deletes all versions of an entity (e.g., when entity is deleted).
     *
     * @param tenantId the tenant ID
     * @param entityId the entity ID
     * @return Promise of count of deleted versions
     */
    Promise<Integer> deleteVersionHistory(String tenantId, UUID entityId);
}
