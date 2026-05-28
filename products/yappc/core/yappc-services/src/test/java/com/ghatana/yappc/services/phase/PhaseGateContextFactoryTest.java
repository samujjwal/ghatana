package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;
import com.ghatana.yappc.services.lifecycle.gate.PhaseGateValidator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PhaseGateContextFactoryTest {

    @Test
    void buildMapsArtifactAndConditionVerdicts() {
        PhaseGateContextFactory factory = new PhaseGateContextFactory();

        PhaseGateValidator.PhaseGateContext context = factory.build(
                "generate",
                "project-1",
                "workspace-1",
                Map.of(
                        "tenantId", "tenant-1",
                        "conditions", Map.of("phase.generate.ready", true),
                        "unsatisfiedCriteria", List.of("criteria.security.reviewed")),
                List.of(
                        new PhasePacket.RequiredArtifact("artifact-a", "CONFIG", "Config", "Config artifact", false),
                        new PhasePacket.RequiredArtifact("artifact-b", "TEST", "Tests", "Test artifact", false)),
                List.of(
                        new PhasePacket.CompletedArtifact(
                                "artifact-a",
                                "CONFIG",
                                "v1",
                                "Config",
                                Instant.parse("2026-05-27T10:15:30Z"),
                                "system",
                                "evidence-1")),
                List.of(new PhasePacket.PhaseEvidence(
                        "ev-1",
                        "PLATFORM",
                        "Evidence",
                        "Evidence",
                        Instant.parse("2026-05-27T10:15:30Z"),
                        Map.of(),
                        "ev-1")),
                List.of(new PhasePacket.GovernanceRecord(
                        "gov-1",
                        "POLICY_APPROVAL",
                        "APPROVED",
                        "system",
                        Instant.parse("2026-05-27T10:15:30Z"),
                        Map.of(),
                        "policy-1")),
                new PhasePacket.HealthSignals(
                        new PhasePacket.PreviewHealth(true, "healthy", List.of()),
                        new PhasePacket.GenerationHealth(true, "healthy", "gen-1", List.of()),
                        new PhasePacket.RuntimeHealth(true, "healthy", "run-1", List.of())),
                Set.of("phase.advance"));

        assertThat(context.requiredArtifactIds()).containsExactlyInAnyOrder("artifact-a", "artifact-b");
        assertThat(context.completedArtifactIds()).containsExactly("artifact-a");
        assertThat(context.evidenceAvailable()).isTrue();
        assertThat(context.policyAllowed()).isTrue();
        assertThat(context.conditionVerdicts())
                .containsEntry("phase.generate.ready", true)
                .containsEntry("criteria.security.reviewed", false)
                .containsEntry("artifact:artifact-a", true)
                .containsEntry("artifact:artifact-b", false)
                .containsEntry("phase.advance-enabled", true);
    }
}
