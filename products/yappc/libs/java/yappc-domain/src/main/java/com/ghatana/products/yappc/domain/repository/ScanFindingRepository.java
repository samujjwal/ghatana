package com.ghatana.products.yappc.domain.repository;

import com.ghatana.products.yappc.domain.model.ScanFinding;
import io.activej.promise.Promise;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for ScanFinding entity operations.
 *
 * <p>Provides data access operations for security findings discovered
 * during scans, including severity-based queries and status filtering.</p>
 *
 * @doc.type interface
 * @doc.purpose Defines data access contract for ScanFinding entities
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ScanFindingRepository extends TenantAwareRepository<ScanFinding, UUID> {

    /**
     * Finds findings for a specific scan job with pagination.
     *
     * @param workspaceId the workspace ID
     * @param scanJobId   the scan job ID
     * @param offset      the zero-based offset for pagination
     * @param limit       the maximum number of results to return
     * @return promise of a paginated result of findings for the scan job
     */
    Promise<PageResult<ScanFinding>> findByWorkspaceIdAndScanJobId(UUID workspaceId, UUID scanJobId, int offset, int limit);

    /**
     * Finds findings for a specific scan job (legacy method with manual pagination).
     *
     * @param workspaceId the workspace ID
     * @param jobId       the scan job ID
     * @param page        the page number (0-based)
     * @param size        the page size
     * @return promise of a list of findings for the page
     */
    Promise<List<ScanFinding>> findByJobId(UUID workspaceId, UUID jobId, int page, int size);

    /**
     * Finds findings by severity level.
     *
     * @param workspaceId the workspace ID
     * @param severity    the severity level (e.g., CRITICAL, HIGH)
     * @param offset      the zero-based offset for pagination
     * @param limit       the maximum number of results to return
     * @return promise of a paginated result of findings with the specified severity
     */
    Promise<PageResult<ScanFinding>> findByWorkspaceIdAndSeverity(UUID workspaceId, String severity, int offset, int limit);

    /**
     * Finds open (unresolved) findings for a workspace.
     *
     * @param workspaceId the workspace ID
     * @param offset      the zero-based offset for pagination
     * @param limit       the maximum number of results to return
     * @return promise of a paginated result of open findings
     */
    Promise<PageResult<ScanFinding>> findOpenFindings(UUID workspaceId, int offset, int limit);

    /**
     * Counts findings by severity for a workspace.
     *
     * @param workspaceId the workspace ID
     * @param severity    the severity level
     * @return promise of the count of findings with the severity
     */
    Promise<Long> countByWorkspaceIdAndSeverity(UUID workspaceId, String severity);

    /**
     * Finds all critical and high severity findings for a workspace.
     *
     * @param workspaceId the workspace ID
     * @return promise of a list of critical/high findings
     */
    Promise<List<ScanFinding>> findCriticalAndHighFindings(UUID workspaceId);
}
