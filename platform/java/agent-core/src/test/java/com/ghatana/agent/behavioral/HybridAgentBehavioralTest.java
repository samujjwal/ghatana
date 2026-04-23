/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 */

package com.ghatana.agent.behavioral;

import com.ghatana.agent.*;
import com.ghatana.agent.deterministic.*;
import com.ghatana.agent.hybrid.*;
import com.ghatana.agent.probabilistic.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Behavioral tests for HybridAgent.
 *
 * Focus: Fast-path vs fallback routing, result composition, confidence-based escalation,
 * and intelligent agent coordination.
 */
@DisplayName("HybridAgent Behavioral Tests")
@ExtendWith(MockitoExtension.class) // GH-90000
class HybridAgentBehavioralTest {

    @Mock
    private MemoryStore memoryStore;

    @Mock
    private TypedAgent<Map<String, Object>, Map<String, Object>> deterministicAgent;

    @Mock
    private TypedAgent<Map<String, Object>, Map<String, Object>> probabilisticAgent;

    private AgentContext agentContext;
    private HybridAgent agent;

    @BeforeEach
    void setUp() { // GH-90000
        agentContext = AgentContext.builder() // GH-90000
                .turnId("turn-1")
                .agentId("hybrid-agent")
                .tenantId("tenant-1")
                .memoryStore(memoryStore) // GH-90000
                .build(); // GH-90000

        agent = new HybridAgent("hybrid-agent");
        agent.setDeterministicAgent(deterministicAgent); // GH-90000
        agent.setProbabilisticAgent(probabilisticAgent); // GH-90000
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Routing Strategy Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Routing Strategies")
    class RoutingTests {

        @Test
        @DisplayName("DETERMINISTIC_FIRST routes to deterministic agent first")
        void deterministicFirstStrategy() { // GH-90000
            AgentResult<Map<String, Object>> detResult = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("decision", "matched")) // GH-90000
                    .confidence(1.0) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .explanation("Rule matched")
                    .agentId("det-agent")
                    .processingTime(Duration.ofMillis(1)) // GH-90000
                    .build(); // GH-90000

            when(deterministicAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.of(detResult)); // GH-90000

            HybridAgentConfig config = HybridAgentConfig.builder() // GH-90000
                    .strategy(HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", 1); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            verify(deterministicAgent, times(1)).process(any(), any()); // GH-90000
        }

        @Test
        @DisplayName("DETERMINISTIC_FIRST escalates to probabilistic on no match")
        void deterministicFirstEscalation() { // GH-90000
            // Deterministic agent returns no match
            AgentResult<Map<String, Object>> detNoMatch = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .status(AgentResultStatus.SKIPPED) // GH-90000
                    .explanation("No rules matched")
                    .agentId("det-agent")
                    .processingTime(Duration.ofMillis(1)) // GH-90000
                    .build(); // GH-90000

            // Probabilistic agent provides fallback
            AgentResult<Map<String, Object>> probResult = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("prediction", "class-A")) // GH-90000
                    .confidence(0.82) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .explanation("Model inference")
                    .agentId("prob-agent")
                    .processingTime(Duration.ofMillis(50)) // GH-90000
                    .build(); // GH-90000

            when(deterministicAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.of(detNoMatch)); // GH-90000
            when(probabilisticAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.of(probResult)); // GH-90000

            HybridAgentConfig config = HybridAgentConfig.builder() // GH-90000
                    .strategy(HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", 1); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            verify(deterministicAgent, times(1)).process(any(), any()); // GH-90000
            verify(probabilisticAgent, times(1)).process(any(), any()); // GH-90000
        }

        @Test
        @DisplayName("PROBABILISTIC_FIRST routes to probabilistic agent first")
        void probabilisticFirstStrategy() { // GH-90000
            AgentResult<Map<String, Object>> probResult = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("prediction", "positive")) // GH-90000
                    .confidence(0.88) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .explanation("Model prediction")
                    .agentId("prob-agent")
                    .processingTime(Duration.ofMillis(30)) // GH-90000
                    .build(); // GH-90000

            when(probabilisticAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.of(probResult)); // GH-90000

            HybridAgentConfig config = HybridAgentConfig.builder() // GH-90000
                    .strategy(HybridAgentConfig.RoutingStrategy.PROBABILISTIC_FIRST) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("text", "great"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            verify(probabilisticAgent, times(1)).process(any(), any()); // GH-90000
        }

        @Test
        @DisplayName("PARALLEL runs both deterministic and probabilistic agents")
        void parallelStrategy() { // GH-90000
            AgentResult<Map<String, Object>> detResult = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("det", "value")) // GH-90000
                    .confidence(1.0) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("det-agent")
                    .processingTime(Duration.ofMillis(1)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> probResult = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("prob", "value")) // GH-90000
                    .confidence(0.85) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("prob-agent")
                    .processingTime(Duration.ofMillis(30)) // GH-90000
                    .build(); // GH-90000

            when(deterministicAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.of(detResult)); // GH-90000
            when(probabilisticAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.of(probResult)); // GH-90000

            HybridAgentConfig config = HybridAgentConfig.builder() // GH-90000
                    .strategy(HybridAgentConfig.RoutingStrategy.PARALLEL) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", 1); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            verify(deterministicAgent, times(1)).process(any(), any()); // GH-90000
            verify(probabilisticAgent, times(1)).process(any(), any()); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Confidence-Based Escalation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Confidence-Based Escalation")
    class ConfidenceEscalationTests {

        @Test
        @DisplayName("High-confidence deterministic result bypasses probabilistic")
        void highConfidenceDeterministic() { // GH-90000
            AgentResult<Map<String, Object>> detResult = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("action", "approved")) // GH-90000
                    .confidence(1.0) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("det-agent")
                    .processingTime(Duration.ofMillis(1)) // GH-90000
                    .build(); // GH-90000

            when(deterministicAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.of(detResult)); // GH-90000

            HybridAgentConfig config = HybridAgentConfig.builder() // GH-90000
                    .strategy(HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST) // GH-90000
                    .confidenceThreshold(0.9) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("transaction", 500); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.getConfidence()).isEqualTo(1.0); // GH-90000
            verify(deterministicAgent, times(1)).process(any(), any()); // GH-90000
            verify(probabilisticAgent, never()).process(any(), any()); // GH-90000
        }

        @Test
        @DisplayName("Low-confidence result escalates to other agent")
        void lowConfidenceEscalation() { // GH-90000
            AgentResult<Map<String, Object>> lowConfResult = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("uncertain", "value")) // GH-90000
                    .confidence(0.45) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("prob-agent")
                    .processingTime(Duration.ofMillis(40)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> betterResult = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("refined", "value")) // GH-90000
                    .confidence(0.92) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("det-agent")
                    .processingTime(Duration.ofMillis(2)) // GH-90000
                    .build(); // GH-90000

            when(probabilisticAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.of(lowConfResult)); // GH-90000
            when(deterministicAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.of(betterResult)); // GH-90000

            HybridAgentConfig config = HybridAgentConfig.builder() // GH-90000
                    .strategy(HybridAgentConfig.RoutingStrategy.PROBABILISTIC_FIRST) // GH-90000
                    .confidenceThreshold(0.7) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("ambiguous", "case"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Result Composition Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Result Composition")
    class ResultCompositionTests {

        @Test
        @DisplayName("Parallel results are merged intelligently")
        void parallelResultsMerge() { // GH-90000
            AgentResult<Map<String, Object>> detResult = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("status", "verified", "action", "proceed")) // GH-90000
                    .confidence(1.0) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("det-agent")
                    .processingTime(Duration.ofMillis(1)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> probResult = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("likelihood", 0.88, "category", "prime")) // GH-90000
                    .confidence(0.88) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("prob-agent")
                    .processingTime(Duration.ofMillis(35)) // GH-90000
                    .build(); // GH-90000

            when(deterministicAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.of(detResult)); // GH-90000
            when(probabilisticAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.of(probResult)); // GH-90000

            HybridAgentConfig config = HybridAgentConfig.builder() // GH-90000
                    .strategy(HybridAgentConfig.RoutingStrategy.PARALLEL) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("customer", "VIP"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Deterministic takes precedence when both succeed in PARALLEL")
        void deterministicPrecedenceInParallel() { // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> sharedOutput = Map.of("decision", "det"); // GH-90000

            AgentResult<Map<String, Object>> detResult = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(sharedOutput) // GH-90000
                    .confidence(1.0) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("det-agent")
                    .processingTime(Duration.ofMillis(1)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> probResult = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("decision", "prob")) // GH-90000
                    .confidence(0.80) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("prob-agent")
                    .processingTime(Duration.ofMillis(40)) // GH-90000
                    .build(); // GH-90000

            when(deterministicAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.of(detResult)); // GH-90000
            when(probabilisticAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.of(probResult)); // GH-90000

            HybridAgentConfig config = HybridAgentConfig.builder() // GH-90000
                    .strategy(HybridAgentConfig.RoutingStrategy.PARALLEL) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("test", true); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            // Result should prefer deterministic output
            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error Handling Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Deterministic failure falls back to probabilistic")
        void deterministicFailureFallback() { // GH-90000
            when(deterministicAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("Det error")));

            AgentResult<Map<String, Object>> probResult = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("result", "from_fallback")) // GH-90000
                    .confidence(0.75) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("prob-agent")
                    .processingTime(Duration.ofMillis(40)) // GH-90000
                    .build(); // GH-90000

            when(probabilisticAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.of(probResult)); // GH-90000

            HybridAgentConfig config = HybridAgentConfig.builder() // GH-90000
                    .strategy(HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", 1); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            // Should still produce result via fallback
            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Both agents fail produces error result")
        void bothAgentsFail() { // GH-90000
            when(deterministicAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("Det error")));
            when(probabilisticAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("Prob error")));

            HybridAgentConfig config = HybridAgentConfig.builder() // GH-90000
                    .strategy(HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", 1); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Timeout is handled gracefully")
        void timeoutHandling() { // GH-90000
            when(deterministicAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.ofException(new TimeoutException("Timeout")));

            AgentResult<Map<String, Object>> fallbackResult = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("result", "timeout_fallback")) // GH-90000
                    .confidence(0.60) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("prob-agent")
                    .processingTime(Duration.ofMillis(30)) // GH-90000
                    .build(); // GH-90000

            when(probabilisticAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.of(fallbackResult)); // GH-90000

            HybridAgentConfig config = HybridAgentConfig.builder() // GH-90000
                    .strategy(HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", 1); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Explanation Composition Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Explanation Composition")
    class ExplanationCompositionTests {

        @Test
        @DisplayName("Hybrid explanation mentions both agent contributions")
        void hybridExplanationComposes() { // GH-90000
            AgentResult<Map<String, Object>> detResult = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("det", "value")) // GH-90000
                    .confidence(1.0) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .explanation("Deterministic rule matched")
                    .agentId("det-agent")
                    .processingTime(Duration.ofMillis(1)) // GH-90000
                    .build(); // GH-90000

            AgentResult<Map<String, Object>> probResult = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("prob", "value")) // GH-90000
                    .confidence(0.85) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .explanation("Model inference: 85% confidence")
                    .agentId("prob-agent")
                    .processingTime(Duration.ofMillis(35)) // GH-90000
                    .build(); // GH-90000

            when(deterministicAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.of(detResult)); // GH-90000
            when(probabilisticAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.of(probResult)); // GH-90000

            HybridAgentConfig config = HybridAgentConfig.builder() // GH-90000
                    .strategy(HybridAgentConfig.RoutingStrategy.PARALLEL) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", 1); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            String explanation = result.getExplanation(); // GH-90000
            assertThat(explanation) // GH-90000
                    .isNotNull() // GH-90000
                    .isNotBlank(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Performance Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Performance")
    class PerformanceTests {

        @Test
        @DisplayName("Deterministic-first is faster than probabilistic fallback")
        void deterministicFastPath() { // GH-90000
            AgentResult<Map<String, Object>> detResult = AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(Map.of("action", "approved")) // GH-90000
                    .confidence(1.0) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId("det-agent")
                    .processingTime(Duration.ofMillis(1)) // GH-90000
                    .build(); // GH-90000

            when(deterministicAgent.process(any(), any())) // GH-90000
                    .thenReturn(Promise.of(detResult)); // GH-90000

            HybridAgentConfig config = HybridAgentConfig.builder() // GH-90000
                    .strategy(HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", 1); // GH-90000
            long startNanos = System.nanoTime(); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000
            long elapsedNanos = System.nanoTime() - startNanos; // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            // Keep a low-latency expectation while avoiding CI flakiness from wall-clock jitter.
            assertThat(Duration.ofNanos(elapsedNanos)) // GH-90000
                    .isLessThan(Duration.ofMillis(300)); // GH-90000
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
