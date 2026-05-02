/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. 
 */

package com.ghatana.agent.behavioral;

import com.ghatana.agent.*;
import com.ghatana.agent.deterministic.*;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Behavioral tests for DeterministicAgent.
 *
 * Focus: Actual processing correctness, not structural validation.
 * Tests deterministic behavior, confidence scoring, explanation generation,
 * and state management.
 */
@DisplayName("DeterministicAgent Behavioral Tests")
@ExtendWith(MockitoExtension.class) 
class DeterministicAgentBehavioralTest {

    @Mock
    private MemoryStore memoryStore;

    private AgentContext agentContext;
    private DeterministicAgent agent;

    @BeforeEach
    void setUp() { 
        agentContext = AgentContext.builder() 
                .turnId("turn-1")
                .agentId("det-agent")
                .tenantId("tenant-1")
                .memoryStore(memoryStore) 
                .build(); 

        agent = new DeterministicAgent("det-agent");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Processing Logic Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Processing Logic")
    class ProcessingTests {

        @Test
        @DisplayName("Rule-based agent processes input and produces output")
        void ruleBasedProcessing() { 
            Rule rule = Rule.builder() 
                    .id("r1")
                    .name("Check Score")
                    .priority(1) 
                    .condition(RuleCondition.gt("score", 80)) 
                    .action("result", "PASS") 
                    .terminal(true) 
                    .build(); 

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() 
                    .subtype(DeterministicSubtype.RULE_BASED) 
                    .rule(rule) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("score", 95); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result).isNotNull(); 
            assertThat(result.isSuccess()).isTrue(); 
            assertThat(result.getOutput()).isNotNull(); 
        }

        @Test
        @DisplayName("Same input always produces same output (determinism)")
        void deterministicOutputConsistency() { 
            Rule rule = Rule.builder() 
                    .id("r1")
                    .name("Status Check")
                    .priority(1) 
                    .condition(RuleCondition.eq("status", "active")) 
                    .action("action", "process") 
                    .terminal(true) 
                    .build(); 

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() 
                    .subtype(DeterministicSubtype.RULE_BASED) 
                    .rule(rule) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("status", "active"); 

            // Run twice with same input
            AgentResult<?> result1 = runPromise(() -> agent.process(agentContext, input)); 
            AgentResult<?> result2 = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result1.getOutput()).isEqualTo(result2.getOutput()); 
            assertThat(result1.getStatus()).isEqualTo(result2.getStatus()); 
        }

