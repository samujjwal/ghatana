package com.ghatana.datacloud.application.audit;

import com.ghatana.datacloud.entity.audit.AuditAction;
import com.ghatana.datacloud.application.audit.legacy.AuditLog;
import com.ghatana.datacloud.application.audit.legacy.AuditRepository;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Application service for audit trail operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Orchestrates audit logging with async persistence, metrics tracking, and bulk
 * operations. Provides high-level audit API for business logic while delegating
 * storage to pluggable AuditRepository.
 *
 * <p>
 * <b>Usage</b><br>
 * 
 * <pre>{@code
 * AuditService audit = new AuditService(repository, metrics);
 *
 * // Log collection creation
 * Promise<AuditLog> created = audit.logCreate(
 *         "tenant-1", "collection", "coll-123", "user-456",
 *         Map.of("name", "Products"));
 *
 * // Log content indexing
 * Promise<AuditLog> indexed = audit.logIndex(
 *         "tenant-1", "content", "content-789", "indexer-bot");
 *
 * // Query audit trail
 * Promise<List<AuditLog>> logs = audit.getResourceHistory(
 *         "tenant-1", "collection", "coll-123");
 * }</pre>
 *
 * <p>
 * <b>Features</b><br>
 * - High-level audit logging methods for common operations - Async
 * Promise-based API (non-blocking) - Metrics tracking (latency, error rates) -
 * Batch audit operations for bulk changes - Tenant-scoped queries - Structured
 * detail maps for forensic analysis
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe (reads immutable fields, delegates to repository).
 *
 * @see AuditLog
 * @see AuditRepository
 * @doc.type class
 * @doc.purpose Application service for audit trail operations
 * @doc.layer product
 * @doc.pattern Service
 */
public final class AuditService {

    private final AuditRepository repository;
    private final MetricsCollector metrics;

