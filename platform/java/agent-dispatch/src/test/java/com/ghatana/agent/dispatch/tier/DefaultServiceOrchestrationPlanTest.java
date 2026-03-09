/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.agent.dispatch.tier;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import com.ghatana.agent.dispatch.AgentDispatcher;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DefaultServiceOrchestrationPlan} — Tier-S sequential delegation.
 *
 * @doc.type class
 * @doc.purpose Unit tests for DefaultServiceOrchestrationPlan
 * @doc.layer framework
 * @doc.pattern Test
 */
@DisplayName("DefaultServiceOrchestrationPlan")
class DefaultServiceOrchestrationPlanTest extends EventloopTestBase {

    @Mock AgentDispatcher dispatcher;
    @Mock AgentContext agentContext;

    private DefaultServiceOrchestrationPlan plan;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        plan = new DefaultServiceOrchestrationPlan();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Successful delegation chains
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Successful delegation")
    class SuccessfulDelegation {

        @Test
        @DisplayName("should dispatch to single delegatee and return SUCCESS")
        void shouldDispatchSingleDelegatee() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("parent-agent")
                    .name("Parent Agent")
                    .delegation(Map.of("can_delegate_to", List.of("sub-agent-1")))
                    .build();

            AgentResult<Object> subResult = AgentResult.builder()
                    .output("sub-output")
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("sub-agent-1")
                    .build();
            when(dispatcher.dispatch(eq("sub-agent-1"), any(), eq(agentContext)))
                    .thenReturn(Promise.of(subResult));

            AgentResult<Object> result = runPromise(() ->
                    plan.execute(entry, "initial-input", agentContext, dispatcher));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("sub-output");
            assertThat(result.getAgentId()).isEqualTo("parent-agent");
            verify(dispatcher).dispatch("sub-agent-1", "initial-input", agentContext);
        }

        @Test
        @DisplayName("should chain multiple delegatees — output of each is input for next")
        void shouldChainMultipleDelegatees() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("chain-agent")
                    .name("Chain Agent")
                    .delegation(Map.of("can_delegate_to", List.of("step-1", "step-2", "step-3")))
                    .build();

            AgentResult<Object> result1 = AgentResult.builder()
                    .output("output-1")
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("step-1")
                    .build();
            AgentResult<Object> result2 = AgentResult.builder()
                    .output("output-2")
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("step-2")
                    .build();
            AgentResult<Object> result3 = AgentResult.builder()
                    .output("final-output")
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("step-3")
                    .build();

            when(dispatcher.dispatch(eq("step-1"), eq("start"), eq(agentContext)))
                    .thenReturn(Promise.of(result1));
            when(dispatcher.dispatch(eq("step-2"), eq("output-1"), eq(agentContext)))
                    .thenReturn(Promise.of(result2));
            when(dispatcher.dispatch(eq("step-3"), eq("output-2"), eq(agentContext)))
                    .thenReturn(Promise.of(result3));

            AgentResult<Object> result = runPromise(() ->
                    plan.execute(entry, "start", agentContext, dispatcher));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("final-output");
            assertThat(result.getAgentId()).isEqualTo("chain-agent");

