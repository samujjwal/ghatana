package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

import java.util.Map;

public final class RunPhasePanelProvider implements PhasePanelProvider {

    private final RunPhaseModelProvider modelProvider;

    public RunPhasePanelProvider() {
        this(new RunPhaseModelProvider());
    }

    public RunPhasePanelProvider(RunPhaseModelProvider modelProvider) {
        this.modelProvider = modelProvider;
    }

    @Override
    public String phase() {
        return "run";
    }

    @Override
    public PhasePacket.PhasePanelView build(PhasePanelInput input) {
        RunPhaseModel model = modelProvider.build(input);
        return new PhasePacket.PhasePanelView(
                "run",
                model.status(),
                input.platformRunStatus() == null ? "phasePanel.run.summary.noRecord" : "phasePanel.run.summary.linked",
                "phasePanel.run.recommendation",
                "backend",
                input.confidence(),
                "backend:platform-run-status",
                java.util.List.of(
                        new PhasePacket.PhasePanelCard("run-status", "phasePanel.run.card.status.label",
                                model.status(), model.status(), input.correlationId(), Map.of("traceId", input.platformRunStatus() == null ? "" : input.platformRunStatus().traceId())),
                        new PhasePacket.PhasePanelCard("run-rollback", "phasePanel.run.card.rollback.label",
                                model.rollbackReady() ? "phasePanel.run.card.rollback.available" : "phasePanel.run.card.rollback.unavailable",
                                model.rollbackReady() ? "available" : "unavailable", input.correlationId(), Map.of("targetVersion", model.targetVersion())))
        );
    }
}
