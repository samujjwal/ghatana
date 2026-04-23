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
@DisplayName("AgentRelease")
class AgentReleaseTest {

    private static AgentRelease minimalRelease() { // GH-90000
        return new AgentReleaseBuilder() // GH-90000
                .agentId("agent-test-001")
                .releaseVersion("1.0.0")
                .redactionProfileId("rp-default")
                .threatModelId("tm-default")
                .addPermittedPurpose("agent.inference")
                .capabilityMaturityProfile("L1")
                .build(); // GH-90000
    }

    @Nested
    @DisplayName("Builder validation")
    class BuilderValidation {

        @Test
        @DisplayName("builder fails when agentId is missing")
        void missingAgentId() { // GH-90000
            assertThatThrownBy(() -> new AgentReleaseBuilder() // GH-90000
                    .releaseVersion("1.0.0")
                    .build()) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("agentId");
        }

        @Test
        @DisplayName("builder fails when releaseVersion is missing")
        void missingReleaseVersion() { // GH-90000
            assertThatThrownBy(() -> new AgentReleaseBuilder() // GH-90000
                    .agentId("agent-001")
                    .build()) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("releaseVersion");
        }

        @Test
        @DisplayName("builder assigns DRAFT as default state")
        void defaultStateIsDraft() { // GH-90000
            AgentRelease release = minimalRelease(); // GH-90000
            assertThat(release.state()).isEqualTo(AgentReleaseState.DRAFT); // GH-90000
        }

