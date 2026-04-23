/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.runtime.handoff;

import com.ghatana.agent.framework.handoff.AgentContextSnapshot;
import com.ghatana.agent.framework.handoff.AgentHandoff;
import com.ghatana.agent.framework.handoff.HandoffReason;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("HandoffCoordinator")
class HandoffCoordinatorTest extends EventloopTestBase {

    private HandoffCoordinator coordinator;

    private AgentHandoff buildHandoff(String source, String target) { // GH-90000
        AgentContextSnapshot snapshot = AgentContextSnapshot.of(source, "tenant-1", "corr-1", Map.of()); // GH-90000
        return AgentHandoff.of(source, target, HandoffReason.SPECIALIST_REQUIRED, snapshot, "task-input"); // GH-90000
    }

    @BeforeEach
    void setUp() { // GH-90000
        coordinator = new HandoffCoordinator(); // GH-90000
    }

    @Nested
    @DisplayName("successful handoff")
    class SuccessPath {

        @Test
        @DisplayName("delivers handoff to registered handler")
        void deliversToHandler() { // GH-90000
            coordinator.registerHandler("agent-b", h -> Promise.of("ACK from agent-b"));
            AgentHandoff handoff = buildHandoff("agent-a", "agent-b"); // GH-90000
            HandoffCoordinator.HandoffResult result = runPromise(() -> coordinator.handoff(handoff)); // GH-90000
            assertThat(result.isAccepted()).isTrue(); // GH-90000
            assertThat(((HandoffCoordinator.HandoffResult.Accepted) result).acknowledgement()) // GH-90000
                    .isEqualTo("ACK from agent-b");
        }

        @Test
        @DisplayName("records handoff in ledger")
        void recordsInLedger() { // GH-90000
            coordinator.registerHandler("agent-c", h -> Promise.of("ok"));
            AgentHandoff handoff = buildHandoff("agent-a", "agent-c"); // GH-90000
            runPromise(() -> coordinator.handoff(handoff)); // GH-90000
            assertThat(coordinator.findHandoff(handoff.handoffId())).isSameAs(handoff); // GH-90000
        }
    }

    @Nested
    @DisplayName("rejected handoff")
    class RejectPath {

        @Test
        @DisplayName("returns Rejected when no handler is registered for target")
        void noHandlerRejects() { // GH-90000
            AgentHandoff handoff = buildHandoff("agent-a", "unregistered-agent"); // GH-90000
            HandoffCoordinator.HandoffResult result = runPromise(() -> coordinator.handoff(handoff)); // GH-90000
            assertThat(result.isAccepted()).isFalse(); // GH-90000
            assertThat(result).isInstanceOf(HandoffCoordinator.HandoffResult.Rejected.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("handler registration")
    class Registration {

        @Test
        @DisplayName("later registration replaces earlier for same agentId")
        void replacesHandler() { // GH-90000
            coordinator.registerHandler("agent-d", h -> Promise.of("first"));
            coordinator.registerHandler("agent-d", h -> Promise.of("second"));
            AgentHandoff handoff = buildHandoff("agent-a", "agent-d"); // GH-90000
            HandoffCoordinator.HandoffResult result = runPromise(() -> coordinator.handoff(handoff)); // GH-90000
            assertThat(((HandoffCoordinator.HandoffResult.Accepted) result).acknowledgement()) // GH-90000
                    .isEqualTo("second");
        }

        @Test
        @DisplayName("null agentId throws NullPointerException")
        void nullAgentIdThrows() { // GH-90000
            assertThatThrownBy(() -> coordinator.registerHandler(null, h -> Promise.of("ack")))
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }
}
