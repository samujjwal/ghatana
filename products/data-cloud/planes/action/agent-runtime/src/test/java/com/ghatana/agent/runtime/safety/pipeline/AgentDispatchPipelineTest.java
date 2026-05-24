/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.pipeline;

import com.ghatana.agent.runtime.safety.gate.AgentDispatchGate;
import com.ghatana.agent.runtime.safety.stage.AgentDispatchStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 7: Contract tests for AgentDispatchPipeline.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Pipeline executes all gates in sequence</li>
 *   <li>Pipeline executes all stages in sequence</li>
 *   <li>Pipeline fails fast on gate failure</li>
 *   <li>Pipeline fails fast on stage failure</li>
 * </ul>
 */
@DisplayName("Agent Dispatch Pipeline Tests (Phase 7)")
class AgentDispatchPipelineTest {

    // =========================================================================
    //  Pipeline Execution
    // =========================================================================

    @Nested
    @DisplayName("Pipeline Execution")
    class ExecutionTests {

        @Test
        @DisplayName("pipeline succeeds when all gates and stages pass")
        void pipelineSucceedsWhenAllGatesAndStagesPass() {
            AgentDispatchGate gate1 = mock(AgentDispatchGate.class);
            AgentDispatchGate gate2 = mock(AgentDispatchGate.class);
            AgentDispatchStage stage1 = mock(AgentDispatchStage.class);
            AgentDispatchStage stage2 = mock(AgentDispatchStage.class);

            when(gate1.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(AgentDispatchGate.GateResult.success());
            when(gate2.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(AgentDispatchGate.GateResult.success());
            when(stage1.execute(org.mockito.ArgumentMatchers.any())).thenReturn(AgentDispatchStage.StageResult.success(Map.of("stage1", "value1")));
            when(stage2.execute(org.mockito.ArgumentMatchers.any())).thenReturn(AgentDispatchStage.StageResult.success(Map.of("stage2", "value2")));

            AgentDispatchPipeline pipeline = new AgentDispatchPipeline(
                List.of(gate1, gate2),
                List.of(stage1, stage2));

            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchPipeline.PipelineResult result = pipeline.execute(context);

            assertThat(result.succeeded()).isTrue();
            assertThat(result.output()).hasSize(2);
            assertThat(result.output()).containsEntry("stage1", "value1");
            assertThat(result.output()).containsEntry("stage2", "value2");
        }

        @Test
        @DisplayName("pipeline fails fast on gate failure")
        void pipelineFailsFastOnGateFailure() {
            AgentDispatchGate gate1 = mock(AgentDispatchGate.class);
            AgentDispatchGate gate2 = mock(AgentDispatchGate.class);
            AgentDispatchStage stage1 = mock(AgentDispatchStage.class);

            when(gate1.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(AgentDispatchGate.GateResult.failure("Gate 1 failed"));
            when(gate2.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(AgentDispatchGate.GateResult.success());
            when(stage1.execute(org.mockito.ArgumentMatchers.any())).thenReturn(AgentDispatchStage.StageResult.success());

            AgentDispatchPipeline pipeline = new AgentDispatchPipeline(
                List.of(gate1, gate2),
                List.of(stage1));

            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchPipeline.PipelineResult result = pipeline.execute(context);

            assertThat(result.succeeded()).isFalse();
            assertThat(result.failedComponent()).isNotNull();
            assertThat(result.errorMessage()).contains("Gate 1 failed");
        }

        @Test
        @DisplayName("pipeline fails fast on stage failure")
        void pipelineFailsFastOnStageFailure() {
            AgentDispatchGate gate1 = mock(AgentDispatchGate.class);
            AgentDispatchStage stage1 = mock(AgentDispatchStage.class);
            AgentDispatchStage stage2 = mock(AgentDispatchStage.class);

            when(gate1.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(AgentDispatchGate.GateResult.success());
            when(stage1.execute(org.mockito.ArgumentMatchers.any())).thenReturn(AgentDispatchStage.StageResult.success(Map.of()));
            when(stage2.execute(org.mockito.ArgumentMatchers.any())).thenReturn(AgentDispatchStage.StageResult.failure("Stage 2 failed"));

            AgentDispatchPipeline pipeline = new AgentDispatchPipeline(
                List.of(gate1),
                List.of(stage1, stage2));

            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchPipeline.PipelineResult result = pipeline.execute(context);

            assertThat(result.succeeded()).isFalse();
            assertThat(result.failedComponent()).isNotNull();
            assertThat(result.errorMessage()).contains("Stage 2 failed");
        }

        @Test
        @DisplayName("requires non-null context")
        void requiresNonNullContext() {
            AgentDispatchPipeline pipeline = new AgentDispatchPipeline(List.of(), List.of());

            assertThatThrownBy(() -> pipeline.execute(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("context must not be null");
        }
    }

    // =========================================================================
    //  Builder
    // =========================================================================

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builder creates pipeline with gates and stages")
        void builderCreatesPipelineWithGatesAndStages() {
            AgentDispatchGate gate = mock(AgentDispatchGate.class);
            AgentDispatchStage stage = mock(AgentDispatchStage.class);

            when(gate.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(AgentDispatchGate.GateResult.success());
            when(stage.execute(org.mockito.ArgumentMatchers.any())).thenReturn(AgentDispatchStage.StageResult.success(Map.of()));

            AgentDispatchPipeline pipeline = new AgentDispatchPipeline.Builder()
                .addGate(gate)
                .addStage(stage)
                .build();

            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchPipeline.PipelineResult result = pipeline.execute(context);

            assertThat(result.succeeded()).isTrue();
        }

        @Test
        @DisplayName("builder requires non-null gate")
        void builderRequiresNonNullGate() {
            AgentDispatchPipeline.Builder builder = new AgentDispatchPipeline.Builder();

            assertThatThrownBy(() -> builder.addGate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("gate must not be null");
        }

        @Test
        @DisplayName("builder requires non-null stage")
        void builderRequiresNonNullStage() {
            AgentDispatchPipeline.Builder builder = new AgentDispatchPipeline.Builder();

            assertThatThrownBy(() -> builder.addStage(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("stage must not be null");
        }
    }

    // =========================================================================
    //  Pipeline Result Records
    // =========================================================================

    @Nested
    @DisplayName("Pipeline Result Records")
    class ResultRecordTests {

        @Test
        @DisplayName("success result contains output")
        void successResultContainsOutput() {
            Map<String, Object> output = Map.of("key", "value");
            AgentDispatchPipeline.PipelineResult result = AgentDispatchPipeline.PipelineResult.success(output);

            assertThat(result.succeeded()).isTrue();
            assertThat(result.failedComponent()).isNull();
            assertThat(result.errorMessage()).isNull();
            assertThat(result.output()).isEqualTo(output);
        }

        @Test
        @DisplayName("gate failure result contains gate name and reason")
        void gateFailureResultContainsGateNameAndReason() {
            AgentDispatchPipeline.PipelineResult result = AgentDispatchPipeline.PipelineResult.gateFailed("TestGate", "Test reason");

            assertThat(result.succeeded()).isFalse();
            assertThat(result.failedComponent()).isEqualTo("TestGate");
            assertThat(result.errorMessage()).isEqualTo("Test reason");
            assertThat(result.output()).isEmpty();
        }

        @Test
        @DisplayName("stage failure result contains stage name and error")
        void stageFailureResultContainsStageNameAndError() {
            AgentDispatchPipeline.PipelineResult result = AgentDispatchPipeline.PipelineResult.stageFailed("TestStage", "Test error");

            assertThat(result.succeeded()).isFalse();
            assertThat(result.failedComponent()).isEqualTo("TestStage");
            assertThat(result.errorMessage()).isEqualTo("Test error");
            assertThat(result.output()).isEmpty();
        }
    }
}
