/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.gate;

import com.ghatana.agent.pluggability.AgentCapabilityManifest;
import com.ghatana.agent.pluggability.HandoffCapability;
import com.ghatana.agent.pluggability.InteractionMode;
import com.ghatana.agent.pluggability.SupervisionRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AgentCapabilityGate.
 */
@DisplayName("AgentCapabilityGate Tests (DC-P9-001)")
class AgentCapabilityGateTest {

    private static AgentCapabilityManifest manifestWithModes(List<InteractionMode> modes) {
        return new AgentCapabilityManifest(
            "agent-1",
            "1.0.0",
            "tenant-1",
            modes,
            SupervisionRole.STANDALONE,
            HandoffCapability.NONE,
            List.of(),
            List.of(),
            Map.of());
    }

    @Nested
    @DisplayName("Autonomous Mode Support")
    class AutonomousModeTests {

        @Test
        @DisplayName("allows dispatch when manifest supports autonomous")
        void allowsDispatchWhenManifestSupportsAutonomous() {
            AgentCapabilityManifest manifest = manifestWithModes(List.of(InteractionMode.AUTONOMOUS));

            AgentCapabilityGate gate = new AgentCapabilityGate(manifest);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", null, Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("allows dispatch when manifest does not support autonomous but supervisor is set")
        void allowsDispatchWhenManifestDoesNotSupportAutonomousButSupervisorIsSet() {
            AgentCapabilityManifest manifest = manifestWithModes(List.of(InteractionMode.SUPERVISED));

            AgentCapabilityGate gate = new AgentCapabilityGate(manifest);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", null, Map.of("supervisorAgentId", "supervisor-1"));

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("rejects dispatch when manifest does not support autonomous and no supervisor")
        void rejectsDispatchWhenManifestDoesNotSupportAutonomousAndNoSupervisor() {
            AgentCapabilityManifest manifest = manifestWithModes(List.of(InteractionMode.SUPERVISED));

            AgentCapabilityGate gate = new AgentCapabilityGate(manifest);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", null, Map.of());

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).contains("does not support AUTONOMOUS execution");
            assertThat(result.reason()).contains("no supervisorAgentId is set");
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("requires non-null manifest")
        void requiresNonNullManifest() {
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new AgentCapabilityGate(null))
                .withMessageContaining("capabilityManifest must not be null");
        }

        @Test
        @DisplayName("requires non-null context")
        void requiresNonNullContext() {
            AgentCapabilityManifest manifest = manifestWithModes(List.of(InteractionMode.AUTONOMOUS));
            AgentCapabilityGate gate = new AgentCapabilityGate(manifest);

            org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> gate.evaluate(null))
                .withMessageContaining("context must not be null");
        }
    }
}
