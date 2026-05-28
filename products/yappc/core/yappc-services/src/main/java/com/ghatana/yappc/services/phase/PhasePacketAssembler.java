package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PhasePacketAssembler {

    private final LearningInsightService learningInsightService;
    private final EvolutionPlanService evolutionPlanService;

    PhasePacketAssembler() {
        this(new LearningInsightService(), new EvolutionPlanService());
    }

    PhasePacketAssembler(
            LearningInsightService learningInsightService,
            EvolutionPlanService evolutionPlanService
    ) {
        this.learningInsightService = learningInsightService;
        this.evolutionPlanService = evolutionPlanService;
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
        List<PhasePacket.PhasePanelView> phasePanels = buildPhasePanels(
                phase,
                readiness,
                blockers,
                activityFeed,
                evidence,
                governance,
                platformRunStatus,
                healthSignals,
                dashboardActions,
                correlationId);

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

    private List<PhasePacket.PhasePanelView> buildPhasePanels(
            String phase,
            PhasePacket.PhaseReadiness readiness,
            List<PhasePacket.PhaseBlocker> blockers,
            List<PhasePacket.ActivityFeedEntry> activityFeed,
            List<PhasePacket.PhaseEvidence> evidence,
            List<PhasePacket.GovernanceRecord> governance,
            PhasePacket.PlatformRunStatus platformRunStatus,
            PhasePacket.HealthSignals healthSignals,
            PhasePacket.DashboardActionClassification dashboardActions,
            String correlationId
    ) {
        List<PhasePacket.PhasePanelView> panels = new ArrayList<>();
        double confidence = readiness.predictionConfidence() == null ? 0.5d : readiness.predictionConfidence();

        panels.add(new PhasePacket.PhasePanelView(
                "generate",
                readiness.canAdvance() ? "ready" : "blocked",
                readiness.canAdvance()
                        ? "Generation readiness is confirmed by backend lifecycle checks."
                        : "Generation is blocked by unmet lifecycle prerequisites.",
                dashboardActions.primaryAction() == null ? "Review blockers before generation." : dashboardActions.primaryAction(),
                "backend:phase-readiness",
                confidence,
                correlationId,
                List.of(new PhasePacket.PhasePanelCard(
                        "generate-blockers",
                        "Lifecycle blockers",
                        blockers.isEmpty() ? "No active blockers" : blockers.get(0).description(),
                        blockers.isEmpty() ? "healthy" : "blocked",
                        correlationId,
                        Map.of("count", blockers.size())))));

        String runStatus = platformRunStatus == null ? healthSignals.runtime().status() : platformRunStatus.status();
        panels.add(new PhasePacket.PhasePanelView(
                "run",
                runStatus,
                platformRunStatus == null
                        ? "No platform run record is currently linked."
                        : "Platform run status is backed by canonical runtime truth.",
                "Use run controls based on backend run status.",
                "backend:platform-run-status",
                confidence,
                correlationId,
                List.of(new PhasePacket.PhasePanelCard(
                        "run-status",
                        "Platform run",
                        runStatus,
                        runStatus,
                        correlationId,
                        Map.of("traceId", platformRunStatus == null ? "" : platformRunStatus.traceId())))));

        panels.add(new PhasePacket.PhasePanelView(
                "observe",
                healthSignals.preview().status(),
                "Observe panel is sourced from canonical preview/runtime health signals.",
                "Investigate degraded preview or runtime issues before promotion.",
                "backend:health-signals",
                confidence,
                correlationId,
                List.of(new PhasePacket.PhasePanelCard(
                        "observe-preview",
                        "Preview health",
                        healthSignals.preview().issues().isEmpty() ? "No preview issues" : healthSignals.preview().issues().get(0),
                        healthSignals.preview().status(),
                        correlationId,
                        Map.of("issueCount", healthSignals.preview().issues().size())))));

        panels.add(new PhasePacket.PhasePanelView(
                "learn",
                healthSignals.agentGovernance().status(),
                "Learning state is driven by governance and evidence signals.",
                "Review governance evidence before accepting learning recommendations.",
                "backend:agent-governance",
                confidence,
                correlationId,
                List.of(new PhasePacket.PhasePanelCard(
                        "learn-evidence",
                        "Governance evidence",
                        healthSignals.agentGovernance().evidenceIds().isEmpty()
                                ? "No evidence linked"
                                : "Evidence linked: " + healthSignals.agentGovernance().evidenceIds().size(),
                        healthSignals.agentGovernance().status(),
                        correlationId,
                        Map.of("evidenceCount", healthSignals.agentGovernance().evidenceIds().size()))),
                learningInsightService.build(
                        healthSignals.agentGovernance(),
                        activityFeed,
                        confidence
                ),
                null));

        panels.add(new PhasePacket.PhasePanelView(
                "evolve",
                readiness.canAdvance() ? "ready" : "needs-review",
                "Evolution planning follows lifecycle readiness and observed outcomes.",
                readiness.nextPhase() == null ? "No next phase available." : "Next phase: " + readiness.nextPhase(),
                "backend:lifecycle",
                confidence,
                correlationId,
                List.of(new PhasePacket.PhasePanelCard(
                        "evolve-activity",
                        "Recent lifecycle activity",
                        activityFeed.isEmpty() ? "No activity yet" : activityFeed.get(0).summary(),
                        activityFeed.isEmpty() ? "unknown" : "observed",
                        activityFeed.isEmpty() ? Instant.now().toString() : String.valueOf(activityFeed.get(0).timestamp()),
                        Map.of("activityCount", activityFeed.size(), "evidenceCount", evidence.size(), "governanceCount", governance.size()))),
                null,
                evolutionPlanService.build(
                        readiness,
                        blockers,
                        activityFeed,
                        evidence,
                        governance
                )));

        if ("generate".equals(phase) || "run".equals(phase) || "observe".equals(phase) || "learn".equals(phase) || "evolve".equals(phase)) {
            return panels;
        }

        return List.of();
    }
}