/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.stage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Phase 7: Contract tests for AgentTraceStage.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Trace event recording</li>
 *   <li>OTel span creation</li>
 *   <li>Stage passes when trace recorded successfully</li>
 * </ul>
 */
@DisplayName("Agent Trace Stage Tests (Phase 7)")
class AgentTraceStageTest {

    // =========================================================================
    //  Stage Execution
    // =========================================================================

    @Nested
    @DisplayName("Stage Execution")
    class ExecutionTests {

        @Test
        @DisplayName("stage succeeds when trace recorded")
        void stageSucceedsWhenTraceRecorded() {
            AgentTraceStage.AgentTraceLedger ledger = mock(AgentTraceStage.AgentTraceLedger.class);
            AgentTraceStage.AgentRunTracer tracer = mock(AgentTraceStage.AgentRunTracer.class);
            
            org.mockito.Mockito.when(tracer.startSpan("agent-1", "1.0.0", "agent_dispatch")).thenReturn("span-123");

            AgentTraceStage stage = new AgentTraceStage(ledger, tracer);
            AgentDispatchStage.StageContext context = new AgentDispatchStage.StageContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchStage.StageResult result = stage.execute(context);

            assertThat(result.succeeded()).isTrue();
            assertThat(result.output()).containsKey("spanId");
            assertThat(result.output().get("spanId")).isEqualTo("span-123");

            org.mockito.Mockito.verify(ledger).appendEvent(org.mockito.ArgumentMatchers.eq("agent-1"), org.mockito.ArgumentMatchers.eq("1.0.0"), org.mockito.ArgumentMatchers.eq("dispatch_start"), org.mockito.ArgumentMatchers.anyMap());
        }

        @Test
        @DisplayName("stage fails when trace recording throws exception")
        void stageFailsWhenTraceRecordingThrowsException() {
            AgentTraceStage.AgentTraceLedger ledger = mock(AgentTraceStage.AgentTraceLedger.class);
            AgentTraceStage.AgentRunTracer tracer = mock(AgentTraceStage.AgentRunTracer.class);
            
            RuntimeException exception = new RuntimeException("Trace service unavailable");
            ledger.appendEvent("agent-1", "1.0.0", "dispatch_start", Map.of());
            org.mockito.Mockito.when(tracer.startSpan("agent-1", "1.0.0", "agent_dispatch")).thenThrow(exception);

            AgentTraceStage stage = new AgentTraceStage(ledger, tracer);
            AgentDispatchStage.StageContext context = new AgentDispatchStage.StageContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchStage.StageResult result = stage.execute(context);

            assertThat(result.succeeded()).isFalse();
            assertThat(result.errorMessage()).contains("recording failed");
        }

        @Test
        @DisplayName("requires non-null context")
        void requiresNonNullContext() {
            AgentTraceStage.AgentTraceLedger ledger = mock(AgentTraceStage.AgentTraceLedger.class);
            AgentTraceStage.AgentRunTracer tracer = mock(AgentTraceStage.AgentRunTracer.class);
            AgentTraceStage stage = new AgentTraceStage(ledger, tracer);

            assertThatThrownBy(() -> stage.execute(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("context must not be null");
        }

        @Test
        @DisplayName("requires non-null trace ledger")
        void requiresNonNullTraceLedger() {
            AgentTraceStage.AgentRunTracer tracer = mock(AgentTraceStage.AgentRunTracer.class);

            assertThatThrownBy(() -> new AgentTraceStage(null, tracer))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("traceLedger must not be null");
        }

        @Test
        @DisplayName("requires non-null run tracer")
        void requiresNonNullRunTracer() {
            AgentTraceStage.AgentTraceLedger ledger = mock(AgentTraceStage.AgentTraceLedger.class);

            assertThatThrownBy(() -> new AgentTraceStage(ledger, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("runTracer must not be null");
        }
    }
}
