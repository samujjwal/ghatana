package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

import java.util.List;

/**
 * Immutable input shared by backend-owned phase panel providers.
 *
 * @doc.type record
 * @doc.purpose Carries canonical packet context used to build phase panels
 * @doc.layer services
 * @doc.pattern DTO
 */
public record PhasePanelInput(
        String phase,
    String tenantId,
    String workspaceId,
    String projectId,
        PhasePacket.PhaseReadiness readiness,
        List<PhasePacket.PhaseBlocker> blockers,
        List<PhasePacket.ActivityFeedEntry> activityFeed,
        List<PhasePacket.PhaseEvidence> evidence,
        List<PhasePacket.GovernanceRecord> governance,
        PhasePacket.PlatformRunStatus platformRunStatus,
        PhasePacket.HealthSignals healthSignals,
        PhasePacket.DashboardActionClassification dashboardActions,
    LearningWorkflowState learningWorkflow,
    EvolutionWorkflowState evolutionWorkflow,
        String correlationId
) {
    public double confidence() {
        return readiness.predictionConfidence() == null ? 0.5d : readiness.predictionConfidence();
    }
}
