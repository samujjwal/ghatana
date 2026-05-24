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
 * Phase 7: Contract tests for AgentModeSelectionGate.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Task classification</li>
 *   <li>Mode selection based on mastery</li>
 *   <li>Gate passes when mode selected successfully</li>
 * </ul>
 */
@DisplayName("Agent Mode Selection Gate Tests (Phase 7)")
class AgentModeSelectionGateTest {

    // =========================================================================
    //  Gate Evaluation
    // =========================================================================

    @Nested
    @DisplayName("Gate Evaluation")
    class EvaluationTests {

        @Test
        @DisplayName("gate passes when mode selected successfully")
        void gatePassesWhenModeSelectedSuccessfully() {
            AgentModeSelectionGate.TaskClassifier classifier = mock(AgentModeSelectionGate.TaskClassifier.class);
            AgentModeSelectionGate.MasteryAwareModeSelector selector = mock(AgentModeSelectionGate.MasteryAwareModeSelector.class);
            
            when(classifier.classifyTask(Map.of())).thenReturn(Map.of("risk", "low"));
            when(selector.selectMode("agent-1", Map.of("risk", "low"))).thenReturn("AUTO");

            AgentModeSelectionGate gate = new AgentModeSelectionGate(classifier, selector);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("gate fails when task classification fails")
        void gateFailsWhenTaskClassificationFails() {
            AgentModeSelectionGate.TaskClassifier classifier = mock(AgentModeSelectionGate.TaskClassifier.class);
            AgentModeSelectionGate.MasteryAwareModeSelector selector = mock(AgentModeSelectionGate.MasteryAwareModeSelector.class);
            
            when(classifier.classifyTask(Map.of())).thenReturn(null);

            AgentModeSelectionGate gate = new AgentModeSelectionGate(classifier, selector);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).contains("classification failed");
        }

        @Test
        @DisplayName("gate fails when mode selection fails")
        void gateFailsWhenModeSelectionFails() {
            AgentModeSelectionGate.TaskClassifier classifier = mock(AgentModeSelectionGate.TaskClassifier.class);
            AgentModeSelectionGate.MasteryAwareModeSelector selector = mock(AgentModeSelectionGate.MasteryAwareModeSelector.class);
            
            when(classifier.classifyTask(Map.of())).thenReturn(Map.of("risk", "high"));
            when(selector.selectMode("agent-1", Map.of("risk", "high"))).thenReturn(null);

            AgentModeSelectionGate gate = new AgentModeSelectionGate(classifier, selector);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).contains("selection failed");
        }

        @Test
        @DisplayName("requires non-null context")
        void requiresNonNullContext() {
            AgentModeSelectionGate.TaskClassifier classifier = mock(AgentModeSelectionGate.TaskClassifier.class);
            AgentModeSelectionGate.MasteryAwareModeSelector selector = mock(AgentModeSelectionGate.MasteryAwareModeSelector.class);
            AgentModeSelectionGate gate = new AgentModeSelectionGate(classifier, selector);

            assertThatThrownBy(() -> gate.evaluate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("context must not be null");
        }

        @Test
        @DisplayName("requires non-null task classifier")
        void requiresNonNullTaskClassifier() {
            AgentModeSelectionGate.MasteryAwareModeSelector selector = mock(AgentModeSelectionGate.MasteryAwareModeSelector.class);

            assertThatThrownBy(() -> new AgentModeSelectionGate(null, selector))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("taskClassifier must not be null");
        }

        @Test
        @DisplayName("requires non-null mode selector")
        void requiresNonNullModeSelector() {
            AgentModeSelectionGate.TaskClassifier classifier = mock(AgentModeSelectionGate.TaskClassifier.class);

            assertThatThrownBy(() -> new AgentModeSelectionGate(classifier, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("modeSelector must not be null");
        }
    }
}
