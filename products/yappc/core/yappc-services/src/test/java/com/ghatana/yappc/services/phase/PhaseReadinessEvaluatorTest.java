package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;
import com.ghatana.yappc.services.lifecycle.TransitionConfigLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhaseReadinessEvaluatorTest {

    @Mock
    private TransitionConfigLoader transitionConfigLoader;

    private PhaseReadinessEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new PhaseReadinessEvaluator(transitionConfigLoader);
        when(transitionConfigLoader.getNextPhase("generate")).thenReturn("run");
    }

    @Test
    void calculateReturnsAdvanceReadyWhenSignalsAreHealthy() {
        PhasePacket.PhaseReadiness readiness = evaluator.calculate(
                "generate",
                "project-1",
                List.of(),
                List.of(new PhasePacket.RequiredArtifact("artifact-a", "CONFIG", "Config", "Config", false)),
                List.of(new PhasePacket.CompletedArtifact(
                        "artifact-a",
                        "CONFIG",
                        "v1",
                        "Config",
                        Instant.parse("2026-05-27T10:15:30Z"),
                        "system",
                        "ev-1")),
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
                Map.of("status", "active"));

        assertThat(readiness.canAdvance()).isTrue();
        assertThat(readiness.nextPhase()).isEqualTo("run");
        assertThat(readiness.isDegraded()).isFalse();
        assertThat(readiness.completenessScore()).isGreaterThanOrEqualTo(0.90d);
    }

    @Test
    void calculateFailsClosedWhenCompletedArtifactsAreUnavailable() {
        PhasePacket.PhaseReadiness readiness = evaluator.calculate(
                "generate",
                "project-1",
                List.of(),
                List.of(new PhasePacket.RequiredArtifact("artifact-a", "CONFIG", "Config", "Config", false)),
                List.of(new PhasePacket.CompletedArtifact(
                        "COMPLETED_ARTIFACT_QUERY_FAILED",
                        "SYSTEM_DEGRADED",
                        null,
                        "Completed artifacts unavailable",
                        Instant.parse("2026-05-27T10:15:30Z"),
                        "system",
                        null)),
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
                Map.of("status", "active"));

        assertThat(readiness.canAdvance()).isFalse();
        assertThat(readiness.isDegraded()).isTrue();
        assertThat(readiness.missingPrerequisites()).contains("Completed artifacts unavailable");
    }

    @Test
    void calculateUsesConfigurableThresholdForAdvanceDecision() {
        PhasePacket.PhaseReadiness readiness = evaluator.calculate(
                "generate",
                "project-1",
                List.of(),
                List.of(new PhasePacket.RequiredArtifact("artifact-a", "CONFIG", "Config", "Config", false)),
                List.of(new PhasePacket.CompletedArtifact(
                        "artifact-a",
                        "CONFIG",
                        "v1",
                        "Config",
                        Instant.parse("2026-05-27T10:15:30Z"),
                        "system",
                        "ev-1")),
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
                Map.of("status", "active"));

        // With default config (threshold 0.90), should advance
        assertThat(readiness.canAdvance()).isTrue();
        assertThat(readiness.completenessScore()).isGreaterThanOrEqualTo(0.90d);
    }

    @Test
    void calculateReturnsDegradedWhenAnyDependencyIsUnhealthy() {
        PhasePacket.PhaseReadiness readiness = evaluator.calculate(
                "generate",
                "project-1",
                List.of(),
                List.of(new PhasePacket.RequiredArtifact("artifact-a", "CONFIG", "Config", "Config", false)),
                List.of(new PhasePacket.CompletedArtifact(
                        "artifact-a",
                        "CONFIG",
                        "v1",
                        "Config",
                        Instant.parse("2026-05-27T10:15:30Z"),
                        "system",
                        "ev-1")),
                List.of(new PhasePacket.PhaseEvidence(
                        "ev-1",
                        "SYSTEM_DEGRADED",
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
                Map.of("status", "active"));

        assertThat(readiness.isDegraded()).isTrue();
        assertThat(readiness.canAdvance()).isFalse();
        assertThat(readiness.missingPrerequisites()).contains("Phase evidence unavailable");
    }

    @Test
    void calculateReturnsDegradedWhenPolicyIsDenied() {
        PhasePacket.PhaseReadiness readiness = evaluator.calculate(
                "generate",
                "project-1",
                List.of(),
                List.of(new PhasePacket.RequiredArtifact("artifact-a", "CONFIG", "Config", "Config", false)),
                List.of(new PhasePacket.CompletedArtifact(
                        "artifact-a",
                        "CONFIG",
                        "v1",
                        "Config",
                        Instant.parse("2026-05-27T10:15:30Z"),
                        "system",
                        "ev-1")),
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
                        "POLICY_DENIAL",
                        "DENIED",
                        "system",
                        Instant.parse("2026-05-27T10:15:30Z"),
                        Map.of(),
                        "policy-1")),
                new PhasePacket.HealthSignals(
                        new PhasePacket.PreviewHealth(true, "healthy", List.of()),
                        new PhasePacket.GenerationHealth(true, "healthy", "gen-1", List.of()),
                        new PhasePacket.RuntimeHealth(true, "healthy", "run-1", List.of())),
                Map.of("status", "active"));

        assertThat(readiness.isDegraded()).isTrue();
        assertThat(readiness.canAdvance()).isFalse();
        assertThat(readiness.missingPrerequisites()).contains("Policy approval");
    }

    @Test
    void calculateReturnsDegradedWhenHealthSignalsAreUnhealthy() {
        PhasePacket.PhaseReadiness readiness = evaluator.calculate(
                "generate",
                "project-1",
                List.of(),
                List.of(new PhasePacket.RequiredArtifact("artifact-a", "CONFIG", "Config", "Config", false)),
                List.of(new PhasePacket.CompletedArtifact(
                        "artifact-a",
                        "CONFIG",
                        "v1",
                        "Config",
                        Instant.parse("2026-05-27T10:15:30Z"),
                        "system",
                        "ev-1")),
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
                        new PhasePacket.PreviewHealth(false, "degraded", List.of("Preview issue")),
                        new PhasePacket.GenerationHealth(true, "healthy", "gen-1", List.of()),
                        new PhasePacket.RuntimeHealth(true, "healthy", "run-1", List.of())),
                Map.of("status", "active"));

        assertThat(readiness.isDegraded()).isTrue();
        assertThat(readiness.canAdvance()).isFalse();
        assertThat(readiness.missingPrerequisites()).contains("Healthy preview, generation, and runtime signals");
    }

    @Test
    void calculateReturnsDegradedWhenCriticalBlockerExists() {
        PhasePacket.PhaseReadiness readiness = evaluator.calculate(
                "generate",
                "project-1",
                List.of(new PhasePacket.PhaseBlocker("blocker-1", "artifact", "Critical blocker", "error", "CRITICAL", "artifact-1", true)),
                List.of(new PhasePacket.RequiredArtifact("artifact-a", "CONFIG", "Config", "Config", false)),
                List.of(new PhasePacket.CompletedArtifact(
                        "artifact-a",
                        "CONFIG",
                        "v1",
                        "Config",
                        Instant.parse("2026-05-27T10:15:30Z"),
                        "system",
                        "ev-1")),
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
                Map.of("status", "active"));

        assertThat(readiness.isDegraded()).isTrue();
        assertThat(readiness.canAdvance()).isFalse();
        assertThat(readiness.missingPrerequisites()).contains("Critical blocker");
    }

    @Test
    void calculateFailsWhenProjectStatusIsInactive() {
        PhasePacket.PhaseReadiness readiness = evaluator.calculate(
                "generate",
                "project-1",
                List.of(),
                List.of(new PhasePacket.RequiredArtifact("artifact-a", "CONFIG", "Config", "Config", false)),
                List.of(new PhasePacket.CompletedArtifact(
                        "artifact-a",
                        "CONFIG",
                        "v1",
                        "Config",
                        Instant.parse("2026-05-27T10:15:30Z"),
                        "system",
                        "ev-1")),
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
                Map.of("status", "inactive"));

        assertThat(readiness.canAdvance()).isFalse();
        assertThat(readiness.completenessScore()).isGreaterThanOrEqualTo(0.90d);
    }

    @Test
    void calculateReturnsMissingPrerequisitesForIncompleteArtifacts() {
        PhasePacket.PhaseReadiness readiness = evaluator.calculate(
                "generate",
                "project-1",
                List.of(),
                List.of(
                        new PhasePacket.RequiredArtifact("artifact-a", "CONFIG", "Config", "Config", false),
                        new PhasePacket.RequiredArtifact("artifact-b", "CODE", "Code", "Code", false)),
                List.of(new PhasePacket.CompletedArtifact(
                        "artifact-a",
                        "CONFIG",
                        "v1",
                        "Config",
                        Instant.parse("2026-05-27T10:15:30Z"),
                        "system",
                        "ev-1")),
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
                Map.of("status", "active"));

        assertThat(readiness.canAdvance()).isFalse();
        assertThat(readiness.missingPrerequisites()).contains("Code");
        assertThat(readiness.completenessScore()).isLessThan(0.90d);
    }
}
