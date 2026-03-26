package com.ghatana.datacloud.application.audit;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.entity.audit.AuditAction;
import com.ghatana.datacloud.entity.audit.AuditLog;
import com.ghatana.datacloud.entity.audit.AuditLogPort;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;

/**
 * Application service for audit logging.
 *
 * <p>
 * <b>Purpose</b><br>
 * Handles creation and querying of audit logs. Tracks all significant
 * operations for compliance, forensics, and debugging. Integrates with metrics
 * for monitoring.
 *
 * <p>
 * <b>Audit Trail Policy</b><br>
 * - Logs all create/update/delete operations on collections and entities - Logs
 * all policy violations - Logs all user management operations - Logs all access
 * control decisions - Tracks IP address and user agent when available -
 * Multi-tenant isolation enforced
 *
 * <p>
 * <b>Usage</b><br>
 * 
 * <pre>{@code
 * AuditingService auditing = new AuditingService(auditStore, metricsCollector);
 *
 * // Log an action
 * Promise<Void> saved = auditing.logAction(userContext, AuditAction.CREATE_ENTITY,
 *         "entity", "entity-123", "collection-456",
 *         Map.of("name", Map.entry("", "New Product")));
 *
 * // Query audit logs
 * Promise<List<AuditLog>> logs = auditing.getUserActivity("tenant-1", "user-1");
 * }</pre>
 *
 * <p>
 * <b>Performance</b><br>
 * O(1) audit log creation. Query performance depends on implementation. All
 * operations are async via Promise.
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe. Can be safely reused across multiple threads.
 *
 * @doc.type class
 * @doc.purpose Audit logging application service
 * @doc.layer application
 * @doc.pattern Service
 */
public class AuditingService {

    private final AuditLogPort auditStore;
    private final MetricsCollector metricsCollector;

    /**
     * Constructs AuditingService.
     *
     * @param auditStore       audit log store (required)
     * @param metricsCollector metrics collector (required)
     * @throws NullPointerException if any parameter is null
     */
    public AuditingService(AuditLogPort auditStore, MetricsCollector metricsCollector) {
        this.auditStore = Objects.requireNonNull(auditStore, "auditStore cannot be null");
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector cannot be null");
    }

    /**
     * Logs an action for audit trail.
     *
     * @param tenantId     tenant ID (required)
     * @param userId       user ID who performed action (required)
     * @param action       action performed (required)
     * @param resourceType type of resource (e.g., "entity", "collection")
     *                     (required)
     * @param resourceId   ID of resource (required)
     * @return Promise completing when log saved
     * @throws NullPointerException if any required parameter is null
     */
    public Promise<Void> logAction(
            String tenantId,
            String userId,
            AuditAction action,
            String resourceType,
            String resourceId) {
        return logAction(tenantId, userId, action, resourceType, resourceId, null, Map.of(), null, null, null);
    }

    /**
     * Logs an action with changes for audit trail.
     *
     * @param tenantId     tenant ID (required)
     * @param userId       user ID who performed action (required)
     * @param action       action performed (required)
     * @param resourceType type of resource (required)
     * @param resourceId   ID of resource (required)
     * @param collectionId collection ID if applicable (optional)
     * @param changes      field changes (old value → new value) (required)
     * @return Promise completing when log saved
     * @throws NullPointerException if any required parameter is null
     */
    public Promise<Void> logAction(
            String tenantId,
            String userId,
            AuditAction action,
            String resourceType,
            String resourceId,
            String collectionId,
            Map<String, Map.Entry<String, String>> changes) {
        return logAction(tenantId, userId, action, resourceType, resourceId, collectionId, changes, null, null, null);
    }

    /**
     * Logs an action with full details for audit trail.
     *
     * @param tenantId     tenant ID (required)
     * @param userId       user ID who performed action (required)
     * @param action       action performed (required)
     * @param resourceType type of resource (required)
     * @param resourceId   ID of resource (required)
     * @param collectionId collection ID if applicable (optional)
     * @param changes      field changes (required)
     * @param details      additional details about action (optional)
     * @param ipAddress    IP address of requester (optional)
     * @param userAgent    user agent of requester (optional)
     * @return Promise completing when log saved
     * @throws NullPointerException if any required parameter is null
     */
    public Promise<Void> logAction(
            String tenantId,
            String userId,
            AuditAction action,
            String resourceType,
            String resourceId,
            String collectionId,
            Map<String, Map.Entry<String, String>> changes,
            String details,
            String ipAddress,
            String userAgent) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(resourceType, "resourceType cannot be null");
        Objects.requireNonNull(resourceId, "resourceId cannot be null");

