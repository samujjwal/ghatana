package com.ghatana.products.yappc.domain.repository;

import com.ghatana.products.yappc.domain.model.ComplianceAssessment;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for ComplianceAssessment entity operations.
 *
 * <p>Provides data access operations for compliance assessments,
 * including framework-based queries and score-based filtering.</p>
 *
 * @doc.type interface
 * @doc.purpose Defines data access contract for ComplianceAssessment entities
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ComplianceAssessmentRepository extends TenantAwareRepository<ComplianceAssessment, UUID> {

    /**
     * Finds assessments for a specific framework.
     *
     * @param workspaceId the workspace ID
     * @param frameworkId the compliance framework ID
     * @param offset      the zero-based offset for pagination
     * @param limit       the maximum number of results to return
     * @return promise of a paginated result of assessments for the framework
     */
    Promise<PageResult<ComplianceAssessment>> findByWorkspaceIdAndFrameworkId(UUID workspaceId, UUID frameworkId, int offset, int limit);

    /**
     * Finds the most recent assessment for a workspace and framework.
     *
     * @param workspaceId the workspace ID
     * @param frameworkId the framework ID
     * @return promise of the most recent assessment if found
     */
    Promise<Optional<ComplianceAssessment>> findLatestByWorkspaceIdAndFrameworkId(UUID workspaceId, UUID frameworkId);

    /**
     * Finds assessments with scores below a threshold.
     *
     * @param workspaceId the workspace ID
     * @param maxScore    the maximum score threshold
     * @return promise of a list of failing assessments
     */
    Promise<List<ComplianceAssessment>> findFailingAssessments(UUID workspaceId, int maxScore);

    /**
     * Finds assessments completed within a time range.
     *
     * @param workspaceId the workspace ID
     * @param startTime   the start of the time range
     * @param endTime     the end of the time range
     * @return promise of a list of assessments in the range
     */
    Promise<List<ComplianceAssessment>> findByWorkspaceIdAndAssessedAtBetween(UUID workspaceId, Instant startTime, Instant endTime);

    /**
     * Finds all completed assessments for a workspace.
     *
     * @param workspaceId the workspace ID
     * @param offset      the zero-based offset for pagination
     * @param limit       the maximum number of results to return
     * @return promise of a paginated result of completed assessments
     */
    Promise<PageResult<ComplianceAssessment>> findCompletedAssessments(UUID workspaceId, int offset, int limit);

    /**
     * Calculates the average compliance score across all frameworks.
     *
     * @param workspaceId the workspace ID
     * @return promise of the average score
     */
    Promise<Double> averageScoreByWorkspaceId(UUID workspaceId);
}
