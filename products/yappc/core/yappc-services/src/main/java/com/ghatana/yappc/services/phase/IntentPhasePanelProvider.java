package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

import java.util.Map;

public final class IntentPhasePanelProvider implements PhasePanelProvider {

    private final IntentPhaseModelProvider modelProvider;

    public IntentPhasePanelProvider() {
        this(new IntentPhaseModelProvider());
    }

    public IntentPhasePanelProvider(IntentPhaseModelProvider modelProvider) {
        this.modelProvider = modelProvider;
    }

    @Override
    public String phase() {
        return "intent";
    }

    @Override
    public PhasePacket.PhasePanelView build(PhasePanelInput input) {
        IntentPhaseModel model = modelProvider.build(input);
        return new PhasePacket.PhasePanelView(
                "intent",
                input.readiness().canAdvance() ? "ready" : "needs-input",
                input.readiness().canAdvance() ? "phasePanel.intent.summary.ready" : "phasePanel.intent.summary.needsInput",
                input.dashboardActions().primaryAction() == null ? "phasePanel.intent.recommendation.capture" : input.dashboardActions().primaryAction(),
                "backend",
                input.confidence(),
                "backend:intent-model",
                java.util.List.of(
                        new PhasePacket.PhasePanelCard("intent-goals", "phasePanel.intent.card.goals.label",
                                model.goals().isEmpty() ? "phasePanel.intent.card.goals.empty" : "phasePanel.intent.card.goals.populated",
                                model.goals().isEmpty() ? "empty" : "populated", input.correlationId(), Map.of("goalCount", model.goals().size())),
                        new PhasePacket.PhasePanelCard("intent-personas", "phasePanel.intent.card.personas.label",
                                model.personas().isEmpty() ? "phasePanel.intent.card.personas.empty" : "phasePanel.intent.card.personas.active",
                                model.personas().isEmpty() ? "unknown" : "active", input.correlationId(), Map.of("personaCount", model.personas().size())),
                        new PhasePacket.PhasePanelCard("intent-constraints", "phasePanel.intent.card.constraints.label",
                                model.constraints().isEmpty() ? "phasePanel.intent.card.constraints.none" : "phasePanel.intent.card.constraints.blocked",
                                model.constraints().isEmpty() ? "healthy" : "blocked", input.correlationId(), Map.of("constraintCount", model.constraints().size())),
                        new PhasePacket.PhasePanelCard("intent-success-criteria", "phasePanel.intent.card.successCriteria.label",
                                model.unresolvedFields().isEmpty() ? "phasePanel.intent.card.successCriteria.met" : "phasePanel.intent.card.successCriteria.pending",
                                model.unresolvedFields().isEmpty() ? "met" : "pending", input.correlationId(), Map.of("readinessScore", input.readiness().completenessScore())))
        );
    }
}
