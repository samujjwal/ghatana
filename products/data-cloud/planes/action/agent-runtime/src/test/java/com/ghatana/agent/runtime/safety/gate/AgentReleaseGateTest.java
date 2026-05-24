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
 * Phase 7: Contract tests for AgentReleaseGate.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Release state validation</li>
 *   <li>Gate passes for response-serving releases</li>
 *   <li>Gate fails for non-response-serving releases</li>
 * </ul>
 */
@DisplayName("Agent Release Gate Tests (Phase 7)")
class AgentReleaseGateTest {

    // =========================================================================
    //  Gate Evaluation
    // =========================================================================

    @Nested
    @DisplayName("Gate Evaluation")
    class EvaluationTests {

        @Test
        @DisplayName("gate passes for response-serving release")
        void gatePassesForResponseServingRelease() {
            AgentReleaseGate.ReleaseStateChecker checker = mock(AgentReleaseGate.ReleaseStateChecker.class);
            when(checker.isResponseServing("agent-1", "1.0.0")).thenReturn(true);

            AgentReleaseGate gate = new AgentReleaseGate(checker);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("gate fails for non-response-serving release")
        void gateFailsForNonResponseServingRelease() {
            AgentReleaseGate.ReleaseStateChecker checker = mock(AgentReleaseGate.ReleaseStateChecker.class);
            when(checker.isResponseServing("agent-1", "1.0.0")).thenReturn(false);

            AgentReleaseGate gate = new AgentReleaseGate(checker);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).contains("agent-1@1.0.0");
            assertThat(result.reason()).contains("not in response-serving state");
        }

        @Test
        @DisplayName("requires non-null context")
        void requiresNonNullContext() {
            AgentReleaseGate.ReleaseStateChecker checker = mock(AgentReleaseGate.ReleaseStateChecker.class);
            AgentReleaseGate gate = new AgentReleaseGate(checker);

            assertThatThrownBy(() -> gate.evaluate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("context must not be null");
        }
    }

    // =========================================================================
    //  Gate Result Records
    // =========================================================================

    @Nested
    @DisplayName("Gate Result Records")
    class ResultRecordTests {

        @Test
        @DisplayName("success result has true passed flag")
        void successResultHasTruePassedFlag() {
            AgentDispatchGate.GateResult result = AgentDispatchGate.GateResult.success();

            assertThat(result.passed()).isTrue();
            assertThat(result.reason()).isNull();
        }

        @Test
        @DisplayName("failure result has false passed flag")
        void failureResultHasFalsePassedFlag() {
            AgentDispatchGate.GateResult result = AgentDispatchGate.GateResult.failure("test reason");

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).isEqualTo("test reason");
        }
    }

    // =========================================================================
    //  Dispatch Context Records
    // =========================================================================

    @Nested
    @DisplayName("Dispatch Context Records")
    class ContextRecordTests {

        @Test
        @DisplayName("context contains all fields")
        void contextContainsAllFields() {
            Map<String, Object> metadata = Map.of("key", "value");
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", metadata);

            assertThat(context.agentId()).isEqualTo("agent-1");
            assertThat(context.agentVersion()).isEqualTo("1.0.0");
            assertThat(context.executionGrant()).isEqualTo("grant-123");
            assertThat(context.metadata()).isEqualTo(metadata);
        }
    }
}
