package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

import java.util.Map;

public final class GeneratePhasePanelProvider implements PhasePanelProvider {

    private final GeneratePhaseModelProvider modelProvider;

    public GeneratePhasePanelProvider() {
        this(new GeneratePhaseModelProvider());
    }

    public GeneratePhasePanelProvider(GeneratePhaseModelProvider modelProvider) {
        this.modelProvider = modelProvider;
    }

    @Override
    public String phase() {
        return "generate";
    }

    @Override
    public PhasePacket.PhasePanelView build(PhasePanelInput input) {
        GeneratePhaseModel model = modelProvider.build(input);
        return new PhasePacket.PhasePanelView(
                "generate",
                input.readiness().canAdvance() ? "ready" : "blocked",
                input.readiness().canAdvance() ? "phasePanel.generate.summary.ready" : "phasePanel.generate.summary.blocked",
                input.dashboardActions().primaryAction() == null ? "phasePanel.generate.recommendation.review" : input.dashboardActions().primaryAction(),
                "backend",
                input.confidence(),
                "backend:generate-model",
                java.util.List.of(
                        new PhasePacket.PhasePanelCard("generate-blockers", "phasePanel.generate.card.blockers.label",
                                input.blockers().isEmpty() ? "phasePanel.generate.card.blockers.none" : input.blockers().get(0).description(),
                                input.blockers().isEmpty() ? "healthy" : "blocked", input.correlationId(), Map.of("count", input.blockers().size())),
                        new PhasePacket.PhasePanelCard("generate-assurance", "phasePanel.generate.card.assurance.label",
                                model.assuranceStatus(), model.assuranceStatus(), input.correlationId(), Map.of("reviewState", model.reviewState())))
        );
    }
}
