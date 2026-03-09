package com.ghatana.products.yappc.domain.repository;

import com.ghatana.products.yappc.domain.model.SecurityAlert;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for SecurityAlert entity operations.
 *
 * <p>Provides data access operations for security alerts, including
 * severity-based filtering, status queries, and time-range searches.</p>
 *
 * @doc.type interface
 * @doc.purpose Defines data access contract for SecurityAlert entities
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface SecurityAlertRepository extends TenantAwareRepository<SecurityAlert, UUID> {

    /**
     * Finds all security alerts for a workspace with pagination.
     *
     * @param workspaceId the workspace ID
     * @param offset      the zero-based offset for pagination
     * @param limit       the maximum number of results to return
     * @return promise of a paginated result of security alerts
     */
    Promise<PageResult<SecurityAlert>> findByWorkspaceId(UUID workspaceId, int offset, int limit);

    /**
     * Finds security alerts by severity.
     *
     * @param workspaceId the workspace ID
     * @param severity    the severity level
     * @param offset      the zero-based offset for pagination
     * @param limit       the maximum number of results to return
     * @return promise of a paginated result of alerts with the specified severity
     */
    Promise<PageResult<SecurityAlert>> findByWorkspaceIdAndSeverity(UUID workspaceId, String severity, int offset, int limit);

    /**
     * Finds security alerts by status.
     *
     * @param workspaceId the workspace ID
     * @param status      the alert status
     * @param offset      the zero-based offset for pagination
     * @param limit       the maximum number of results to return
     * @return promise of a paginated result of alerts with the specified status
     */
    Promise<PageResult<SecurityAlert>> findByWorkspaceIdAndStatus(UUID workspaceId, String status, int offset, int limit);

    /**
     * Finds open (unacknowledged) alerts for a workspace.
     *
     * @param workspaceId the workspace ID
     * @return promise of a list of open alerts
     */
    Promise<List<SecurityAlert>> findOpenAlerts(UUID workspaceId);

    /**
     * Finds critical alerts created within a time range.
     *
     * @param workspaceId the workspace ID
     * @param startTime   the start of the time range
     * @param endTime     the end of the time range
     * @return promise of a list of critical alerts in the range
     */
    Promise<List<SecurityAlert>> findCriticalAlertsBetween(UUID workspaceId, Instant startTime, Instant endTime);

    /**
     * Counts open alerts by severity.
     *
     * @param workspaceId the workspace ID
     * @param severity    the severity level
     * @return promise of the count of open alerts with the severity
     */
    Promise<Long> countOpenAlertsBySeverity(UUID workspaceId, String severity);
}
