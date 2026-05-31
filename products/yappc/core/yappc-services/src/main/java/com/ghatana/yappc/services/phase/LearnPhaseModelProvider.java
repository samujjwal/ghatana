package com.ghatana.yappc.services.phase;

public final class LearnPhaseModelProvider {

    public LearnPhaseModel build(PhasePanelInput input) {
        String sourceEvent = input.learningWorkflow().sourceEvent();
        return new LearnPhaseModel(
                java.util.List.of(input.learningWorkflow().learnedSignal()),
                sourceEvent,
                input.learningWorkflow().confidence(),
                input.learningWorkflow().approvalState(),
                input.learningWorkflow().rollbackPath(),
                input.learningWorkflow().evidenceIds()
        );
    }
}
