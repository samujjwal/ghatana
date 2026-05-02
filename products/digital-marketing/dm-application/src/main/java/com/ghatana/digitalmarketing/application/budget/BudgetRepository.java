package com.ghatana.digitalmarketing.application.budget;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.budget.Budget;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for DMOS budget persistence.
 *
 * @doc.type interface
 * @doc.purpose Budget persistence contract for DMOS application services
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface BudgetRepository {

    /**
     * Saves a budget (insert or update by ID within the workspace).
     *
     * @param budget the budget to save; must not be null
     * @return promise resolving to the saved budget
     */
    Promise<Budget> save(Budget budget);

    /**
     * Finds a budget by ID within the given workspace.
     *
     * @param workspaceId the owning workspace; must not be null
     * @param budgetId    the budget ID; must not be null
     * @return promise resolving to an optional budget
     */
    Promise<Optional<Budget>> findById(DmWorkspaceId workspaceId, String budgetId);

    /**
     * Finds the approved budget allocated for a specific campaign.
     *
     * @param workspaceId the owning workspace; must not be null
     * @param campaignId  the campaign ID; must not be null
     * @return promise resolving to an optional budget (the current approved budget for the campaign)
     */
    Promise<Optional<Budget>> findApprovedByCampaign(DmWorkspaceId workspaceId, String campaignId);

    /**
     * Lists all budgets in the workspace.
     *
     * @param workspaceId the owning workspace; must not be null
     * @return promise resolving to a list of budgets; never null
     */
    Promise<List<Budget>> listByWorkspace(DmWorkspaceId workspaceId);
}
