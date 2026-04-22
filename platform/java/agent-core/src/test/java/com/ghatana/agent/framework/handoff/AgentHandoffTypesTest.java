/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.framework.handoff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AgentHandoff value types [GH-90000]")
class AgentHandoffTypesTest {

    // ─── AgentContextSnapshot ────────────────────────────────────────────────

    @Nested
    @DisplayName("AgentContextSnapshot [GH-90000]")
    class ContextSnapshotTests {

        @Test
        @DisplayName("factory sets all fields correctly [GH-90000]")
        void factoryCreatesSnapshot() { // GH-90000
            var snapshot = AgentContextSnapshot.of("agent-1", "tenant-1", "corr-1", Map.of("key", "val")); // GH-90000
            assertThat(snapshot.agentId()).isEqualTo("agent-1 [GH-90000]");
            assertThat(snapshot.tenantId()).isEqualTo("tenant-1 [GH-90000]");
            assertThat(snapshot.correlationId()).isEqualTo("corr-1 [GH-90000]");
            assertThat(snapshot.conversationId()).isNull(); // GH-90000
            assertThat(snapshot.metadata()).containsEntry("key", "val"); // GH-90000
        }

        @Test
        @DisplayName("metadata is immutable [GH-90000]")
        void metadataIsImmutable() { // GH-90000
            var snapshot = AgentContextSnapshot.of("a", "t", "c", Map.of("k", "v")); // GH-90000
            assertThatThrownBy(() -> snapshot.metadata().put("x", "y")) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("null agentId throws NullPointerException [GH-90000]")
        void rejectsNullAgentId() { // GH-90000
            assertThatThrownBy(() -> new AgentContextSnapshot(null, "t", "c", null, Map.of())) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("conversationId can be set explicitly [GH-90000]")
        void conversationIdCanBeSet() { // GH-90000
            var snapshot = new AgentContextSnapshot("a", "t", "c", "conv-123", Map.of()); // GH-90000
            assertThat(snapshot.conversationId()).isEqualTo("conv-123 [GH-90000]");
        }
    }

    // ─── AgentHandoff ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AgentHandoff [GH-90000]")
    class HandoffTests {

        private AgentContextSnapshot snapshot() { // GH-90000
            return AgentContextSnapshot.of("src-agent", "tenant-1", "corr-xyz", Map.of()); // GH-90000
        }

        @Test
        @DisplayName("factory generates handoffId and sets fields [GH-90000]")
        void factoryCreatesHandoff() { // GH-90000
            var handoff = AgentHandoff.of("src-agent", "tgt-agent", // GH-90000
                    HandoffReason.SPECIALIST_REQUIRED, snapshot(), "my-input"); // GH-90000
            assertThat(handoff.handoffId()).isNotBlank(); // GH-90000
            assertThat(handoff.sourceAgentId()).isEqualTo("src-agent [GH-90000]");
            assertThat(handoff.targetAgentId()).isEqualTo("tgt-agent [GH-90000]");
            assertThat(handoff.reason()).isEqualTo(HandoffReason.SPECIALIST_REQUIRED); // GH-90000
            assertThat(handoff.originalInput()).isEqualTo("my-input [GH-90000]");
            assertThat(handoff.initiatedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("each factory call generates a unique handoffId [GH-90000]")
        void handoffIdsAreUnique() { // GH-90000
            var h1 = AgentHandoff.of("a", "b", HandoffReason.BUDGET_EXHAUSTED, snapshot(), "input"); // GH-90000
            var h2 = AgentHandoff.of("a", "b", HandoffReason.BUDGET_EXHAUSTED, snapshot(), "input"); // GH-90000
            assertThat(h1.handoffId()).isNotEqualTo(h2.handoffId()); // GH-90000
        }

        @Test
        @DisplayName("all HandoffReason values are representable [GH-90000]")
        void allReasonsWork() { // GH-90000
            for (HandoffReason reason : HandoffReason.values()) { // GH-90000
                var handoff = AgentHandoff.of("s", "t", reason, snapshot(), "x"); // GH-90000
                assertThat(handoff.reason()).isEqualTo(reason); // GH-90000
            }
        }

        @Test
        @DisplayName("null contextSnapshot throws NullPointerException [GH-90000]")
        void rejectsNullSnapshot() { // GH-90000
            assertThatThrownBy(() -> AgentHandoff.of("s", "t", HandoffReason.USER_REQUESTED, null, "x")) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }
}
