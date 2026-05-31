package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

final class EvolutionPlanService {

    PhasePacket.EvolutionPlanPanel build(
            EvolutionWorkflowState workflowState
    ) {
        return new PhasePacket.EvolutionPlanPanel(
                workflowState.proposal(),
                workflowState.impactSummary(),
                workflowState.diffSummary(),
                String.join("; ", workflowState.validationRequirements()),
                workflowState.approvalState(),
                workflowState.rollbackPath(),
                workflowState.rerunTarget()
        );
    }
}
