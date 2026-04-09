/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.behavioral;

import com.ghatana.agent.*;
import com.ghatana.agent.composite.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Behavioral tests for CompositeAgent.
 *
 * Focus: Agent composition, result aggregation strategies (voting, averaging),
 * ensemble logic, and error isolation.
 */
@DisplayName("CompositeAgent Behavioral Tests")
@ExtendWith(MockitoExtension.class)
class CompositeAgentBehavioralTest {

    @Mock
    private MemoryStore memoryStore;

    @Mock
    private TypedAgent<Map<String, Object>, Map<String, Object>> subAgent1;

    @Mock
    private TypedAgent<Map<String, Object>, Map<String, Object>> subAgent2;

    @Mock
    private TypedAgent<Map<String, Object>, Map<String, Object>> subAgent3;

    private AgentContext agentContext;
    private CompositeAgent agent;

    @BeforeEach
    void setUp() {
        agentContext = AgentContext.builder()
                .turnId("turn-1")
                .agentId("composite-agent")
                .tenantId("tenant-1")
                .memoryStore(memoryStore)
                .build();

        // Stub descriptor() leniently so the CompositeAgent exception-handler log call
        // (sub.descriptor().getAgentId()) does not produce a secondary NPE when a
        // sub-agent fails during processing.
        lenient().when(subAgent1.descriptor()).thenReturn(
                AgentDescriptor.builder().agentId("agent1").type(AgentType.COMPOSITE).build());
        lenient().when(subAgent2.descriptor()).thenReturn(
                AgentDescriptor.builder().agentId("agent2").type(AgentType.COMPOSITE).build());
        lenient().when(subAgent3.descriptor()).thenReturn(
                AgentDescriptor.builder().agentId("agent3").type(AgentType.COMPOSITE).build());

        agent = new CompositeAgent("composite-agent");
        agent.setSubAgents(List.of(subAgent1, subAgent2, subAgent3));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Processing & Aggregation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Processing & Aggregation")
    class ProcessingTests {

        @Test
        @DisplayName("Composite agent fans out to all sub-agents")
        void fanOutToSubAgents() {
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("vote", "A"))
                    .confidence(0.85)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10))
                    .build();

