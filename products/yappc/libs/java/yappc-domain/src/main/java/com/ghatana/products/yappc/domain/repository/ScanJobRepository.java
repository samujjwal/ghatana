package com.ghatana.products.yappc.domain.repository;

import com.ghatana.products.yappc.domain.enums.ScanStatus;
import com.ghatana.products.yappc.domain.enums.ScanType;
import com.ghatana.products.yappc.domain.model.ScanJob;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for ScanJob entity operations.
 *
 * <p>Provides data access operations for security scan jobs, including
 * status-based queries, project filtering, and scan history retrieval.</p>
 *
 * @doc.type interface
 * @doc.purpose Defines data access contract for ScanJob entities
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ScanJobRepository extends TenantAwareRepository<ScanJob, UUID> {

    /**
     * Finds scan jobs by workspace and scan type.
     *
     * @param workspaceId the workspace ID
     * @param scanType    the type of scan
     * @param offset      the zero-based offset for pagination
     * @param limit       the maximum number of results to return
     * @return promise of a paginated result of matching scan jobs
     */
    Promise<PageResult<ScanJob>> findByWorkspaceIdAndScanType(UUID workspaceId, ScanType scanType, int offset, int limit);

    /**
     * Finds scan jobs by workspace and status.
     *
     * @param workspaceId the workspace ID
     * @param status      the scan status
     * @param offset      the zero-based offset for pagination
     * @param limit       the maximum number of results to return
     * @return promise of a paginated result of matching scan jobs
     */
    Promise<PageResult<ScanJob>> findByWorkspaceIdAndStatus(UUID workspaceId, ScanStatus status, int offset, int limit);

    /**
     * Finds scan jobs for a specific project.
     *
     * @param workspaceId the workspace ID
     * @param projectId   the project ID
     * @param offset      the zero-based offset for pagination
     * @param limit       the maximum number of results to return
     * @return promise of a paginated result of scan jobs for the project
     */
    Promise<PageResult<ScanJob>> findByWorkspaceIdAndProjectId(UUID workspaceId, UUID projectId, int offset, int limit);

    /**
     * Finds completed scans that have critical or high severity findings.
     *
     * @param workspaceId the workspace ID
     * @return promise of a list of scans with critical/high findings
     */
    Promise<List<ScanJob>> findCompletedScansWithCriticalOrHigh(UUID workspaceId);

    /**
     * Finds scans started within a date range.
     *
     * @param workspaceId the workspace ID
     * @param startDate   the start of the date range
     * @param endDate     the end of the date range
     * @return promise of a list of scans within the range
     */
    Promise<List<ScanJob>> findByWorkspaceIdAndStartedAtBetween(UUID workspaceId, Instant startDate, Instant endDate);

    /**
     * Finds the most recent completed scan for a project and scan type.
     *
     * @param workspaceId the workspace ID
     * @param projectId   the project ID
     * @param scanType    the scan type
     * @return promise of the most recent completed scan if found
     */
    Promise<Optional<ScanJob>> findMostRecentCompletedScan(UUID workspaceId, UUID projectId, ScanType scanType);

    /**
     * Counts scan jobs by workspace and status.
     *
     * @param workspaceId the workspace ID
     * @param status      the scan status
     * @return promise of the count of matching scans
     */
    Promise<Long> countByWorkspaceIdAndStatus(UUID workspaceId, ScanStatus status);
}
