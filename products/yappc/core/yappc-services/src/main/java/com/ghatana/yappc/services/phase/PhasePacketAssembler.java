package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

import java.util.List;
import java.util.Set;

/**
 * Assembles phase packets from various phase service components.
 *
 * @doc.type class
 * @doc.purpose Assembles phase packets from various phase service components
 * @doc.layer service
 * @doc.pattern Assembler
 */
public final class PhasePacketAssembler {

        private final PhasePanelProviderRegistry phasePanelProviderRegistry;

    public PhasePacketAssembler() {
                this(new PhasePanelProviderRegistry(
                                new LearningInsightService(),
                                new EvolutionPlanService()));
    }

        public PhasePacketAssembler(PhasePanelProviderRegistry phasePanelProviderRegistry) {
                this.phasePanelProviderRegistry = phasePanelProviderRegistry;
    }

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
        String fallbackSourceEvent = activityFeed.isEmpty()
                ? "No source event available"
                : activityFeed.get(0).summary();
        LearningWorkflowState learningWorkflowState = LearningWorkflowState.fallback(
                fallbackSourceEvent,
                readiness.predictionConfidence() == null ? 0.5d : readiness.predictionConfidence(),
                healthSignals.agentGovernance().governanceState(),
                healthSignals.agentGovernance().evidenceIds());
        EvolutionWorkflowState evolutionWorkflowState = EvolutionWorkflowState.fallback(
                activityFeed.isEmpty() ? "No evolution proposal is available yet." : "Proposal derived from latest lifecycle activity.",
                "Evidence: " + evidence.size() + ", Governance: " + governance.size(),
                activityFeed.isEmpty() ? "No diff summary available" : activityFeed.get(0).summary(),
                blockers.isEmpty() ? List.of("No additional validation blockers.") : List.of("Resolve lifecycle blockers before approval."),
                blockers.isEmpty() ? "READY_FOR_REVIEW" : "PENDING_REMEDIATION",
                readiness.nextPhase() == null ? "observe" : readiness.nextPhase());

        return assemble(
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
                learningWorkflowState,
                evolutionWorkflowState,
                degradedDetails,
                timestamp,
                correlationId);
    }

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
                        LearningWorkflowState learningWorkflowState,
                        EvolutionWorkflowState evolutionWorkflowState,
            PhasePacket.DegradedPacketDetails degradedDetails,
            long timestamp,
            String correlationId
    ) {
        List<PhasePacket.PhasePanelView> phasePanels = phasePanelProviderRegistry.buildPanels(new PhasePanelInput(
                phase,
                                tenantId,
                                workspaceId,
                                projectId,
                readiness,
                blockers,
                activityFeed,
                evidence,
                governance,
                platformRunStatus,
                healthSignals,
                dashboardActions,
                                learningWorkflowState,
                                evolutionWorkflowState,
                correlationId));

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
                phasePanels,
                healthSignals,
                degradedDetails,
                timestamp,
                correlationId);
    }
}