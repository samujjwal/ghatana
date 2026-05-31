package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

import java.util.Map;

public final class ShapePhasePanelProvider implements PhasePanelProvider {

    private final ShapePhaseModelProvider modelProvider;

    public ShapePhasePanelProvider() {
        this(new ShapePhaseModelProvider());
    }

    public ShapePhasePanelProvider(ShapePhaseModelProvider modelProvider) {
        this.modelProvider = modelProvider;
    }

    @Override
    public String phase() {
        return "shape";
    }

    @Override
    public PhasePacket.PhasePanelView build(PhasePanelInput input) {
        ShapePhaseModel model = modelProvider.build(input);
        return new PhasePacket.PhasePanelView(
                "shape",
                input.readiness().canAdvance() ? "ready" : "needs-modeling",
                input.readiness().canAdvance() ? "phasePanel.shape.summary.ready" : "phasePanel.shape.summary.needsModeling",
                "phasePanel.shape.recommendation",
                "backend",
                input.confidence(),
                "backend:shape-model",
                java.util.List.of(
                        new PhasePacket.PhasePanelCard("shape-surfaces", "phasePanel.shape.card.surfaces.label",
                                model.selectedSurfaces().isEmpty() ? "phasePanel.shape.card.surfaces.none" : "phasePanel.shape.card.surfaces.selected",
                                model.selectedSurfaces().isEmpty() ? "empty" : "selected", input.correlationId(), Map.of("surfaceCount", model.selectedSurfaces().size())),
                        new PhasePacket.PhasePanelCard("shape-architecture", "phasePanel.shape.card.architecture.label",
                                input.readiness().canAdvance() ? "phasePanel.shape.card.architecture.valid" : "phasePanel.shape.card.architecture.incomplete",
                                input.readiness().canAdvance() ? "valid" : "incomplete", input.correlationId(), Map.of("architectureScore", input.readiness().completenessScore())),
                        new PhasePacket.PhasePanelCard("shape-dependencies", "phasePanel.shape.card.dependencies.label",
                                model.dependencies().isEmpty() ? "phasePanel.shape.card.dependencies.resolved" : "phasePanel.shape.card.dependencies.blocked",
                                model.dependencies().isEmpty() ? "resolved" : "blocked", input.correlationId(), Map.of("dependencyCount", model.dependencies().size())),
                        new PhasePacket.PhasePanelCard("shape-modeling-gaps", "phasePanel.shape.card.gaps.label",
                                model.unresolvedDesignGaps().isEmpty() ? "phasePanel.shape.card.gaps.none" : "phasePanel.shape.card.gaps.detected",
                                model.unresolvedDesignGaps().isEmpty() ? "none" : "detected", input.correlationId(), Map.of("gapCount", model.unresolvedDesignGaps().size())))
        );
    }
}
