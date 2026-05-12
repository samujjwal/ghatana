/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import com.ghatana.agent.mastery.ConfidenceVector;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.VersionScope;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ObsolescencePolicy.
 *
 * @doc.type class
 * @doc.purpose Tests for ObsolescencePolicy
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("ObsolescencePolicy Tests")
class ObsolescencePolicyTest {

    @Test
    @DisplayName("Default policy should mark obsolete when severity exceeds threshold")
    void defaultPolicyShouldMarkObsoleteWhenSeverityExceedsThreshold() {
        MasteryItem item = createMasteryItem();
        ObsolescenceSignal signal = ObsolescenceSignal.withSeverity(
                item.masteryId(),
                ObsolescenceSignalType.VERSION_MISMATCH,
                "test-source",
                "Version mismatch detected",
                0.8
        );

        Promise<ObsolescencePolicy.ObsolescenceDecision> result = ObsolescencePolicy.DEFAULT.evaluate(item, List.of(signal));

        ObsolescencePolicy.ObsolescenceDecision decision = result.await();
        assertThat(decision.shouldMarkObsolete()).isTrue();
        assertThat(decision.reason()).contains("Severity threshold exceeded");
    }

    @Test
    @DisplayName("Default policy should keep item when severity below threshold")
    void defaultPolicyShouldKeepItemWhenSeverityBelowThreshold() {
        MasteryItem item = createMasteryItem();
        ObsolescenceSignal signal = ObsolescenceSignal.withSeverity(
                item.masteryId(),
                ObsolescenceSignalType.VERSION_MISMATCH,
                "test-source",
                "Version mismatch detected",
                0.5
        );

        Promise<ObsolescencePolicy.ObsolescenceDecision> result = ObsolescencePolicy.DEFAULT.evaluate(item, List.of(signal));

        ObsolescencePolicy.ObsolescenceDecision decision = result.await();
        assertThat(decision.shouldMarkObsolete()).isFalse();
        assertThat(decision.reason()).isEqualTo("No obsolescence detected");
    }

    @Test
    @DisplayName("Conservative policy should require multiple high severity signals")
    void conservativePolicyShouldRequireMultipleHighSeveritySignals() {
        MasteryItem item = createMasteryItem();
        ObsolescenceSignal signal1 = ObsolescenceSignal.withSeverity(
                item.masteryId(),
                ObsolescenceSignalType.VERSION_MISMATCH,
                "test-source",
                "Version mismatch detected",
                0.8
        );
        ObsolescenceSignal signal2 = ObsolescenceSignal.withSeverity(
                item.masteryId(),
                ObsolescenceSignalType.PERFORMANCE_DEGRADATION,
                "test-source",
                "Performance degraded",
                0.75
        );

        Promise<ObsolescencePolicy.ObsolescenceDecision> result = ObsolescencePolicy.CONSERVATIVE.evaluate(item, List.of(signal1, signal2));

        ObsolescencePolicy.ObsolescenceDecision decision = result.await();
        assertThat(decision.shouldMarkObsolete()).isTrue();
        assertThat(decision.reason()).contains("Multiple high severity signals");
    }

    @Test
    @DisplayName("Conservative policy should keep item with single high severity signal")
    void conservativePolicyShouldKeepItemWithSingleHighSeveritySignal() {
        MasteryItem item = createMasteryItem();
        ObsolescenceSignal signal = ObsolescenceSignal.withSeverity(
                item.masteryId(),
                ObsolescenceSignalType.VERSION_MISMATCH,
                "test-source",
                "Version mismatch detected",
                0.8
        );

        Promise<ObsolescencePolicy.ObsolescenceDecision> result = ObsolescencePolicy.CONSERVATIVE.evaluate(item, List.of(signal));

        ObsolescencePolicy.ObsolescenceDecision decision = result.await();
        assertThat(decision.shouldMarkObsolete()).isFalse();
    }

    @Test
    @DisplayName("Obsolescence signal should detect high severity")
    void obsolescenceSignalShouldDetectHighSeverity() {
        ObsolescenceSignal signal = ObsolescenceSignal.withSeverity(
                "mastery-123",
                ObsolescenceSignalType.VERSION_MISMATCH,
                "test-source",
                "Version mismatch detected",
                0.8
        );

        assertThat(signal.isHighSeverity()).isTrue();
    }

    @Test
    @DisplayName("Obsolescence signal should not detect high severity for low values")
    void obsolescenceSignalShouldNotDetectHighSeverityForLowValues() {
        ObsolescenceSignal signal = ObsolescenceSignal.withSeverity(
                "mastery-123",
                ObsolescenceSignalType.VERSION_MISMATCH,
                "test-source",
                "Version mismatch detected",
                0.5
        );

        assertThat(signal.isHighSeverity()).isFalse();
    }

    // Helper methods

    private MasteryItem createMasteryItem() {
        return MasteryItem.builder()
                .masteryId("mastery-123")
                .skillId("skill-123")
                .agentId("agent-123")
                .tenantId("tenant-123")
                .state(MasteryState.COMPETENT)
                .versionScope(VersionScope.active())
                .confidence(ConfidenceVector.of(0.8, 0.7, 0.9))
                .lastVerifiedAt(Instant.now())
                .knownFailureModeIds(Set.of())
                .labels(Map.of())
                .build();
    }
}
