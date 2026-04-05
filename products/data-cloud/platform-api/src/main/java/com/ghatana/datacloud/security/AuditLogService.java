/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.security;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Service interface for audit logging.
 *
 * @doc.type interface
 * @doc.purpose Audit logging for compliance
 * @doc.layer product
 * @doc.pattern Service Interface
 */
public interface AuditLogService {

    /**
     * Log an audit event.
     *
     * @param event audit event
     * @return promise completing when logged
     */
    Promise<Void> log(AuditEvent event);

    /**
     * Query audit logs.
     *
     * @param tenantId tenant identifier
     * @param query query parameters
     * @return promise of matching events
     */
    Promise<List<AuditEvent>> query(String tenantId, AuditQuery query);

    /**
     * Get audit event by ID.
     *
     * @param eventId event identifier
     * @return promise of event if found
     */
    Promise<Optional<AuditEvent>> getEvent(String eventId);

    /**
     * Export audit logs.
     *
     * @param tenantId tenant identifier
     * @param startTime start time
     * @param endTime end time
     * @param format export format
     * @return promise of export data
     */
    Promise<byte[]> export(String tenantId, Instant startTime, Instant endTime, ExportFormat format);

    /**
     * Get retention statistics.
     *
     * @param tenantId tenant identifier
     * @return promise of retention stats
     */
    Promise<RetentionStats> getRetentionStats(String tenantId);

    /**
     * Purge old audit logs.
     *
     * @param tenantId tenant identifier
     * @param olderThan purge logs older than this
     * @return promise of purged count
     */
    Promise<Integer> purgeOldLogs(String tenantId, Instant olderThan);

    /**
     * Audit event types.
     */
    enum EventType {
        ACCESS, CREATE, UPDATE, DELETE, LOGIN, LOGOUT, EXPORT, IMPORT,
        POLICY_VIOLATION, SECURITY_ALERT, CONFIG_CHANGE
    }

    /**
     * Export formats.
     */
    enum ExportFormat {
        JSON, CSV, PDF
    }

    /**
     * Audit event.
     */
    record AuditEvent(
        String id,
        String tenantId,
        String userId,
        EventType type,
        String action,
        String resource,
        String resourceId,
        boolean success,
        Map<String, Object> details,
        String ipAddress,
        String userAgent,
        Instant timestamp
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String id;
            private String tenantId;
            private String userId;
            private EventType type;
            private String action;
            private String resource;
            private String resourceId;
            private boolean success;
            private Map<String, Object> details = Map.of();
            private String ipAddress;
            private String userAgent;
            private Instant timestamp = Instant.now();

            public Builder id(String id) { this.id = id; return this; }
            public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
            public Builder userId(String userId) { this.userId = userId; return this; }
            public Builder type(EventType type) { this.type = type; return this; }
            public Builder action(String action) { this.action = action; return this; }
            public Builder resource(String resource) { this.resource = resource; return this; }
            public Builder resourceId(String resourceId) { this.resourceId = resourceId; return this; }
            public Builder success(boolean success) { this.success = success; return this; }
            public Builder details(Map<String, Object> details) { this.details = details; return this; }
            public Builder ipAddress(String ip) { this.ipAddress = ip; return this; }
            public Builder userAgent(String ua) { this.userAgent = ua; return this; }

            public AuditEvent build() {
                return new AuditEvent(id, tenantId, userId, type, action, resource, resourceId,
                    success, details, ipAddress, userAgent, timestamp);
            }
        }
    }

    /**
     * Audit query.
     */
    record AuditQuery(
        Set<EventType> types,
        String userId,
        String resource,
        Instant startTime,
        Instant endTime,
        Boolean success,
        int limit,
        int offset
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Set<EventType> types;
            private String userId;
            private String resource;
            private Instant startTime;
            private Instant endTime;
            private Boolean success;
            private int limit = 100;
            private int offset = 0;

            public Builder types(Set<EventType> types) { this.types = types; return this; }
            public Builder userId(String userId) { this.userId = userId; return this; }
            public Builder resource(String resource) { this.resource = resource; return this; }
            public Builder startTime(Instant start) { this.startTime = start; return this; }
            public Builder endTime(Instant end) { this.endTime = end; return this; }
            public Builder success(Boolean success) { this.success = success; return this; }
            public Builder limit(int limit) { this.limit = limit; return this; }
            public Builder offset(int offset) { this.offset = offset; return this; }

            public AuditQuery build() {
                return new AuditQuery(types, userId, resource, startTime, endTime, success, limit, offset);
            }
        }
    }

    /**
     * Retention statistics.
     */
    record RetentionStats(
        long totalEvents,
        long eventsInRetention,
        long eventsPendingPurge,
        Instant oldestEvent,
        Instant newestEvent,
        long storageBytes
    ) {}
}
