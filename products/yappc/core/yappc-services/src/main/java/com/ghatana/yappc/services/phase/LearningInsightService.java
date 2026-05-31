package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

final class LearningInsightService {

    PhasePacket.LearningInsightPanel build(
            LearningWorkflowState workflowState,
            PhasePacket.AgentGovernanceHealth governanceHealth
    ) {
        boolean approvalRequired = !"APPROVED".equalsIgnoreCase(workflowState.approvalState());

        return new PhasePacket.LearningInsightPanel(
                workflowState.learnedSignal(),
                workflowState.sourceEvent(),
                workflowState.confidence(),
                workflowState.recommendation(),
                approvalRequired,
                workflowState.rollbackPath()
        );
    }
}
