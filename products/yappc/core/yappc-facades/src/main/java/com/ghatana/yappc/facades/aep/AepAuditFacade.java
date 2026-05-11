/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.facades.aep;

import com.ghatana.yappc.facades.common.TenantScopedRequest;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * YAPPC-facing facade for AEP audit operations.
 *
 * Provides a typed interface for YAPPC to interact with AEP audit logging
 * without direct dependencies on AEP internals.
 *
 * @doc.type interface
 * @doc.purpose YAPPC facade for AEP audit operations
 * @doc.layer product
 * @doc.pattern Facade
 */
public interface AepAuditFacade {

    /**
     * Log an audit event.
     *
     * @param request The audit log request
     * @return Promise containing the audit ID
     */
    Promise<String> logAuditEvent(AuditLogRequest request);

    /**
     * Retrieve an audit event.
     *
     * @param auditId The audit ID
     * @param tenantId The tenant ID
     * @return Promise containing the audit event
     */
    Promise<Optional<AuditEvent>> retrieveAuditEvent(String auditId, String tenantId);

    /**
     * Query audit events by criteria.
     *
     * @param query The audit query
     * @return Promise containing list of matching audit events
     */
    Promise<List<AuditEvent>> queryAuditEvents(AuditQuery query);

    /**
     * Get audit statistics for a tenant.
     *
     * @param tenantId The tenant ID
     * @param timeRangeMs The time range in milliseconds
     * @return Promise containing audit statistics
     */
    Promise<AuditStatistics> getAuditStatistics(String tenantId, long timeRangeMs);

    /**
     * Audit log request.
     */
    record AuditLogRequest(
        String auditEventType,
        String actor,
        String tenantId,
        String resourceId,
        String resourceType,
        Map<String, String> metadata,
        Optional<String> outcome,
        Optional<String> failureReason
    ) implements TenantScopedRequest {
        @Override
        public String getTenantId() {
            return tenantId;
        }
    }

    /**
     * Audit event.
     */
    record AuditEvent(
        String auditId,
        String auditEventType,
        String actor,
        String tenantId,
        String resourceId,
        String resourceType,
        Map<String, String> metadata,
        Optional<String> outcome,
        Optional<String> failureReason,
        long timestamp
    ) {}

    /**
     * Audit query.
     */
    record AuditQuery(
        String tenantId,
        Optional<String> auditEventType,
        Optional<String> actor,
        Optional<String> resourceId,
        Optional<String> resourceType,
        Optional<Long> afterTimestamp,
        Optional<Long> beforeTimestamp,
        Optional<Integer> limit
    ) implements TenantScopedRequest {
        @Override
        public String getTenantId() {
            return tenantId;
        }
    }

    /**
     * Audit statistics.
     */
    record AuditStatistics(
        long totalEvents,
        long successfulEvents,
        long failedEvents,
        Map<String, Long> eventsByType,
        Map<String, Long> eventsByActor
    ) {}
}
