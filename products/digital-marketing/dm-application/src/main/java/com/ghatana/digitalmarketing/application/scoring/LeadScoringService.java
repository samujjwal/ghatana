package com.ghatana.digitalmarketing.application.scoring;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.scoring.LeadScore;
import io.activej.promise.Promise;

import java.util.Objects;

/**
 * Application service for F1-012 lead scoring.
 *
 * @doc.type class
 * @doc.purpose Generates and retrieves deterministic lead scores for prospects
 * @doc.layer product
 * @doc.pattern Service
 */
public interface LeadScoringService {

    /**
     * Generates a new lead score for the workspace identified in the context.
     *
     * @param ctx     operation context carrying workspace and principal
     * @param command scoring command
     * @return generated {@link LeadScore}
     */
    Promise<LeadScore> generateScore(DmOperationContext ctx, GenerateLeadScoreCommand command);

    /**
     * Retrieves the most recent lead score for the workspace in the context.
     *
     * @param ctx operation context
     * @return latest {@link LeadScore}, or failed with {@link java.util.NoSuchElementException}
     */
    Promise<LeadScore> getLatestScore(DmOperationContext ctx);

    /**
     * Command for generating a lead score.
     *
     * @param intakeCompletionPct       0–100 percentage of intake questionnaire completed
     * @param auditFindingCount         number of audit findings from website audit
     * @param trackingGapsDetected      whether tracking gaps were detected in the audit
     * @param keywordOpportunityCount   number of keyword opportunities identified in research
     * @param serviceArea               geographic service area (non-blank)
     * @param monthlyBudgetHint         estimated monthly budget hint in dollars (must be &gt;= 0)
     */
    record GenerateLeadScoreCommand(
            int intakeCompletionPct,
            int auditFindingCount,
            boolean trackingGapsDetected,
            int keywordOpportunityCount,
            String serviceArea,
            int monthlyBudgetHint
    ) {
        /**
         * Validates the command on construction.
         */
        public GenerateLeadScoreCommand {
            Objects.requireNonNull(serviceArea, "serviceArea must not be null");
            if (serviceArea.isBlank()) {
                throw new IllegalArgumentException("serviceArea must not be blank");
            }
            if (intakeCompletionPct < 0 || intakeCompletionPct > 100) {
                throw new IllegalArgumentException(
                        "intakeCompletionPct must be between 0 and 100, got: " + intakeCompletionPct);
            }
            if (monthlyBudgetHint < 0) {
                throw new IllegalArgumentException("monthlyBudgetHint must not be negative");
            }
        }
    }
}
