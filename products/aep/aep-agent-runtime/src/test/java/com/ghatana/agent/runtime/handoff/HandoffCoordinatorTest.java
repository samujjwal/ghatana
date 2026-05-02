/*
 * Copyright (c) 2026 Ghatana Inc. 
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

    private AgentHandoff buildHandoff(String source, String target) { 
        AgentContextSnapshot snapshot = AgentContextSnapshot.of(source, "tenant-1", "corr-1", Map.of()); 
        return AgentHandoff.of(source, target, HandoffReason.SPECIALIST_REQUIRED, snapshot, "task-input"); 
    }

    @BeforeEach
    void setUp() { 
        coordinator = new HandoffCoordinator(); 
    }

    @Nested
    @DisplayName("successful handoff")
    class SuccessPath {

        @Test
        @DisplayName("delivers handoff to registered handler")
        void deliversToHandler() { 
            coordinator.registerHandler("agent-b", h -> Promise.of("ACK from agent-b"));
            AgentHandoff handoff = buildHandoff("agent-a", "agent-b"); 
            HandoffCoordinator.HandoffResult result = runPromise(() -> coordinator.handoff(handoff)); 
            assertThat(result.isAccepted()).isTrue(); 
            assertThat(((HandoffCoordinator.HandoffResult.Accepted) result).acknowledgement()) 
                    .isEqualTo("ACK from agent-b");
        }

        @Test
        @DisplayName("records handoff in ledger")
        void recordsInLedger() { 
            coordinator.registerHandler("agent-c", h -> Promise.of("ok"));
            AgentHandoff handoff = buildHandoff("agent-a", "agent-c"); 
            runPromise(() -> coordinator.handoff(handoff)); 
            assertThat(coordinator.findHandoff(handoff.handoffId())).isSameAs(handoff); 
        }
    }

    @Nested
    @DisplayName("rejected handoff")
    class RejectPath {

        @Test
        @DisplayName("returns Rejected when no handler is registered for target")
        void noHandlerRejects() { 
            AgentHandoff handoff = buildHandoff("agent-a", "unregistered-agent"); 
            HandoffCoordinator.HandoffResult result = runPromise(() -> coordinator.handoff(handoff)); 
            assertThat(result.isAccepted()).isFalse(); 
            assertThat(result).isInstanceOf(HandoffCoordinator.HandoffResult.Rejected.class); 
        }
    }

    @Nested
    @DisplayName("handler registration")
    class Registration {

        @Test
        @DisplayName("later registration replaces earlier for same agentId")
        void replacesHandler() { 
            coordinator.registerHandler("agent-d", h -> Promise.of("first"));
            coordinator.registerHandler("agent-d", h -> Promise.of("second"));
            AgentHandoff handoff = buildHandoff("agent-a", "agent-d"); 
            HandoffCoordinator.HandoffResult result = runPromise(() -> coordinator.handoff(handoff)); 
            assertThat(((HandoffCoordinator.HandoffResult.Accepted) result).acknowledgement()) 
                    .isEqualTo("second");
        }

        @Test
        @DisplayName("null agentId throws NullPointerException")
        void nullAgentIdThrows() { 
            assertThatThrownBy(() -> coordinator.registerHandler(null, h -> Promise.of("ack")))
                    .isInstanceOf(NullPointerException.class); 
        }
    }
}
