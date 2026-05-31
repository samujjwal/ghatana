package com.ghatana.yappc.services.phase;

public final class EvolvePhaseModelProvider {

    public EvolvePhaseModel build(PhasePanelInput input) {
    EvolutionWorkflowState workflow = input.evolutionWorkflow();
        return new EvolvePhaseModel(
        workflow.proposal(),
        workflow.impactSummary(),
        workflow.diffSummary(),
        workflow.validationRequirements(),
        workflow.approvalState(),
        workflow.rollbackPath(),
        workflow.rerunTarget()
        );
    }
}
