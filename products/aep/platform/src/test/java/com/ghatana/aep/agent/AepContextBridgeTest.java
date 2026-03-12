/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.agent;

import com.ghatana.aep.domain.agent.registry.AgentExecutionContext;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link AepContextBridge}.
 *
 * <p>AepContextBridge is synchronous — no ActiveJ Eventloop required.
 */
@DisplayName("AepContextBridge")
class AepContextBridgeTest {

    private AepContextBridge bridge;
    private MemoryStore noOpStore;

    @BeforeEach
    void setUp() {
        noOpStore  = MemoryStore.noOp();
        bridge     = new AepContextBridge(noOpStore);
    }

    // ─── 3-arg overload (explicit traceId) ───────────────────────────────────

    @Nested
    @DisplayName("toAgentContext(execCtx, agentId, traceId)")
    class ExplicitTraceId {

        private static final AgentExecutionContext EXEC_CTX = () -> "tenant-123";

        @Test
        @DisplayName("sets tenantId from AgentExecutionContext")
        void setsTenantId() {
            AgentContext ctx = bridge.toAgentContext(EXEC_CTX, "agent-1", "trace-abc");
            assertThat(ctx.getTenantId()).isEqualTo("tenant-123");
        }

        @Test
        @DisplayName("sets agentId from parameter")
        void setsAgentId() {
            AgentContext ctx = bridge.toAgentContext(EXEC_CTX, "fraud-detector", "trace-abc");
            assertThat(ctx.getAgentId()).isEqualTo("fraud-detector");
        }

        @Test
        @DisplayName("sets traceId from parameter")
        void setsTraceId() {
            AgentContext ctx = bridge.toAgentContext(EXEC_CTX, "agent-1", "my-trace-id");
            assertThat(ctx.getTraceId()).isEqualTo("my-trace-id");
        }

        @Test
        @DisplayName("generates a non-blank turnId")
        void generatesTurnId() {
            AgentContext ctx = bridge.toAgentContext(EXEC_CTX, "agent-1", "trace-abc");
            assertThat(ctx.getTurnId()).isNotBlank();
        }

        @Test
        @DisplayName("sets startTime to approximately now (within 5 seconds)")
        void setsStartTime() {
            Instant before = Instant.now();
            AgentContext ctx = bridge.toAgentContext(EXEC_CTX, "agent-1", "t");
            Instant after   = Instant.now();

            assertThat(ctx.getStartTime())
                    .isAfterOrEqualTo(before)
                    .isBeforeOrEqualTo(after);
        }

        @Test
        @DisplayName("wires the MemoryStore from constructor")
        void wiresMemoryStore() {
            AgentContext ctx = bridge.toAgentContext(EXEC_CTX, "agent-1", "t");
            assertThat(ctx.getMemoryStore()).isSameAs(noOpStore);
        }

        @Test
        @DisplayName("sets a non-null logger named 'agent.<agentId>'")
        void setsLogger() {
            AgentContext ctx = bridge.toAgentContext(EXEC_CTX, "fraud-detector", "t");
            assertThat(ctx.getLogger()).isNotNull();
            assertThat(ctx.getLogger().getName()).isEqualTo("agent.fraud-detector");
        }

        @Test
        @DisplayName("two calls produce different turnIds")
        void differentTurnIds() {
            AgentContext a = bridge.toAgentContext(EXEC_CTX, "a1", "t1");
            AgentContext b = bridge.toAgentContext(EXEC_CTX, "a1", "t2");
            assertThat(a.getTurnId()).isNotEqualTo(b.getTurnId());
        }
    }

    // ─── 2-arg overload (auto-generates traceId) ─────────────────────────────

    @Nested
    @DisplayName("toAgentContext(execCtx, agentId)")
    class AutoTraceId {

        @Test
        @DisplayName("auto-generates a non-blank traceId")
        void generatesTraceId() {
            AgentContext ctx = bridge.toAgentContext(() -> "tenant-x", "agent-1");
            assertThat(ctx.getTraceId()).isNotBlank();
        }

        @Test
        @DisplayName("two calls produce different traceIds")
        void differentTraceIds() {
            AgentContext a = bridge.toAgentContext(() -> "t", "a");
            AgentContext b = bridge.toAgentContext(() -> "t", "a");
            assertThat(a.getTraceId()).isNotEqualTo(b.getTraceId());
        }

        @Test
        @DisplayName("tenantId flows from the execCtx lambda")
        void tenantIdFromLambda() {
            AgentContext ctx = bridge.toAgentContext(() -> "my-tenant", "agent-x");
            assertThat(ctx.getTenantId()).isEqualTo("my-tenant");
        }
    }

    // ─── null-safety ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("null argument handling")
    class NullSafety {

        @Test
        @DisplayName("toAgentContext throws NPE when execCtx is null")
        void nullExecCtx() {
            assertThatNullPointerException()
                    .isThrownBy(() -> bridge.toAgentContext(null, "agent-1", "trace"));
        }

        @Test
        @DisplayName("toAgentContext throws NPE when agentId is null")
        void nullAgentId() {
            assertThatNullPointerException()
                    .isThrownBy(() -> bridge.toAgentContext(() -> "t", null, "trace"));
        }

        @Test
        @DisplayName("toAgentContext throws NPE when traceId is null")
        void nullTraceId() {
            assertThatNullPointerException()
                    .isThrownBy(() -> bridge.toAgentContext(() -> "t", "agent", null));
        }

        @Test
        @DisplayName("AepContextBridge constructor throws NPE when memoryStore is null")
        void nullMemoryStoreInConstructor() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new AepContextBridge(null));
        }
    }
}