        AuditLog.Builder builder = AuditLog.builder()
                .tenantId(tenantId)
                .userId(userId)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId);

        if (collectionId != null) {
            builder.collectionId(collectionId);
        }
        if (changes != null && !changes.isEmpty()) {
            builder.changes(changes);
        }
        if (details != null && !details.isBlank()) {
            builder.details(details);
        }
        if (ipAddress != null) {
            builder.ipAddress(ipAddress);
        }
        if (userAgent != null) {
            builder.userAgent(userAgent);
        }

        AuditLog log = builder
                .timestamp(Instant.now())
                .build();

        metricsCollector.incrementCounter("audit.action_logged",
                "tenant", tenantId,
                "action", action.getActionId(),
                "resource_type", resourceType);

        return auditStore.save(log);
    }

    /**
     * Gets audit logs for specific user.
     *
     * @param tenantId tenant ID (required)
     * @param userId   user ID (required)
     * @return Promise resolving to list of user's audit logs
     * @throws NullPointerException if any parameter is null
     */
    public Promise<List<AuditLog>> getUserActivity(String tenantId, String userId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");

        metricsCollector.incrementCounter("audit.query_user_activity",
                "tenant", tenantId,
                "user", userId);

        return auditStore.findByTenantAndUser(tenantId, userId);
    }

    /**
     * Gets audit logs for specific resource.
     *
     * @param tenantId     tenant ID (required)
     * @param resourceType resource type (required)
     * @param resourceId   resource ID (required)
     * @return Promise resolving to list of resource's audit logs
     * @throws NullPointerException if any parameter is null
     */
    public Promise<List<AuditLog>> getResourceAuditTrail(String tenantId, String resourceType, String resourceId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(resourceType, "resourceType cannot be null");
        Objects.requireNonNull(resourceId, "resourceId cannot be null");

        metricsCollector.incrementCounter("audit.query_resource",
                "tenant", tenantId,
                "resource_type", resourceType);

        return auditStore.findByResource(tenantId, resourceType, resourceId);
    }

    /**
     * Gets audit logs for specific action type.
     *
     * @param tenantId tenant ID (required)
     * @param action   action to filter by (required)
     * @return Promise resolving to list of audit logs for action
     * @throws NullPointerException if any parameter is null
     */
    public Promise<List<AuditLog>> getActionLogs(String tenantId, AuditAction action) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(action, "action cannot be null");

        metricsCollector.incrementCounter("audit.query_action",
                "tenant", tenantId,
                "action", action.getActionId());

        return auditStore.findByAction(tenantId, action);
    }

    /**
     * Gets audit logs within date range.
     *
     * @param tenantId  tenant ID (required)
     * @param startTime start of date range (required)
     * @param endTime   end of date range (required)
     * @return Promise resolving to list of audit logs in range
     * @throws NullPointerException     if any parameter is null
     * @throws IllegalArgumentException if startTime > endTime
     */
    public Promise<List<AuditLog>> getAuditTrailByDateRange(String tenantId, Instant startTime, Instant endTime) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(startTime, "startTime cannot be null");
        Objects.requireNonNull(endTime, "endTime cannot be null");
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("startTime cannot be after endTime");
        }

        metricsCollector.incrementCounter("audit.query_date_range",
                "tenant", tenantId);

        return auditStore.findByDateRange(tenantId, startTime, endTime);
    }

    /**
     * Gets total audit log count for tenant.
     *
     * @param tenantId tenant ID (required)
     * @return Promise resolving to count of audit logs
     * @throws NullPointerException if tenantId is null
     */
    public Promise<Long> getAuditLogCount(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");

        metricsCollector.incrementCounter("audit.query_count",
                "tenant", tenantId);

        return auditStore.countByTenant(tenantId);
    }

    /**
     * Exports audit logs for tenant as JSON.
     *
     * @param tenantId  tenant ID (required)
     * @param startTime start of date range (required)
     * @param endTime   end of date range (required)
     * @return Promise resolving to JSON string of audit logs
     * @throws NullPointerException if any parameter is null
     */
    public Promise<String> exportAuditLogs(String tenantId, Instant startTime, Instant endTime) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(startTime, "startTime cannot be null");
        Objects.requireNonNull(endTime, "endTime cannot be null");

        metricsCollector.incrementCounter("audit.export",
                "tenant", tenantId);

        return auditStore.exportAsJson(tenantId, startTime, endTime);
    }

    /**
     * Deletes old audit logs to free space and comply with retention policy.
     *
     * @param tenantId      tenant ID (required)
     * @param retentionDays number of days to retain (required, must be
     *                      positive)
     * @return Promise resolving to number of deleted logs
     * @throws NullPointerException     if tenantId is null
     * @throws IllegalArgumentException if retentionDays ≤ 0
     */
    public Promise<Long> cleanupOldLogs(String tenantId, int retentionDays) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("retentionDays must be positive");
        }

        return auditStore.deleteOlderThan(tenantId, retentionDays)
                .whenResult(deleted -> metricsCollector.incrementCounter("audit.cleanup",
                        "tenant", tenantId,
                        "deleted_count", String.valueOf(deleted)));
    }
}
