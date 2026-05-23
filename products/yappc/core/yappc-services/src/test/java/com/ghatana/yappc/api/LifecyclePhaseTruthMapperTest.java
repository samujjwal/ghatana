package com.ghatana.yappc.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LifecyclePhaseTruthMapper")
class LifecyclePhaseTruthMapperTest {

    @Test
    @DisplayName("extracts complete gate context from artifacts, policy, evidence, runtime health, flags, and entitlements")
    void extractsCompleteGateContext() {
        LifecycleExecutionRepository.LifecycleExecution execution = execution(
                Map.of(
                        "valid", true,
                        "runtimeHealthy", true,
                        "evidenceRefs", List.of("ev-validate-1"),
                        "traceId", "trace-validate-1"),
                Map.of(
                        "featureFlags", "phase.advance,release.cockpit",
                        "tenantEntitlements", "foundation.healthcare,release.readiness"));

        LifecyclePhaseTruthMapper.PhaseTruthSnapshot snapshot =
                LifecyclePhaseTruthMapper.fromExecution(execution, "VALIDATE");

        assertThat(snapshot.gateContext())
                .containsEntry("artifacts.present", true)
                .containsEntry("policy.allowed", true)
                .containsEntry("evidence.available", true)
                .containsEntry("runtime.healthy", true)
                .containsEntry("featureFlags.loaded", true)
                .containsEntry("tenantEntitlements.loaded", true)
                .containsEntry("complete", true);
        assertThat(snapshot.evidence()).extracting(ref -> ref.get("ref"))
                .contains("ev-validate-1", "trace-validate-1");
        assertThat(snapshot.featureFlags()).containsExactly("phase.advance", "release.cockpit");
        assertThat(snapshot.tenantEntitlements()).containsExactly("foundation.healthcare", "release.readiness");
    }

    @Test
    @DisplayName("marks gate context incomplete when production inputs are absent")
    void marksIncompleteGateContext() {
        LifecycleExecutionRepository.LifecycleExecution execution = execution(Map.of(), Map.of());

        LifecyclePhaseTruthMapper.PhaseTruthSnapshot snapshot =
                LifecyclePhaseTruthMapper.fromExecution(execution, "GENERATE");

        assertThat(snapshot.gateContext()).containsEntry("complete", false);
        assertThat(strings(snapshot.gateContext().get("missingInputs")))
                .contains("artifacts", "evidence", "featureFlags", "tenantEntitlements");
    }

    @Test
    @DisplayName("policy violations close the policy gate")
    void policyViolationsCloseGate() {
        LifecycleExecutionRepository.LifecycleExecution execution = execution(
                Map.of("violations", List.of("missing-risk-approval"), "evidenceIds", List.of("ev-1")),
                Map.of("featureFlags", "phase.advance", "tenantEntitlements", "foundation.marketing"));

        LifecyclePhaseTruthMapper.PhaseTruthSnapshot snapshot =
                LifecyclePhaseTruthMapper.fromExecution(execution, "VALIDATE");

        assertThat(snapshot.gateContext())
                .containsEntry("policy.allowed", false)
                .containsEntry("complete", false);
        assertThat(strings(snapshot.gateContext().get("missingInputs"))).contains("policy");
    }

    private static List<String> strings(Object values) {
        return ((List<?>) values).stream().map(String::valueOf).toList();
    }

    private static LifecycleExecutionRepository.LifecycleExecution execution(
            Map<String, Object> validationResult,
            Map<String, String> metadata) {
        Instant startedAt = Instant.parse("2026-05-23T00:00:00Z");
        Instant completedAt = Instant.parse("2026-05-23T00:00:05Z");
        return new LifecycleExecutionRepository.LifecycleExecution(
                "exec-1",
                "tenant-1",
                "workspace-1",
                "project-1",
                "actor-1",
                "corr-1",
                "idem-1",
                startedAt,
                completedAt,
                5000L,
                List.of("VALIDATE", "GENERATE"),
                Map.of("VALIDATE", 125L, "GENERATE", 250L),
                "SUCCESS",
                Map.of("intentId", "intent-1"),
                Map.of("shapeId", "shape-1"),
                validationResult,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                metadata);
    }
}
