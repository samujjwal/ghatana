package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

import java.util.List;
import java.util.Set;

final class PhasePacketAssembler {

    PhasePacket assemble(
            String phase,
            String projectId,
            String projectName,
            String tenantId,
            String workspaceId,
            String workspaceName,
            PhasePacket.ActorContext actor,
            String lifecyclePhase,
            PhasePacket.TenantTier tenantTier,
            Set<String> enabledPhaseFlags,
            PhasePacket.CapabilityModel capabilities,
            List<PhasePacket.PhaseBlocker> blockers,
            PhasePacket.PhaseReadiness readiness,
            List<PhasePacket.RequiredArtifact> requiredArtifacts,
            List<PhasePacket.CompletedArtifact> completedArtifacts,
            List<PhasePacket.ActivityFeedEntry> activityFeed,
            List<PhasePacket.PhaseEvidence> evidence,
            List<PhasePacket.GovernanceRecord> governance,
            PhasePacket.PlatformRunStatus platformRunStatus,
            List<PhasePacket.PhaseAction> availableActions,
            PhasePacket.DashboardActionClassification dashboardActions,
            PhasePacket.HealthSignals healthSignals,
            PhasePacket.DegradedPacketDetails degradedDetails,
            long timestamp,
            String correlationId
    ) {
        return new PhasePacket(
                phase,
                projectId,
                projectName,
                tenantId,
                workspaceId,
                workspaceName,
                actor,
                lifecyclePhase,
                tenantTier,
                enabledPhaseFlags,
                capabilities,
                blockers,
                readiness,
                requiredArtifacts,
                completedArtifacts,
                activityFeed,
                evidence,
                governance,
                platformRunStatus,
                availableActions,
                dashboardActions,
                healthSignals,
                degradedDetails,
                timestamp,
                correlationId);
    }
}