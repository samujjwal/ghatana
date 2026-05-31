package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

import java.util.List;
import java.util.Map;

/**
 * Input envelope for phase-specific readiness policy checks.
 *
 * @doc.type record
 * @doc.purpose Phase readiness input envelope
 * @doc.layer product
 * @doc.pattern PhaseReadinessInput
 */
public record PhaseReadinessInput(
        String phase,
        List<PhasePacket.PhaseBlocker> blockers,
        List<PhasePacket.ActivityFeedEntry> activityFeed,
        List<PhasePacket.RequiredArtifact> requiredArtifacts,
        List<PhasePacket.CompletedArtifact> completedArtifacts,
        List<PhasePacket.PhaseEvidence> evidence,
        List<PhasePacket.GovernanceRecord> governance,
        PhasePacket.HealthSignals healthSignals,
        Map<String, Object> projectState
) {
}
