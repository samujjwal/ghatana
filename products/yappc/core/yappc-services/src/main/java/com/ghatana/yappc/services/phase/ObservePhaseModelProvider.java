package com.ghatana.yappc.services.phase;

import java.util.List;

public final class ObservePhaseModelProvider {

    public ObservePhaseModel build(PhasePanelInput input) {
        String traceId = input.platformRunStatus() == null ? "" : input.platformRunStatus().traceId();
        List<String> incidents = input.healthSignals().runtime().issues();
        String remediation = input.platformRunStatus() == null ? "" : input.platformRunStatus().remediationHint();
        return new ObservePhaseModel(
                input.healthSignals().preview().status(),
                input.healthSignals().runtime().status(),
                traceId.isBlank() ? List.of() : List.of(traceId),
                incidents,
                input.healthSignals().preview().issues(),
                remediation
        );
    }
}
