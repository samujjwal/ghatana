/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * PHASE: A
 * OWNER: @platform-team
 * MIGRATED: 2026-02-04
 * DEPENDS_ON: platform:java:core
 */
package com.ghatana.platform.audit;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Interface for querying audit events.
 *
 * @doc.type interface
 * @doc.purpose Query interface for searching audit event history
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface AuditQueryService {

    /**
     * Find all audit events for a tenant.
     *
     * @param tenantId the tenant identifier
     * @return Promise of list of audit events
     */
    Promise<List<AuditEvent>> findByTenantId(String tenantId);

    /**
     * Find audit events for a tenant with pagination.
     *
     * @param tenantId the tenant identifier
     * @param offset starting position
     * @param limit maximum number of events to return
     * @return Promise of list of audit events
     */
    Promise<List<AuditEvent>> findByTenantId(String tenantId, int offset, int limit);

    /**
     * Find audit events by resource type and ID.
     *
     * @param tenantId the tenant identifier
     * @param resourceType the type of resource
     * @param resourceId the unique resource identifier
     * @return Promise of list of audit events for the resource
     */
    Promise<List<AuditEvent>> findByResource(String tenantId, String resourceType, String resourceId);

    /**
     * Find audit events by actor/principal.
     *
     * @param tenantId the tenant identifier
     * @param principal the user/actor who performed actions
     * @return Promise of list of audit events by the actor
     */
    Promise<List<AuditEvent>> findByPrincipal(String tenantId, String principal);

    /**
     * Find audit events by event type.
     *
     * @param tenantId the tenant identifier
     * @param eventType the type of event
     * @return Promise of list of audit events of the specified type
     */
    Promise<List<AuditEvent>> findByEventType(String tenantId, String eventType);

    /**
     * Find audit events within a time range.
     *
     * @param tenantId the tenant identifier
     * @param from start of time range (inclusive)
     * @param to end of time range (inclusive)
     * @return Promise of list of audit events within the range
     */
    Promise<List<AuditEvent>> findByTimeRange(String tenantId, Instant from, Instant to);

    /**
     * Find a specific audit event by ID.
     *
     * @param tenantId the tenant identifier
     * @param eventId the unique event identifier
     * @return Promise of optional audit event
     */
    Promise<Optional<AuditEvent>> findById(String tenantId, String eventId);

    /**
     * Count total audit events for a tenant.
     *
     * @param tenantId the tenant identifier
     * @return Promise of the count
     */
    Promise<Long> countByTenantId(String tenantId);

    /**
     * Search audit events with multiple criteria.
     *
     * @param tenantId the tenant identifier
     * @param criteria the search criteria
     * @return Promise of list of matching audit events
     */
    Promise<List<AuditEvent>> search(String tenantId, AuditSearchCriteria criteria);

    /**
     * Search criteria for audit events.
     */
    record AuditSearchCriteria(
            String resourceType,
            String resourceId,
            String principal,
            String eventType,
            Instant fromDate,
            Instant toDate,
            Boolean success,
            int offset,
            int limit
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String resourceType;
            private String resourceId;
            private String principal;
            private String eventType;
            private Instant fromDate;
            private Instant toDate;
            private Boolean success;
            private int offset = 0;
            private int limit = 100;

            public Builder resourceType(String resourceType) {
                this.resourceType = resourceType;
                return this;
            }

            public Builder resourceId(String resourceId) {
                this.resourceId = resourceId;
                return this;
            }

            public Builder principal(String principal) {
                this.principal = principal;
                return this;
            }

            public Builder eventType(String eventType) {
                this.eventType = eventType;
                return this;
            }

            public Builder fromDate(Instant fromDate) {
                this.fromDate = fromDate;
                return this;
            }

            public Builder toDate(Instant toDate) {
                this.toDate = toDate;
                return this;
            }

            public Builder success(Boolean success) {
                this.success = success;
                return this;
            }

            public Builder offset(int offset) {
                this.offset = offset;
                return this;
            }

            public Builder limit(int limit) {
                this.limit = limit;
                return this;
            }

            public AuditSearchCriteria build() {
                return new AuditSearchCriteria(
                        resourceType, resourceId, principal, eventType,
                        fromDate, toDate, success, offset, limit
                );
            }
        }
    }
}
