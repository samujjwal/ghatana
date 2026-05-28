/*
 * Copyright (c) 2026 Ghatana Inc. 
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

    private static AgentCapabilityManifest build( 
            List<InteractionMode> modes, SupervisionRole role, HandoffCapability hc) {
        return new AgentCapabilityManifest( 
                AGENT_ID, VERSION, TENANT_ID, modes, role, hc, List.of(), List.of(), Map.of()); 
    }

    // ─── Record validation ────────────────────────────────────────────────────

    @Nested
    @DisplayName("record validation")
    class RecordValidation {

        @Test
        @DisplayName("blank agentId is rejected")
        void blankAgentIdRejected() { 
            assertThatThrownBy(() -> new AgentCapabilityManifest( 
                    "", VERSION, TENANT_ID, List.of(InteractionMode.AUTONOMOUS), 
                    null, HandoffCapability.NONE, List.of(), List.of(), Map.of())) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("agentId");
        }

        @Test
        @DisplayName("empty interactionModes is rejected")
        void emptyModesRejected() { 
            assertThatThrownBy(() -> new AgentCapabilityManifest( 
                    AGENT_ID, VERSION, TENANT_ID, List.of(), 
                    null, HandoffCapability.NONE, List.of(), List.of(), Map.of())) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("interactionModes");
        }

        @Test
        @DisplayName("collections are defensively immutable")
        void collectionsAreImmutable() { 
            AgentCapabilityManifest m = build( 
                    List.of(InteractionMode.AUTONOMOUS), null, HandoffCapability.NONE); 
            assertThatThrownBy(() -> m.interactionModes().add(InteractionMode.CONVERSATIONAL)) 
                    .isInstanceOf(UnsupportedOperationException.class); 
        }
    }

    // ─── supports() / handoff helpers ──────────────────────────────────────── 

    @Nested
    @DisplayName("capability query helpers")
    class CapabilityHelpers {

        @Test
        @DisplayName("supports() returns true for declared mode")
        void supportsTrue() { 
            AgentCapabilityManifest m = build( 
                    List.of(InteractionMode.AUTONOMOUS, InteractionMode.SUPERVISED), 
                    null, HandoffCapability.NONE);
            assertThat(m.supports(InteractionMode.SUPERVISED)).isTrue(); 
        }

        @Test
        @DisplayName("supports() returns false for undeclared mode")
        void supportsFalse() { 
            AgentCapabilityManifest m = build( 
                    List.of(InteractionMode.AUTONOMOUS), null, HandoffCapability.NONE); 
            assertThat(m.supports(InteractionMode.COLLABORATIVE)).isFalse(); 
        }

        @Test
        @DisplayName("canReceiveHandoff() is true for RECEIVER_ONLY")
        void canReceiveTrueForReceiverOnly() { 
            AgentCapabilityManifest m = build( 
                    List.of(InteractionMode.AUTONOMOUS), null, HandoffCapability.RECEIVER_ONLY); 
            assertThat(m.canReceiveHandoff()).isTrue(); 
        }

        @Test
        @DisplayName("canReceiveHandoff() is true for BIDIRECTIONAL")
        void canReceiveTrueForBidirectional() { 
            AgentCapabilityManifest m = build( 
                    List.of(InteractionMode.AUTONOMOUS), null, HandoffCapability.BIDIRECTIONAL); 
            assertThat(m.canReceiveHandoff()).isTrue(); 
        }

        @Test
        @DisplayName("canInitiateHandoff() is true for INITIATOR_ONLY")
        void canInitiateForInitiatorOnly() { 
            AgentCapabilityManifest m = build( 
                    List.of(InteractionMode.AUTONOMOUS), null, HandoffCapability.INITIATOR_ONLY); 
            assertThat(m.canInitiateHandoff()).isTrue(); 
        }

        @Test
        @DisplayName("canInitiateHandoff() is false for NONE")
        void cannotInitiateForNone() { 
            AgentCapabilityManifest m = build( 
                    List.of(InteractionMode.AUTONOMOUS), null, HandoffCapability.NONE); 
            assertThat(m.canInitiateHandoff()).isFalse(); 
        }
    }

    // ─── standalone factory ───────────────────────────────────────────────────

    @Nested
    @DisplayName("standalone factory")
    class StandaloneFactory {

        @Test
        @DisplayName("standalone() creates manifest with AUTONOMOUS mode and NONE handoff")
        void standaloneFactory() { 
            AgentCapabilityManifest m = AgentCapabilityManifest.standalone(AGENT_ID, VERSION, TENANT_ID); 
            assertThat(m.interactionModes()).containsExactly(InteractionMode.AUTONOMOUS); 
            assertThat(m.handoffCapability()).isEqualTo(HandoffCapability.NONE); 
            assertThat(m.supervisionRole()).isEqualTo(SupervisionRole.STANDALONE); 
        }
    }

    // ─── AgentCapabilityManifestValidator ────────────────────────────────────

    @Nested
    @DisplayName("AgentCapabilityManifestValidator")
    class ValidatorTests {

        @Test
        @DisplayName("valid standalone manifest passes")
        void validStandaloneManifestPasses() { 
            AgentCapabilityManifest m = AgentCapabilityManifest.standalone(AGENT_ID, VERSION, TENANT_ID); 
            AgentCapabilityManifestValidator.ValidationResult r =
                    AgentCapabilityManifestValidator.validate(m); 
            assertThat(r.valid()).isTrue(); 
            assertThat(r.errors()).isEmpty(); 
        }

        @Test
        @DisplayName("ORCHESTRATOR without INITIATOR handoff produces error")
        void orchestratorWithoutInitiatorHandoffProducesError() { 
            AgentCapabilityManifest m = build( 
                    List.of(InteractionMode.ORCHESTRATOR), 
                    SupervisionRole.SUPERVISOR,
                    HandoffCapability.RECEIVER_ONLY); // wrong — should be INITIATOR_ONLY+
            AgentCapabilityManifestValidator.ValidationResult r =
                    AgentCapabilityManifestValidator.validate(m); 
            assertThat(r.valid()).isFalse(); 
            assertThat(r.errors()).anyMatch(e -> e.contains("ORCHESTRATOR"));
        }

        @Test
        @DisplayName("SUPERVISOR role without ORCHESTRATOR / COLLABORATIVE mode produces error")
        void supervisorRoleWithoutRightModeProducesError() { 
            AgentCapabilityManifest m = build( 
                    List.of(InteractionMode.AUTONOMOUS), 
                    SupervisionRole.SUPERVISOR,
                    HandoffCapability.BIDIRECTIONAL);
            AgentCapabilityManifestValidator.ValidationResult r =
                    AgentCapabilityManifestValidator.validate(m); 
            assertThat(r.valid()).isFalse(); 
            assertThat(r.errors()).anyMatch(e -> e.contains("SUPERVISOR"));
        }

        @Test
        @DisplayName("STANDALONE role with SPECIALIST mode produces error")
        void standaloneAndSpecialistProducesError() { 
            AgentCapabilityManifest m = build( 
                    List.of(InteractionMode.SPECIALIST), 
                    SupervisionRole.STANDALONE,
                    HandoffCapability.NONE);
            AgentCapabilityManifestValidator.ValidationResult r =
                    AgentCapabilityManifestValidator.validate(m); 
            assertThat(r.valid()).isFalse(); 
            assertThat(r.errors()).anyMatch(e -> e.contains("STANDALONE"));
        }

        @Test
        @DisplayName("COLLABORATIVE with NONE handoff produces warning not error")
        void collaborativeWithNoneHandoffProducesWarning() { 
            AgentCapabilityManifest m = build( 
                    List.of(InteractionMode.COLLABORATIVE), 
                    SupervisionRole.PEER,
                    HandoffCapability.NONE);
            AgentCapabilityManifestValidator.ValidationResult r =
                    AgentCapabilityManifestValidator.validate(m); 
            assertThat(r.valid()).isTrue(); // warning only 
            assertThat(r.warnings()).isNotEmpty(); 
        }

        @Test
        @DisplayName("valid ORCHESTRATOR manifest passes")
        void validOrchestratorManifestPasses() { 
            AgentCapabilityManifest m = build( 
                    List.of(InteractionMode.ORCHESTRATOR), 
                    SupervisionRole.SUPERVISOR,
                    HandoffCapability.BIDIRECTIONAL);
            AgentCapabilityManifestValidator.ValidationResult r =
                    AgentCapabilityManifestValidator.validate(m); 
            assertThat(r.valid()).isTrue(); 
        }

        @Test
        @DisplayName("ORCHESTRATOR without explicit supervisionRole produces error")
        void orchestratorWithoutExplicitRoleProducesError() {
            AgentCapabilityManifest m = build(
                    List.of(InteractionMode.ORCHESTRATOR),
                    null,
                    HandoffCapability.BIDIRECTIONAL);
            AgentCapabilityManifestValidator.ValidationResult r =
                    AgentCapabilityManifestValidator.validate(m);
            assertThat(r.valid()).isFalse();
            assertThat(r.errors()).anyMatch(e -> e.contains("explicit supervisionRole"));
        }

        @Test
        @DisplayName("SUPERVISOR role without initiator handoff produces error")
        void supervisorWithoutInitiatorHandoffProducesError() {
            AgentCapabilityManifest m = build(
                    List.of(InteractionMode.COLLABORATIVE),
                    SupervisionRole.SUPERVISOR,
                    HandoffCapability.RECEIVER_ONLY);
            AgentCapabilityManifestValidator.ValidationResult r =
                    AgentCapabilityManifestValidator.validate(m);
            assertThat(r.valid()).isFalse();
            assertThat(r.errors()).anyMatch(e -> e.contains("SUPERVISOR role requires INITIATOR_ONLY or BIDIRECTIONAL"));
        }

        @Test
        @DisplayName("SUBORDINATE role without receiver handoff produces error")
        void subordinateWithoutReceiverHandoffProducesError() {
            AgentCapabilityManifest m = build(
                    List.of(InteractionMode.SPECIALIST),
                    SupervisionRole.SUBORDINATE,
                    HandoffCapability.INITIATOR_ONLY);
            AgentCapabilityManifestValidator.ValidationResult r =
                    AgentCapabilityManifestValidator.validate(m);
            assertThat(r.valid()).isFalse();
            assertThat(r.errors()).anyMatch(e -> e.contains("SUBORDINATE role requires RECEIVER_ONLY or BIDIRECTIONAL"));
        }

        @Test
        @DisplayName("null manifest throws NullPointerException")
        void nullManifestThrows() { 
            assertThatThrownBy(() -> AgentCapabilityManifestValidator.validate(null)) 
                    .isInstanceOf(NullPointerException.class); 
        }
    }
}
