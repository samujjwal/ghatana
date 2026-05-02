package com.ghatana.digitalmarketing.application.intake;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.intake.BusinessIntakeProfile;
import io.activej.promise.Promise;

import java.math.BigDecimal;
import java.util.List;

/**
 * Application service for AI intake questionnaire and structured business profile capture.
 *
 * @doc.type interface
 * @doc.purpose DMOS intake questionnaire lifecycle service for draft resume and submission
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public interface IntakeQuestionnaireService {

    Promise<BusinessIntakeProfile> saveDraft(DmOperationContext ctx, SaveDraftCommand command);

    Promise<BusinessIntakeProfile> getDraft(DmOperationContext ctx);

    Promise<BusinessIntakeProfile> submitIntake(DmOperationContext ctx, SubmitIntakeCommand command);

    record SaveDraftCommand(
        String businessName,
        String websiteUrl,
        String offerSummary,
        String targetAudience,
        String primaryGeography,
        BigDecimal monthlyBudgetAmount,
        List<String> competitorDomains,
        List<String> constraints,
        String growthGoal,
        String riskTolerance
    ) {
        public SaveDraftCommand {
            competitorDomains = competitorDomains != null ? List.copyOf(competitorDomains) : List.of();
            constraints = constraints != null ? List.copyOf(constraints) : List.of();
        }
    }

    record SubmitIntakeCommand(
        String aiSummary,
        double aiConfidenceScore,
        List<String> aiUnknowns
    ) {
        public SubmitIntakeCommand {
            if (aiSummary == null || aiSummary.isBlank()) {
                throw new IllegalArgumentException("aiSummary must not be blank");
            }
            if (aiConfidenceScore < 0.0 || aiConfidenceScore > 1.0) {
                throw new IllegalArgumentException("aiConfidenceScore must be between 0.0 and 1.0");
            }
            aiUnknowns = aiUnknowns != null ? List.copyOf(aiUnknowns) : List.of();
        }
    }
}
