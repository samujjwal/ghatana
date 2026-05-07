package com.ghatana.datacloud.entity.audit;

import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for audit log persistence and retrieval.
 *
 * <p>
 * <b>Purpose</b><br>
 * Extended repository interface for audit log operations including append-only
 * storage, time-range queries, and resource-based lookups.
 *
 * @see AuditLog
 * @see AuditLogPort
 * @doc.type interface
 * @doc.purpose Audit log repository with extended query capabilities
 * @doc.layer domain
 * @doc.pattern Repository
 */
public interface AuditRepository extends AuditLogPort {

    /**
     * Appends an audit log entry (append-only).
     *
     * @param auditLog the audit log to append
     * @return Promise completing when appended
     */
    Promise<Void> append(AuditLog auditLog);

    /**
     * Finds audit logs by resource.
     *
     * @param tenantId tenant identifier
     * @param resourceType type of resource
     * @param resourceId resource identifier
     * @return Promise of matching audit logs
     */
    Promise<List<AuditLog>> findByResource(String tenantId, String resourceType, String resourceId);

    /**
     * Finds audit logs in a time range.
     *
     * @param tenantId tenant identifier
     * @param start start of time range
     * @param end end of time range
     * @return Promise of matching audit logs
     */
    Promise<List<AuditLog>> findByTimeRange(String tenantId, Instant start, Instant end);

    /**
     * Finds audit logs by action type.
     *
     * @param tenantId tenant identifier
     * @param action the audit action
     * @return Promise of matching audit logs
     */
    Promise<List<AuditLog>> findByAction(String tenantId, AuditAction action);

    /**
     * Finds audit log by ID.
     *
     * @param id audit log UUID
     * @return Promise of audit log if found
     */
    Promise<Optional<AuditLog>> findById(UUID id);

    /**
     * Queries audit logs with filter criteria.
     *
     * @param filter the query filter
     * @return Promise of matching audit logs
     */
    Promise<List<AuditLog>> query(AuditQueryFilter filter);

    /**
     * Counts audit logs matching filter.
     *
     * @param filter the query filter
     * @return Promise of count
     */
    Promise<Long> count(AuditQueryFilter filter);

    /**
     * Clears all audit logs (for testing only).
     *
     * @return Promise completing when cleared
     */
    Promise<Void> clear();
}
