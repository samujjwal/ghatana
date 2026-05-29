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

        // Intent panel
        panels.add(new PhasePacket.PhasePanelView(
                "intent",
                readiness.canAdvance() ? "ready" : "needs-input",
                readiness.canAdvance()
                        ? "phasePanel.intent.summary.ready"
                        : "phasePanel.intent.summary.needsInput",
                dashboardActions.primaryAction() == null ? "phasePanel.intent.recommendation.capture" : dashboardActions.primaryAction(),
                "backend",
                confidence,
                "backend:intent-evidence",
                List.of(
                        new PhasePacket.PhasePanelCard(
                                "intent-goals",
                                "phasePanel.intent.card.goals.label",
                                evidence.isEmpty() ? "phasePanel.intent.card.goals.empty" : "phasePanel.intent.card.goals.populated",
                                evidence.isEmpty() ? "empty" : "populated",
                                correlationId,
                                Map.of("goalCount", evidence.size())),
                        new PhasePacket.PhasePanelCard(
                                "intent-personas",
                                "phasePanel.intent.card.personas.label",
                                activityFeed.isEmpty() ? "phasePanel.intent.card.personas.empty" : "phasePanel.intent.card.personas.active",
                                activityFeed.isEmpty() ? "unknown" : "active",
                                correlationId,
                                Map.of("personaCount", activityFeed.size())),
                        new PhasePacket.PhasePanelCard(
                                "intent-constraints",
                                "phasePanel.intent.card.constraints.label",
                                blockers.isEmpty() ? "phasePanel.intent.card.constraints.none" : "phasePanel.intent.card.constraints.blocked",
                                blockers.isEmpty() ? "healthy" : "blocked",
                                correlationId,
                                Map.of("constraintCount", blockers.size())),
                        new PhasePacket.PhasePanelCard(
                                "intent-success-criteria",
                                "phasePanel.intent.card.successCriteria.label",
                                readiness.canAdvance() ? "phasePanel.intent.card.successCriteria.met" : "phasePanel.intent.card.successCriteria.pending",
                                readiness.canAdvance() ? "met" : "pending",
                                correlationId,
                                Map.of("readinessScore", readiness.completenessScore())))));

        // Shape panel
        panels.add(new PhasePacket.PhasePanelView(
                "shape",
                readiness.canAdvance() ? "ready" : "needs-modeling",
                readiness.canAdvance()
                        ? "phasePanel.shape.summary.ready"
                        : "phasePanel.shape.summary.needsModeling",
                "phasePanel.shape.recommendation",
                "backend",
                confidence,
                "backend:shape-model",
                List.of(
                        new PhasePacket.PhasePanelCard(
                                "shape-surfaces",
                                "phasePanel.shape.card.surfaces.label",
                                activityFeed.isEmpty() ? "phasePanel.shape.card.surfaces.none" : "phasePanel.shape.card.surfaces.selected",
                                activityFeed.isEmpty() ? "empty" : "selected",
                                correlationId,
                                Map.of("surfaceCount", activityFeed.size())),
                        new PhasePacket.PhasePanelCard(
                                "shape-architecture",
                                "phasePanel.shape.card.architecture.label",
                                readiness.canAdvance() ? "phasePanel.shape.card.architecture.valid" : "phasePanel.shape.card.architecture.incomplete",
                                readiness.canAdvance() ? "valid" : "incomplete",
                                correlationId,
                                Map.of("architectureScore", readiness.completenessScore())),
                        new PhasePacket.PhasePanelCard(
                                "shape-dependencies",
                                "phasePanel.shape.card.dependencies.label",
                                blockers.isEmpty() ? "phasePanel.shape.card.dependencies.resolved" : "phasePanel.shape.card.dependencies.blocked",
                                blockers.isEmpty() ? "resolved" : "blocked",
                                correlationId,
                                Map.of("dependencyCount", blockers.size())),
                        new PhasePacket.PhasePanelCard(
                                "shape-modeling-gaps",
                                "phasePanel.shape.card.gaps.label",
                                evidence.isEmpty() ? "phasePanel.shape.card.gaps.none" : "phasePanel.shape.card.gaps.detected",
                                evidence.isEmpty() ? "none" : "detected",
                                correlationId,
                                Map.of("gapCount", evidence.size())))));

        // Validate panel
        panels.add(new PhasePacket.PhasePanelView(
                "validate",
                readiness.canAdvance() ? "ready" : "blocked",
                readiness.canAdvance()
                        ? "phasePanel.validate.summary.ready"
                        : "phasePanel.validate.summary.blocked",
                readiness.canAdvance() ? "phasePanel.validate.recommendation.proceed" : "phasePanel.validate.recommendation.remediate",
                "backend",
                confidence,
                "backend:validation-gate",
                List.of(
                        new PhasePacket.PhasePanelCard(
                                "validate-gate-result",
                                "phasePanel.validate.card.gate.label",
                                readiness.canAdvance() ? "phasePanel.validate.card.gate.passed" : "phasePanel.validate.card.gate.failed",
                                readiness.canAdvance() ? "passed" : "failed",
                                correlationId,
                                Map.of("gateScore", readiness.completenessScore())),
                        new PhasePacket.PhasePanelCard(
                                "validate-missing-artifacts",
                                "phasePanel.validate.card.artifacts.label",
                                blockers.isEmpty() ? "phasePanel.validate.card.artifacts.complete" : "phasePanel.validate.card.artifacts.missing",
                                blockers.isEmpty() ? "complete" : "missing",
                                correlationId,
                                Map.of("missingCount", blockers.size())),
                        new PhasePacket.PhasePanelCard(
                                "validate-policy-outcome",
                                "phasePanel.validate.card.policy.label",
                                governance.isEmpty() ? "phasePanel.validate.card.policy.noPolicy" : "phasePanel.validate.card.policy.evaluated",
                                governance.isEmpty() ? "none" : "evaluated",
                                correlationId,
                                Map.of("policyCount", governance.size())),
                        new PhasePacket.PhasePanelCard(
                                "validate-confidence",
                                "phasePanel.validate.card.confidence.label",
                                confidence >= 0.8 ? "phasePanel.validate.card.confidence.high" : "phasePanel.validate.card.confidence.low",
                                confidence >= 0.8 ? "high" : "low",
                                correlationId,
                                Map.of("confidence", confidence)),
                        new PhasePacket.PhasePanelCard(
                                "validate-remediation",
                                "phasePanel.validate.card.remediation.label",
                                blockers.isEmpty() ? "phasePanel.validate.card.remediation.none" : "phasePanel.validate.card.remediation.required",
                                blockers.isEmpty() ? "none" : "required",
                                correlationId,
                                Map.of("remediationSteps", blockers.size())))));

        panels.add(new PhasePacket.PhasePanelView(
                "generate",
                readiness.canAdvance() ? "ready" : "blocked",
                readiness.canAdvance()
                        ? "phasePanel.generate.summary.ready"
                        : "phasePanel.generate.summary.blocked",
                dashboardActions.primaryAction() == null ? "phasePanel.generate.recommendation.review" : dashboardActions.primaryAction(),
                "backend",
                confidence,
                "backend:phase-readiness",
                List.of(new PhasePacket.PhasePanelCard(
                        "generate-blockers",
                        "phasePanel.generate.card.blockers.label",
                        blockers.isEmpty() ? "phasePanel.generate.card.blockers.none" : blockers.get(0).description(),
                        blockers.isEmpty() ? "healthy" : "blocked",
                        correlationId,
                        Map.of("count", blockers.size())))));

        String runStatus = platformRunStatus == null ? healthSignals.runtime().status() : platformRunStatus.status();
        panels.add(new PhasePacket.PhasePanelView(
                "run",
                runStatus,
                platformRunStatus == null
                        ? "phasePanel.run.summary.noRecord"
                        : "phasePanel.run.summary.linked",
                "phasePanel.run.recommendation",
                "backend",
                confidence,
                "backend:platform-run-status",
                List.of(new PhasePacket.PhasePanelCard(
                        "run-status",
                        "phasePanel.run.card.status.label",
                        runStatus,
                        runStatus,
                        correlationId,
                        Map.of("traceId", platformRunStatus == null ? "" : platformRunStatus.traceId())))));

        panels.add(new PhasePacket.PhasePanelView(
                "observe",
                healthSignals.preview().status(),
                "phasePanel.observe.summary.source",
                "phasePanel.observe.recommendation",
                "backend",
                confidence,
                "backend:health-signals",
                List.of(new PhasePacket.PhasePanelCard(
                        "observe-preview",
                        "phasePanel.observe.card.preview.label",
                        healthSignals.preview().issues().isEmpty() ? "phasePanel.observe.card.preview.healthy" : healthSignals.preview().issues().get(0),
                        healthSignals.preview().status(),
                        correlationId,
                        Map.of("issueCount", healthSignals.preview().issues().size())))));

        panels.add(new PhasePacket.PhasePanelView(
                "learn",
                healthSignals.agentGovernance().status(),
                "phasePanel.learn.summary.source",
                "phasePanel.learn.recommendation",
                "backend",
                confidence,
                "backend:agent-governance",
                List.of(new PhasePacket.PhasePanelCard(
                        "learn-evidence",
                        "phasePanel.learn.card.evidence.label",
                        healthSignals.agentGovernance().evidenceIds().isEmpty()
                                ? "phasePanel.learn.card.evidence.none"
                                : "phasePanel.learn.card.evidence.linked",
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
                "phasePanel.evolve.summary.source",
                readiness.nextPhase() == null ? "phasePanel.evolve.recommendation.noNext" : "phasePanel.evolve.recommendation.next",
                "backend",
                confidence,
                "backend:lifecycle",
                List.of(new PhasePacket.PhasePanelCard(
                        "evolve-activity",
                        "phasePanel.evolve.card.activity.label",
                        activityFeed.isEmpty() ? "phasePanel.evolve.card.activity.none" : activityFeed.get(0).summary(),
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

        return panels;
    }
}