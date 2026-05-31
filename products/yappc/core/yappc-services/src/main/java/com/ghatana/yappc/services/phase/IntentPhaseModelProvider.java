package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

import java.util.List;

/**
 * Builds intent model from canonical packet signals.
 *
 * @doc.type class
 * @doc.purpose Build Intent phase models
 * @doc.layer product
 * @doc.pattern PhaseModelProvider
 */
public final class IntentPhaseModelProvider {

    public IntentPhaseModel build(PhasePanelInput input) {
        List<String> goals = input.evidence().stream().map(PhasePacket.PhaseEvidence::title).toList();
        List<String> personas = input.activityFeed().stream().map(PhasePacket.ActivityFeedEntry::actor).distinct().toList();
        List<String> constraints = input.blockers().stream().map(PhasePacket.PhaseBlocker::title).toList();
        List<String> successCriteria = List.of("readiness:" + input.readiness().completenessScore());
        List<String> unresolved = input.readiness().missingPrerequisites();
        List<String> sourceArtifactIds = input.evidence().stream().map(PhasePacket.PhaseEvidence::id).toList();
        return new IntentPhaseModel(
                goals,
                personas,
                constraints,
                successCriteria,
                unresolved,
                "v1",
                sourceArtifactIds
        );
    }
}
