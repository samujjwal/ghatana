/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.gate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 7: Contract tests for AgentMasteryGate.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Mastery state validation</li>
 *   <li>Gate passes when mastery sufficient</li>
 *   <li>Gate fails on insufficient mastery</li>
 * </ul>
 */
@DisplayName("Agent Mastery Gate Tests (Phase 7)")
class AgentMasteryGateTest {

    // =========================================================================
    //  Gate Evaluation
    // =========================================================================

    @Nested
    @DisplayName("Gate Evaluation")
    class EvaluationTests {

        @Test
        @DisplayName("gate passes when mastery state is ready")
        void gatePassesWhenMasteryStateIsReady() {
            AgentMasteryGate.MasteryRegistry registry = mock(AgentMasteryGate.MasteryRegistry.class);
            when(registry.getMasteryState("agent-1", "1.0.0"))
                .thenReturn(Map.of("isReady", true, "currentMasteryLevel", 5));

            AgentMasteryGate gate = new AgentMasteryGate(registry);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("gate fails when mastery state not found")
        void gateFailsWhenMasteryStateNotFound() {
            AgentMasteryGate.MasteryRegistry registry = mock(AgentMasteryGate.MasteryRegistry.class);
            when(registry.getMasteryState("agent-1", "1.0.0")).thenReturn(null);

            AgentMasteryGate gate = new AgentMasteryGate(registry);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).contains("not found");
        }

        @Test
        @DisplayName("gate fails when mastery state not ready")
        void gateFailsWhenMasteryStateNotReady() {
            AgentMasteryGate.MasteryRegistry registry = mock(AgentMasteryGate.MasteryRegistry.class);
            when(registry.getMasteryState("agent-1", "1.0.0"))
                .thenReturn(Map.of("isReady", false));

            AgentMasteryGate gate = new AgentMasteryGate(registry);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).contains("not ready");
        }

        @Test
        @DisplayName("gate fails when mastery level insufficient")
        void gateFailsWhenMasteryLevelInsufficient() {
            AgentMasteryGate.MasteryRegistry registry = mock(AgentMasteryGate.MasteryRegistry.class);
            when(registry.getMasteryState("agent-1", "1.0.0"))
                .thenReturn(Map.of("isReady", true, "currentMasteryLevel", 3, "requiredMasteryLevel", 5));

            AgentMasteryGate gate = new AgentMasteryGate(registry);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).contains("Insufficient mastery level");
        }

        @Test
        @DisplayName("gate passes when mastery level sufficient")
        void gatePassesWhenMasteryLevelSufficient() {
            AgentMasteryGate.MasteryRegistry registry = mock(AgentMasteryGate.MasteryRegistry.class);
            when(registry.getMasteryState("agent-1", "1.0.0"))
                .thenReturn(Map.of("isReady", true, "currentMasteryLevel", 5, "requiredMasteryLevel", 3));

            AgentMasteryGate gate = new AgentMasteryGate(registry);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("requires non-null context")
        void requiresNonNullContext() {
            AgentMasteryGate.MasteryRegistry registry = mock(AgentMasteryGate.MasteryRegistry.class);
            AgentMasteryGate gate = new AgentMasteryGate(registry);

            assertThatThrownBy(() -> gate.evaluate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("context must not be null");
        }
    }
}
