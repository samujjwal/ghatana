package com.ghatana.datacloud.entity.audit;

import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;

/**
 * Port for audit log persistence and retrieval.
 *
 * <p><b>Purpose</b><br>
 * Abstraction for storing and querying audit logs. Enables multiple implementations
 * (in-memory for testing, database for production, event store, etc.).
 *
 * <p><b>Design Pattern</b><br>
 * Hexagonal architecture port - allows swapping implementations:
 * - InMemoryAuditStore (testing)
 * - JpaAuditStore (database persistence)
 * - EventStoreAuditAdapter (event sourcing)
 * - CachedAuditStore (with LRU cache)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AuditLog log = AuditLog.builder()
 *     .tenantId("tenant-123")
 *     .userId("user-456")
 *     .action(AuditAction.CREATE_ENTITY)
 *     .resourceType("entity")
 *     .resourceId("entity-789")
 *     .build();
 *
 * Promise<Void> saved = auditStore.save(log);
 * saved.then(() -> {
 *     Promise<List<AuditLog>> logs = auditStore.findByTenantAndUser("tenant-123", "user-456");
 *     logs.then(list -> System.out.println("User actions: " + list.size()));
 * });
 * }</pre>
 *
 * <p><b>Multi-Tenancy</b><br>
 * All queries automatically scoped to tenant. Cross-tenant access prevented at port level.
 *
 * <p><b>Thread Safety</b><br>
 * All implementations must be thread-safe and async-safe.
 *
 * @doc.type interface
 * @doc.purpose Audit log persistence port (repository abstraction)
 * @doc.layer domain
 * @doc.pattern Port
 */
public interface AuditLogPort {

    /**
     * Saves audit log entry.
     *
     * @param auditLog audit log to save (required)
     * @return Promise completing when save finished
     * @throws NullPointerException if auditLog is null
     */
    Promise<Void> save(AuditLog auditLog);

    /**
     * Finds audit logs by tenant ID.
     *
     * @param tenantId tenant ID (required)
     * @param limit maximum number of results (required, must be positive)
     * @param offset offset from beginning (required, must be non-negative)
     * @return Promise resolving to list of audit logs (never null)
     * @throws NullPointerException if tenantId is null
     * @throws IllegalArgumentException if limit ≤ 0 or offset < 0
     */
    Promise<List<AuditLog>> findByTenant(String tenantId, int limit, int offset);

    /**
     * Finds audit logs by tenant and user.
     *
     * @param tenantId tenant ID (required)
     * @param userId user ID (required)
     * @return Promise resolving to list of audit logs (never null)
     * @throws NullPointerException if tenantId or userId is null
     */
    Promise<List<AuditLog>> findByTenantAndUser(String tenantId, String userId);

    /**
     * Finds audit logs by tenant and resource.
     *
     * @param tenantId tenant ID (required)
     * @param resourceType resource type (required)
     * @param resourceId resource ID (required)
     * @return Promise resolving to list of audit logs (never null)
     * @throws NullPointerException if any parameter is null
     */
    Promise<List<AuditLog>> findByResource(String tenantId, String resourceType, String resourceId);

    /**
     * Finds audit logs by tenant and action.
     *
     * @param tenantId tenant ID (required)
     * @param action action to filter by (required)
     * @return Promise resolving to list of audit logs (never null)
     * @throws NullPointerException if tenantId or action is null
     */
    Promise<List<AuditLog>> findByAction(String tenantId, AuditAction action);

    /**
     * Finds audit logs by tenant and date range.
     *
     * @param tenantId tenant ID (required)
     * @param startTime start of time range (required)
     * @param endTime end of time range (required)
     * @return Promise resolving to list of audit logs (never null)
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if startTime > endTime
     */
    Promise<List<AuditLog>> findByDateRange(String tenantId, Instant startTime, Instant endTime);

    /**
     * Counts audit logs by tenant.
     *
     * @param tenantId tenant ID (required)
     * @return Promise resolving to count of audit logs
     * @throws NullPointerException if tenantId is null
     */
    Promise<Long> countByTenant(String tenantId);

    /**
     * Deletes old audit logs by tenant and retention period.
     *
     * <p>Deletes logs older than the specified retention period.
     * Used for data cleanup and compliance with retention policies.
     *
     * @param tenantId tenant ID (required)
     * @param retentionDays number of days to retain (required, must be positive)
     * @return Promise resolving to number of deleted logs
     * @throws NullPointerException if tenantId is null
     * @throws IllegalArgumentException if retentionDays ≤ 0
     */
    Promise<Long> deleteOlderThan(String tenantId, int retentionDays);

    /**
     * Exports audit logs for tenant to safe format.
     *
     * <p>Returns logs as JSON for external storage/archival.
     *
     * @param tenantId tenant ID (required)
     * @param startTime start of time range (required)
     * @param endTime end of time range (required)
     * @return Promise resolving to JSON string
     * @throws NullPointerException if any parameter is null
     */
    Promise<String> exportAsJson(String tenantId, Instant startTime, Instant endTime);
}
