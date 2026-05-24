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
import static org.mockito.Mockito.when;

/**
 * Phase 7: Contract tests for AgentMemoryRetrievalStage.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Memory retrieval</li>
 *   <li>Stage passes when memory retrieved</li>
 *   <li>Stage handles missing memory gracefully</li>
 * </ul>
 */
@DisplayName("Agent Memory Retrieval Stage Tests (Phase 7)")
class AgentMemoryRetrievalStageTest {

    // =========================================================================
    //  Stage Execution
    // =========================================================================

    @Nested
    @DisplayName("Stage Execution")
    class ExecutionTests {

        @Test
        @DisplayName("stage succeeds when memory retrieved")
        void stageSucceedsWhenMemoryRetrieved() {
            AgentMemoryRetrievalStage.MemoryRetriever retriever = mock(AgentMemoryRetrievalStage.MemoryRetriever.class);
            when(retriever.retrieveMemory("agent-1", "1.0.0"))
                .thenReturn(Map.of("context", "test context"));

            AgentMemoryRetrievalStage stage = new AgentMemoryRetrievalStage(retriever);
            AgentDispatchStage.StageContext context = new AgentDispatchStage.StageContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchStage.StageResult result = stage.execute(context);

            assertThat(result.succeeded()).isTrue();
            assertThat(result.output()).containsKey("memory");
        }

        @Test
        @DisplayName("stage succeeds when memory is null")
        void stageSucceedsWhenMemoryIsNull() {
            AgentMemoryRetrievalStage.MemoryRetriever retriever = mock(AgentMemoryRetrievalStage.MemoryRetriever.class);
            when(retriever.retrieveMemory("agent-1", "1.0.0")).thenReturn(null);

            AgentMemoryRetrievalStage stage = new AgentMemoryRetrievalStage(retriever);
            AgentDispatchStage.StageContext context = new AgentDispatchStage.StageContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchStage.StageResult result = stage.execute(context);

            assertThat(result.succeeded()).isTrue();
            assertThat(result.output()).containsKey("memory");
        }

        @Test
        @DisplayName("stage fails when memory retrieval throws exception")
        void stageFailsWhenMemoryRetrievalThrowsException() {
            AgentMemoryRetrievalStage.MemoryRetriever retriever = mock(AgentMemoryRetrievalStage.MemoryRetriever.class);
            when(retriever.retrieveMemory("agent-1", "1.0.0"))
                .thenThrow(new RuntimeException("Database error"));

            AgentMemoryRetrievalStage stage = new AgentMemoryRetrievalStage(retriever);
            AgentDispatchStage.StageContext context = new AgentDispatchStage.StageContext(
                "agent-1", "1.0.0", "grant-123", Map.of());

            AgentDispatchStage.StageResult result = stage.execute(context);

            assertThat(result.succeeded()).isFalse();
            assertThat(result.errorMessage()).contains("retrieval failed");
        }

        @Test
        @DisplayName("requires non-null context")
        void requiresNonNullContext() {
            AgentMemoryRetrievalStage.MemoryRetriever retriever = mock(AgentMemoryRetrievalStage.MemoryRetriever.class);
            AgentMemoryRetrievalStage stage = new AgentMemoryRetrievalStage(retriever);

            assertThatThrownBy(() -> stage.execute(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("context must not be null");
        }
    }

    // =========================================================================
    //  Stage Result Records
    // =========================================================================

    @Nested
    @DisplayName("Stage Result Records")
    class ResultRecordTests {

        @Test
        @DisplayName("success result with output")
        void successResultWithOutput() {
            Map<String, Object> output = Map.of("key", "value");
            AgentDispatchStage.StageResult result = AgentDispatchStage.StageResult.success(output);

            assertThat(result.succeeded()).isTrue();
            assertThat(result.errorMessage()).isNull();
            assertThat(result.output()).isEqualTo(output);
        }

        @Test
        @DisplayName("success result without output")
        void successResultWithoutOutput() {
            AgentDispatchStage.StageResult result = AgentDispatchStage.StageResult.success();

            assertThat(result.succeeded()).isTrue();
            assertThat(result.errorMessage()).isNull();
            assertThat(result.output()).isEmpty();
        }

        @Test
        @DisplayName("failure result contains error message")
        void failureResultContainsErrorMessage() {
            AgentDispatchStage.StageResult result = AgentDispatchStage.StageResult.failure("Error occurred");

            assertThat(result.succeeded()).isFalse();
            assertThat(result.errorMessage()).isEqualTo("Error occurred");
            assertThat(result.output()).isEmpty();
        }
    }
}
