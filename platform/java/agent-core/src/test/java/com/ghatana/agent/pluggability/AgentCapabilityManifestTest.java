/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.pluggability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link AgentCapabilityManifest}, the pluggability enums, and
 * {@link AgentCapabilityManifestValidator}.
 *
 * @doc.type class
 * @doc.purpose Tests for P8-T1: AgentCapabilityManifest + enums + validator
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AgentCapabilityManifest (P8-T1)")
class AgentCapabilityManifestTest {

    private static final String AGENT_ID   = "agent-001";
    private static final String VERSION    = "1.0.0";
    private static final String TENANT_ID  = "tenant-001";

    private static AgentCapabilityManifest build( // GH-90000
            List<InteractionMode> modes, SupervisionRole role, HandoffCapability hc) {
        return new AgentCapabilityManifest( // GH-90000
                AGENT_ID, VERSION, TENANT_ID, modes, role, hc, List.of(), List.of(), Map.of()); // GH-90000
    }

    // ─── Record validation ────────────────────────────────────────────────────

    @Nested
    @DisplayName("record validation")
    class RecordValidation {

        @Test
        @DisplayName("blank agentId is rejected")
        void blankAgentIdRejected() { // GH-90000
            assertThatThrownBy(() -> new AgentCapabilityManifest( // GH-90000
                    "", VERSION, TENANT_ID, List.of(InteractionMode.AUTONOMOUS), // GH-90000
                    null, HandoffCapability.NONE, List.of(), List.of(), Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("agentId");
        }

        @Test
        @DisplayName("empty interactionModes is rejected")
        void emptyModesRejected() { // GH-90000
            assertThatThrownBy(() -> new AgentCapabilityManifest( // GH-90000
                    AGENT_ID, VERSION, TENANT_ID, List.of(), // GH-90000
                    null, HandoffCapability.NONE, List.of(), List.of(), Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("interactionModes");
        }

        @Test
        @DisplayName("collections are defensively immutable")
        void collectionsAreImmutable() { // GH-90000
            AgentCapabilityManifest m = build( // GH-90000
                    List.of(InteractionMode.AUTONOMOUS), null, HandoffCapability.NONE); // GH-90000
            assertThatThrownBy(() -> m.interactionModes().add(InteractionMode.CONVERSATIONAL)) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    // ─── supports() / handoff helpers ──────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("capability query helpers")
    class CapabilityHelpers {

        @Test
        @DisplayName("supports() returns true for declared mode")
        void supportsTrue() { // GH-90000
            AgentCapabilityManifest m = build( // GH-90000
                    List.of(InteractionMode.AUTONOMOUS, InteractionMode.SUPERVISED), // GH-90000
                    null, HandoffCapability.NONE);
            assertThat(m.supports(InteractionMode.SUPERVISED)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("supports() returns false for undeclared mode")
        void supportsFalse() { // GH-90000
            AgentCapabilityManifest m = build( // GH-90000
                    List.of(InteractionMode.AUTONOMOUS), null, HandoffCapability.NONE); // GH-90000
            assertThat(m.supports(InteractionMode.COLLABORATIVE)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("canReceiveHandoff() is true for RECEIVER_ONLY")
        void canReceiveTrueForReceiverOnly() { // GH-90000
            AgentCapabilityManifest m = build( // GH-90000
                    List.of(InteractionMode.AUTONOMOUS), null, HandoffCapability.RECEIVER_ONLY); // GH-90000
            assertThat(m.canReceiveHandoff()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("canReceiveHandoff() is true for BIDIRECTIONAL")
        void canReceiveTrueForBidirectional() { // GH-90000
            AgentCapabilityManifest m = build( // GH-90000
                    List.of(InteractionMode.AUTONOMOUS), null, HandoffCapability.BIDIRECTIONAL); // GH-90000
            assertThat(m.canReceiveHandoff()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("canInitiateHandoff() is true for INITIATOR_ONLY")
        void canInitiateForInitiatorOnly() { // GH-90000
            AgentCapabilityManifest m = build( // GH-90000
                    List.of(InteractionMode.AUTONOMOUS), null, HandoffCapability.INITIATOR_ONLY); // GH-90000
            assertThat(m.canInitiateHandoff()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("canInitiateHandoff() is false for NONE")
        void cannotInitiateForNone() { // GH-90000
            AgentCapabilityManifest m = build( // GH-90000
                    List.of(InteractionMode.AUTONOMOUS), null, HandoffCapability.NONE); // GH-90000
            assertThat(m.canInitiateHandoff()).isFalse(); // GH-90000
        }
    }

    // ─── standalone factory ───────────────────────────────────────────────────

    @Nested
    @DisplayName("standalone factory")
    class StandaloneFactory {

        @Test
        @DisplayName("standalone() creates manifest with AUTONOMOUS mode and NONE handoff")
        void standaloneFactory() { // GH-90000
            AgentCapabilityManifest m = AgentCapabilityManifest.standalone(AGENT_ID, VERSION, TENANT_ID); // GH-90000
            assertThat(m.interactionModes()).containsExactly(InteractionMode.AUTONOMOUS); // GH-90000
            assertThat(m.handoffCapability()).isEqualTo(HandoffCapability.NONE); // GH-90000
            assertThat(m.supervisionRole()).isEqualTo(SupervisionRole.STANDALONE); // GH-90000
        }
    }

    // ─── AgentCapabilityManifestValidator ────────────────────────────────────

    @Nested
    @DisplayName("AgentCapabilityManifestValidator")
    class ValidatorTests {

        @Test
        @DisplayName("valid standalone manifest passes")
        void validStandaloneManifestPasses() { // GH-90000
            AgentCapabilityManifest m = AgentCapabilityManifest.standalone(AGENT_ID, VERSION, TENANT_ID); // GH-90000
            AgentCapabilityManifestValidator.ValidationResult r =
                    AgentCapabilityManifestValidator.validate(m); // GH-90000
            assertThat(r.valid()).isTrue(); // GH-90000
            assertThat(r.errors()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("ORCHESTRATOR without INITIATOR handoff produces error")
        void orchestratorWithoutInitiatorHandoffProducesError() { // GH-90000
            AgentCapabilityManifest m = build( // GH-90000
                    List.of(InteractionMode.ORCHESTRATOR), // GH-90000
                    SupervisionRole.SUPERVISOR,
                    HandoffCapability.RECEIVER_ONLY); // wrong — should be INITIATOR_ONLY+
            AgentCapabilityManifestValidator.ValidationResult r =
                    AgentCapabilityManifestValidator.validate(m); // GH-90000
            assertThat(r.valid()).isFalse(); // GH-90000
            assertThat(r.errors()).anyMatch(e -> e.contains("ORCHESTRATOR"));
        }

        @Test
        @DisplayName("SUPERVISOR role without ORCHESTRATOR / COLLABORATIVE mode produces error")
        void supervisorRoleWithoutRightModeProducesError() { // GH-90000
            AgentCapabilityManifest m = build( // GH-90000
                    List.of(InteractionMode.AUTONOMOUS), // GH-90000
                    SupervisionRole.SUPERVISOR,
                    HandoffCapability.BIDIRECTIONAL);
            AgentCapabilityManifestValidator.ValidationResult r =
                    AgentCapabilityManifestValidator.validate(m); // GH-90000
            assertThat(r.valid()).isFalse(); // GH-90000
            assertThat(r.errors()).anyMatch(e -> e.contains("SUPERVISOR"));
        }

        @Test
        @DisplayName("STANDALONE role with SPECIALIST mode produces error")
        void standaloneAndSpecialistProducesError() { // GH-90000
            AgentCapabilityManifest m = build( // GH-90000
                    List.of(InteractionMode.SPECIALIST), // GH-90000
                    SupervisionRole.STANDALONE,
                    HandoffCapability.NONE);
            AgentCapabilityManifestValidator.ValidationResult r =
                    AgentCapabilityManifestValidator.validate(m); // GH-90000
            assertThat(r.valid()).isFalse(); // GH-90000
            assertThat(r.errors()).anyMatch(e -> e.contains("STANDALONE"));
        }

        @Test
        @DisplayName("COLLABORATIVE with NONE handoff produces warning not error")
        void collaborativeWithNoneHandoffProducesWarning() { // GH-90000
            AgentCapabilityManifest m = build( // GH-90000
                    List.of(InteractionMode.COLLABORATIVE), // GH-90000
                    SupervisionRole.PEER,
                    HandoffCapability.NONE);
            AgentCapabilityManifestValidator.ValidationResult r =
                    AgentCapabilityManifestValidator.validate(m); // GH-90000
            assertThat(r.valid()).isTrue(); // warning only // GH-90000
            assertThat(r.warnings()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("valid ORCHESTRATOR manifest passes")
        void validOrchestratorManifestPasses() { // GH-90000
            AgentCapabilityManifest m = build( // GH-90000
                    List.of(InteractionMode.ORCHESTRATOR), // GH-90000
                    SupervisionRole.SUPERVISOR,
                    HandoffCapability.BIDIRECTIONAL);
            AgentCapabilityManifestValidator.ValidationResult r =
                    AgentCapabilityManifestValidator.validate(m); // GH-90000
            assertThat(r.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("null manifest throws NullPointerException")
        void nullManifestThrows() { // GH-90000
            assertThatThrownBy(() -> AgentCapabilityManifestValidator.validate(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }
}
