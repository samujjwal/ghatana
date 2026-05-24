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
 * Phase 7: Contract tests for AgentGrantGate.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Grant validation</li>
 *   <li>Permission checking</li>
 *   <li>Gate passes for valid grants</li>
 * </ul>
 */
@DisplayName("Agent Grant Gate Tests (Phase 7)")
class AgentGrantGateTest {

    // =========================================================================
    //  Gate Evaluation
    // =========================================================================

    @Nested
    @DisplayName("Gate Evaluation")
    class EvaluationTests {

        @Test
        @DisplayName("gate passes for valid grant with permission")
        void gatePassesForValidGrantWithPermission() {
            AgentGrantGate.GrantValidator validator = mock(AgentGrantGate.GrantValidator.class);
            when(validator.isValidGrant("grant-123")).thenReturn(true);
            when(validator.hasRequiredPermission("grant-123", "agent-1")).thenReturn(true);

            AgentGrantGate gate = new AgentGrantGate(validator);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("gate fails for missing grant")
        void gateFailsForMissingGrant() {
            AgentGrantGate.GrantValidator validator = mock(AgentGrantGate.GrantValidator.class);
            AgentGrantGate gate = new AgentGrantGate(validator);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).contains("missing or blank");
        }

        @Test
        @DisplayName("gate fails for null grant")
        void gateFailsForNullGrant() {
            AgentGrantGate.GrantValidator validator = mock(AgentGrantGate.GrantValidator.class);
            AgentGrantGate gate = new AgentGrantGate(validator);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", null, Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).contains("missing or blank");
        }

        @Test
        @DisplayName("gate fails for invalid grant")
        void gateFailsForInvalidGrant() {
            AgentGrantGate.GrantValidator validator = mock(AgentGrantGate.GrantValidator.class);
            when(validator.isValidGrant("invalid-grant")).thenReturn(false);

            AgentGrantGate gate = new AgentGrantGate(validator);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "invalid-grant", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).contains("invalid or expired");
        }

        @Test
        @DisplayName("gate fails for grant without permission")
        void gateFailsForGrantWithoutPermission() {
            AgentGrantGate.GrantValidator validator = mock(AgentGrantGate.GrantValidator.class);
            when(validator.isValidGrant("grant-123")).thenReturn(true);
            when(validator.hasRequiredPermission("grant-123", "agent-1")).thenReturn(false);

            AgentGrantGate gate = new AgentGrantGate(validator);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).contains("does not have permission");
        }

        @Test
        @DisplayName("requires non-null context")
        void requiresNonNullContext() {
            AgentGrantGate.GrantValidator validator = mock(AgentGrantGate.GrantValidator.class);
            AgentGrantGate gate = new AgentGrantGate(validator);

            assertThatThrownBy(() -> gate.evaluate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("context must not be null");
        }
    }
}
