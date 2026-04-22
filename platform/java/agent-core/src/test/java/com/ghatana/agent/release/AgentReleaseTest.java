/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.release;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AgentRelease} and {@link AgentReleaseBuilder}.
 *
 * @doc.type class
 * @doc.purpose Tests for AgentRelease immutability, state transitions, builder validation
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AgentRelease [GH-90000]")
class AgentReleaseTest {

    private static AgentRelease minimalRelease() { // GH-90000
        return new AgentReleaseBuilder() // GH-90000
                .agentId("agent-test-001 [GH-90000]")
                .releaseVersion("1.0.0 [GH-90000]")
                .redactionProfileId("rp-default [GH-90000]")
                .threatModelId("tm-default [GH-90000]")
                .addPermittedPurpose("agent.inference [GH-90000]")
                .capabilityMaturityProfile("L1 [GH-90000]")
                .build(); // GH-90000
    }

    @Nested
    @DisplayName("Builder validation [GH-90000]")
    class BuilderValidation {

        @Test
        @DisplayName("builder fails when agentId is missing [GH-90000]")
        void missingAgentId() { // GH-90000
            assertThatThrownBy(() -> new AgentReleaseBuilder() // GH-90000
                    .releaseVersion("1.0.0 [GH-90000]")
                    .build()) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("agentId [GH-90000]");
        }

        @Test
        @DisplayName("builder fails when releaseVersion is missing [GH-90000]")
        void missingReleaseVersion() { // GH-90000
            assertThatThrownBy(() -> new AgentReleaseBuilder() // GH-90000
                    .agentId("agent-001 [GH-90000]")
                    .build()) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("releaseVersion [GH-90000]");
        }

        @Test
        @DisplayName("builder assigns DRAFT as default state [GH-90000]")
        void defaultStateIsDraft() { // GH-90000
            AgentRelease release = minimalRelease(); // GH-90000
            assertThat(release.state()).isEqualTo(AgentReleaseState.DRAFT); // GH-90000
        }

