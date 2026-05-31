package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

import java.util.Map;

/**
 * Learn phase panel provider.
 *
 * @doc.type class
 * @doc.purpose Provide Learn phase panel views
 * @doc.layer product
 * @doc.pattern PhasePanelProvider
 */
public final class LearnPhasePanelProvider implements PhasePanelProvider {

    private final LearnPhaseModelProvider modelProvider;
    private final LearningInsightService learningInsightService;

    public LearnPhasePanelProvider(LearningInsightService learningInsightService) {
        this(new LearnPhaseModelProvider(), learningInsightService);
    }

    LearnPhasePanelProvider(LearnPhaseModelProvider modelProvider, LearningInsightService learningInsightService) {
        this.modelProvider = modelProvider;
        this.learningInsightService = learningInsightService;
    }

    @Override
    public String phase() {
        return "learn";
    }

    @Override
    public PhasePacket.PhasePanelView build(PhasePanelInput input) {
        LearnPhaseModel model = modelProvider.build(input);
        return new PhasePacket.PhasePanelView(
                "learn",
                input.healthSignals().agentGovernance().status(),
                "phasePanel.learn.summary.source",
                "phasePanel.learn.recommendation",
                "backend",
                input.confidence(),
                "backend:learn-model",
                java.util.List.of(
                        new PhasePacket.PhasePanelCard("learn-evidence", "phasePanel.learn.card.evidence.label",
                                model.evidenceIds().isEmpty() ? "phasePanel.learn.card.evidence.none" : "phasePanel.learn.card.evidence.linked",
                                input.healthSignals().agentGovernance().status(), input.correlationId(), Map.of("evidenceCount", model.evidenceIds().size()))),
                learningInsightService.build(input.learningWorkflow(), input.healthSignals().agentGovernance()),
                null
        );
    }
}
