/*
 * Copyright (c) 2026 Ghatana Inc.
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

    private static AgentRelease minimalRelease() {
        return new AgentReleaseBuilder()
                .agentId("agent-test-001")
                .releaseVersion("1.0.0")
                .redactionProfileId("rp-default")
                .threatModelId("tm-default")
                .addPermittedPurpose("agent.inference")
                .capabilityMaturityProfile("L1")
                .build();
    }

    @Nested
    @DisplayName("Builder validation")
    class BuilderValidation {

        @Test
        @DisplayName("builder fails when agentId is missing")
        void missingAgentId() {
            assertThatThrownBy(() -> new AgentReleaseBuilder()
                    .releaseVersion("1.0.0")
                    .build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("agentId");
        }

        @Test
        @DisplayName("builder fails when releaseVersion is missing")
        void missingReleaseVersion() {
            assertThatThrownBy(() -> new AgentReleaseBuilder()
                    .agentId("agent-001")
                    .build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("releaseVersion");
        }

        @Test
        @DisplayName("builder assigns DRAFT as default state")
        void defaultStateIsDraft() {
            AgentRelease release = minimalRelease();
            assertThat(release.state()).isEqualTo(AgentReleaseState.DRAFT);
        }

        @Test
        @DisplayName("builder generates UUID for agentReleaseId when not set")
        void generatesAgentReleaseId() {
            AgentRelease release = minimalRelease();
            assertThat(release.agentReleaseId()).isNotBlank();
        }

        @Test
        @DisplayName("builder sets specVersion default to 1.0.0")
        void defaultSpecVersion() {
            AgentRelease release = minimalRelease();
            assertThat(release.specVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("builder with all optional fields")
        void builderWithAllFields() {
            AgentRelease release = new AgentReleaseBuilder()
                    .agentId("agent-001")
                    .releaseVersion("2.0.0")
                    .specVersion("2.0.0")
                    .state(AgentReleaseState.VALIDATED)
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
                    .build();

            assertThat(release.agentId()).isEqualTo("agent-001");
            assertThat(release.releaseVersion()).isEqualTo("2.0.0");
            assertThat(release.state()).isEqualTo(AgentReleaseState.VALIDATED);
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
        void validTransition() {
            AgentRelease draft = minimalRelease();
            Instant now = Instant.now();

            AgentRelease validated = draft.withState(AgentReleaseState.VALIDATED, now);

            assertThat(validated.state()).isEqualTo(AgentReleaseState.VALIDATED);
            assertThat(validated.updatedAt()).isEqualTo(now);
            assertThat(validated.agentReleaseId()).isEqualTo(draft.agentReleaseId());
        }

        @Test
        @DisplayName("invalid transition throws IllegalStateException")
        void invalidTransitionThrows() {
            AgentRelease draft = minimalRelease();

            assertThatThrownBy(() -> draft.withState(AgentReleaseState.ACTIVE, Instant.now()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DRAFT")
                    .hasMessageContaining("ACTIVE");
        }

        @Test
        @DisplayName("original record is unchanged after withState")
        void originalUnchanged() {
            AgentRelease draft = minimalRelease();
            draft.withState(AgentReleaseState.VALIDATED, Instant.now());

            assertThat(draft.state()).isEqualTo(AgentReleaseState.DRAFT);
        }
    }

    @Nested
    @DisplayName("Dispatchability")
    class Dispatchability {

        @Test
        @DisplayName("ACTIVE release is dispatchable")
        void activeIsDispatchable() {
            AgentRelease release = new AgentReleaseBuilder()
                    .agentId("agent-001")
                    .releaseVersion("1.0.0")
                    .state(AgentReleaseState.ACTIVE)
                    .build();

            assertThat(release.isDispatchable()).isTrue();
        }

        @Test
        @DisplayName("DRAFT release is not dispatchable")
        void draftNotDispatchable() {
            AgentRelease release = minimalRelease();
            assertThat(release.isDispatchable()).isFalse();
        }

        @Test
        @DisplayName("BLOCKED release is not dispatchable")
        void blockedNotDispatchable() {
            AgentRelease release = new AgentReleaseBuilder()
                    .agentId("agent-001")
                    .releaseVersion("1.0.0")
                    .state(AgentReleaseState.BLOCKED)
                    .build();

            assertThat(release.isDispatchable()).isFalse();
        }
    }

    @Nested
    @DisplayName("Immutability guards")
    class Immutability {

        @Test
        @DisplayName("compatibleRuntimeVersions list is unmodifiable")
        void runtimeVersionsUnmodifiable() {
            AgentRelease release = new AgentReleaseBuilder()
                    .agentId("agent-001")
                    .releaseVersion("1.0.0")
                    .addCompatibleRuntime("aep:2.x")
                    .build();

            assertThatThrownBy(() -> release.compatibleRuntimeVersions().add("extra"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("dataClassesHandled set is unmodifiable")
        void dataClassesUnmodifiable() {
            AgentRelease release = new AgentReleaseBuilder()
                    .agentId("agent-001")
                    .releaseVersion("1.0.0")
                    .addDataClass("PII")
                    .build();

            assertThatThrownBy(() -> release.dataClassesHandled().add("extra"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    @DisplayName("record compact constructor rejects blank agentReleaseId")
    void rejectsBlankReleaseId() {
        assertThatThrownBy(() -> new AgentRelease(
                "", "agentId", "1.0.0", "1.0.0", AgentReleaseState.DRAFT,
                null, null, null, null, null, null,
                List.of(), null, null, null, null, null, null,
                Set.of(), Set.of(), null,
                Instant.now(), Instant.now(), "user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentReleaseId");
    }
}