        @Test
        @DisplayName("builder generates UUID for agentReleaseId when not set [GH-90000]")
        void generatesAgentReleaseId() { // GH-90000
            AgentRelease release = minimalRelease(); // GH-90000
            assertThat(release.agentReleaseId()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("builder sets specVersion default to 1.0.0 [GH-90000]")
        void defaultSpecVersion() { // GH-90000
            AgentRelease release = minimalRelease(); // GH-90000
            assertThat(release.specVersion()).isEqualTo("1.0.0 [GH-90000]");
        }

        @Test
        @DisplayName("builder with all optional fields [GH-90000]")
        void builderWithAllFields() { // GH-90000
            AgentRelease release = new AgentReleaseBuilder() // GH-90000
                    .agentId("agent-001 [GH-90000]")
                    .releaseVersion("2.0.0 [GH-90000]")
                    .specVersion("2.0.0 [GH-90000]")
                    .state(AgentReleaseState.VALIDATED) // GH-90000
                    .specDigest("sha256:abc [GH-90000]")
                    .policyPackId("pp-001 [GH-90000]")
                    .policyPackDigest("sha256:def [GH-90000]")
                    .evaluationPackId("ep-001 [GH-90000]")
                    .evaluationPackDigest("sha256:ghi [GH-90000]")
                    .memoryContractId("mc-001 [GH-90000]")
                    .addCompatibleRuntime("aep-runtime:2.x [GH-90000]")
                    .signingReference("sigstore:bundle:123 [GH-90000]")
                    .toolContractVersion("1.0.0 [GH-90000]")
                    .addDataClass("PII [GH-90000]")
                    .redactionProfileId("rp-001 [GH-90000]")
                    .threatModelId("tm-001 [GH-90000]")
                    .addPermittedPurpose("analytics [GH-90000]")
                    .capabilityMaturityProfile("L2 [GH-90000]")
                    .createdBy("service-account@ghatana.ai [GH-90000]")
                    .build(); // GH-90000

            assertThat(release.agentId()).isEqualTo("agent-001 [GH-90000]");
            assertThat(release.releaseVersion()).isEqualTo("2.0.0 [GH-90000]");
            assertThat(release.state()).isEqualTo(AgentReleaseState.VALIDATED); // GH-90000
            assertThat(release.policyPackId()).isEqualTo("pp-001 [GH-90000]");
            assertThat(release.compatibleRuntimeVersions()).containsExactly("aep-runtime:2.x [GH-90000]");
            assertThat(release.dataClassesHandled()).contains("PII [GH-90000]");
            assertThat(release.permittedPurposes()).contains("analytics [GH-90000]");
        }
    }

    @Nested
    @DisplayName("State transitions via withState [GH-90000]")
    class StateTransitions {

        @Test
        @DisplayName("valid DRAFT → VALIDATED transition returns new record [GH-90000]")
        void validTransition() { // GH-90000
            AgentRelease draft = minimalRelease(); // GH-90000
            Instant now = Instant.now(); // GH-90000

            AgentRelease validated = draft.withState(AgentReleaseState.VALIDATED, now); // GH-90000

            assertThat(validated.state()).isEqualTo(AgentReleaseState.VALIDATED); // GH-90000
            assertThat(validated.updatedAt()).isEqualTo(now); // GH-90000
            assertThat(validated.agentReleaseId()).isEqualTo(draft.agentReleaseId()); // GH-90000
        }

        @Test
        @DisplayName("invalid transition throws IllegalStateException [GH-90000]")
        void invalidTransitionThrows() { // GH-90000
            AgentRelease draft = minimalRelease(); // GH-90000

            assertThatThrownBy(() -> draft.withState(AgentReleaseState.ACTIVE, Instant.now())) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("DRAFT [GH-90000]")
                    .hasMessageContaining("ACTIVE [GH-90000]");
        }

        @Test
        @DisplayName("original record is unchanged after withState [GH-90000]")
        void originalUnchanged() { // GH-90000
            AgentRelease draft = minimalRelease(); // GH-90000
            draft.withState(AgentReleaseState.VALIDATED, Instant.now()); // GH-90000

            assertThat(draft.state()).isEqualTo(AgentReleaseState.DRAFT); // GH-90000
        }
    }

    @Nested
    @DisplayName("Dispatchability [GH-90000]")
    class Dispatchability {

        @Test
        @DisplayName("ACTIVE release is dispatchable [GH-90000]")
        void activeIsDispatchable() { // GH-90000
            AgentRelease release = new AgentReleaseBuilder() // GH-90000
                    .agentId("agent-001 [GH-90000]")
                    .releaseVersion("1.0.0 [GH-90000]")
                    .state(AgentReleaseState.ACTIVE) // GH-90000
                    .redactionProfileId("rp-test [GH-90000]")
                    .threatModelId("tm-test [GH-90000]")
                    .addPermittedPurpose("test.purpose [GH-90000]")
                    .capabilityMaturityProfile("L1 [GH-90000]")
                    .build(); // GH-90000

            assertThat(release.isDispatchable()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("DRAFT release is not dispatchable [GH-90000]")
        void draftNotDispatchable() { // GH-90000
            AgentRelease release = minimalRelease(); // GH-90000
            assertThat(release.isDispatchable()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("BLOCKED release is not dispatchable [GH-90000]")
        void blockedNotDispatchable() { // GH-90000
            AgentRelease release = new AgentReleaseBuilder() // GH-90000
                    .agentId("agent-001 [GH-90000]")
                    .releaseVersion("1.0.0 [GH-90000]")
                    .state(AgentReleaseState.BLOCKED) // GH-90000
                    .redactionProfileId("rp-test [GH-90000]")
                    .threatModelId("tm-test [GH-90000]")
                    .addPermittedPurpose("test.purpose [GH-90000]")
                    .capabilityMaturityProfile("L1 [GH-90000]")
                    .build(); // GH-90000

            assertThat(release.isDispatchable()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Immutability guards [GH-90000]")
    class Immutability {

        @Test
        @DisplayName("compatibleRuntimeVersions list is unmodifiable [GH-90000]")
        void runtimeVersionsUnmodifiable() { // GH-90000
            AgentRelease release = new AgentReleaseBuilder() // GH-90000
                    .agentId("agent-001 [GH-90000]")
                    .releaseVersion("1.0.0 [GH-90000]")
                    .redactionProfileId("rp-test [GH-90000]")
                    .threatModelId("tm-test [GH-90000]")
                    .addPermittedPurpose("test.purpose [GH-90000]")
                    .capabilityMaturityProfile("L1 [GH-90000]")
                    .addCompatibleRuntime("aep:2.x [GH-90000]")
                    .build(); // GH-90000

            assertThatThrownBy(() -> release.compatibleRuntimeVersions().add("extra [GH-90000]"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("dataClassesHandled set is unmodifiable [GH-90000]")
        void dataClassesUnmodifiable() { // GH-90000
            AgentRelease release = new AgentReleaseBuilder() // GH-90000
                    .agentId("agent-001 [GH-90000]")
                    .releaseVersion("1.0.0 [GH-90000]")
                    .redactionProfileId("rp-test [GH-90000]")
                    .threatModelId("tm-test [GH-90000]")
                    .addPermittedPurpose("test.purpose [GH-90000]")
                    .capabilityMaturityProfile("L1 [GH-90000]")
                    .addDataClass("PII [GH-90000]")
                    .build(); // GH-90000

            assertThatThrownBy(() -> release.dataClassesHandled().add("extra [GH-90000]"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    @Test
    @DisplayName("record compact constructor rejects blank agentReleaseId [GH-90000]")
    void rejectsBlankReleaseId() { // GH-90000
        assertThatThrownBy(() -> new AgentRelease( // GH-90000
                "", "agentId", "1.0.0", "1.0.0", AgentReleaseState.DRAFT,
                null, null, null, null, null, null,
                List.of(), null, null, null, null, null, null, // GH-90000
                Set.of(), Set.of(), null, // GH-90000
                Instant.now(), Instant.now(), "user")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("agentReleaseId [GH-90000]");
    }
}
