package com.ghatana.digitalmarketing.application.strategy;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.strategy.MarketingStrategy;
import io.activej.promise.Promise;

/**
 * Application service for generating and managing 30-day marketing strategies.
 *
 * @doc.type class
 * @doc.purpose Service contract for marketing strategy generation, retrieval, and approval
 * @doc.layer product
 * @doc.pattern Service
 */
public interface StrategyGeneratorService {

    /**
     * Generates a 30-day marketing strategy for the workspace.
     *
     * @param ctx     operation context with tenant, workspace, actor information
     * @param command generation command with signals from intake, audit, research, and budget
     * @return promise resolving to the newly generated strategy in DRAFT status
     * @throws SecurityException     if the actor is not authorized to generate a strategy
     * @throws NullPointerException  if ctx or command is null
     */
    Promise<MarketingStrategy> generateStrategy(DmOperationContext ctx, GenerateStrategyCommand command);

    /**
     * Retrieves the latest strategy for the workspace.
     *
     * @param ctx operation context
     * @return promise resolving to the latest strategy
     * @throws SecurityException        if the actor is not authorized to read the strategy
     * @throws java.util.NoSuchElementException if no strategy exists
     */
    Promise<MarketingStrategy> getLatestStrategy(DmOperationContext ctx);

    /**
     * Submits the current strategy for human approval.
     *
     * @param ctx        operation context
     * @param strategyId the strategy to submit
     * @return promise resolving to the updated strategy in PENDING_APPROVAL status
     * @throws SecurityException        if the actor is not authorized
     * @throws java.util.NoSuchElementException if the strategy is not found
     * @throws IllegalStateException    if the strategy is not in DRAFT status
     */
    Promise<MarketingStrategy> submitForApproval(DmOperationContext ctx, String strategyId);

    /**
     * Approves the specified strategy, making it immutable.
     *
     * @param ctx        operation context
     * @param strategyId the strategy to approve
     * @return promise resolving to the APPROVED strategy
     * @throws SecurityException        if the actor is not authorized
     * @throws java.util.NoSuchElementException if the strategy is not found
     * @throws IllegalStateException    if the strategy is not in PENDING_APPROVAL status
     */
    Promise<MarketingStrategy> approveStrategy(DmOperationContext ctx, String strategyId);

    /**
     * Command carrying all signals needed to generate a 30-day marketing strategy.
     *
     * @param intakeCompletionPct     percentage of intake questionnaire completed (0–100)
     * @param serviceArea             non-blank description of the service area
     * @param monthlyBudget           client's monthly budget in currency minor units (≥ 0)
     * @param auditFindingCount       number of audit findings from F1-010
     * @param trackingGapsDetected    whether tracking gaps were found in the audit
     * @param keywordOpportunityCount number of keyword opportunities found in F1-011
     * @param topCompetitorCount      number of competitors identified in F1-011
     * @param primaryOffer            non-blank description of the primary service/offer
     */
    record GenerateStrategyCommand(
        int intakeCompletionPct,
        String serviceArea,
        int monthlyBudget,
        int auditFindingCount,
        boolean trackingGapsDetected,
        int keywordOpportunityCount,
        int topCompetitorCount,
        String primaryOffer
    ) {
        /**
         * Compact constructor that validates all fields.
         */
        public GenerateStrategyCommand {
            if (intakeCompletionPct < 0 || intakeCompletionPct > 100) {
                throw new IllegalArgumentException("intakeCompletionPct must be between 0 and 100");
            }
            java.util.Objects.requireNonNull(serviceArea, "serviceArea must not be null");
            if (serviceArea.isBlank()) {
                throw new IllegalArgumentException("serviceArea must not be blank");
            }
            if (monthlyBudget < 0) {
                throw new IllegalArgumentException("monthlyBudget must not be negative");
            }
            if (auditFindingCount < 0) {
                throw new IllegalArgumentException("auditFindingCount must not be negative");
            }
            if (keywordOpportunityCount < 0) {
                throw new IllegalArgumentException("keywordOpportunityCount must not be negative");
            }
            if (topCompetitorCount < 0) {
                throw new IllegalArgumentException("topCompetitorCount must not be negative");
            }
            java.util.Objects.requireNonNull(primaryOffer, "primaryOffer must not be null");
            if (primaryOffer.isBlank()) {
                throw new IllegalArgumentException("primaryOffer must not be blank");
            }
        }
    }
}
