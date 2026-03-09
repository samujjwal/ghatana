package com.ghatana.datacloud.application.audit.legacy;

import com.ghatana.datacloud.entity.audit.AuditAction;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Legacy port interface for audit log persistence used by AuditService.
 *
 * <p>
 * Newer code should prefer domain-layer ports instead.
 
 *
 * @doc.type interface
 * @doc.purpose Audit repository
 * @doc.layer platform
 * @doc.pattern Repository
*/
public interface AuditRepository {

    Promise<AuditLog> append(AuditLog entry);

    Promise<Integer> appendBatch(List<AuditLog> entries);

    Promise<AuditLog> findById(UUID id, String tenantId);

    Promise<List<AuditLog>> findByTenant(String tenantId);

    Promise<List<AuditLog>> findByResource(String tenantId, String resourceType, String resourceId);

    Promise<List<AuditLog>> findByAction(String tenantId, AuditAction action);

    Promise<List<AuditLog>> findByUser(String tenantId, String userId);

    Promise<List<AuditLog>> findByTimeRange(String tenantId, Instant startTime, Instant endTime);

    Promise<List<AuditLog>> findByFilters(String tenantId, AuditQueryFilter filters);

    Promise<Long> countByTenant(String tenantId);

    Promise<Long> countByResource(String tenantId, String resourceType, String resourceId);

    Promise<Long> deleteOlderThan(String tenantId, Instant before);

    final class AuditQueryFilter {
        public final String resourceType; // Optional
        public final AuditAction action; // Optional
        public final String userId; // Optional
        public final AuditLog.AuditStatus status; // Optional
        public final Integer limit; // Optional, default 1000
        public final Integer offset; // Optional, default 0

        public AuditQueryFilter(
                String resourceType,
                AuditAction action,
                String userId,
                AuditLog.AuditStatus status,
                Integer limit,
                Integer offset) {
            this.resourceType = resourceType;
            this.action = action;
            this.userId = userId;
            this.status = status;
            this.limit = limit != null ? limit : 1000;
            this.offset = offset != null ? offset : 0;
        }
    }
}
