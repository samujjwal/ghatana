/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.agent.framework.handoff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AgentHandoff value types")
class AgentHandoffTypesTest {

    // ─── AgentContextSnapshot ────────────────────────────────────────────────

    @Nested
    @DisplayName("AgentContextSnapshot")
    class ContextSnapshotTests {

        @Test
        @DisplayName("factory sets all fields correctly")
        void factoryCreatesSnapshot() { 
            var snapshot = AgentContextSnapshot.of("agent-1", "tenant-1", "corr-1", Map.of("key", "val")); 
            assertThat(snapshot.agentId()).isEqualTo("agent-1");
            assertThat(snapshot.tenantId()).isEqualTo("tenant-1");
            assertThat(snapshot.correlationId()).isEqualTo("corr-1");
            assertThat(snapshot.conversationId()).isNull(); 
            assertThat(snapshot.metadata()).containsEntry("key", "val"); 
        }

        @Test
        @DisplayName("metadata is immutable")
        void metadataIsImmutable() { 
            var snapshot = AgentContextSnapshot.of("a", "t", "c", Map.of("k", "v")); 
            assertThatThrownBy(() -> snapshot.metadata().put("x", "y")) 
                    .isInstanceOf(UnsupportedOperationException.class); 
        }

        @Test
        @DisplayName("null agentId throws NullPointerException")
        void rejectsNullAgentId() { 
            assertThatThrownBy(() -> new AgentContextSnapshot(null, "t", "c", null, Map.of())) 
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        @DisplayName("conversationId can be set explicitly")
        void conversationIdCanBeSet() { 
            var snapshot = new AgentContextSnapshot("a", "t", "c", "conv-123", Map.of()); 
            assertThat(snapshot.conversationId()).isEqualTo("conv-123");
        }
    }

    // ─── AgentHandoff ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AgentHandoff")
    class HandoffTests {

        private AgentContextSnapshot snapshot() { 
            return AgentContextSnapshot.of("src-agent", "tenant-1", "corr-xyz", Map.of()); 
        }

        @Test
        @DisplayName("factory generates handoffId and sets fields")
        void factoryCreatesHandoff() { 
            var handoff = AgentHandoff.of("src-agent", "tgt-agent", 
                    HandoffReason.SPECIALIST_REQUIRED, snapshot(), "my-input"); 
            assertThat(handoff.handoffId()).isNotBlank(); 
            assertThat(handoff.sourceAgentId()).isEqualTo("src-agent");
            assertThat(handoff.targetAgentId()).isEqualTo("tgt-agent");
            assertThat(handoff.reason()).isEqualTo(HandoffReason.SPECIALIST_REQUIRED); 
            assertThat(handoff.originalInput()).isEqualTo("my-input");
            assertThat(handoff.initiatedAt()).isNotNull(); 
        }

        @Test
        @DisplayName("each factory call generates a unique handoffId")
        void handoffIdsAreUnique() { 
            var h1 = AgentHandoff.of("a", "b", HandoffReason.BUDGET_EXHAUSTED, snapshot(), "input"); 
            var h2 = AgentHandoff.of("a", "b", HandoffReason.BUDGET_EXHAUSTED, snapshot(), "input"); 
            assertThat(h1.handoffId()).isNotEqualTo(h2.handoffId()); 
        }

        @Test
        @DisplayName("all HandoffReason values are representable")
        void allReasonsWork() { 
            for (HandoffReason reason : HandoffReason.values()) { 
                var handoff = AgentHandoff.of("s", "t", reason, snapshot(), "x"); 
                assertThat(handoff.reason()).isEqualTo(reason); 
            }
        }

        @Test
        @DisplayName("null contextSnapshot throws NullPointerException")
        void rejectsNullSnapshot() { 
            assertThatThrownBy(() -> AgentHandoff.of("s", "t", HandoffReason.USER_REQUESTED, null, "x")) 
                    .isInstanceOf(NullPointerException.class); 
        }
    }
}