    /**
     * Constructs AuditService with dependencies.
     *
     * @param repository audit persistence implementation
     * @param metrics    metrics collector for observability
     * @throws NullPointerException if any dependency is null
     */
    public AuditService(AuditRepository repository, MetricsCollector metrics) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
    }

    // ========== High-Level Audit Methods ==========
    /**
     * Logs resource creation.
     *
     * <p>
     * Records CREATE action with optional details about new resource
     * properties. Useful for tracking resource instantiation.
     *
     * @param tenantId     tenant scope
     * @param resourceType resource type (collection, content, config)
     * @param resourceId   resource ID
     * @param userId       user ID
     * @param details      creation details (optional)
     * @return Promise of audit log entry
     */
    public Promise<AuditLog> logCreate(
            String tenantId, String resourceType, String resourceId, String userId,
            Map<String, Object> details) {
        long startTime = System.currentTimeMillis();

        AuditLog entry = AuditLog.builder()
                .tenantId(tenantId)
                .action(AuditAction.CREATE_COLLECTION)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .userId(userId)
                .details(details)
                .build();

        return repository.append(entry)
                .map(logged -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("audit.log_duration_ms", duration, "action", "CREATE");
                    metrics.incrementCounter("audit.created", "resource_type", resourceType, "tenant", tenantId);
                    return logged;
                })
                .whenException(e -> {
                    metrics.incrementCounter("audit.error", "action", "CREATE", "error", e.getClass().getSimpleName());
                });
    }

    /**
     * Logs resource update.
     *
     * <p>
     * Records UPDATE action with details about what changed. Details map
     * typically contains old/new values for changed fields.
     *
     * @param tenantId     tenant scope
     * @param resourceType resource type
     * @param resourceId   resource ID
     * @param userId       user ID
     * @param changes      what changed (field names → new values)
     * @return Promise of audit log entry
     */
    public Promise<AuditLog> logUpdate(
            String tenantId, String resourceType, String resourceId, String userId,
            Map<String, Object> changes) {
        long startTime = System.currentTimeMillis();

        AuditLog entry = AuditLog.builder()
                .tenantId(tenantId)
                .action(AuditAction.UPDATE_COLLECTION)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .userId(userId)
                .details(changes)
                .build();

        return repository.append(entry)
                .map(logged -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("audit.log_duration_ms", duration, "action", "UPDATE");
                    metrics.incrementCounter("audit.updated", "resource_type", resourceType, "tenant", tenantId);
                    return logged;
                })
                .whenException(e -> {
                    metrics.incrementCounter("audit.error", "action", "UPDATE", "error", e.getClass().getSimpleName());
                });
    }

    /**
     * Logs resource deletion.
     *
     * <p>
     * Records DELETE action. Details may include soft/hard delete info.
     *
     * @param tenantId     tenant scope
     * @param resourceType resource type
     * @param resourceId   resource ID
     * @param userId       user ID
     * @param reason       deletion reason (optional)
     * @return Promise of audit log entry
     */
    public Promise<AuditLog> logDelete(
            String tenantId, String resourceType, String resourceId, String userId, String reason) {
        long startTime = System.currentTimeMillis();

        Map<String, Object> details = reason != null ? Map.of("reason", reason) : Map.of();

        AuditLog entry = AuditLog.builder()
                .tenantId(tenantId)
                .action(AuditAction.DELETE_COLLECTION)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .userId(userId)
                .details(details)
                .build();

        return repository.append(entry)
                .map(logged -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("audit.log_duration_ms", duration, "action", "DELETE");
                    metrics.incrementCounter("audit.deleted", "resource_type", resourceType, "tenant", tenantId);
                    return logged;
                })
                .whenException(e -> {
                    metrics.incrementCounter("audit.error", "action", "DELETE", "error", e.getClass().getSimpleName());
                });
    }

    /**
     * Logs document indexing.
     *
     * <p>
     * Records search indexing operations.
     *
     * @param tenantId  tenant scope
     * @param contentId content ID
     * @param userId    user ID (often "indexer-bot" for automated)
     * @param details   indexing details (indexed fields, vector dimensions)
     * @return Promise of audit log entry
     */
    public Promise<AuditLog> logIndex(
            String tenantId, String contentId, String userId, Map<String, Object> details) {
        long startTime = System.currentTimeMillis();

        AuditLog entry = AuditLog.builder()
                .tenantId(tenantId)
                .action(AuditAction.CREATE_ENTITY)
                .resourceType("content")
                .resourceId(contentId)
                .userId(userId)
                .details(details)
                .build();

        return repository.append(entry)
                .map(logged -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("audit.log_duration_ms", duration, "action", "INDEX");
                    metrics.incrementCounter("audit.indexed", "tenant", tenantId);
                    return logged;
                })
                .whenException(e -> {
                    metrics.incrementCounter("audit.error", "action", "INDEX", "error", e.getClass().getSimpleName());
                });
    }

    /**
     * Logs search query.
     *
     * <p>
     * Records search operations for query analysis.
     *
     * @param tenantId    tenant scope
     * @param userId      user ID
     * @param query       search query text
     * @param resultCount results returned
     * @return Promise of audit log entry
     */
    public Promise<AuditLog> logSearch(
            String tenantId, String userId, String query, int resultCount) {
        long startTime = System.currentTimeMillis();

        AuditLog entry = AuditLog.builder()
                .tenantId(tenantId)
                .action(AuditAction.ACCESS_GRANTED)
                .resourceType("search")
                .resourceId("query")
                .userId(userId)
                .details(Map.of("query", query, "result_count", resultCount))
                .build();

        return repository.append(entry)
                .map(logged -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("audit.log_duration_ms", duration, "action", "SEARCH");
                    metrics.incrementCounter("audit.searches", "tenant", tenantId);
                    return logged;
                })
                .whenException(e -> {
                    metrics.incrementCounter("audit.error", "action", "SEARCH", "error", e.getClass().getSimpleName());
                });
    }

    /**
     * Logs operation error.
     *
     * <p>
     * Records when operation failed. Useful for debugging and forensics.
     *
     * @param tenantId     tenant scope
     * @param resourceType resource type
     * @param resourceId   resource ID
     * @param userId       user ID
     * @param error        error message
     * @return Promise of audit log entry
     */
    public Promise<AuditLog> logError(
            String tenantId, String resourceType, String resourceId, String userId, String error) {
        long startTime = System.currentTimeMillis();

        AuditLog entry = AuditLog.builder()
                .tenantId(tenantId)
                .action(AuditAction.ACCESS_DENIED)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .userId(userId)
                .status(AuditLog.AuditStatus.FAILURE)
                .details(Map.of("error", error))
                .build();

        return repository.append(entry)
                .map(logged -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("audit.log_duration_ms", duration, "action", "ERROR");
                    metrics.incrementCounter("audit.errors_logged", "resource_type", resourceType, "tenant", tenantId);
                    return logged;
                })
                .whenException(e -> {
                    metrics.incrementCounter("audit.log_error", "error", e.getClass().getSimpleName());
                });
    }

    // ========== Query Operations ==========
    /**
     * Gets resource change history.
     *
     * <p>
     * Returns all audit entries for specific resource ordered by timestamp.
     * Useful for tracking complete lifecycle of resource.
     *
     * @param tenantId     tenant scope
     * @param resourceType resource type
     * @param resourceId   resource ID
     * @return Promise of audit logs for resource
     */
    public Promise<List<AuditLog>> getResourceHistory(
            String tenantId, String resourceType, String resourceId) {
        long startTime = System.currentTimeMillis();

        return repository.findByResource(tenantId, resourceType, resourceId)
                .map(logs -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("audit.query_duration_ms", duration, "query_type", "history");
                    metrics.incrementCounter("audit.queries", "query_type", "history", "tenant", tenantId);
                    return logs;
                })
                .whenException(e -> {
                    metrics.incrementCounter("audit.query_error", "query_type", "history", "error",
                            e.getClass().getSimpleName());
                });
    }

    /**
     * Gets user activity log.
     *
     * <p>
     * All actions performed by specific user within tenant.
     *
     * @param tenantId tenant scope
     * @param userId   user ID
     * @return Promise of user's audit logs
     */
    public Promise<List<AuditLog>> getUserActivity(String tenantId, String userId) {
        long startTime = System.currentTimeMillis();

        return repository.findByUser(tenantId, userId)
                .map(logs -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("audit.query_duration_ms", duration, "query_type", "user_activity");
                    return logs;
                })
                .whenException(e -> {
                    metrics.incrementCounter("audit.query_error", "query_type", "user_activity", "error",
                            e.getClass().getSimpleName());
                });
    }

    /**
     * Gets audit entries in time range.
     *
     * <p>
     * Useful for incident investigation - find all changes during outage
     * window.
     *
     * @param tenantId  tenant scope
     * @param startTime range start
     * @param endTime   range end
     * @return Promise of audit logs in range
     */
    public Promise<List<AuditLog>> getChangeLog(String tenantId, Instant startTime, Instant endTime) {
        long queryStartTime = System.currentTimeMillis();

        return repository.findByTimeRange(tenantId, startTime, endTime)
                .map(logs -> {
                    long duration = System.currentTimeMillis() - queryStartTime;
                    metrics.recordTimer("audit.query_duration_ms", duration, "query_type", "time_range");
                    return logs;
                })
                .whenException(e -> {
                    metrics.incrementCounter("audit.query_error", "query_type", "time_range", "error",
                            e.getClass().getSimpleName());
                });
    }

    /**
     * Logs batch audit entries.
     *
     * <p>
     * For bulk operations that generate multiple audit events. More efficient
     * than individual appends.
     *
     * @param entries audit entries to log
     * @return Promise of count of logged entries
     */
    public Promise<Integer> logBatch(List<AuditLog> entries) {
        long startTime = System.currentTimeMillis();

        if (entries == null || entries.isEmpty()) {
            return Promise.of(0);
        }

        return repository.appendBatch(entries)
                .map(count -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("audit.batch_duration_ms", duration, "count", String.valueOf(count));
                    metrics.incrementCounter("audit.batch_logged", "count", String.valueOf(count));
                    return count;
                })
                .whenException(e -> {
                    metrics.incrementCounter("audit.batch_error", "error", e.getClass().getSimpleName());
                });
    }

    /**
     * Gets audit statistics for tenant.
     *
     * @param tenantId tenant scope
     * @return Promise of total audit log count
     */
    public Promise<Long> getAuditCount(String tenantId) {
        return repository.countByTenant(tenantId);
    }
}