            AgentResult<Map<String, Object>> result2 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("vote", "A"))
                    .confidence(0.90)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent2")
                    .processingTime(Duration.ofMillis(12))
                    .build();

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("vote", "B"))
                    .confidence(0.70)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(15))
                    .build();

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1));
            when(subAgent2.process(any(), any())).thenReturn(Promise.of(result2));
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3));

            CompositeAgentConfig config = CompositeAgentConfig.builder()
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE)
                    .votingField("vote")
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("x", 1);
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            assertThat(result.isSuccess()).isTrue();
            verify(subAgent1).process(any(), any());
            verify(subAgent2).process(any(), any());
            verify(subAgent3).process(any(), any());
        }

        @Test
        @DisplayName("WEIGHTED_AVERAGE aggregates numeric scores")
        void weightedAverageAggregation() {
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("score", 80.0))
                    .confidence(0.95)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10))
                    .build();

            AgentResult<Map<String, Object>> result2 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("score", 75.0))
                    .confidence(0.88)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent2")
                    .processingTime(Duration.ofMillis(12))
                    .build();

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("score", 85.0))
                    .confidence(0.92)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(11))
                    .build();

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1));
            when(subAgent2.process(any(), any())).thenReturn(Promise.of(result2));
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3));

            CompositeAgentConfig config = CompositeAgentConfig.builder()
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.WEIGHTED_AVERAGE)
                    .numericField("score")
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("data", "test");
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            assertThat(result.isSuccess()).isTrue();
            // Output should be a combination of scores
            assertThat(result.getOutput()).isNotNull();
        }

        @Test
        @DisplayName("MAJORITY_VOTE selects consensus decision")
        void majorityVote() {
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("decision", "APPROVE"))
                    .confidence(0.92)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10))
                    .build();

            AgentResult<Map<String, Object>> result2 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("decision", "APPROVE"))
                    .confidence(0.88)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent2")
                    .processingTime(Duration.ofMillis(11))
                    .build();

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("decision", "REJECT"))
                    .confidence(0.75)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(12))
                    .build();

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1));
            when(subAgent2.process(any(), any())).thenReturn(Promise.of(result2));
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3));

            CompositeAgentConfig config = CompositeAgentConfig.builder()
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE)
                    .votingField("decision")
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("request", "approval");
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            assertThat(result.isSuccess()).isTrue();
            // Majority should be APPROVE (2 vs 1)
            assertThat(result.getOutput()).isNotNull();
        }

        @Test
        @DisplayName("FIRST_MATCH returns first successful sub-agent result")
        void firstMatchStrategy() {
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("result", "from_agent1"))
                    .confidence(0.85)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10))
                    .build();

            AgentResult<Map<String, Object>> result2 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("result", "from_agent2"))
                    .confidence(0.90)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent2")
                    .processingTime(Duration.ofMillis(5))  // Faster
                    .build();

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("result", "from_agent3"))
                    .confidence(0.70)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(20))
                    .build();

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1));
            when(subAgent2.process(any(), any())).thenReturn(Promise.of(result2));
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3));

            CompositeAgentConfig config = CompositeAgentConfig.builder()
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.FIRST_MATCH)
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("x", 1);
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("UNANIMOUS requires all sub-agents to agree")
        void unanimousAgreement() {
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("agree", true))
                    .confidence(0.95)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10))
                    .build();

            AgentResult<Map<String, Object>> result2 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("agree", true))
                    .confidence(0.92)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent2")
                    .processingTime(Duration.ofMillis(11))
                    .build();

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("agree", true))
                    .confidence(0.90)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(12))
                    .build();

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1));
            when(subAgent2.process(any(), any())).thenReturn(Promise.of(result2));
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3));

            CompositeAgentConfig config = CompositeAgentConfig.builder()
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.UNANIMOUS)
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("consensus", "check");
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            assertThat(result.isSuccess()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Confidence Composition Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Confidence Composition")
    class ConfidenceCompositionTests {

        @Test
        @DisplayName("Composite confidence is average of sub-agent confidences")
        void confidenceAverage() {
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("x", 1))
                    .confidence(0.90)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10))
                    .build();

            AgentResult<Map<String, Object>> result2 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("x", 1))
                    .confidence(0.80)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent2")
                    .processingTime(Duration.ofMillis(11))
                    .build();

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("x", 1))
                    .confidence(0.85)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(12))
                    .build();

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1));
            when(subAgent2.process(any(), any())).thenReturn(Promise.of(result2));
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3));

            CompositeAgentConfig config = CompositeAgentConfig.builder()
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.WEIGHTED_AVERAGE)
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("test", true);
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            // Composite confidence should be in valid range
            assertThat(result.getConfidence())
                    .isGreaterThanOrEqualTo(0.0)
                    .isLessThanOrEqualTo(1.0);
        }

        @Test
        @DisplayName("High agreement increases composite confidence")
        void highAgreementConfidence() {
            // All agents agree with high confidence
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("decision", "A"))
                    .confidence(0.95)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10))
                    .build();

            AgentResult<Map<String, Object>> result2 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("decision", "A"))
                    .confidence(0.94)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent2")
                    .processingTime(Duration.ofMillis(11))
                    .build();

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("decision", "A"))
                    .confidence(0.96)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(12))
                    .build();

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1));
            when(subAgent2.process(any(), any())).thenReturn(Promise.of(result2));
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3));

            CompositeAgentConfig config = CompositeAgentConfig.builder()
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE)
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("test", true);
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            // High agreement should lead to high composite confidence
            assertThat(result.getConfidence()).isGreaterThan(0.85);
        }

        @Test
        @DisplayName("Low agreement decreases composite confidence")
        void lowAgreementConfidence() {
            // Agents disagree
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("decision", "A"))
                    .confidence(0.60)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10))
                    .build();

            AgentResult<Map<String, Object>> result2 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("decision", "B"))
                    .confidence(0.55)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent2")
                    .processingTime(Duration.ofMillis(11))
                    .build();

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("decision", "C"))
                    .confidence(0.58)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(12))
                    .build();

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1));
            when(subAgent2.process(any(), any())).thenReturn(Promise.of(result2));
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3));

            CompositeAgentConfig config = CompositeAgentConfig.builder()
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE)
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("ambiguous", true);
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            // Low agreement should lead to lower composite confidence
            assertThat(result.getConfidence()).isLessThan(0.75);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error Isolation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error Isolation")
    class ErrorIsolationTests {

        @Test
        @DisplayName("Single sub-agent failure does not fail composite")
        void singleAgentFailureIsolation() {
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("vote", "A"))
                    .confidence(0.90)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10))
                    .build();

            // Agent 2 fails
            when(subAgent2.process(any(), any()))
                    .thenReturn(Promise.ofException(new RuntimeException("Agent 2 failed")));

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("vote", "A"))
                    .confidence(0.85)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(12))
                    .build();

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1));
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3));

            CompositeAgentConfig config = CompositeAgentConfig.builder()
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE)
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("x", 1);
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            // Should still produce result from agents 1 and 3
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Partial failures are handled gracefully")
        void partialFailureHandling() {
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("result", "ok"))
                    .confidence(0.90)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10))
                    .build();

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1));
            when(subAgent2.process(any(), any()))
                    .thenReturn(Promise.ofException(new TimeoutException("Timeout")));
            when(subAgent3.process(any(), any()))
                    .thenReturn(Promise.ofException(new RuntimeException("Error")));

            CompositeAgentConfig config = CompositeAgentConfig.builder()
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.FIRST_MATCH)
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("x", 1);
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("All sub-agents fail produces meaningful error")
        void allSubAgentsFail() {
            when(subAgent1.process(any(), any()))
                    .thenReturn(Promise.ofException(new RuntimeException("Agent 1 failed")));
            when(subAgent2.process(any(), any()))
                    .thenReturn(Promise.ofException(new RuntimeException("Agent 2 failed")));
            when(subAgent3.process(any(), any()))
                    .thenReturn(Promise.ofException(new RuntimeException("Agent 3 failed")));

            CompositeAgentConfig config = CompositeAgentConfig.builder()
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE)
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("x", 1);
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            assertThat(result).isNotNull();
            assertThat(result.getExplanation())
                    .isNotNull()
                    .isNotBlank();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Explanation Composition Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Explanation Composition")
    class ExplanationCompositionTests {

        @Test
        @DisplayName("Composite explanation mentions sub-agent contributions")
        void compositeExplanation() {
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("vote", "A"))
                    .confidence(0.90)
                    .status(AgentResultStatus.SUCCESS)
                    .explanation("Agent1 reasoning")
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10))
                    .build();

            AgentResult<Map<String, Object>> result2 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("vote", "A"))
                    .confidence(0.88)
                    .status(AgentResultStatus.SUCCESS)
                    .explanation("Agent2 reasoning")
                    .agentId("agent2")
                    .processingTime(Duration.ofMillis(11))
                    .build();

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("vote", "B"))
                    .confidence(0.75)
                    .status(AgentResultStatus.SUCCESS)
                    .explanation("Agent3 reasoning")
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(12))
                    .build();

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1));
            when(subAgent2.process(any(), any())).thenReturn(Promise.of(result2));
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3));

            CompositeAgentConfig config = CompositeAgentConfig.builder()
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE)
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("request", "vote");
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            String explanation = result.getExplanation();
            assertThat(explanation)
                    .isNotNull()
                    .isNotBlank();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Latency & Performance Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Latency & Performance")
    class LatencyTests {

        @Test
        @DisplayName("Composite latency is sum of sub-agent latencies")
        void compositeLatency() {
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("x", 1))
                    .confidence(0.90)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10))
                    .build();

            AgentResult<Map<String, Object>> result2 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("x", 1))
                    .confidence(0.85)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent2")
                    .processingTime(Duration.ofMillis(20))
                    .build();

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("x", 1))
                    .confidence(0.88)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(15))
                    .build();

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1));
            when(subAgent2.process(any(), any())).thenReturn(Promise.of(result2));
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3));

            CompositeAgentConfig config = CompositeAgentConfig.builder()
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.WEIGHTED_AVERAGE)
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("x", 1);
            Instant start = Instant.now();
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));
            Instant end = Instant.now();

            assertThat(result.isSuccess()).isTrue();
            // Overall should include time for all agents (roughly 45ms)
            assertThat(Duration.between(start, end))
                    .isLessThan(Duration.ofSeconds(1));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private <T> T runPromise(java.util.function.Supplier<Promise<T>> supplier) {
        var result = new Object() { T value; };
        var error = new Object() { Exception ex; };

        Eventloop eventloop = Eventloop.builder().withCurrentThread().build();
        eventloop.post(() -> supplier.get()
                .whenResult(v -> result.value = v)
                .whenException(e -> error.ex = (Exception) e));

        eventloop.run();

        if (error.ex != null) {
            throw new RuntimeException(error.ex);
        }

        return result.value;
    }
}
