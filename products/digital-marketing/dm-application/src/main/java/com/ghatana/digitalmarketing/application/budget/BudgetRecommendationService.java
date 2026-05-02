package com.ghatana.digitalmarketing.application.budget;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.budget.BudgetRecommendation;
import io.activej.promise.Promise;

import java.util.Objects;

/**
 * Application service for budget recommendation and guardrail management (F1-014).
 *
 * <p>Generates a workspace-level budget recommendation from strategy signals, manages the
 * approval lifecycle, and enforces guardrails before any campaign spend is authorized.</p>
 *
 * @doc.type interface
 * @doc.purpose Budget recommendation service contract for DMOS F1-014
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public interface BudgetRecommendationService {

    /**
     * Generates a budget recommendation for the workspace based on the strategy's monthly
     * budget cap. Requires write authorization.
     *
     * @param ctx     operation context; must not be null
     * @param command generation inputs; must not be null
     * @return promise resolving to the saved draft recommendation
     */
    Promise<BudgetRecommendation> recommendBudget(DmOperationContext ctx, GenerateBudgetCommand command);

    /**
     * Retrieves the latest budget recommendation for the workspace. Requires read authorization.
     *
     * @param ctx operation context; must not be null
     * @return promise resolving to the latest recommendation
     */
    Promise<BudgetRecommendation> getLatestRecommendation(DmOperationContext ctx);

    /**
     * Submits a DRAFT recommendation for owner/manager approval.
     *
     * @param ctx              operation context; must not be null
     * @param recommendationId the recommendation to submit; must not be null or blank
     * @return promise resolving to the updated recommendation in PENDING_APPROVAL state
     */
    Promise<BudgetRecommendation> submitForApproval(DmOperationContext ctx, String recommendationId);

    /**
     * Approves a PENDING_APPROVAL recommendation, authorizing it to back campaign launches.
     *
     * @param ctx              operation context; must not be null
     * @param recommendationId the recommendation to approve; must not be null or blank
     * @return promise resolving to the APPROVED recommendation
     */
    Promise<BudgetRecommendation> approveRecommendation(DmOperationContext ctx, String recommendationId);

    /**
     * Command carrying the inputs needed to generate a budget recommendation.
     *
     * @param strategyId      the source strategy ID; must not be blank
     * @param totalMonthlyCap total budget cap from the strategy; must be {@code >= 0}
     * @param changeThreshold percentage change requiring re-approval (0–100); defaults to 10
     */
    record GenerateBudgetCommand(String strategyId, double totalMonthlyCap, double changeThreshold) {
        public GenerateBudgetCommand {
            Objects.requireNonNull(strategyId, "strategyId must not be null");
            if (strategyId.isBlank()) {
                throw new IllegalArgumentException("strategyId must not be blank");
            }
            if (totalMonthlyCap < 0) {
                throw new IllegalArgumentException("totalMonthlyCap must be non-negative");
            }
            if (changeThreshold < 0 || changeThreshold > 100) {
                throw new IllegalArgumentException("changeThreshold must be between 0 and 100");
            }
        }
    }
}
