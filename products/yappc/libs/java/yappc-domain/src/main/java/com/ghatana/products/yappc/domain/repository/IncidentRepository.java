package com.ghatana.products.yappc.domain.repository;

import com.ghatana.products.yappc.domain.model.Incident;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for Incident entity operations.
 *
 * <p>Provides data access operations for security incidents, including
 * status-based filtering, assignee queries, and resolution tracking.</p>
 *
 * @doc.type interface
 * @doc.purpose Defines data access contract for Incident entities
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface IncidentRepository extends TenantAwareRepository<Incident, UUID> {

    /**
     * Finds incidents by workspace and status.
     *
     * @param workspaceId the workspace ID
     * @param status      the incident status
     * @param offset      the zero-based offset for pagination
     * @param limit       the maximum number of results to return
     * @return promise of a paginated result of incidents with the specified status
     */
    Promise<PageResult<Incident>> findByWorkspaceIdAndStatus(UUID workspaceId, String status, int offset, int limit);

    /**
     * Finds incidents assigned to a specific user.
     *
     * @param workspaceId the workspace ID
     * @param assigneeId  the assignee's user ID
     * @param offset      the zero-based offset for pagination
     * @param limit       the maximum number of results to return
     * @return promise of a paginated result of incidents assigned to the user
     */
    Promise<PageResult<Incident>> findByWorkspaceIdAndAssigneeId(UUID workspaceId, UUID assigneeId, int offset, int limit);

    /**
     * Finds open incidents (not resolved or closed) for a workspace.
     *
     * @param workspaceId the workspace ID
     * @return promise of a list of open incidents
     */
    Promise<List<Incident>> findOpenIncidents(UUID workspaceId);

    /**
     * Finds incidents by severity.
     *
     * @param workspaceId the workspace ID
     * @param severity    the severity level
     * @param offset      the zero-based offset for pagination
     * @param limit       the maximum number of results to return
     * @return promise of a paginated result of incidents with the specified severity
     */
    Promise<PageResult<Incident>> findByWorkspaceIdAndSeverity(UUID workspaceId, String severity, int offset, int limit);

    /**
     * Finds incidents created within a time range.
     *
     * @param workspaceId the workspace ID
     * @param startTime   the start of the time range
     * @param endTime     the end of the time range
     * @return promise of a list of incidents in the range
     */
    Promise<List<Incident>> findByWorkspaceIdAndCreatedAtBetween(UUID workspaceId, Instant startTime, Instant endTime);

    /**
     * Counts incidents by status for a workspace.
     *
     * @param workspaceId the workspace ID
     * @param status      the incident status
     * @return promise of the count of incidents with the status
     */
    Promise<Long> countByWorkspaceIdAndStatus(UUID workspaceId, String status);
}
