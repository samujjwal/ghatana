package com.ghatana.yappc.services.phase;

import java.util.List;

/**
 * Durable evolve-phase workflow projection.
 *
 * @doc.type record
 * @doc.purpose Carries evolution workflow state for Evolve phase panel and model rendering
 * @doc.layer service
 * @doc.pattern DTO
 */
public record EvolutionWorkflowState(
        String proposalId,
        String proposal,
        String impactSummary,
        String diffSummary,
        List<String> validationRequirements,
        String approvalState,
        String rollbackPath,
        String rerunTarget
) {
    static EvolutionWorkflowState fallback(
            String proposal,
            String impactSummary,
            String diffSummary,
            List<String> validationRequirements,
            String approvalState,
            String rerunTarget
    ) {
        return new EvolutionWorkflowState(
                "proposal-unavailable",
                proposal,
                impactSummary,
                diffSummary,
                validationRequirements,
                approvalState,
                "Use run rollback controls and regenerate proposal from latest learn signals.",
                rerunTarget
        );
    }
}
