package com.ghatana.digitalmarketing.application.budget;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.budget.BudgetRecommendation;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Repository interface for {@link BudgetRecommendation} persistence.
 *
 * @doc.type interface
 * @doc.purpose Budget recommendation persistence contract for DMOS F1-014
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface BudgetRecommendationRepository {

    /**
     * Persists a recommendation (insert or update by ID).
     *
     * @param recommendation the recommendation to save; must not be null
     * @return promise resolving to the saved recommendation
     */
    Promise<BudgetRecommendation> save(BudgetRecommendation recommendation);

    /**
     * Finds the latest recommendation for the given workspace.
     *
     * @param workspaceId the owning workspace; must not be null
     * @return promise resolving to an optional recommendation
     */
    Promise<Optional<BudgetRecommendation>> findLatestByWorkspace(DmWorkspaceId workspaceId);

    /**
     * Finds a recommendation by its unique identifier.
     *
     * @param recommendationId the recommendation ID; must not be null
     * @return promise resolving to an optional recommendation
     */
    Promise<Optional<BudgetRecommendation>> findById(String recommendationId);
}
