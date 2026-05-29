package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PhasePacketAssemblerTest {

    @Test
    void assemblePreservesLifecyclePhaseAndBuildsCanonicalPanels() {
        PhasePacketAssembler assembler = new PhasePacketAssembler();

        PhasePacket packet = assembler.assemble(
                "run",
                "project-1",
                "Project One",
                "tenant-1",
                "workspace-1",
                "Workspace One",
                new PhasePacket.ActorContext("user-1", "User One", "EDITOR", false, false),
                "deploy",
                PhasePacket.TenantTier.PRO,
                Set.of("phase.advance"),
                new PhasePacket.CapabilityModel(true, true, true, false, false, false, false),
                List.of(),
                new PhasePacket.PhaseReadiness(true, "observe", List.of(), 0.95d, false, "Ready now", 0, 0.93d),
                List.of(),
                List.of(),
                List.of(new PhasePacket.ActivityFeedEntry(
                        "evt-1",
                        "RUN",
                        "run.execute",
                        "Run completed",
                        "system",
                        Instant.parse("2026-05-27T10:15:30Z"),
                        "INFO",
                        "RUN_COMPLETED",
                        true,
                        "SUCCESS",
                        "corr-1")),
                List.of(),
                List.of(),
                new PhasePacket.PlatformRunStatus(
                        "run-1",
                        "healthy",
                        "platform",
                        Instant.parse("2026-05-27T10:10:00Z"),
                        Instant.parse("2026-05-27T10:15:00Z"),
                        "trace-1",
                        List.of("ev-1")),
                List.of(),
                new PhasePacket.DashboardActionClassification("advance-phase", List.of(), List.of(), List.of("advance-phase")),
                new PhasePacket.HealthSignals(
                        new PhasePacket.PreviewHealth(true, "healthy", List.of()),
                        new PhasePacket.GenerationHealth(true, "healthy", "gen-1", List.of()),
                        new PhasePacket.RuntimeHealth(true, "healthy", "run-1", List.of())),
                null,
                Instant.parse("2026-05-27T10:15:30Z").toEpochMilli(),
                "corr-1");

        assertThat(packet.lifecyclePhase()).isEqualTo("deploy");
        assertThat(packet.phasePanels()).isNotEmpty();
        assertThat(packet.phasePanels()).extracting(PhasePacket.PhasePanelView::phase)
                .contains("generate", "run", "observe", "learn", "evolve");
        assertThat(packet.phasePanels().stream().filter(panel -> "run".equals(panel.phase())).findFirst())
                .isPresent()
                .get()
                .extracting(PhasePacket.PhasePanelView::status)
                .isEqualTo("healthy");
        assertThat(packet.phasePanels().stream().filter(panel -> "learn".equals(panel.phase())).findFirst())
                .isPresent()
                .get()
                .extracting(PhasePacket.PhasePanelView::learningInsight)
                .isNotNull();
        assertThat(packet.phasePanels().stream().filter(panel -> "evolve".equals(panel.phase())).findFirst())
                .isPresent()
                .get()
                .extracting(PhasePacket.PhasePanelView::evolutionPlan)
                .isNotNull();
    }

    @Test
    void assembleReturnsPanelsForAllEightPhases() {
        PhasePacketAssembler assembler = new PhasePacketAssembler();

        // Test Intent phase
        PhasePacket intentPacket = assembler.assemble(
                "intent",
                "project-1",
                "Project One",
                "tenant-1",
                "workspace-1",
                "Workspace One",
                new PhasePacket.ActorContext("user-1", "User One", "EDITOR", false, false),
                "intent",
                PhasePacket.TenantTier.PRO,
                Set.of(),
                new PhasePacket.CapabilityModel(true, false, false, false, false, false, false),
                List.of(),
                new PhasePacket.PhaseReadiness(false, "shape", List.of("Need intent"), 0.40d, false, "~6 hours", 6, 0.45d),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                new PhasePacket.DashboardActionClassification("view", List.of(), List.of(), List.of()),
                new PhasePacket.HealthSignals(
                        new PhasePacket.PreviewHealth(true, "healthy", List.of()),
                        new PhasePacket.GenerationHealth(true, "healthy", "gen-1", List.of()),
                        new PhasePacket.RuntimeHealth(true, "healthy", "run-1", List.of())),
                null,
                Instant.parse("2026-05-27T10:15:30Z").toEpochMilli(),
                "corr-1");

        assertThat(intentPacket.phasePanels()).isNotEmpty();
        assertThat(intentPacket.phasePanels()).extracting(PhasePacket.PhasePanelView::phase)
                .contains("intent");
        assertThat(intentPacket.phasePanels().stream().filter(panel -> "intent".equals(panel.phase())).findFirst())
                .isPresent()
                .get()
                .extracting(PhasePacket.PhasePanelView::summary)
                .isEqualTo("phasePanel.intent.summary.needsInput");

        // Test Shape phase
        PhasePacket shapePacket = assembler.assemble(
                "shape",
                "project-1",
                "Project One",
                "tenant-1",
                "workspace-1",
                "Workspace One",
                new PhasePacket.ActorContext("user-1", "User One", "EDITOR", false, false),
                "shape",
                PhasePacket.TenantTier.PRO,
                Set.of(),
                new PhasePacket.CapabilityModel(true, false, false, false, false, false, false),
                List.of(),
                new PhasePacket.PhaseReadiness(false, "validate", List.of("Need shape"), 0.50d, false, "~4 hours", 4, 0.55d),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                new PhasePacket.DashboardActionClassification("view", List.of(), List.of(), List.of()),
                new PhasePacket.HealthSignals(
                        new PhasePacket.PreviewHealth(true, "healthy", List.of()),
                        new PhasePacket.GenerationHealth(true, "healthy", "gen-1", List.of()),
                        new PhasePacket.RuntimeHealth(true, "healthy", "run-1", List.of())),
                null,
                Instant.parse("2026-05-27T10:15:30Z").toEpochMilli(),
                "corr-1");

        assertThat(shapePacket.phasePanels()).isNotEmpty();
        assertThat(shapePacket.phasePanels()).extracting(PhasePacket.PhasePanelView::phase)
                .contains("shape");
        assertThat(shapePacket.phasePanels().stream().filter(panel -> "shape".equals(panel.phase())).findFirst())
                .isPresent()
                .get()
                .extracting(PhasePacket.PhasePanelView::summary)
                .isEqualTo("phasePanel.shape.summary.needsModeling");

        // Test Validate phase
        PhasePacket validatePacket = assembler.assemble(
                "validate",
                "project-1",
                "Project One",
                "tenant-1",
                "workspace-1",
                "Workspace One",
                new PhasePacket.ActorContext("user-1", "User One", "EDITOR", false, false),
                "validate",
                PhasePacket.TenantTier.PRO,
                Set.of(),
                new PhasePacket.CapabilityModel(true, false, false, false, false, false, false),
                List.of(new PhasePacket.PhaseBlocker("blocker-1", "artifact", "Missing artifact", "error", "high", "artifact-1", true)),
                new PhasePacket.PhaseReadiness(false, "generate", List.of("Gate failed"), 0.60d, false, "~2 hours", 2, 0.65d),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new PhasePacket.GovernanceRecord("gov-1", "POLICY", "DENIED", "system", Instant.parse("2026-05-27T10:15:30Z"), Map.of(), "decision-1")),
                null,
                List.of(),
                new PhasePacket.DashboardActionClassification("view", List.of(), List.of(), List.of()),
                new PhasePacket.HealthSignals(
                        new PhasePacket.PreviewHealth(true, "healthy", List.of()),
                        new PhasePacket.GenerationHealth(true, "healthy", "gen-1", List.of()),
                        new PhasePacket.RuntimeHealth(true, "healthy", "run-1", List.of())),
                null,
                Instant.parse("2026-05-27T10:15:30Z").toEpochMilli(),
                "corr-1");

        assertThat(validatePacket.phasePanels()).isNotEmpty();
        assertThat(validatePacket.phasePanels()).extracting(PhasePacket.PhasePanelView::phase)
                .contains("validate");
        assertThat(validatePacket.phasePanels().stream().filter(panel -> "validate".equals(panel.phase())).findFirst())
                .isPresent()
                .get()
                .extracting(PhasePacket.PhasePanelView::summary)
                .isEqualTo("phasePanel.validate.summary.blocked");
    }

    @Test
    void intentPanelContainsAllRequiredCards() {
        PhasePacketAssembler assembler = new PhasePacketAssembler();

        PhasePacket packet = assembler.assemble(
                "intent",
                "project-1",
                "Project One",
                "tenant-1",
                "workspace-1",
                "Workspace One",
                new PhasePacket.ActorContext("user-1", "User One", "EDITOR", false, false),
                "intent",
                PhasePacket.TenantTier.PRO,
                Set.of(),
                new PhasePacket.CapabilityModel(true, false, false, false, false, false, false),
                List.of(),
                new PhasePacket.PhaseReadiness(true, "shape", List.of(), 0.80d, false, "Ready", 0, 0.85d),
                List.of(),
                List.of(),
                List.of(new PhasePacket.ActivityFeedEntry(
                        "evt-1",
                        "INTENT",
                        "intent.capture",
                        "Intent captured",
                        "system",
                        Instant.parse("2026-05-27T10:15:30Z"),
                        "INFO",
                        "INTENT_CAPTURED",
                        true,
                        "SUCCESS",
                        "corr-1")),
                List.of(new PhasePacket.PhaseEvidence(
                        "ev-1",
                        "INTENT_EVIDENCE",
                        "Goal 1",
                        "Description",
                        Instant.parse("2026-05-27T10:15:30Z"),
                        Map.of(),
                        "evidence-1")),
                List.of(),
                null,
                List.of(),
                new PhasePacket.DashboardActionClassification("view", List.of(), List.of(), List.of()),
                new PhasePacket.HealthSignals(
                        new PhasePacket.PreviewHealth(true, "healthy", List.of()),
                        new PhasePacket.GenerationHealth(true, "healthy", "gen-1", List.of()),
                        new PhasePacket.RuntimeHealth(true, "healthy", "run-1", List.of())),
                null,
                Instant.parse("2026-05-27T10:15:30Z").toEpochMilli(),
                "corr-1");

        PhasePacket.PhasePanelView intentPanel = packet.phasePanels().stream()
                .filter(panel -> "intent".equals(panel.phase()))
                .findFirst()
                .orElseThrow();

        assertThat(intentPanel.cards()).extracting(PhasePacket.PhasePanelCard::id)
                .containsExactlyInAnyOrder("intent-goals", "intent-personas", "intent-constraints", "intent-success-criteria");
        assertThat(intentPanel.supportTrace()).isNotNull();
        assertThat(intentPanel.supportTrace()).isEqualTo("backend:intent-evidence");
    }

    @Test
    void shapePanelContainsAllRequiredCards() {
        PhasePacketAssembler assembler = new PhasePacketAssembler();

        PhasePacket packet = assembler.assemble(
                "shape",
                "project-1",
                "Project One",
                "tenant-1",
                "workspace-1",
                "Workspace One",
                new PhasePacket.ActorContext("user-1", "User One", "EDITOR", false, false),
                "shape",
                PhasePacket.TenantTier.PRO,
                Set.of(),
                new PhasePacket.CapabilityModel(true, false, false, false, false, false, false),
                List.of(),
                new PhasePacket.PhaseReadiness(true, "validate", List.of(), 0.85d, false, "Ready", 0, 0.90d),
                List.of(),
                List.of(),
                List.of(new PhasePacket.ActivityFeedEntry(
                        "evt-1",
                        "SHAPE",
                        "shape.model",
                        "Shape modeled",
                        "system",
                        Instant.parse("2026-05-27T10:15:30Z"),
                        "INFO",
                        "SHAPE_MODELED",
                        true,
                        "SUCCESS",
                        "corr-1")),
                List.of(new PhasePacket.PhaseEvidence(
                        "ev-1",
                        "SHAPE_EVIDENCE",
                        "Surface 1",
                        "Description",
                        Instant.parse("2026-05-27T10:15:30Z"),
                        Map.of(),
                        "evidence-1")),
                List.of(),
                null,
                List.of(),
                new PhasePacket.DashboardActionClassification("view", List.of(), List.of(), List.of()),
                new PhasePacket.HealthSignals(
                        new PhasePacket.PreviewHealth(true, "healthy", List.of()),
                        new PhasePacket.GenerationHealth(true, "healthy", "gen-1", List.of()),
                        new PhasePacket.RuntimeHealth(true, "healthy", "run-1", List.of())),
                null,
                Instant.parse("2026-05-27T10:15:30Z").toEpochMilli(),
                "corr-1");

        PhasePacket.PhasePanelView shapePanel = packet.phasePanels().stream()
                .filter(panel -> "shape".equals(panel.phase()))
                .findFirst()
                .orElseThrow();

        assertThat(shapePanel.cards()).extracting(PhasePacket.PhasePanelCard::id)
                .containsExactlyInAnyOrder("shape-surfaces", "shape-architecture", "shape-dependencies", "shape-modeling-gaps");
        assertThat(shapePanel.supportTrace()).isEqualTo("backend:shape-model");
    }

    @Test
    void validatePanelContainsAllRequiredCards() {
        PhasePacketAssembler assembler = new PhasePacketAssembler();

        PhasePacket packet = assembler.assemble(
                "validate",
                "project-1",
                "Project One",
                "tenant-1",
                "workspace-1",
                "Workspace One",
                new PhasePacket.ActorContext("user-1", "User One", "EDITOR", false, false),
                "validate",
                PhasePacket.TenantTier.PRO,
                Set.of(),
                new PhasePacket.CapabilityModel(true, false, false, false, false, false, false),
                List.of(new PhasePacket.PhaseBlocker("blocker-1", "artifact", "Missing artifact", "error", "high", "artifact-1", true)),
                new PhasePacket.PhaseReadiness(false, "generate", List.of("Gate failed"), 0.60d, false, "~2 hours", 2, 0.65d),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new PhasePacket.GovernanceRecord("gov-1", "POLICY", "DENIED", "system", Instant.parse("2026-05-27T10:15:30Z"), Map.of(), "decision-1")),
                null,
                List.of(),
                new PhasePacket.DashboardActionClassification("view", List.of(), List.of(), List.of()),
                new PhasePacket.HealthSignals(
                        new PhasePacket.PreviewHealth(true, "healthy", List.of()),
                        new PhasePacket.GenerationHealth(true, "healthy", "gen-1", List.of()),
                        new PhasePacket.RuntimeHealth(true, "healthy", "run-1", List.of())),
                null,
                Instant.parse("2026-05-27T10:15:30Z").toEpochMilli(),
                "corr-1");

        PhasePacket.PhasePanelView validatePanel = packet.phasePanels().stream()
                .filter(panel -> "validate".equals(panel.phase()))
                .findFirst()
                .orElseThrow();

        assertThat(validatePanel.cards()).extracting(PhasePacket.PhasePanelCard::id)
                .containsExactlyInAnyOrder("validate-gate-result", "validate-missing-artifacts", "validate-policy-outcome", "validate-confidence", "validate-remediation");
        assertThat(validatePanel.supportTrace()).isEqualTo("backend:validation-gate");
    }

    @Test
    void generatePanelContainsRequiredCardsAndSupportTrace() {
        PhasePacketAssembler assembler = new PhasePacketAssembler();

        PhasePacket packet = assembler.assemble(
                "generate",
                "project-1",
                "Project One",
                "tenant-1",
                "workspace-1",
                "Workspace One",
                new PhasePacket.ActorContext("user-1", "User One", "EDITOR", false, false),
                "generate",
                PhasePacket.TenantTier.PRO,
                Set.of(),
                new PhasePacket.CapabilityModel(true, false, false, false, false, false, false),
                List.of(new PhasePacket.PhaseBlocker("blocker-1", "artifact", "Missing artifact", "error", "high", "artifact-1", true)),
                new PhasePacket.PhaseReadiness(false, "run", List.of("Need generation"), 0.70d, false, "~1 hour", 1, 0.75d),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                new PhasePacket.DashboardActionClassification("view", List.of(), List.of(), List.of()),
                new PhasePacket.HealthSignals(
                        new PhasePacket.PreviewHealth(true, "healthy", List.of()),
                        new PhasePacket.GenerationHealth(true, "healthy", "gen-1", List.of()),
                        new PhasePacket.RuntimeHealth(true, "healthy", "run-1", List.of())),
                null,
                Instant.parse("2026-05-27T10:15:30Z").toEpochMilli(),
                "corr-1");

        PhasePacket.PhasePanelView generatePanel = packet.phasePanels().stream()
                .filter(panel -> "generate".equals(panel.phase()))
                .findFirst()
                .orElseThrow();

        assertThat(generatePanel.cards()).extracting(PhasePacket.PhasePanelCard::id)
                .containsExactly("generate-blockers");
        assertThat(generatePanel.supportTrace()).isEqualTo("backend:phase-readiness");
    }

    @Test
    void runPanelContainsRequiredCardsAndSupportTrace() {
        PhasePacketAssembler assembler = new PhasePacketAssembler();

        PhasePacket packet = assembler.assemble(
                "run",
                "project-1",
                "Project One",
                "tenant-1",
                "workspace-1",
                "Workspace One",
                new PhasePacket.ActorContext("user-1", "User One", "EDITOR", false, false),
                "run",
                PhasePacket.TenantTier.PRO,
                Set.of(),
                new PhasePacket.CapabilityModel(true, false, false, false, false, false, false),
                List.of(),
                new PhasePacket.PhaseReadiness(true, "observe", List.of(), 0.90d, false, "Ready", 0, 0.95d),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new PhasePacket.PlatformRunStatus(
                        "run-1",
                        "healthy",
                        "platform",
                        Instant.parse("2026-05-27T10:10:00Z"),
                        Instant.parse("2026-05-27T10:15:00Z"),
                        "trace-1",
                        List.of("ev-1")),
                List.of(),
                new PhasePacket.DashboardActionClassification("view", List.of(), List.of(), List.of()),
                new PhasePacket.HealthSignals(
                        new PhasePacket.PreviewHealth(true, "healthy", List.of()),
                        new PhasePacket.GenerationHealth(true, "healthy", "gen-1", List.of()),
                        new PhasePacket.RuntimeHealth(true, "healthy", "run-1", List.of())),
                null,
                Instant.parse("2026-05-27T10:15:30Z").toEpochMilli(),
                "corr-1");

        PhasePacket.PhasePanelView runPanel = packet.phasePanels().stream()
                .filter(panel -> "run".equals(panel.phase()))
                .findFirst()
                .orElseThrow();

        assertThat(runPanel.cards()).extracting(PhasePacket.PhasePanelCard::id)
                .containsExactly("run-status");
        assertThat(runPanel.supportTrace()).isEqualTo("backend:platform-run-status");
    }

    @Test
    void observePanelContainsRequiredCardsAndSupportTrace() {
        PhasePacketAssembler assembler = new PhasePacketAssembler();

        PhasePacket packet = assembler.assemble(
                "observe",
                "project-1",
                "Project One",
                "tenant-1",
                "workspace-1",
                "Workspace One",
                new PhasePacket.ActorContext("user-1", "User One", "EDITOR", false, false),
                "observe",
                PhasePacket.TenantTier.PRO,
                Set.of(),
                new PhasePacket.CapabilityModel(true, false, false, false, false, false, false),
                List.of(),
                new PhasePacket.PhaseReadiness(true, "learn", List.of(), 0.95d, false, "Ready", 0, 0.97d),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                new PhasePacket.DashboardActionClassification("view", List.of(), List.of(), List.of()),
                new PhasePacket.HealthSignals(
                        new PhasePacket.PreviewHealth(true, "healthy", List.of()),
                        new PhasePacket.GenerationHealth(true, "healthy", "gen-1", List.of()),
                        new PhasePacket.RuntimeHealth(true, "healthy", "run-1", List.of())),
                null,
                Instant.parse("2026-05-27T10:15:30Z").toEpochMilli(),
                "corr-1");

        PhasePacket.PhasePanelView observePanel = packet.phasePanels().stream()
                .filter(panel -> "observe".equals(panel.phase()))
                .findFirst()
                .orElseThrow();

        assertThat(observePanel.cards()).extracting(PhasePacket.PhasePanelCard::id)
                .containsExactly("observe-preview");
        assertThat(observePanel.supportTrace()).isEqualTo("backend:health-signals");
    }

    @Test
    void learnPanelContainsRequiredCardsAndLearningInsight() {
        PhasePacketAssembler assembler = new PhasePacketAssembler();

        PhasePacket packet = assembler.assemble(
                "learn",
                "project-1",
                "Project One",
                "tenant-1",
                "workspace-1",
                "Workspace One",
                new PhasePacket.ActorContext("user-1", "User One", "EDITOR", false, false),
                "learn",
                PhasePacket.TenantTier.PRO,
                Set.of(),
                new PhasePacket.CapabilityModel(true, false, false, false, false, false, false),
                List.of(),
                new PhasePacket.PhaseReadiness(true, "evolve", List.of(), 0.98d, false, "Ready", 0, 0.99d),
                List.of(),
                List.of(),
                List.of(new PhasePacket.ActivityFeedEntry(
                        "evt-1",
                        "LEARN",
                        "learn.capture",
                        "Learning captured",
                        "system",
                        Instant.parse("2026-05-27T10:15:30Z"),
                        "INFO",
                        "LEARN_CAPTURED",
                        true,
                        "SUCCESS",
                        "corr-1")),
                List.of(),
                List.of(),
                null,
                List.of(),
                new PhasePacket.DashboardActionClassification("view", List.of(), List.of(), List.of()),
                new PhasePacket.HealthSignals(
                        new PhasePacket.PreviewHealth(true, "healthy", List.of()),
                        new PhasePacket.GenerationHealth(true, "healthy", "gen-1", List.of()),
                        new PhasePacket.RuntimeHealth(true, "healthy", "run-1", List.of())),
                null,
                Instant.parse("2026-05-27T10:15:30Z").toEpochMilli(),
                "corr-1");

        PhasePacket.PhasePanelView learnPanel = packet.phasePanels().stream()
                .filter(panel -> "learn".equals(panel.phase()))
                .findFirst()
                .orElseThrow();

        assertThat(learnPanel.cards()).extracting(PhasePacket.PhasePanelCard::id)
                .containsExactly("learn-evidence");
        assertThat(learnPanel.supportTrace()).isEqualTo("backend:agent-governance");
        assertThat(learnPanel.learningInsight()).isNotNull();
    }

    @Test
    void evolvePanelContainsRequiredCardsAndEvolutionPlan() {
        PhasePacketAssembler assembler = new PhasePacketAssembler();

        PhasePacket packet = assembler.assemble(
                "evolve",
                "project-1",
                "Project One",
                "tenant-1",
                "workspace-1",
                "Workspace One",
                new PhasePacket.ActorContext("user-1", "User One", "EDITOR", false, false),
                "evolve",
                PhasePacket.TenantTier.PRO,
                Set.of(),
                new PhasePacket.CapabilityModel(true, false, false, false, false, false, false),
                List.of(),
                new PhasePacket.PhaseReadiness(true, "intent", List.of(), 1.0d, false, "Ready", 0, 1.0d),
                List.of(),
                List.of(),
                List.of(new PhasePacket.ActivityFeedEntry(
                        "evt-1",
                        "EVOLVE",
                        "evolve.plan",
                        "Evolution planned",
                        "system",
                        Instant.parse("2026-05-27T10:15:30Z"),
                        "INFO",
                        "EVOLVE_PLANNED",
                        true,
                        "SUCCESS",
                        "corr-1")),
                List.of(new PhasePacket.PhaseEvidence(
                        "ev-1",
                        "EVOLVE_EVIDENCE",
                        "Evolution evidence",
                        "Description",
                        Instant.parse("2026-05-27T10:15:30Z"),
                        Map.of(),
                        "evidence-1")),
                List.of(new PhasePacket.GovernanceRecord("gov-1", "POLICY", "APPROVED", "system", Instant.parse("2026-05-27T10:15:30Z"), Map.of(), "decision-1")),
                null,
                List.of(),
                new PhasePacket.DashboardActionClassification("view", List.of(), List.of(), List.of()),
                new PhasePacket.HealthSignals(
                        new PhasePacket.PreviewHealth(true, "healthy", List.of()),
                        new PhasePacket.GenerationHealth(true, "healthy", "gen-1", List.of()),
                        new PhasePacket.RuntimeHealth(true, "healthy", "run-1", List.of())),
                null,
                Instant.parse("2026-05-27T10:15:30Z").toEpochMilli(),
                "corr-1");

        PhasePacket.PhasePanelView evolvePanel = packet.phasePanels().stream()
                .filter(panel -> "evolve".equals(panel.phase()))
                .findFirst()
                .orElseThrow();

        assertThat(evolvePanel.cards()).extracting(PhasePacket.PhasePanelCard::id)
                .containsExactly("evolve-activity");
        assertThat(evolvePanel.supportTrace()).isEqualTo("backend:lifecycle");
        assertThat(evolvePanel.evolutionPlan()).isNotNull();
    }
}
