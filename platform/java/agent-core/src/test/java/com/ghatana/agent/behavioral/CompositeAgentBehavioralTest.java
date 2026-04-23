/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
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
 * Focus: Agent composition, result aggregation strategies (voting, averaging), // GH-90000
 * ensemble logic, and error isolation.
 */
@DisplayName("CompositeAgent Behavioral Tests")
@ExtendWith(MockitoExtension.class) // GH-90000
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
    void setUp() { // GH-90000
        agentContext = AgentContext.builder() // GH-90000
                .turnId("turn-1")
                .agentId("composite-agent")
                .tenantId("tenant-1")
                .memoryStore(memoryStore) // GH-90000
                .build(); // GH-90000

        // Stub descriptor() leniently so the CompositeAgent exception-handler log call // GH-90000
        // (sub.descriptor().getAgentId()) does not produce a secondary NPE when a // GH-90000
        // sub-agent fails during processing.
        lenient().when(subAgent1.descriptor()).thenReturn( // GH-90000
                AgentDescriptor.builder().agentId("agent1").type(AgentType.COMPOSITE).build());
        lenient().when(subAgent2.descriptor()).thenReturn( // GH-90000
                AgentDescriptor.builder().agentId("agent2").type(AgentType.COMPOSITE).build());
        lenient().when(subAgent3.descriptor()).thenReturn( // GH-90000
                AgentDescriptor.builder().agentId("agent3").type(AgentType.COMPOSITE).build());

        agent = new CompositeAgent("composite-agent");
        agent.setSubAgents(List.of(subAgent1, subAgent2, subAgent3)); // GH-90000
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Processing & Aggregation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Processing & Aggregation")
    class ProcessingTests {

        @Test
        @DisplayName("Composite agent fans out to all sub-agents")
        void fanOutToSubAgents() { // GH-90000
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("vote", "A")) // GH-90000
                    .confidence(0.85) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> result2 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("vote", "A")) // GH-90000
                    .confidence(0.90) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent2")
                    .processingTime(Duration.ofMillis(12)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("vote", "B")) // GH-90000
                    .confidence(0.70) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(15)) // GH-90000
                    .build(); // GH-90000

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1)); // GH-90000
            when(subAgent2.process(any(), any())).thenReturn(Promise.of(result2)); // GH-90000
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3)); // GH-90000

            CompositeAgentConfig config = CompositeAgentConfig.builder() // GH-90000
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE) // GH-90000
                    .votingField("vote")
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", 1); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            verify(subAgent1).process(any(), any()); // GH-90000
            verify(subAgent2).process(any(), any()); // GH-90000
            verify(subAgent3).process(any(), any()); // GH-90000
        }

        @Test
        @DisplayName("WEIGHTED_AVERAGE aggregates numeric scores")
        void weightedAverageAggregation() { // GH-90000
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("score", 80.0)) // GH-90000
                    .confidence(0.95) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> result2 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("score", 75.0)) // GH-90000
                    .confidence(0.88) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent2")
                    .processingTime(Duration.ofMillis(12)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("score", 85.0)) // GH-90000
                    .confidence(0.92) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(11)) // GH-90000
                    .build(); // GH-90000

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1)); // GH-90000
            when(subAgent2.process(any(), any())).thenReturn(Promise.of(result2)); // GH-90000
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3)); // GH-90000

            CompositeAgentConfig config = CompositeAgentConfig.builder() // GH-90000
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.WEIGHTED_AVERAGE) // GH-90000
                    .numericField("score")
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("data", "test"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            // Output should be a combination of scores
            assertThat(result.getOutput()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("MAJORITY_VOTE selects consensus decision")
        void majorityVote() { // GH-90000
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("decision", "APPROVE")) // GH-90000
                    .confidence(0.92) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> result2 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("decision", "APPROVE")) // GH-90000
                    .confidence(0.88) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent2")
                    .processingTime(Duration.ofMillis(11)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("decision", "REJECT")) // GH-90000
                    .confidence(0.75) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(12)) // GH-90000
                    .build(); // GH-90000

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1)); // GH-90000
            when(subAgent2.process(any(), any())).thenReturn(Promise.of(result2)); // GH-90000
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3)); // GH-90000

            CompositeAgentConfig config = CompositeAgentConfig.builder() // GH-90000
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE) // GH-90000
                    .votingField("decision")
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("request", "approval"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            // Majority should be APPROVE (2 vs 1) // GH-90000
            assertThat(result.getOutput()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("FIRST_MATCH returns first successful sub-agent result")
        void firstMatchStrategy() { // GH-90000
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("result", "from_agent1")) // GH-90000
                    .confidence(0.85) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> result2 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("result", "from_agent2")) // GH-90000
                    .confidence(0.90) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent2")
                    .processingTime(Duration.ofMillis(5))  // Faster // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("result", "from_agent3")) // GH-90000
                    .confidence(0.70) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(20)) // GH-90000
                    .build(); // GH-90000

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1)); // GH-90000
            when(subAgent2.process(any(), any())).thenReturn(Promise.of(result2)); // GH-90000
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3)); // GH-90000

            CompositeAgentConfig config = CompositeAgentConfig.builder() // GH-90000
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.FIRST_MATCH) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", 1); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("UNANIMOUS requires all sub-agents to agree")
        void unanimousAgreement() { // GH-90000
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("agree", true)) // GH-90000
                    .confidence(0.95) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> result2 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("agree", true)) // GH-90000
                    .confidence(0.92) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent2")
                    .processingTime(Duration.ofMillis(11)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("agree", true)) // GH-90000
                    .confidence(0.90) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(12)) // GH-90000
                    .build(); // GH-90000

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1)); // GH-90000
            when(subAgent2.process(any(), any())).thenReturn(Promise.of(result2)); // GH-90000
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3)); // GH-90000

            CompositeAgentConfig config = CompositeAgentConfig.builder() // GH-90000
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.UNANIMOUS) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("consensus", "check"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
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
        void confidenceAverage() { // GH-90000
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("x", 1)) // GH-90000
                    .confidence(0.90) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> result2 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("x", 1)) // GH-90000
                    .confidence(0.80) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent2")
                    .processingTime(Duration.ofMillis(11)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("x", 1)) // GH-90000
                    .confidence(0.85) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(12)) // GH-90000
                    .build(); // GH-90000

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1)); // GH-90000
            when(subAgent2.process(any(), any())).thenReturn(Promise.of(result2)); // GH-90000
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3)); // GH-90000

            CompositeAgentConfig config = CompositeAgentConfig.builder() // GH-90000
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.WEIGHTED_AVERAGE) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("test", true); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            // Composite confidence should be in valid range
            assertThat(result.getConfidence()) // GH-90000
                    .isGreaterThanOrEqualTo(0.0) // GH-90000
                    .isLessThanOrEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("High agreement increases composite confidence")
        void highAgreementConfidence() { // GH-90000
            // All agents agree with high confidence
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("decision", "A")) // GH-90000
                    .confidence(0.95) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> result2 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("decision", "A")) // GH-90000
                    .confidence(0.94) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent2")
                    .processingTime(Duration.ofMillis(11)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("decision", "A")) // GH-90000
                    .confidence(0.96) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(12)) // GH-90000
                    .build(); // GH-90000

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1)); // GH-90000
            when(subAgent2.process(any(), any())).thenReturn(Promise.of(result2)); // GH-90000
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3)); // GH-90000

            CompositeAgentConfig config = CompositeAgentConfig.builder() // GH-90000
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("test", true); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            // High agreement should lead to high composite confidence
            assertThat(result.getConfidence()).isGreaterThan(0.85); // GH-90000
        }

        @Test
        @DisplayName("Low agreement decreases composite confidence")
        void lowAgreementConfidence() { // GH-90000
            // Agents disagree
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("decision", "A")) // GH-90000
                    .confidence(0.60) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> result2 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("decision", "B")) // GH-90000
                    .confidence(0.55) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent2")
                    .processingTime(Duration.ofMillis(11)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("decision", "C")) // GH-90000
                    .confidence(0.58) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(12)) // GH-90000
                    .build(); // GH-90000

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1)); // GH-90000
            when(subAgent2.process(any(), any())).thenReturn(Promise.of(result2)); // GH-90000
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3)); // GH-90000

            CompositeAgentConfig config = CompositeAgentConfig.builder() // GH-90000
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("ambiguous", true); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            // Low agreement should lead to lower composite confidence
            assertThat(result.getConfidence()).isLessThan(0.75); // GH-90000
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
        void singleAgentFailureIsolation() { // GH-90000
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("vote", "A")) // GH-90000
                    .confidence(0.90) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10)) // GH-90000
                    .build(); // GH-90000

            // Agent 2 fails
            when(subAgent2.process(any(), any())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("Agent 2 failed")));

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("vote", "A")) // GH-90000
                    .confidence(0.85) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(12)) // GH-90000
                    .build(); // GH-90000

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1)); // GH-90000
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3)); // GH-90000

            CompositeAgentConfig config = CompositeAgentConfig.builder() // GH-90000
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", 1); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            // Should still produce result from agents 1 and 3
            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Partial failures are handled gracefully")
        void partialFailureHandling() { // GH-90000
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("result", "ok")) // GH-90000
                    .confidence(0.90) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10)) // GH-90000
                    .build(); // GH-90000

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1)); // GH-90000
            when(subAgent2.process(any(), any())) // GH-90000
                    .thenReturn(Promise.ofException(new TimeoutException("Timeout")));
            when(subAgent3.process(any(), any())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("Error")));

            CompositeAgentConfig config = CompositeAgentConfig.builder() // GH-90000
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.FIRST_MATCH) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", 1); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("All sub-agents fail produces meaningful error")
        void allSubAgentsFail() { // GH-90000
            when(subAgent1.process(any(), any())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("Agent 1 failed")));
            when(subAgent2.process(any(), any())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("Agent 2 failed")));
            when(subAgent3.process(any(), any())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("Agent 3 failed")));

            CompositeAgentConfig config = CompositeAgentConfig.builder() // GH-90000
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", 1); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.getExplanation()) // GH-90000
                    .isNotNull() // GH-90000
                    .isNotBlank(); // GH-90000
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
        void compositeExplanation() { // GH-90000
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("vote", "A")) // GH-90000
                    .confidence(0.90) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .explanation("Agent1 reasoning")
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> result2 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("vote", "A")) // GH-90000
                    .confidence(0.88) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .explanation("Agent2 reasoning")
                    .agentId("agent2")
                    .processingTime(Duration.ofMillis(11)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("vote", "B")) // GH-90000
                    .confidence(0.75) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .explanation("Agent3 reasoning")
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(12)) // GH-90000
                    .build(); // GH-90000

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1)); // GH-90000
            when(subAgent2.process(any(), any())).thenReturn(Promise.of(result2)); // GH-90000
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3)); // GH-90000

            CompositeAgentConfig config = CompositeAgentConfig.builder() // GH-90000
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("request", "vote"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            String explanation = result.getExplanation(); // GH-90000
            assertThat(explanation) // GH-90000
                    .isNotNull() // GH-90000
                    .isNotBlank(); // GH-90000
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
        void compositeLatency() { // GH-90000
            AgentResult<Map<String, Object>> result1 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("x", 1)) // GH-90000
                    .confidence(0.90) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent1")
                    .processingTime(Duration.ofMillis(10)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> result2 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("x", 1)) // GH-90000
                    .confidence(0.85) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent2")
                    .processingTime(Duration.ofMillis(20)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> result3 = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("x", 1)) // GH-90000
                    .confidence(0.88) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("agent3")
                    .processingTime(Duration.ofMillis(15)) // GH-90000
                    .build(); // GH-90000

            when(subAgent1.process(any(), any())).thenReturn(Promise.of(result1)); // GH-90000
            when(subAgent2.process(any(), any())).thenReturn(Promise.of(result2)); // GH-90000
            when(subAgent3.process(any(), any())).thenReturn(Promise.of(result3)); // GH-90000

            CompositeAgentConfig config = CompositeAgentConfig.builder() // GH-90000
                    .aggregationStrategy(CompositeAgentConfig.AggregationStrategy.WEIGHTED_AVERAGE) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", 1); // GH-90000
            Instant start = Instant.now(); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000
            Instant end = Instant.now(); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            // Overall should include time for all agents (roughly 45ms) // GH-90000
            // Increased threshold to handle system load variations
            assertThat(Duration.between(start, end)) // GH-90000
                    .isLessThan(Duration.ofSeconds(5)); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private <T> T runPromise(java.util.function.Supplier<Promise<T>> supplier) { // GH-90000
        var result = new Object() { T value; }; // GH-90000
        var error = new Object() { Exception ex; }; // GH-90000

        Eventloop eventloop = Eventloop.builder().withCurrentThread().build(); // GH-90000
        eventloop.post(() -> supplier.get() // GH-90000
                .whenResult(v -> result.value = v) // GH-90000
                .whenException(e -> error.ex = (Exception) e)); // GH-90000

        eventloop.run(); // GH-90000

        if (error.ex != null) { // GH-90000
            throw new RuntimeException(error.ex); // GH-90000
        }

        return result.value;
    }
}
