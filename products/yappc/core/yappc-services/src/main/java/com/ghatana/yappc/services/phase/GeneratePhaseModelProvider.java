package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

import java.util.List;

public final class GeneratePhaseModelProvider {

    public GeneratePhaseModel build(PhasePanelInput input) {
        List<String> generatedArtifacts = input.evidence().stream().map(PhasePacket.PhaseEvidence::id).toList();
        String assuranceStatus = input.blockers().isEmpty() ? "passed" : "failed";
        String reviewState = input.governance().isEmpty() ? "not-reviewed" : "reviewed";
        return new GeneratePhaseModel(
                generatedArtifacts,
                assuranceStatus,
                reviewState,
                input.activityFeed().isEmpty() ? "no-diff" : input.activityFeed().get(0).summary(),
                input.readiness().canAdvance() ? "passed" : "failed",
                input.readiness().canAdvance() ? "ready" : "blocked"
        );
    }
}
