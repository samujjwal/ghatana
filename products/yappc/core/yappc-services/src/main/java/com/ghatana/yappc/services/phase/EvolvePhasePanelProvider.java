package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

import java.time.Instant;
import java.util.Map;

public final class EvolvePhasePanelProvider implements PhasePanelProvider {

    private final EvolvePhaseModelProvider modelProvider;
    private final EvolutionPlanService evolutionPlanService;

    public EvolvePhasePanelProvider(EvolutionPlanService evolutionPlanService) {
        this(new EvolvePhaseModelProvider(), evolutionPlanService);
    }

    EvolvePhasePanelProvider(EvolvePhaseModelProvider modelProvider, EvolutionPlanService evolutionPlanService) {
        this.modelProvider = modelProvider;
        this.evolutionPlanService = evolutionPlanService;
    }

    @Override
    public String phase() {
        return "evolve";
    }

    @Override
    public PhasePacket.PhasePanelView build(PhasePanelInput input) {
        EvolvePhaseModel model = modelProvider.build(input);
        return new PhasePacket.PhasePanelView(
                "evolve",
                input.readiness().canAdvance() ? "ready" : "needs-review",
                "phasePanel.evolve.summary.source",
                input.readiness().nextPhase() == null ? "phasePanel.evolve.recommendation.noNext" : "phasePanel.evolve.recommendation.next",
                "backend",
                input.confidence(),
                "backend:evolve-model",
                java.util.List.of(
                        new PhasePacket.PhasePanelCard("evolve-activity", "phasePanel.evolve.card.activity.label",
                        model.proposal(),
                        input.activityFeed().isEmpty() ? "unknown" : "observed",
                        input.activityFeed().isEmpty() ? Instant.now().toString() : String.valueOf(input.activityFeed().get(0).timestamp()),
                        Map.of("activityCount", input.activityFeed().size(), "evidenceCount", input.evidence().size(), "governanceCount", input.governance().size(), "approvalState", model.approvalState(), "proposalId", input.evolutionWorkflow().proposalId()))),
                null,
                evolutionPlanService.build(input.evolutionWorkflow())
        );
    }
}
