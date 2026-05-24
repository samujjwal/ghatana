/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.gate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 7: Contract tests for AgentInvariantGate.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Invariant evaluation</li>
 *   <li>Gate passes when invariants satisfied</li>
 *   <li>Gate fails on invariant violations</li>
 * </ul>
 */
@DisplayName("Agent Invariant Gate Tests (Phase 7)")
class AgentInvariantGateTest {

    // =========================================================================
    //  Gate Evaluation
    // =========================================================================

    @Nested
    @DisplayName("Gate Evaluation")
    class EvaluationTests {

        @Test
        @DisplayName("gate passes when all invariants satisfied")
        void gatePassesWhenAllInvariantsSatisfied() {
            AgentInvariantGate.InvariantMonitor monitor = mock(AgentInvariantGate.InvariantMonitor.class);
            when(monitor.checkInvariants()).thenReturn(Map.of("allPassed", true));

            AgentInvariantGate gate = new AgentInvariantGate(monitor);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("gate fails when invariants not satisfied")
        void gateFailsWhenInvariantsNotSatisfied() {
            AgentInvariantGate.InvariantMonitor monitor = mock(AgentInvariantGate.InvariantMonitor.class);
            when(monitor.checkInvariants()).thenReturn(Map.of("allPassed", false));

            AgentInvariantGate gate = new AgentInvariantGate(monitor);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).contains("not satisfied");
        }

        @Test
        @DisplayName("gate fails when violations detected")
        void gateFailsWhenViolationsDetected() {
            AgentInvariantGate.InvariantMonitor monitor = mock(AgentInvariantGate.InvariantMonitor.class);
            when(monitor.checkInvariants()).thenReturn(Map.of(
                "violations", List.of("invariant-1 failed", "invariant-2 failed")));

            AgentInvariantGate gate = new AgentInvariantGate(monitor);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).contains("invariant-1 failed");
            assertThat(result.reason()).contains("invariant-2 failed");
        }

        @Test
        @DisplayName("gate passes when violations list is empty")
        void gatePassesWhenViolationsListIsEmpty() {
            AgentInvariantGate.InvariantMonitor monitor = mock(AgentInvariantGate.InvariantMonitor.class);
            when(monitor.checkInvariants()).thenReturn(Map.of(
                "violations", List.of(),
                "allPassed", true));

            AgentInvariantGate gate = new AgentInvariantGate(monitor);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("requires non-null context")
        void requiresNonNullContext() {
            AgentInvariantGate.InvariantMonitor monitor = mock(AgentInvariantGate.InvariantMonitor.class);
            AgentInvariantGate gate = new AgentInvariantGate(monitor);

            assertThatThrownBy(() -> gate.evaluate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("context must not be null");
        }
    }
}
