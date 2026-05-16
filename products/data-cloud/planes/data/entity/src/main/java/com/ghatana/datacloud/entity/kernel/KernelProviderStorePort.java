package com.ghatana.datacloud.entity.kernel;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port for Kernel lifecycle provider data persistence.
 *
 * <p><b>Purpose</b><br>
 * Durable storage abstraction for Kernel provider records (events, artifacts, health snapshots,
 * approvals, provenance, memory, runtime truth). Replaces in-memory gateway storage with
 * Data Cloud-backed persistence.
 *
 * <p><b>Design Pattern</b><br>
 * Hexagonal architecture port - allows multiple implementations:
 * - InMemoryKernelProviderStore (testing)
 * - JpaKernelProviderStore (database persistence)
 * - CachedKernelProviderStore (with LRU cache)
 *
 * <p><b>Multi-Tenancy</b><br>
 * All operations are tenant-scoped. Cross-tenant access prevented at port level.
 *
 * <p><b>Privacy & Retention</b><br>
 * Supports privacy classification and automatic expiration based on retention policies.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * KernelProviderRecord record = KernelProviderRecord.create(
 *     "tenant-123",
 *     "workspace-456",
 *     "project-789",
 *     "events",
 *     "ref-abc",
 *     eventData,
 *     "public",
 *     null,
 *     "user-123"
 * );
 *
 * Promise<KernelProviderRecord> saved = store.save(record);
 *
 * // Find by provider ref
 * Promise<Optional<KernelProviderRecord>> found = store.findByRef("tenant-123", "ref-abc");
 *
 * // List by provider type with filtering
 * Promise<List<KernelProviderRecord>> events = store.listByProviderType(
 *     "tenant-123",
 *     "events",
 *     Map.of("productUnitId", "pu-123"),
 *     100
 * );
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * All implementations must be thread-safe and async-safe.
 *
 * @doc.type interface
 * @doc.purpose Kernel provider data persistence port
 * @doc.layer product
 * @doc.pattern Repository Port
 */
public interface KernelProviderStorePort {

    /**
     * Saves a provider record.
     *
     * @param record provider record to save (required)
     * @return Promise resolving to saved record
     * @throws NullPointerException if record is null
     */
    Promise<KernelProviderRecord> save(KernelProviderRecord record);

    /**
     * Finds a provider record by reference ID.
     *
     * @param tenantId tenant ID (required)
     * @param providerRef provider reference (required)
     * @return Promise resolving to Optional containing record if found and not expired
     * @throws NullPointerException if tenantId or providerRef is null
     */
    Promise<Optional<KernelProviderRecord>> findByRef(String tenantId, String providerRef);

    /**
     * Finds the latest record by provider type and optional filters.
     *
     * @param tenantId tenant ID (required)
     * @param providerType provider type (required)
     * @param filters optional filter criteria (can be empty)
     * @return Promise resolving to Optional containing latest record if found and not expired
     * @throws NullPointerException if tenantId or providerType is null
     */
    Promise<Optional<KernelProviderRecord>> findLatestByProviderType(
        String tenantId,
        String providerType,
        Map<String, Object> filters
    );

    /**
     * Lists provider records by type with optional filtering.
     *
     * @param tenantId tenant ID (required)
     * @param providerType provider type (required)
     * @param filters optional filter criteria (can be empty)
     * @param limit maximum number of results (required, must be positive)
     * @return Promise resolving to list of records (never null)
     * @throws NullPointerException if tenantId or providerType is null
     * @throws IllegalArgumentException if limit ≤ 0
     */
    Promise<List<KernelProviderRecord>> listByProviderType(
        String tenantId,
        String providerType,
        Map<String, Object> filters,
        int limit
    );

    /**
     * Deletes expired records by tenant.
     *
     * <p>Used for cleanup based on retention policies.
     *
     * @param tenantId tenant ID (required)
     * @return Promise resolving to number of deleted records
     * @throws NullPointerException if tenantId is null
     */
    Promise<Long> deleteExpired(String tenantId);

    /**
     * Deletes a provider record by reference ID.
     *
     * @param tenantId tenant ID (required)
     * @param providerRef provider reference (required)
     * @return Promise resolving to true if deleted, false if not found
     * @throws NullPointerException if tenantId or providerRef is null
     */
    Promise<Boolean> deleteByRef(String tenantId, String providerRef);

    /**
     * Counts provider records by type for a tenant.
     *
     * @param tenantId tenant ID (required)
     * @param providerType provider type (required)
     * @return Promise resolving to count of records
     * @throws NullPointerException if tenantId or providerType is null
     */
    Promise<Long> countByProviderType(String tenantId, String providerType);
}