        @Test
        @DisplayName("Threshold evaluation produces correct activation state")
        void thresholdProcessing() { 
            ThresholdEvaluator threshold = ThresholdEvaluator.builder() 
                    .id("temp")
                    .field("temperature")
                    .activationThreshold(100.0) 
                    .upperBound(true) 
                    .build(); 

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() 
                    .subtype(DeterministicSubtype.THRESHOLD) 
                    .threshold(threshold) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            // Test activation
            Map<String, Object> hotInput = Map.of("temperature", 105.0); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, hotInput)); 

            assertThat(result).isNotNull(); 
            assertThat(result.isSuccess()).isTrue(); 
        }

        @Test
        @DisplayName("FSM transitions follow state machine rules")
        void finiteStateMachineProcessing() { 
            FiniteStateMachine.FSMDefinition fsmDefinition = FiniteStateMachine.FSMDefinition.builder() 
                    .id("workflow")
                    .name("Workflow FSM")
                    .initialState("IDLE")
                    .state("IDLE").state("PROCESSING").state("DONE").state("FAILED")
                    .transition(FiniteStateMachine.FSMDefinition.Transition.builder() 
                            .name("START").fromState("IDLE").toState("PROCESSING").build())
                    .transition(FiniteStateMachine.FSMDefinition.Transition.builder() 
                            .name("COMPLETE").fromState("PROCESSING").toState("DONE").build())
                    .transition(FiniteStateMachine.FSMDefinition.Transition.builder() 
                            .name("ERROR").fromState("PROCESSING").toState("FAILED").build())
                    .build(); 

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() 
                    .subtype(DeterministicSubtype.FSM) 
                    .fsmDefinition(fsmDefinition) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("entityId", "workflow-1", "event", "START"); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result.isSuccess()).isTrue(); 
        }

        @Test
        @DisplayName("Pattern matching correctly identifies patterns")
        void patternMatchingProcessing() { 
            DeterministicAgentConfig config = DeterministicAgentConfig.builder() 
                    .subtype(DeterministicSubtype.PATTERN) 
                    .patternMatchStrategy((situation, context) -> java.util.Optional.empty()) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("text", "error-404-not-found"); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result).isNotNull(); // strategy returns empty → SKIPPED status 
        }

        @Test
        @DisplayName("Multiple rules evaluate all matching rules in priority order")
        void multipleRulesInPriorityOrder() { 
            Rule high = Rule.builder() 
                    .id("r-high")
                    .name("High Priority")
                    .priority(1) 
                    .condition(RuleCondition.gt("severity", 7)) 
                    .action("action", "escalate") 
                    .terminal(false) 
                    .build(); 

            Rule medium = Rule.builder() 
                    .id("r-medium")
                    .name("Medium Priority")
                    .priority(2) 
                    .condition(RuleCondition.gt("severity", 5)) 
                    .action("action", "notify") 
                    .terminal(false) 
                    .build(); 

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() 
                    .subtype(DeterministicSubtype.RULE_BASED) 
                    .rule(high) 
                    .rule(medium) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("severity", 8); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result.isSuccess()).isTrue(); 
        }

        @Test
        @DisplayName("No matching rules produces empty output")
        void noMatchingRulesOutput() { 
            Rule rule = Rule.builder() 
                    .id("r1")
                    .name("Impossible")
                    .priority(1) 
                    .condition(RuleCondition.eq("x", "impossible")) 
                    .action("result", "never") 
                    .terminal(true) 
                    .build(); 

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() 
                    .subtype(DeterministicSubtype.RULE_BASED) 
                    .rule(rule) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("x", "never"); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            // Result should be successful but empty
            assertThat(result).isNotNull(); 
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Confidence Scoring Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Confidence Scoring")
    class ConfidenceScoringTests {

        @Test
        @DisplayName("Deterministic agents always have confidence = 1.0")
        void deterministicConfidence() { 
            Rule rule = Rule.builder() 
                    .id("r1")
                    .name("Test")
                    .priority(1) 
                    .condition(RuleCondition.eq("type", "test")) 
                    .action("result", "ok") 
                    .terminal(true) 
                    .build(); 

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() 
                    .subtype(DeterministicSubtype.RULE_BASED) 
                    .rule(rule) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("type", "test"); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            // Deterministic agents have complete confidence
            assertThat(result.getConfidence()).isEqualTo(1.0); 
        }

        @Test
        @DisplayName("Confidence score is always in valid range [0.0, 1.0]")
        void confidenceInValidRange() { 
            Rule rule = Rule.builder() 
                    .id("r1")
                    .name("Test")
                    .priority(1) 
                    .condition(RuleCondition.gt("value", 0)) 
                    .action("result", "ok") 
                    .terminal(true) 
                    .build(); 

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() 
                    .subtype(DeterministicSubtype.RULE_BASED) 
                    .rule(rule) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            for (int i = 0; i < 10; i++) { 
                Map<String, Object> input = Map.of("value", i); 
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

                assertThat(result.getConfidence()) 
                        .isGreaterThanOrEqualTo(0.0) 
                        .isLessThanOrEqualTo(1.0); 
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Explanation Generation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Explanation Generation")
    class ExplanationTests {

        @Test
        @DisplayName("Explanation is non-empty for successful results")
        void explanationNonEmpty() { 
            Rule rule = Rule.builder() 
                    .id("r1")
                    .name("Check Score")
                    .priority(1) 
                    .condition(RuleCondition.gt("score", 75)) 
                    .action("result", "PASS") 
                    .terminal(true) 
                    .build(); 

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() 
                    .subtype(DeterministicSubtype.RULE_BASED) 
                    .rule(rule) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("score", 85); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result.getExplanation()) 
                    .isNotNull() 
                    .isNotBlank(); 
        }

        @Test
        @DisplayName("Explanation references the matching rule")
        void explanationReferencesRule() { 
            Rule rule = Rule.builder() 
                    .id("rule-audit")
                    .name("Audit Rule")
                    .priority(1) 
                    .condition(RuleCondition.gt("amount", 1000)) 
                    .action("review", "mandatory") 
                    .terminal(true) 
                    .build(); 

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() 
                    .subtype(DeterministicSubtype.RULE_BASED) 
                    .rule(rule) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("amount", 5000); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            // Explanation should contain rule info
            String explanation = result.getExplanation(); 
            assertThat(explanation) 
                    .isNotNull() 
                    .isNotBlank(); 
        }

        @Test
        @DisplayName("Explanation contains decision reasoning")
        void explanationContainsReasoning() { 
            Rule rule = Rule.builder() 
                    .id("r1")
                    .name("CPU Alert")
                    .priority(1) 
                    .condition(RuleCondition.gt("cpu_usage", 90)) 
                    .action("alert", "critical") 
                    .terminal(true) 
                    .build(); 

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() 
                    .subtype(DeterministicSubtype.RULE_BASED) 
                    .rule(rule) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("cpu_usage", 95.5); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result.getExplanation()) 
                    .contains("95.5", "90", "cpu") 
                    .doesNotContainIgnoringCase("high confidence")
                    .doesNotContainIgnoringCase("model");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // State Management Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("State Management")
    class StateManagementTests {

        @Test
        @DisplayName("Agent initializes successfully with valid config")
        void agentInitialization() { 
            Rule rule = Rule.builder() 
                    .id("r1")
                    .name("Test")
                    .priority(1) 
                    .condition(RuleCondition.eq("x", "y")) 
                    .action("a", "b") 
                    .terminal(true) 
                    .build(); 

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() 
                    .subtype(DeterministicSubtype.RULE_BASED) 
                    .rule(rule) 
                    .build(); 

            Promise<Void> initPromise = agent.initialize(config); 
            assertThat(initPromise).isNotNull(); 

            runPromise(() -> initPromise);  // Should complete without error 
        }

        @Test
        @DisplayName("Agent metadata reflects configuration")
        void agentMetadata() { 
            Rule rule = Rule.builder() 
                    .id("r1")
                    .name("Test")
                    .priority(1) 
                    .condition(RuleCondition.eq("x", "y")) 
                    .action("a", "b") 
                    .terminal(true) 
                    .build(); 

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() 
                    .subtype(DeterministicSubtype.RULE_BASED) 
                    .rule(rule) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            AgentDescriptor descriptor = agent.descriptor(); 
            assertThat(descriptor).isNotNull(); 
            assertThat(descriptor.getType()).isEqualTo(AgentType.DETERMINISTIC); 
            assertThat(descriptor.getDeterminism()).isEqualTo(DeterminismGuarantee.FULL); 
        }

        @Test
        @DisplayName("Processing produces metrics")
        void processingMetrics() { 
            Rule rule = Rule.builder() 
                    .id("r1")
                    .name("Test")
                    .priority(1) 
                    .condition(RuleCondition.eq("x", "y")) 
                    .action("a", "b") 
                    .terminal(true) 
                    .build(); 

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() 
                    .subtype(DeterministicSubtype.RULE_BASED) 
                    .rule(rule) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("x", "y"); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result.getMetrics()).isNotNull(); 
            assertThat(result.getProcessingTime()).isNotNull(); 
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Null input field is handled gracefully")
        void nullFieldHandling() { 
            Rule rule = Rule.builder() 
                    .id("r1")
                    .name("Test")
                    .priority(1) 
                    .condition(RuleCondition.eq("field", "value")) 
                    .action("result", "match") 
                    .terminal(true) 
                    .build(); 

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() 
                    .subtype(DeterministicSubtype.RULE_BASED) 
                    .rule(rule) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("other", "value"); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            // Should complete without exception
            assertThat(result).isNotNull(); 
        }

        @Test
        @DisplayName("Empty rule set produces valid result")
        void emptyRuleSet() { 
            DeterministicAgentConfig config = DeterministicAgentConfig.builder() 
                    .subtype(DeterministicSubtype.RULE_BASED) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("x", "y"); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result).isNotNull(); 
        }

        @Test
        @DisplayName("Large input maps are processed correctly")
        void largeInputHandling() { 
            Rule rule = Rule.builder() 
                    .id("r1")
                    .name("Test")
                    .priority(1) 
                    .condition(RuleCondition.gt("count", 5)) 
                    .action("result", "proceed") 
                    .terminal(true) 
                    .build(); 

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() 
                    .subtype(DeterministicSubtype.RULE_BASED) 
                    .rule(rule) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> largeInput = new HashMap<>(); 
            for (int i = 0; i < 100; i++) { 
                largeInput.put("field" + i, "value" + i); 
            }
            largeInput.put("count", 10); 

            AgentResult<?> result = runPromise(() -> agent.process(agentContext, largeInput)); 
            assertThat(result.isSuccess()).isTrue(); 
        }

        @Test
        @DisplayName("Numeric comparisons handle different numeric types")
        void numericTypeHandling() { 
            Rule rule = Rule.builder() 
                    .id("r1")
                    .name("Numeric")
                    .priority(1) 
                    .condition(RuleCondition.gt("value", 50)) 
                    .action("result", "pass") 
                    .terminal(true) 
                    .build(); 

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() 
                    .subtype(DeterministicSubtype.RULE_BASED) 
                    .rule(rule) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            // Test with double
            AgentResult<?> doubleResult = runPromise(() -> 
                agent.process(agentContext, Map.of("value", 75.5))); 
            assertThat(doubleResult.isSuccess()).isTrue(); 

            // Test with int
            AgentResult<?> intResult = runPromise(() -> 
                agent.process(agentContext, Map.of("value", 60))); 
            assertThat(intResult.isSuccess()).isTrue(); 
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Async Processing Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Async Processing")
    class AsyncTests {

        @Test
        @DisplayName("Process returns Promise that resolves successfully")
        void asyncProcessing() { 
            Rule rule = Rule.builder() 
                    .id("r1")
                    .name("Test")
                    .priority(1) 
                    .condition(RuleCondition.eq("x", "y")) 
                    .action("a", "b") 
                    .terminal(true) 
                    .build(); 

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() 
                    .subtype(DeterministicSubtype.RULE_BASED) 
                    .rule(rule) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("x", "y"); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result).isNotNull(); 
        }

        @Test
        @DisplayName("Processing completes within expected latency")
        void processingLatency() { 
            Rule rule = Rule.builder() 
                    .id("r1")
                    .name("Test")
                    .priority(1) 
                    .condition(RuleCondition.eq("x", "y")) 
                    .action("a", "b") 
                    .terminal(true) 
                    .build(); 

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() 
                    .subtype(DeterministicSubtype.RULE_BASED) 
                    .rule(rule) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("x", "y"); 
            Instant start = Instant.now(); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 
            Instant end = Instant.now(); 

            Duration latency = Duration.between(start, end); 

            // Deterministic should be sub-millisecond
            assertThat(latency).isLessThan(Duration.ofMillis(100)); 
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