            // Verify sequential dispatch order
            var inOrder = inOrder(dispatcher);
            inOrder.verify(dispatcher).dispatch("step-1", "start", agentContext);
            inOrder.verify(dispatcher).dispatch("step-2", "output-1", agentContext);
            inOrder.verify(dispatcher).dispatch("step-3", "output-2", agentContext);
        }

        @Test
        @DisplayName("should use current input when sub-agent returns null output")
        void shouldFallbackToCurrentInputOnNullOutput() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("null-output-agent")
                    .name("Null Output Agent")
                    .delegation(Map.of("can_delegate_to", List.of("step-1", "step-2")))
                    .build();

            AgentResult<Object> nullOutputResult = AgentResult.builder()
                    .output(null)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("step-1")
                    .build();
            AgentResult<Object> finalResult = AgentResult.builder()
                    .output("final")
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("step-2")
                    .build();

            when(dispatcher.dispatch(eq("step-1"), any(), eq(agentContext)))
                    .thenReturn(Promise.of(nullOutputResult));
            when(dispatcher.dispatch(eq("step-2"), eq("original-input"), eq(agentContext)))
                    .thenReturn(Promise.of(finalResult));

            AgentResult<Object> result = runPromise(() ->
                    plan.execute(entry, "original-input", agentContext, dispatcher));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("final");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Failure paths
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Failure paths")
    class FailurePaths {

        @Test
        @DisplayName("should return FAILED when no delegatees configured")
        void shouldFailWhenNoDelegatees() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("no-delegates")
                    .name("No Delegates Agent")
                    .build();

            AgentResult<Object> result = runPromise(() ->
                    plan.execute(entry, "input", agentContext, dispatcher));

            assertThat(result.isFailed()).isTrue();
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.FAILED);
            assertThat(result.getExplanation()).contains("No delegatees");
            verify(dispatcher, never()).dispatch(anyString(), any(), any());
        }

        @Test
        @DisplayName("should return FAILED with empty delegation list")
        void shouldFailWithEmptyDelegationList() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("empty-delegates")
                    .name("Empty Delegates Agent")
                    .delegation(Map.of("can_delegate_to", List.of()))
                    .build();

            AgentResult<Object> result = runPromise(() ->
                    plan.execute(entry, "input", agentContext, dispatcher));

            assertThat(result.isFailed()).isTrue();
        }

        @Test
        @DisplayName("should stop chain and return FAILED when sub-agent fails")
        void shouldStopOnSubAgentFailure() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("fail-chain")
                    .name("Failing Chain Agent")
                    .delegation(Map.of("can_delegate_to", List.of("step-1", "step-2", "step-3")))
                    .build();

            AgentResult<Object> success = AgentResult.builder()
                    .output("step-1-output")
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("step-1")
                    .build();
            AgentResult<Object> failure = AgentResult.builder()
                    .status(AgentResultStatus.FAILED)
                    .agentId("step-2")
                    .explanation("step-2 crashed")
                    .build();

            when(dispatcher.dispatch(eq("step-1"), any(), eq(agentContext)))
                    .thenReturn(Promise.of(success));
            when(dispatcher.dispatch(eq("step-2"), any(), eq(agentContext)))
                    .thenReturn(Promise.of(failure));

            AgentResult<Object> result = runPromise(() ->
                    plan.execute(entry, "input", agentContext, dispatcher));

            assertThat(result.isFailed()).isTrue();
            assertThat(result.getExplanation()).contains("step-2");
            assertThat(result.getExplanation()).contains("step-2 crashed");

            // step-3 should never be reached
            verify(dispatcher, never()).dispatch(eq("step-3"), any(), any());
        }

        @Test
        @DisplayName("should stop on TIMEOUT sub-agent result")
        void shouldStopOnTimeout() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("timeout-chain")
                    .name("Timeout Chain Agent")
                    .delegation(Map.of("can_delegate_to", List.of("slow-agent")))
                    .build();

            AgentResult<Object> timeout = AgentResult.builder()
                    .status(AgentResultStatus.TIMEOUT)
                    .agentId("slow-agent")
                    .explanation("timed out")
                    .build();

            when(dispatcher.dispatch(eq("slow-agent"), any(), eq(agentContext)))
                    .thenReturn(Promise.of(timeout));

            AgentResult<Object> result = runPromise(() ->
                    plan.execute(entry, "input", agentContext, dispatcher));

            assertThat(result.isFailed()).isTrue();
            assertThat(result.getExplanation()).contains("slow-agent");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Orchestration metadata
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Orchestration metadata")
    class OrchestrationMetadata {

        @Test
        @DisplayName("should include delegation metrics in result")
        void shouldIncludeMetrics() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("metrics-agent")
                    .name("Metrics Agent")
                    .delegation(Map.of("can_delegate_to", List.of("sub-1")))
                    .build();

            AgentResult<Object> subResult = AgentResult.builder()
                    .output("result")
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("sub-1")
                    .build();
            when(dispatcher.dispatch(any(), any(), any())).thenReturn(Promise.of(subResult));

            AgentResult<Object> result = runPromise(() ->
                    plan.execute(entry, "input", agentContext, dispatcher));

            assertThat(result.getMetrics())
                    .containsEntry("tier", "SERVICE_ORCHESTRATED");
            assertThat(result.getProcessingTime()).isNotNull();
        }

        @Test
        @DisplayName("should set confidence to 1.0 on successful orchestration")
        void shouldSetConfidence() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("confidence-agent")
                    .name("Confidence Agent")
                    .delegation(Map.of("can_delegate_to", List.of("sub-1")))
                    .build();

            AgentResult<Object> subResult = AgentResult.builder()
                    .output("done")
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("sub-1")
                    .build();
            when(dispatcher.dispatch(any(), any(), any())).thenReturn(Promise.of(subResult));

            AgentResult<Object> result = runPromise(() ->
                    plan.execute(entry, "input", agentContext, dispatcher));

            assertThat(result.getConfidence()).isEqualTo(1.0);
        }
    }
}