        @Test
        @DisplayName("builder generates UUID for agentReleaseId when not set")
        void generatesAgentReleaseId() { // GH-90000
            AgentRelease release = minimalRelease(); // GH-90000
            assertThat(release.agentReleaseId()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("builder sets specVersion default to 1.0.0")
        void defaultSpecVersion() { // GH-90000
            AgentRelease release = minimalRelease(); // GH-90000
            assertThat(release.specVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("builder with all optional fields")
        void builderWithAllFields() { // GH-90000
            AgentRelease release = new AgentReleaseBuilder() // GH-90000
                    .agentId("agent-001")
                    .releaseVersion("2.0.0")
                    .specVersion("2.0.0")
                    .state(AgentReleaseState.VALIDATED) // GH-90000
                    .specDigest("sha256:abc")
                    .policyPackId("pp-001")
                    .policyPackDigest("sha256:def")
                    .evaluationPackId("ep-001")
                    .evaluationPackDigest("sha256:ghi")
                    .memoryContractId("mc-001")
                    .addCompatibleRuntime("aep-runtime:2.x")
                    .signingReference("sigstore:bundle:123")
                    .toolContractVersion("1.0.0")
                    .addDataClass("PII")
                    .redactionProfileId("rp-001")
                    .threatModelId("tm-001")
                    .addPermittedPurpose("analytics")
                    .capabilityMaturityProfile("L2")
                    .createdBy("service-account@ghatana.ai")
                    .build(); // GH-90000

            assertThat(release.agentId()).isEqualTo("agent-001");
            assertThat(release.releaseVersion()).isEqualTo("2.0.0");
            assertThat(release.state()).isEqualTo(AgentReleaseState.VALIDATED); // GH-90000
            assertThat(release.policyPackId()).isEqualTo("pp-001");
            assertThat(release.compatibleRuntimeVersions()).containsExactly("aep-runtime:2.x");
            assertThat(release.dataClassesHandled()).contains("PII");
            assertThat(release.permittedPurposes()).contains("analytics");
        }
    }

    @Nested
    @DisplayName("State transitions via withState")
    class StateTransitions {

        @Test
        @DisplayName("valid DRAFT → VALIDATED transition returns new record")
        void validTransition() { // GH-90000
            AgentRelease draft = minimalRelease(); // GH-90000
            Instant now = Instant.now(); // GH-90000

            AgentRelease validated = draft.withState(AgentReleaseState.VALIDATED, now); // GH-90000

            assertThat(validated.state()).isEqualTo(AgentReleaseState.VALIDATED); // GH-90000
            assertThat(validated.updatedAt()).isEqualTo(now); // GH-90000
            assertThat(validated.agentReleaseId()).isEqualTo(draft.agentReleaseId()); // GH-90000
        }

        @Test
        @DisplayName("invalid transition throws IllegalStateException")
        void invalidTransitionThrows() { // GH-90000
            AgentRelease draft = minimalRelease(); // GH-90000

            assertThatThrownBy(() -> draft.withState(AgentReleaseState.ACTIVE, Instant.now())) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("DRAFT")
                    .hasMessageContaining("ACTIVE");
        }

        @Test
        @DisplayName("original record is unchanged after withState")
        void originalUnchanged() { // GH-90000
            AgentRelease draft = minimalRelease(); // GH-90000
            draft.withState(AgentReleaseState.VALIDATED, Instant.now()); // GH-90000

            assertThat(draft.state()).isEqualTo(AgentReleaseState.DRAFT); // GH-90000
        }
    }

    @Nested
    @DisplayName("Dispatchability")
    class Dispatchability {

        @Test
        @DisplayName("ACTIVE release is dispatchable")
        void activeIsDispatchable() { // GH-90000
            AgentRelease release = new AgentReleaseBuilder() // GH-90000
                    .agentId("agent-001")
                    .releaseVersion("1.0.0")
                    .state(AgentReleaseState.ACTIVE) // GH-90000
                    .redactionProfileId("rp-test")
                    .threatModelId("tm-test")
                    .addPermittedPurpose("test.purpose")
                    .capabilityMaturityProfile("L1")
                    .build(); // GH-90000

            assertThat(release.isDispatchable()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("DRAFT release is not dispatchable")
        void draftNotDispatchable() { // GH-90000
            AgentRelease release = minimalRelease(); // GH-90000
            assertThat(release.isDispatchable()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("BLOCKED release is not dispatchable")
        void blockedNotDispatchable() { // GH-90000
            AgentRelease release = new AgentReleaseBuilder() // GH-90000
                    .agentId("agent-001")
                    .releaseVersion("1.0.0")
                    .state(AgentReleaseState.BLOCKED) // GH-90000
                    .redactionProfileId("rp-test")
                    .threatModelId("tm-test")
                    .addPermittedPurpose("test.purpose")
                    .capabilityMaturityProfile("L1")
                    .build(); // GH-90000

            assertThat(release.isDispatchable()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Immutability guards")
    class Immutability {

        @Test
        @DisplayName("compatibleRuntimeVersions list is unmodifiable")
        void runtimeVersionsUnmodifiable() { // GH-90000
            AgentRelease release = new AgentReleaseBuilder() // GH-90000
                    .agentId("agent-001")
                    .releaseVersion("1.0.0")
                    .redactionProfileId("rp-test")
                    .threatModelId("tm-test")
                    .addPermittedPurpose("test.purpose")
                    .capabilityMaturityProfile("L1")
                    .addCompatibleRuntime("aep:2.x")
                    .build(); // GH-90000

            assertThatThrownBy(() -> release.compatibleRuntimeVersions().add("extra"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("dataClassesHandled set is unmodifiable")
        void dataClassesUnmodifiable() { // GH-90000
            AgentRelease release = new AgentReleaseBuilder() // GH-90000
                    .agentId("agent-001")
                    .releaseVersion("1.0.0")
                    .redactionProfileId("rp-test")
                    .threatModelId("tm-test")
                    .addPermittedPurpose("test.purpose")
                    .capabilityMaturityProfile("L1")
                    .addDataClass("PII")
                    .build(); // GH-90000

            assertThatThrownBy(() -> release.dataClassesHandled().add("extra"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    @Test
    @DisplayName("record compact constructor rejects blank agentReleaseId")
    void rejectsBlankReleaseId() { // GH-90000
        assertThatThrownBy(() -> new AgentRelease( // GH-90000
                "", "agentId", "1.0.0", "1.0.0", AgentReleaseState.DRAFT,
                null, null, null, null, null, null,
                List.of(), null, null, null, null, null, null, // GH-90000
                Set.of(), Set.of(), null, // GH-90000
                Instant.now(), Instant.now(), "user")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("agentReleaseId");
    }
}
