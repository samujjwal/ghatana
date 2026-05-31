package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

import java.util.List;
import java.util.Map;

/**
 * Input envelope for phase-specific readiness policy checks.
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
