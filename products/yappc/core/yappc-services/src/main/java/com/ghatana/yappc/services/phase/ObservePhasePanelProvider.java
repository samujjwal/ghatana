package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

import java.util.Map;

public final class ObservePhasePanelProvider implements PhasePanelProvider {

    private final ObservePhaseModelProvider modelProvider;

    public ObservePhasePanelProvider() {
        this(new ObservePhaseModelProvider());
    }

    public ObservePhasePanelProvider(ObservePhaseModelProvider modelProvider) {
        this.modelProvider = modelProvider;
    }

    @Override
    public String phase() {
        return "observe";
    }

    @Override
    public PhasePacket.PhasePanelView build(PhasePanelInput input) {
        ObservePhaseModel model = modelProvider.build(input);
        return new PhasePacket.PhasePanelView(
                "observe",
                model.previewStatus(),
                "phasePanel.observe.summary.source",
                "phasePanel.observe.recommendation",
                "backend",
                input.confidence(),
                "backend:observe-model",
                java.util.List.of(
                        new PhasePacket.PhasePanelCard("observe-preview", "phasePanel.observe.card.preview.label",
                                model.incidents().isEmpty() ? "phasePanel.observe.card.preview.healthy" : model.incidents().get(0),
                                model.previewStatus(), input.correlationId(), Map.of("issueCount", model.incidents().size())),
                        new PhasePacket.PhasePanelCard("observe-runtime", "phasePanel.observe.card.runtime.label",
                                model.runtimeStatus(), model.runtimeStatus(), input.correlationId(), Map.of("traceCount", model.traceIds().size())))
        );
    }
}
