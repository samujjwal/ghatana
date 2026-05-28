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
    }

    @Test
    void assembleReturnsNoPanelsForNonPanelPhases() {
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

        assertThat(packet.phasePanels()).isEmpty();
    }
}
