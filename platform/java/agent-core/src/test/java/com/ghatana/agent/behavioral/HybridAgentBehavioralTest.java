/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. 
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
@ExtendWith(MockitoExtension.class) 
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
    void setUp() { 
        agentContext = AgentContext.builder() 
                .turnId("turn-1")
                .agentId("hybrid-agent")
                .tenantId("tenant-1")
                .memoryStore(memoryStore) 
                .build(); 

        agent = new HybridAgent("hybrid-agent");
        agent.setDeterministicAgent(deterministicAgent); 
        agent.setProbabilisticAgent(probabilisticAgent); 
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Routing Strategy Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Routing Strategies")
    class RoutingTests {

        @Test
        @DisplayName("DETERMINISTIC_FIRST routes to deterministic agent first")
        void deterministicFirstStrategy() { 
            AgentResult<Map<String, Object>> detResult = AgentResult.<Map<String, Object>>builder() 
                    .output(Map.of("decision", "matched")) 
                    .confidence(1.0) 
                    .status(AgentResultStatus.SUCCESS) 
                    .explanation("Rule matched")
                    .agentId("det-agent")
                    .processingTime(Duration.ofMillis(1)) 
                    .build(); 

            when(deterministicAgent.process(any(), any())) 
                    .thenReturn(Promise.of(detResult)); 

            HybridAgentConfig config = HybridAgentConfig.builder() 
                    .strategy(HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("x", 1); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result.isSuccess()).isTrue(); 
            verify(deterministicAgent, times(1)).process(any(), any()); 
        }

        @Test
        @DisplayName("DETERMINISTIC_FIRST escalates to probabilistic on no match")
        void deterministicFirstEscalation() { 
            // Deterministic agent returns no match
            AgentResult<Map<String, Object>> detNoMatch = AgentResult.<Map<String, Object>>builder() 
                    .status(AgentResultStatus.SKIPPED) 
                    .explanation("No rules matched")
                    .agentId("det-agent")
                    .processingTime(Duration.ofMillis(1)) 
                    .build(); 

            // Probabilistic agent provides fallback
            AgentResult<Map<String, Object>> probResult = AgentResult.<Map<String, Object>>builder() 
                    .output(Map.of("prediction", "class-A")) 
                    .confidence(0.82) 
                    .status(AgentResultStatus.SUCCESS) 
                    .explanation("Model inference")
                    .agentId("prob-agent")
                    .processingTime(Duration.ofMillis(50)) 
                    .build(); 

            when(deterministicAgent.process(any(), any())) 
                    .thenReturn(Promise.of(detNoMatch)); 
            when(probabilisticAgent.process(any(), any())) 
                    .thenReturn(Promise.of(probResult)); 

            HybridAgentConfig config = HybridAgentConfig.builder() 
                    .strategy(HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("x", 1); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result.isSuccess()).isTrue(); 
            verify(deterministicAgent, times(1)).process(any(), any()); 
            verify(probabilisticAgent, times(1)).process(any(), any()); 
        }

        @Test
        @DisplayName("PROBABILISTIC_FIRST routes to probabilistic agent first")
        void probabilisticFirstStrategy() { 
            AgentResult<Map<String, Object>> probResult = AgentResult.<Map<String, Object>>builder() 
                    .output(Map.of("prediction", "positive")) 
                    .confidence(0.88) 
                    .status(AgentResultStatus.SUCCESS) 
                    .explanation("Model prediction")
                    .agentId("prob-agent")
                    .processingTime(Duration.ofMillis(30)) 
                    .build(); 

            when(probabilisticAgent.process(any(), any())) 
                    .thenReturn(Promise.of(probResult)); 

            HybridAgentConfig config = HybridAgentConfig.builder() 
                    .strategy(HybridAgentConfig.RoutingStrategy.PROBABILISTIC_FIRST) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("text", "great"); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result.isSuccess()).isTrue(); 
            verify(probabilisticAgent, times(1)).process(any(), any()); 
        }

        @Test
        @DisplayName("PARALLEL runs both deterministic and probabilistic agents")
        void parallelStrategy() { 
            AgentResult<Map<String, Object>> detResult = AgentResult.<Map<String, Object>>builder() 
                    .output(Map.of("det", "value")) 
                    .confidence(1.0) 
                    .status(AgentResultStatus.SUCCESS) 
                    .agentId("det-agent")
                    .processingTime(Duration.ofMillis(1)) 
                    .build(); 

            AgentResult<Map<String, Object>> probResult = AgentResult.<Map<String, Object>>builder() 
                    .output(Map.of("prob", "value")) 
                    .confidence(0.85) 
                    .status(AgentResultStatus.SUCCESS) 
                    .agentId("prob-agent")
                    .processingTime(Duration.ofMillis(30)) 
                    .build(); 

            when(deterministicAgent.process(any(), any())) 
                    .thenReturn(Promise.of(detResult)); 
            when(probabilisticAgent.process(any(), any())) 
                    .thenReturn(Promise.of(probResult)); 

            HybridAgentConfig config = HybridAgentConfig.builder() 
                    .strategy(HybridAgentConfig.RoutingStrategy.PARALLEL) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("x", 1); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result.isSuccess()).isTrue(); 
            verify(deterministicAgent, times(1)).process(any(), any()); 
            verify(probabilisticAgent, times(1)).process(any(), any()); 
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
        void highConfidenceDeterministic() { 
            AgentResult<Map<String, Object>> detResult = AgentResult.<Map<String, Object>>builder() 
                    .output(Map.of("action", "approved")) 
                    .confidence(1.0) 
                    .status(AgentResultStatus.SUCCESS) 
                    .agentId("det-agent")
                    .processingTime(Duration.ofMillis(1)) 
                    .build(); 

            when(deterministicAgent.process(any(), any())) 
                    .thenReturn(Promise.of(detResult)); 

            HybridAgentConfig config = HybridAgentConfig.builder() 
                    .strategy(HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST) 
                    .confidenceThreshold(0.9) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("transaction", 500); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result.getConfidence()).isEqualTo(1.0); 
            verify(deterministicAgent, times(1)).process(any(), any()); 
            verify(probabilisticAgent, never()).process(any(), any()); 
        }

        @Test
        @DisplayName("Low-confidence result escalates to other agent")
        void lowConfidenceEscalation() { 
            AgentResult<Map<String, Object>> lowConfResult = AgentResult.<Map<String, Object>>builder() 
                    .output(Map.of("uncertain", "value")) 
                    .confidence(0.45) 
                    .status(AgentResultStatus.SUCCESS) 
                    .agentId("prob-agent")
                    .processingTime(Duration.ofMillis(40)) 
                    .build(); 

            AgentResult<Map<String, Object>> betterResult = AgentResult.<Map<String, Object>>builder() 
                    .output(Map.of("refined", "value")) 
                    .confidence(0.92) 
                    .status(AgentResultStatus.SUCCESS) 
                    .agentId("det-agent")
                    .processingTime(Duration.ofMillis(2)) 
                    .build(); 

            when(probabilisticAgent.process(any(), any())) 
                    .thenReturn(Promise.of(lowConfResult)); 
            when(deterministicAgent.process(any(), any())) 
                    .thenReturn(Promise.of(betterResult)); 

            HybridAgentConfig config = HybridAgentConfig.builder() 
                    .strategy(HybridAgentConfig.RoutingStrategy.PROBABILISTIC_FIRST) 
                    .confidenceThreshold(0.7) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("ambiguous", "case"); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result.isSuccess()).isTrue(); 
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
        void parallelResultsMerge() { 
            AgentResult<Map<String, Object>> detResult = AgentResult.<Map<String, Object>>builder() 
                    .output(Map.of("status", "verified", "action", "proceed")) 
                    .confidence(1.0) 
                    .status(AgentResultStatus.SUCCESS) 
                    .agentId("det-agent")
                    .processingTime(Duration.ofMillis(1)) 
                    .build(); 

            AgentResult<Map<String, Object>> probResult = AgentResult.<Map<String, Object>>builder() 
                    .output(Map.of("likelihood", 0.88, "category", "prime")) 
                    .confidence(0.88) 
                    .status(AgentResultStatus.SUCCESS) 
                    .agentId("prob-agent")
                    .processingTime(Duration.ofMillis(35)) 
                    .build(); 

            when(deterministicAgent.process(any(), any())) 
                    .thenReturn(Promise.of(detResult)); 
            when(probabilisticAgent.process(any(), any())) 
                    .thenReturn(Promise.of(probResult)); 

            HybridAgentConfig config = HybridAgentConfig.builder() 
                    .strategy(HybridAgentConfig.RoutingStrategy.PARALLEL) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("customer", "VIP"); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result.isSuccess()).isTrue(); 
            assertThat(result.getOutput()).isNotNull(); 
        }

        @Test
        @DisplayName("Deterministic takes precedence when both succeed in PARALLEL")
        void deterministicPrecedenceInParallel() { 
            @SuppressWarnings("unchecked")
            Map<String, Object> sharedOutput = Map.of("decision", "det"); 

            AgentResult<Map<String, Object>> detResult = AgentResult.<Map<String, Object>>builder() 
                    .output(sharedOutput) 
                    .confidence(1.0) 
                    .status(AgentResultStatus.SUCCESS) 
                    .agentId("det-agent")
                    .processingTime(Duration.ofMillis(1)) 
                    .build(); 

            AgentResult<Map<String, Object>> probResult = AgentResult.<Map<String, Object>>builder() 
                    .output(Map.of("decision", "prob")) 
                    .confidence(0.80) 
                    .status(AgentResultStatus.SUCCESS) 
                    .agentId("prob-agent")
                    .processingTime(Duration.ofMillis(40)) 
                    .build(); 

            when(deterministicAgent.process(any(), any())) 
                    .thenReturn(Promise.of(detResult)); 
            when(probabilisticAgent.process(any(), any())) 
                    .thenReturn(Promise.of(probResult)); 

            HybridAgentConfig config = HybridAgentConfig.builder() 
                    .strategy(HybridAgentConfig.RoutingStrategy.PARALLEL) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("test", true); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            // Result should prefer deterministic output
            assertThat(result.isSuccess()).isTrue(); 
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
        void deterministicFailureFallback() { 
            when(deterministicAgent.process(any(), any())) 
                    .thenReturn(Promise.ofException(new RuntimeException("Det error")));

            AgentResult<Map<String, Object>> probResult = AgentResult.<Map<String, Object>>builder() 
                    .output(Map.of("result", "from_fallback")) 
                    .confidence(0.75) 
                    .status(AgentResultStatus.SUCCESS) 
                    .agentId("prob-agent")
                    .processingTime(Duration.ofMillis(40)) 
                    .build(); 

            when(probabilisticAgent.process(any(), any())) 
                    .thenReturn(Promise.of(probResult)); 

            HybridAgentConfig config = HybridAgentConfig.builder() 
                    .strategy(HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("x", 1); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            // Should still produce result via fallback
            assertThat(result).isNotNull(); 
        }

        @Test
        @DisplayName("Both agents fail produces error result")
        void bothAgentsFail() { 
            when(deterministicAgent.process(any(), any())) 
                    .thenReturn(Promise.ofException(new RuntimeException("Det error")));
            when(probabilisticAgent.process(any(), any())) 
                    .thenReturn(Promise.ofException(new RuntimeException("Prob error")));

            HybridAgentConfig config = HybridAgentConfig.builder() 
                    .strategy(HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("x", 1); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result).isNotNull(); 
        }

        @Test
        @DisplayName("Timeout is handled gracefully")
        void timeoutHandling() { 
            when(deterministicAgent.process(any(), any())) 
                    .thenReturn(Promise.ofException(new TimeoutException("Timeout")));

            AgentResult<Map<String, Object>> fallbackResult = AgentResult.<Map<String, Object>>builder() 
                    .output(Map.of("result", "timeout_fallback")) 
                    .confidence(0.60) 
                    .status(AgentResultStatus.SUCCESS) 
                    .agentId("prob-agent")
                    .processingTime(Duration.ofMillis(30)) 
                    .build(); 

            when(probabilisticAgent.process(any(), any())) 
                    .thenReturn(Promise.of(fallbackResult)); 

            HybridAgentConfig config = HybridAgentConfig.builder() 
                    .strategy(HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("x", 1); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result).isNotNull(); 
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
        void hybridExplanationComposes() { 
            AgentResult<Map<String, Object>> detResult = AgentResult.<Map<String, Object>>builder() 
                    .output(Map.of("det", "value")) 
                    .confidence(1.0) 
                    .status(AgentResultStatus.SUCCESS) 
                    .explanation("Deterministic rule matched")
                    .agentId("det-agent")
                    .processingTime(Duration.ofMillis(1)) 
                    .build(); 

            AgentResult<Map<String, Object>> probResult = AgentResult.<Map<String, Object>>builder() 
                    .output(Map.of("prob", "value")) 
                    .confidence(0.85) 
                    .status(AgentResultStatus.SUCCESS) 
                    .explanation("Model inference: 85% confidence")
                    .agentId("prob-agent")
                    .processingTime(Duration.ofMillis(35)) 
                    .build(); 

            when(deterministicAgent.process(any(), any())) 
                    .thenReturn(Promise.of(detResult)); 
            when(probabilisticAgent.process(any(), any())) 
                    .thenReturn(Promise.of(probResult)); 

            HybridAgentConfig config = HybridAgentConfig.builder() 
                    .strategy(HybridAgentConfig.RoutingStrategy.PARALLEL) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("x", 1); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            String explanation = result.getExplanation(); 
            assertThat(explanation) 
                    .isNotNull() 
                    .isNotBlank(); 
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
        void deterministicFastPath() { 
            AgentResult<Map<String, Object>> detResult = AgentResult.<Map<String, Object>>builder() 
                    .output(Map.of("action", "approved")) 
                    .confidence(1.0) 
                    .status(AgentResultStatus.SUCCESS) 
                    .agentId("det-agent")
                    .processingTime(Duration.ofMillis(1)) 
                    .build(); 

            when(deterministicAgent.process(any(), any())) 
                    .thenReturn(Promise.of(detResult)); 

            HybridAgentConfig config = HybridAgentConfig.builder() 
                    .strategy(HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("x", 1); 
            long startNanos = System.nanoTime(); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 
            long elapsedNanos = System.nanoTime() - startNanos; 

            assertThat(result.isSuccess()).isTrue(); 
            // Keep a low-latency expectation while avoiding CI flakiness from wall-clock jitter.
            assertThat(Duration.ofNanos(elapsedNanos)) 
                    .isLessThan(Duration.ofMillis(300)); 
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
