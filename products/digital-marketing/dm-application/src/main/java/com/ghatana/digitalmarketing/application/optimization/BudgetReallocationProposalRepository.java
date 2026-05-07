package com.ghatana.digitalmarketing.application.optimization;

import com.ghatana.digitalmarketing.domain.optimization.BudgetReallocationProposal;
import com.ghatana.digitalmarketing.domain.optimization.BudgetReallocationStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for budget reallocation proposal persistence.
 *
 * @doc.type interface
 * @doc.purpose Persistence operations for budget reallocation proposals (P3-004)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface BudgetReallocationProposalRepository {

    /**
     * Save a budget reallocation proposal.
     *
     * @param proposal the proposal to save
     * @return Promise containing the saved proposal
     */
    Promise<BudgetReallocationProposal> save(BudgetReallocationProposal proposal);

    /**
     * Update a budget reallocation proposal.
     *
     * @param proposal the proposal to update
     * @return Promise containing the updated proposal
     */
    Promise<BudgetReallocationProposal> update(BudgetReallocationProposal proposal);

    /**
     * Find a proposal by ID.
     *
     * @param id the proposal ID
     * @return Promise containing optional proposal
     */
    Promise<Optional<BudgetReallocationProposal>> findById(String id);

    /**
     * List proposals by tenant.
     *
     * @param tenantId the tenant ID
     * @return Promise containing list of proposals
     */
    Promise<List<BudgetReallocationProposal>> listByTenant(String tenantId);

    /**
     * List proposals by workspace.
     *
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @return Promise containing list of proposals
     */
    Promise<List<BudgetReallocationProposal>> listByWorkspace(String tenantId, String workspaceId);

    /**
     * List proposals by budget recommendation.
     *
     * @param tenantId the tenant ID
     * @param budgetRecommendationId the budget recommendation ID
     * @return Promise containing list of proposals
     */
    Promise<List<BudgetReallocationProposal>> listByBudgetRecommendation(String tenantId, String budgetRecommendationId);

    /**
     * List proposals by status.
     *
     * @param tenantId the tenant ID
     * @param status the status to filter by
     * @return Promise containing list of proposals
     */
    Promise<List<BudgetReallocationProposal>> listByStatus(String tenantId, BudgetReallocationStatus status);
}
