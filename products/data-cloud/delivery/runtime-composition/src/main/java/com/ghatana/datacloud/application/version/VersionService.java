package com.ghatana.datacloud.application.version;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.version.*;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;

/**
 * Application service for entity versioning and version management.
 *
 * <p><b>Purpose</b><br>
 * Handles entity versioning workflows: creating versions, retrieving history,
 * computing diffs, and supporting rollback operations.
 * Orchestrates domain models with repository and caching layers.
 *
 * <p><b>Responsibilities</b><br>
 * - Create versions when entity changes
 * - Retrieve complete version history
 * - Compare versions and compute field-level diffs
 * - Support rollback to prior versions
 * - Maintain multi-tenant isolation
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * VersionService versionService = new VersionService(versionRepository, cacheManager);
 *
 * // Create a version
 * Promise<EntityVersion> version = versionService.createVersion(
 *     "tenant-123",
 *     entity,
 *     new VersionMetadata("user-456", Instant.now(), "Updated email address")
 * );
 *
 * // Get version history
 * Promise<List<EntityVersion>> history = versionService.getVersionHistory("tenant-123", entityId);
 *
 * // Compare versions
 * Promise<VersionDiff> diff = versionService.compareVersions(
 *     "tenant-123", entityId, versionNumber1, versionNumber2
 * );
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Application service layer (orchestrates domain + infrastructure)
 * - Uses Promise for async execution
 * - Enforces multi-tenancy at every operation
 * - Provides use-case specific APIs above domain models
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe if underlying repository and cache are thread-safe.
 * Uses ActiveJ Promise for async execution without blocking.
 *
 * @doc.type class
 * @doc.purpose Entity versioning service
 * @doc.layer application
 * @doc.pattern Service (Application Layer)
 */
public class VersionService {

    private final VersionRecord versionRepository;
    private final VersionComparator versionComparator;

    /**
     * Creates a VersionService.
     *
     * @param versionRepository the version repository port
     * @param versionComparator utility for computing diffs
     */
    public VersionService(VersionRecord versionRepository, VersionComparator versionComparator) {
        this.versionRepository = Objects.requireNonNull(versionRepository, "Version repository must not be null");
        this.versionComparator = Objects.requireNonNull(versionComparator, "Version comparator must not be null");
    }

    /**
     * Creates and stores a new version of an entity.
     *
     * GIVEN: Entity updated with new values
     * WHEN: createVersion() is called
     * THEN: Version is stored and version metadata recorded
     *
     * @param tenantId the tenant ID
     * @param entity the entity to version
     * @param author the user ID making the change
     * @param reason the change reason
     * @return Promise of stored EntityVersion
     */
    public Promise<EntityVersion> createVersion(
            String tenantId,
            Entity entity,
            String author,
            String reason) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(entity, "Entity must not be null");
        Objects.requireNonNull(author, "Author must not be null");

        VersionMetadata metadata = new VersionMetadata(author, Instant.now(), reason);
        return versionRepository.saveVersion(tenantId, entity, metadata);
    }

    /**
     * Retrieves the complete version history of an entity.
     *
     * GIVEN: Entity exists with multiple versions
     * WHEN: getVersionHistory() is called
     * THEN: All versions returned in chronological order (oldest first)
     *
     * @param tenantId the tenant ID
     * @param entityId the entity ID
     * @return Promise of list of EntityVersions
     */
    public Promise<List<EntityVersion>> getVersionHistory(String tenantId, UUID entityId) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(entityId, "Entity ID must not be null");

        return versionRepository.getVersionHistory(tenantId, entityId);
    }

    /**
     * Retrieves a specific version by version number.
     *
     * GIVEN: Version exists at specified number
     * WHEN: getVersion() is called
     * THEN: Version returned with complete entity snapshot
     *
     * @param tenantId the tenant ID
     * @param entityId the entity ID
     * @param versionNumber the version number (1-based)
     * @return Promise of EntityVersion or empty Optional if not found
     */
    public Promise<EntityVersion> getVersion(String tenantId, UUID entityId, Integer versionNumber) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(entityId, "Entity ID must not be null");
        Objects.requireNonNull(versionNumber, "Version number must not be null");

        if (versionNumber < 1) {
            return Promise.ofException(
                new IllegalArgumentException("Version number must be >= 1"));
        }

        return versionRepository.getVersion(tenantId, entityId, versionNumber);
    }

    /**
     * Gets the latest version of an entity.
     *
     * GIVEN: Entity has version history
     * WHEN: getLatestVersion() is called
     * THEN: Most recent version returned
     *
     * @param tenantId the tenant ID
     * @param entityId the entity ID
     * @return Promise of latest EntityVersion or empty if no versions exist
     */
    public Promise<EntityVersion> getLatestVersion(String tenantId, UUID entityId) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(entityId, "Entity ID must not be null");

        return versionRepository.getLatestVersion(tenantId, entityId);
    }

    /**
     * Computes field-level differences between two versions.
     *
     * GIVEN: Two versions exist
     * WHEN: compareVersions() is called
     * THEN: VersionDiff returned with changed/added/removed fields
     *
     * @param tenantId the tenant ID
     * @param entityId the entity ID
     * @param fromVersion the starting version number (inclusive)
     * @param toVersion the ending version number (inclusive)
     * @return Promise of VersionDiff
     */
    public Promise<VersionDiff> compareVersions(
            String tenantId,
            UUID entityId,
            Integer fromVersion,
            Integer toVersion) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(entityId, "Entity ID must not be null");
        Objects.requireNonNull(fromVersion, "From version must not be null");
        Objects.requireNonNull(toVersion, "To version must not be null");

        if (fromVersion < 1 || toVersion < 1) {
            return Promise.ofException(
                new IllegalArgumentException("Version numbers must be >= 1"));
        }

        if (fromVersion.equals(toVersion)) {
            // Same version - no diff
            return Promise.of(VersionDiff.empty());
        }

        // Ensure from < to for consistent comparison
        Integer min = Math.min(fromVersion, toVersion);
        Integer max = Math.max(fromVersion, toVersion);

        return versionRepository.computeDiff(tenantId, entityId, min, max);
    }

    /**
     * Gets total number of versions for an entity.
     *
     * GIVEN: Entity has version history
     * WHEN: countVersions() is called
     * THEN: Integer count returned
     *
     * @param tenantId the tenant ID
     * @param entityId the entity ID
     * @return Promise of version count
     */
    public Promise<Integer> countVersions(String tenantId, UUID entityId) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(entityId, "Entity ID must not be null");

        return versionRepository.countVersions(tenantId, entityId);
    }

    /**
     * Checks if an entity has any versions.
     *
     * @param tenantId the tenant ID
     * @param entityId the entity ID
     * @return Promise of boolean (true if versions exist)
     */
    public Promise<Boolean> hasVersions(String tenantId, UUID entityId) {
        return countVersions(tenantId, entityId)
            .map(count -> count > 0);
    }

    /**
     * Deletes all version history for an entity (e.g., when entity is deleted).
     *
     * GIVEN: Entity to be deleted
     * WHEN: deleteVersionHistory() is called
     * THEN: All versions removed from storage
     *
     * @param tenantId the tenant ID
     * @param entityId the entity ID
     * @return Promise of count of deleted versions
     */
    public Promise<Integer> deleteVersionHistory(String tenantId, UUID entityId) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(entityId, "Entity ID must not be null");

        return versionRepository.deleteVersionHistory(tenantId, entityId);
    }
}
