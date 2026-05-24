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
 * Phase 7: Contract tests for AgentVersionGate.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Version context validation</li>
 *   <li>Gate passes when version compatible</li>
 *   <li>Gate fails on version incompatibility</li>
 * </ul>
 */
@DisplayName("Agent Version Gate Tests (Phase 7)")
class AgentVersionGateTest {

    // =========================================================================
    //  Gate Evaluation
    // =========================================================================

    @Nested
    @DisplayName("Gate Evaluation")
    class EvaluationTests {

        @Test
        @DisplayName("gate passes when version compatible")
        void gatePassesWhenVersionCompatible() {
            AgentVersionGate.VersionContextResolver resolver = mock(AgentVersionGate.VersionContextResolver.class);
            when(resolver.resolveVersionContext("agent-1", "1.0.0"))
                .thenReturn(Map.of("isCompatible", true));
            when(resolver.getRuntimeFingerprint()).thenReturn(Map.of("dep1", "v1"));

            AgentVersionGate gate = new AgentVersionGate(resolver);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("gate fails when version context not found")
        void gateFailsWhenVersionContextNotFound() {
            AgentVersionGate.VersionContextResolver resolver = mock(AgentVersionGate.VersionContextResolver.class);
            when(resolver.resolveVersionContext("agent-1", "1.0.0")).thenReturn(null);

            AgentVersionGate gate = new AgentVersionGate(resolver);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).contains("not found");
        }

        @Test
        @DisplayName("gate fails when version not compatible")
        void gateFailsWhenVersionNotCompatible() {
            AgentVersionGate.VersionContextResolver resolver = mock(AgentVersionGate.VersionContextResolver.class);
            when(resolver.resolveVersionContext("agent-1", "1.0.0"))
                .thenReturn(Map.of("isCompatible", false));

            AgentVersionGate gate = new AgentVersionGate(resolver);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).contains("not compatible");
        }

        @Test
        @DisplayName("gate fails when dependency fingerprint mismatch")
        void gateFailsWhenDependencyFingerprintMismatch() {
            AgentVersionGate.VersionContextResolver resolver = mock(AgentVersionGate.VersionContextResolver.class);
            when(resolver.resolveVersionContext("agent-1", "1.0.0"))
                .thenReturn(Map.of(
                    "isCompatible", true,
                    "dependencyFingerprint", Map.of("dep1", "v1", "dep2", "v2")));
            when(resolver.getRuntimeFingerprint()).thenReturn(Map.of("dep1", "v1", "dep2", "v3"));

            AgentVersionGate gate = new AgentVersionGate(resolver);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).contains("fingerprint mismatch");
        }

        @Test
        @DisplayName("gate passes when dependency fingerprint matches")
        void gatePassesWhenDependencyFingerprintMatches() {
            AgentVersionGate.VersionContextResolver resolver = mock(AgentVersionGate.VersionContextResolver.class);
            when(resolver.resolveVersionContext("agent-1", "1.0.0"))
                .thenReturn(Map.of(
                    "isCompatible", true,
                    "dependencyFingerprint", Map.of("dep1", "v1", "dep2", "v2")));
            when(resolver.getRuntimeFingerprint()).thenReturn(Map.of("dep1", "v1", "dep2", "v2"));

            AgentVersionGate gate = new AgentVersionGate(resolver);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("requires non-null context")
        void requiresNonNullContext() {
            AgentVersionGate.VersionContextResolver resolver = mock(AgentVersionGate.VersionContextResolver.class);
            AgentVersionGate gate = new AgentVersionGate(resolver);

            assertThatThrownBy(() -> gate.evaluate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("context must not be null");
        }
    }
}
