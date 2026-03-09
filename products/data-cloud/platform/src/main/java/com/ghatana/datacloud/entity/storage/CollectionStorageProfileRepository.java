package com.ghatana.datacloud.entity.storage;

import com.ghatana.platform.types.identity.Identifier;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository abstraction for collection storage profile persistence.
 *
 * <p><b>Purpose</b><br>
 * Provides data access abstraction for CollectionStorageProfile persistence,
 * enabling multiple backend implementations (in-memory, database, cache).
 * Enforces tenant isolation across all operations.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Get repository instance from DI container
 * CollectionStorageProfileRepository repository = // injected
 *
 * // Save new profile
 * CollectionStorageProfile profile = TestDataBuilders.collectionStorageProfile()
 *     .tenantId("tenant-123")
 *     .collectionName("products")
 *     .build();
 * Promise<CollectionStorageProfile> saved = repository.save(profile);
 *
 * // Query by tenant and collection
 * Promise<Optional<CollectionStorageProfile>> found =
 *     repository.findByTenantAndName("tenant-123", "products");
 *
 * // List all for tenant
 * Promise<List<CollectionStorageProfile>> all =
 *     repository.findAllByTenant("tenant-123");
 *
 * // Delete specific profile
 * Promise<Void> deleted =
 *     repository.deleteByTenantAndName("tenant-123", "products");
 * }</pre>
 *
 * <p><b>Tenant Isolation</b><br>
 * All operations are tenant-scoped. The repository automatically filters
 * by tenantId parameter to prevent cross-tenant access.
 * The repository does NOT support cross-tenant operations.
 *
 * <p><b>Async Model</b><br>
 * All operations are Promise-based for ActiveJ integration:
 * - save(profile): Save new or update existing profile
 * - findByTenantAndName(tenantId, name): Find single profile by tenant and name
 * - findAllByTenant(tenantId): List all profiles for tenant
 * - deleteByTenantAndName(tenantId, name): Delete specific profile
 *
 * <p><b>Implementation Considerations</b><br>
 * - Implementations must enforce (tenantId, collectionName) uniqueness per storage backend
 * - Implementations must return empty Optional for not-found, not exception
 * - Implementations must handle concurrent access safely (thread-safe)
 * - Implementations must support TTL-based expiration if caching
 * - Implementations should emit metrics for cache hits/misses
 *
 * @see CollectionStorageProfile
 * @see io.activej.promise.Promise
 * @see java.util.Optional
 * @doc.type interface
 * @doc.purpose Data access abstraction for collection storage profiles
 * @doc.layer core
 * @doc.pattern Repository Port
 */
public interface CollectionStorageProfileRepository {

    /**
     * Saves collection storage profile (insert or update).
     *
     * <p>If profile with same (tenantId, collectionName) exists, updates it.
     * Otherwise, creates new profile with generated ID.
     *
     * <p>Enforces tenant isolation: profile must be for specified tenant.
     *
     * @param profile the profile to save (must have tenantId)
     * @return Promise resolving to saved profile with ID set
     * @throws IllegalArgumentException if profile has no tenantId
     * @see CollectionStorageProfile
     */
    Promise<CollectionStorageProfile> save(CollectionStorageProfile profile);

    /**
     * Retrieves collection storage profile by tenant and collection name.
     *
     * <p>Returns empty Optional if profile not found for this tenant/name.
     * Does not throw exception for missing profiles.
     *
     * @param tenantId tenant identifier
     * @param collectionName name of collection
     * @return Promise resolving to Optional with profile or empty if not found
     * @throws IllegalArgumentException if tenantId or collectionName is null/blank
     */
    Promise<Optional<CollectionStorageProfile>> findByTenantAndName(
        String tenantId,
        String collectionName
    );

    /**
     * Retrieves profile by its ID within tenant scope.
     *
     * <p>Enforces tenant isolation: will not return profiles from other tenants.
     *
     * @param tenantId tenant identifier
     * @param profileId profile identifier
     * @return Promise resolving to Optional with profile or empty if not found
     * @throws IllegalArgumentException if tenantId or profileId is null
     */
    Promise<Optional<CollectionStorageProfile>> findByTenantAndId(
        String tenantId,
        UUID profileId
    );

    /**
     * Lists all collection storage profiles for specified tenant.
     *
     * <p>Returns empty list if tenant has no profiles.
     * Results are tenant-scoped: will never include profiles from other tenants.
     *
     * @param tenantId tenant identifier
     * @return Promise resolving to List of profiles (may be empty)
     * @throws IllegalArgumentException if tenantId is null/blank
     */
    Promise<List<CollectionStorageProfile>> findAllByTenant(String tenantId);

    /**
     * Lists all profiles for tenant filtered by whether they have failover support.
     *
     * <p>Failover support means hasFailoverSupport() returns true.
     *
     * @param tenantId tenant identifier
     * @param hasFailover true to get profiles with failover, false for non-failover
     * @return Promise resolving to filtered list
     */
    Promise<List<CollectionStorageProfile>> findByTenantAndFailoverSupport(
        String tenantId,
        boolean hasFailover
    );

    /**
     * Deletes collection storage profile by tenant and collection name.
     *
     * <p>Returns successfully (no error) if profile doesn't exist.
     * Enforces tenant isolation: only deletes for specified tenant.
     *
     * @param tenantId tenant identifier
     * @param collectionName name of collection
     * @return Promise resolving to void on success
     * @throws IllegalArgumentException if tenantId or collectionName is null/blank
     */
    Promise<Void> deleteByTenantAndName(String tenantId, String collectionName);

    /**
     * Deletes profile by ID within tenant scope.
     *
     * <p>Returns successfully if profile doesn't exist.
     * Enforces tenant isolation: only deletes for specified tenant.
     *
     * @param tenantId tenant identifier
     * @param profileId profile identifier to delete
     * @return Promise resolving to void on success
     * @throws IllegalArgumentException if tenantId or profileId is null
     */
    Promise<Void> deleteByTenantAndId(String tenantId, UUID profileId);

    /**
     * Counts total profiles for tenant.
     *
     * @param tenantId tenant identifier
     * @return Promise resolving to count (0 if no profiles)
     * @throws IllegalArgumentException if tenantId is null/blank
     */
    Promise<Long> countByTenant(String tenantId);

    /**
     * Checks if profile exists for tenant and collection.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @return Promise resolving to true if exists, false otherwise
     * @throws IllegalArgumentException if tenantId or collectionName is null/blank
     */
    Promise<Boolean> existsByTenantAndName(String tenantId, String collectionName);

    /**
     * Deletes all profiles for tenant (destructive operation).
     *
     * <p><b>WARNING</b>: This is a destructive operation that deletes all
     * collection profiles for a tenant. Should only be called during tenant
     * deprovisioning or cleanup.
     *
     * @param tenantId tenant identifier
     * @return Promise resolving to count of deleted profiles
     * @throws IllegalArgumentException if tenantId is null/blank
     */
    Promise<Long> deleteAllByTenant(String tenantId);
}
