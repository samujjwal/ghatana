package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

import java.util.List;

/**
 * Shape phase model provider.
 *
 * @doc.type class
 * @doc.purpose Build Shape phase models
 * @doc.layer product
 * @doc.pattern PhaseModelProvider
 */
public final class ShapePhaseModelProvider {

    public ShapePhaseModel build(PhasePanelInput input) {
        List<String> selectedSurfaces = input.activityFeed().stream()
                .map(PhasePacket.ActivityFeedEntry::type)
                .distinct()
                .toList();
        List<String> architectureDecisions = input.evidence().stream().map(PhasePacket.PhaseEvidence::title).toList();
        List<String> dependencies = input.blockers().stream().map(PhasePacket.PhaseBlocker::resourceId).toList();
        return new ShapePhaseModel(
                selectedSurfaces,
                List.of("runtime:default"),
                architectureDecisions,
                "canvas:" + input.phase(),
                "ui-builder:" + input.phase(),
                dependencies,
                input.readiness().missingPrerequisites()
        );
    }
}
