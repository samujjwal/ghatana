package com.ghatana.yappc.services.phase;

import java.util.List;

/**
 * Durable learn-phase workflow projection.
 *
 * @doc.type record
 * @doc.purpose Carries learning workflow state used by Learn phase panel and readiness
 * @doc.layer service
 * @doc.pattern DTO
 */
public record LearningWorkflowState(
        String learnedSignal,
        String sourceEvent,
        double confidence,
        String recommendation,
        String approvalState,
        String rollbackPath,
        List<String> evidenceIds
) {
    static LearningWorkflowState fallback(
            String sourceEvent,
            double confidence,
            String approvalState,
            List<String> evidenceIds
    ) {
        return new LearningWorkflowState(
                approvalState,
                sourceEvent,
                confidence,
                "Approve learning recommendation only when governance evidence is healthy.",
                approvalState,
                "Revert to previous approved learning baseline and re-run observe checks.",
                evidenceIds
        );
    }
}
