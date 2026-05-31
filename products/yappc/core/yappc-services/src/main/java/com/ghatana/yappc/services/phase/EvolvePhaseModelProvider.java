package com.ghatana.yappc.services.phase;

/**
 * Evolve phase model provider.
 *
 * @doc.type class
 * @doc.purpose Build Evolve phase models
 * @doc.layer product
 * @doc.pattern PhaseModelProvider
 */
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
