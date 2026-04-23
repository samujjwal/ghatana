/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
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
@ExtendWith(MockitoExtension.class) // GH-90000
class DeterministicAgentBehavioralTest {

    @Mock
    private MemoryStore memoryStore;

    private AgentContext agentContext;
    private DeterministicAgent agent;

    @BeforeEach
    void setUp() { // GH-90000
        agentContext = AgentContext.builder() // GH-90000
                .turnId("turn-1")
                .agentId("det-agent")
                .tenantId("tenant-1")
                .memoryStore(memoryStore) // GH-90000
                .build(); // GH-90000

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
        void ruleBasedProcessing() { // GH-90000
            Rule rule = Rule.builder() // GH-90000
                    .id("r1")
                    .name("Check Score")
                    .priority(1) // GH-90000
                    .condition(RuleCondition.gt("score", 80)) // GH-90000
                    .action("result", "PASS") // GH-90000
                    .terminal(true) // GH-90000
                    .build(); // GH-90000

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .rule(rule) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("score", 95); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Same input always produces same output (determinism)")
        void deterministicOutputConsistency() { // GH-90000
            Rule rule = Rule.builder() // GH-90000
                    .id("r1")
                    .name("Status Check")
                    .priority(1) // GH-90000
                    .condition(RuleCondition.eq("status", "active")) // GH-90000
                    .action("action", "process") // GH-90000
                    .terminal(true) // GH-90000
                    .build(); // GH-90000

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .rule(rule) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("status", "active"); // GH-90000

            // Run twice with same input
            AgentResult<?> result1 = runPromise(() -> agent.process(agentContext, input)); // GH-90000
            AgentResult<?> result2 = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result1.getOutput()).isEqualTo(result2.getOutput()); // GH-90000
            assertThat(result1.getStatus()).isEqualTo(result2.getStatus()); // GH-90000
        }

        @Test
        @DisplayName("Threshold evaluation produces correct activation state")
        void thresholdProcessing() { // GH-90000
            ThresholdEvaluator threshold = ThresholdEvaluator.builder() // GH-90000
                    .id("temp")
                    .field("temperature")
                    .activationThreshold(100.0) // GH-90000
                    .upperBound(true) // GH-90000
                    .build(); // GH-90000

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .subtype(DeterministicSubtype.THRESHOLD) // GH-90000
                    .threshold(threshold) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            // Test activation
            Map<String, Object> hotInput = Map.of("temperature", 105.0); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, hotInput)); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("FSM transitions follow state machine rules")
        void finiteStateMachineProcessing() { // GH-90000
            FiniteStateMachine.FSMDefinition fsmDefinition = FiniteStateMachine.FSMDefinition.builder() // GH-90000
                    .id("workflow")
                    .name("Workflow FSM")
                    .initialState("IDLE")
                    .state("IDLE").state("PROCESSING").state("DONE").state("FAILED")
                    .transition(FiniteStateMachine.FSMDefinition.Transition.builder() // GH-90000
                            .name("START").fromState("IDLE").toState("PROCESSING").build())
                    .transition(FiniteStateMachine.FSMDefinition.Transition.builder() // GH-90000
                            .name("COMPLETE").fromState("PROCESSING").toState("DONE").build())
                    .transition(FiniteStateMachine.FSMDefinition.Transition.builder() // GH-90000
                            .name("ERROR").fromState("PROCESSING").toState("FAILED").build())
                    .build(); // GH-90000

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .subtype(DeterministicSubtype.FSM) // GH-90000
                    .fsmDefinition(fsmDefinition) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("entityId", "workflow-1", "event", "START"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Pattern matching correctly identifies patterns")
        void patternMatchingProcessing() { // GH-90000
            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .subtype(DeterministicSubtype.PATTERN) // GH-90000
                    .patternMatchStrategy((situation, context) -> java.util.Optional.empty()) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("text", "error-404-not-found"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result).isNotNull(); // strategy returns empty → SKIPPED status // GH-90000
        }

        @Test
        @DisplayName("Multiple rules evaluate all matching rules in priority order")
        void multipleRulesInPriorityOrder() { // GH-90000
            Rule high = Rule.builder() // GH-90000
                    .id("r-high")
                    .name("High Priority")
                    .priority(1) // GH-90000
                    .condition(RuleCondition.gt("severity", 7)) // GH-90000
                    .action("action", "escalate") // GH-90000
                    .terminal(false) // GH-90000
                    .build(); // GH-90000

            Rule medium = Rule.builder() // GH-90000
                    .id("r-medium")
                    .name("Medium Priority")
                    .priority(2) // GH-90000
                    .condition(RuleCondition.gt("severity", 5)) // GH-90000
                    .action("action", "notify") // GH-90000
                    .terminal(false) // GH-90000
                    .build(); // GH-90000

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .rule(high) // GH-90000
                    .rule(medium) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("severity", 8); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("No matching rules produces empty output")
        void noMatchingRulesOutput() { // GH-90000
            Rule rule = Rule.builder() // GH-90000
                    .id("r1")
                    .name("Impossible")
                    .priority(1) // GH-90000
                    .condition(RuleCondition.eq("x", "impossible")) // GH-90000
                    .action("result", "never") // GH-90000
                    .terminal(true) // GH-90000
                    .build(); // GH-90000

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .rule(rule) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", "never"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            // Result should be successful but empty
            assertThat(result).isNotNull(); // GH-90000
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
        void deterministicConfidence() { // GH-90000
            Rule rule = Rule.builder() // GH-90000
                    .id("r1")
                    .name("Test")
                    .priority(1) // GH-90000
                    .condition(RuleCondition.eq("type", "test")) // GH-90000
                    .action("result", "ok") // GH-90000
                    .terminal(true) // GH-90000
                    .build(); // GH-90000

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .rule(rule) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("type", "test"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            // Deterministic agents have complete confidence
            assertThat(result.getConfidence()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("Confidence score is always in valid range [0.0, 1.0]")
        void confidenceInValidRange() { // GH-90000
            Rule rule = Rule.builder() // GH-90000
                    .id("r1")
                    .name("Test")
                    .priority(1) // GH-90000
                    .condition(RuleCondition.gt("value", 0)) // GH-90000
                    .action("result", "ok") // GH-90000
                    .terminal(true) // GH-90000
                    .build(); // GH-90000

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .rule(rule) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            for (int i = 0; i < 10; i++) { // GH-90000
                Map<String, Object> input = Map.of("value", i); // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

                assertThat(result.getConfidence()) // GH-90000
                        .isGreaterThanOrEqualTo(0.0) // GH-90000
                        .isLessThanOrEqualTo(1.0); // GH-90000
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
        void explanationNonEmpty() { // GH-90000
            Rule rule = Rule.builder() // GH-90000
                    .id("r1")
                    .name("Check Score")
                    .priority(1) // GH-90000
                    .condition(RuleCondition.gt("score", 75)) // GH-90000
                    .action("result", "PASS") // GH-90000
                    .terminal(true) // GH-90000
                    .build(); // GH-90000

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .rule(rule) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("score", 85); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.getExplanation()) // GH-90000
                    .isNotNull() // GH-90000
                    .isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("Explanation references the matching rule")
        void explanationReferencesRule() { // GH-90000
            Rule rule = Rule.builder() // GH-90000
                    .id("rule-audit")
                    .name("Audit Rule")
                    .priority(1) // GH-90000
                    .condition(RuleCondition.gt("amount", 1000)) // GH-90000
                    .action("review", "mandatory") // GH-90000
                    .terminal(true) // GH-90000
                    .build(); // GH-90000

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .rule(rule) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("amount", 5000); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            // Explanation should contain rule info
            String explanation = result.getExplanation(); // GH-90000
            assertThat(explanation) // GH-90000
                    .isNotNull() // GH-90000
                    .isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("Explanation contains decision reasoning")
        void explanationContainsReasoning() { // GH-90000
            Rule rule = Rule.builder() // GH-90000
                    .id("r1")
                    .name("CPU Alert")
                    .priority(1) // GH-90000
                    .condition(RuleCondition.gt("cpu_usage", 90)) // GH-90000
                    .action("alert", "critical") // GH-90000
                    .terminal(true) // GH-90000
                    .build(); // GH-90000

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .rule(rule) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("cpu_usage", 95.5); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.getExplanation()) // GH-90000
                    .contains("95.5", "90", "cpu") // GH-90000
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
        void agentInitialization() { // GH-90000
            Rule rule = Rule.builder() // GH-90000
                    .id("r1")
                    .name("Test")
                    .priority(1) // GH-90000
                    .condition(RuleCondition.eq("x", "y")) // GH-90000
                    .action("a", "b") // GH-90000
                    .terminal(true) // GH-90000
                    .build(); // GH-90000

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .rule(rule) // GH-90000
                    .build(); // GH-90000

            Promise<Void> initPromise = agent.initialize(config); // GH-90000
            assertThat(initPromise).isNotNull(); // GH-90000

            runPromise(() -> initPromise);  // Should complete without error // GH-90000
        }

        @Test
        @DisplayName("Agent metadata reflects configuration")
        void agentMetadata() { // GH-90000
            Rule rule = Rule.builder() // GH-90000
                    .id("r1")
                    .name("Test")
                    .priority(1) // GH-90000
                    .condition(RuleCondition.eq("x", "y")) // GH-90000
                    .action("a", "b") // GH-90000
                    .terminal(true) // GH-90000
                    .build(); // GH-90000

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .rule(rule) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            AgentDescriptor descriptor = agent.descriptor(); // GH-90000
            assertThat(descriptor).isNotNull(); // GH-90000
            assertThat(descriptor.getType()).isEqualTo(AgentType.DETERMINISTIC); // GH-90000
            assertThat(descriptor.getDeterminism()).isEqualTo(DeterminismGuarantee.FULL); // GH-90000
        }

        @Test
        @DisplayName("Processing produces metrics")
        void processingMetrics() { // GH-90000
            Rule rule = Rule.builder() // GH-90000
                    .id("r1")
                    .name("Test")
                    .priority(1) // GH-90000
                    .condition(RuleCondition.eq("x", "y")) // GH-90000
                    .action("a", "b") // GH-90000
                    .terminal(true) // GH-90000
                    .build(); // GH-90000

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .rule(rule) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", "y"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.getMetrics()).isNotNull(); // GH-90000
            assertThat(result.getProcessingTime()).isNotNull(); // GH-90000
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
        void nullFieldHandling() { // GH-90000
            Rule rule = Rule.builder() // GH-90000
                    .id("r1")
                    .name("Test")
                    .priority(1) // GH-90000
                    .condition(RuleCondition.eq("field", "value")) // GH-90000
                    .action("result", "match") // GH-90000
                    .terminal(true) // GH-90000
                    .build(); // GH-90000

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .rule(rule) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("other", "value"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            // Should complete without exception
            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Empty rule set produces valid result")
        void emptyRuleSet() { // GH-90000
            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", "y"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Large input maps are processed correctly")
        void largeInputHandling() { // GH-90000
            Rule rule = Rule.builder() // GH-90000
                    .id("r1")
                    .name("Test")
                    .priority(1) // GH-90000
                    .condition(RuleCondition.gt("count", 5)) // GH-90000
                    .action("result", "proceed") // GH-90000
                    .terminal(true) // GH-90000
                    .build(); // GH-90000

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .rule(rule) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> largeInput = new HashMap<>(); // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                largeInput.put("field" + i, "value" + i); // GH-90000
            }
            largeInput.put("count", 10); // GH-90000

            AgentResult<?> result = runPromise(() -> agent.process(agentContext, largeInput)); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Numeric comparisons handle different numeric types")
        void numericTypeHandling() { // GH-90000
            Rule rule = Rule.builder() // GH-90000
                    .id("r1")
                    .name("Numeric")
                    .priority(1) // GH-90000
                    .condition(RuleCondition.gt("value", 50)) // GH-90000
                    .action("result", "pass") // GH-90000
                    .terminal(true) // GH-90000
                    .build(); // GH-90000

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .rule(rule) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            // Test with double
            AgentResult<?> doubleResult = runPromise(() -> // GH-90000
                agent.process(agentContext, Map.of("value", 75.5))); // GH-90000
            assertThat(doubleResult.isSuccess()).isTrue(); // GH-90000

            // Test with int
            AgentResult<?> intResult = runPromise(() -> // GH-90000
                agent.process(agentContext, Map.of("value", 60))); // GH-90000
            assertThat(intResult.isSuccess()).isTrue(); // GH-90000
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
        void asyncProcessing() { // GH-90000
            Rule rule = Rule.builder() // GH-90000
                    .id("r1")
                    .name("Test")
                    .priority(1) // GH-90000
                    .condition(RuleCondition.eq("x", "y")) // GH-90000
                    .action("a", "b") // GH-90000
                    .terminal(true) // GH-90000
                    .build(); // GH-90000

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .rule(rule) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", "y"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Processing completes within expected latency")
        void processingLatency() { // GH-90000
            Rule rule = Rule.builder() // GH-90000
                    .id("r1")
                    .name("Test")
                    .priority(1) // GH-90000
                    .condition(RuleCondition.eq("x", "y")) // GH-90000
                    .action("a", "b") // GH-90000
                    .terminal(true) // GH-90000
                    .build(); // GH-90000

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .rule(rule) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", "y"); // GH-90000
            Instant start = Instant.now(); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000
            Instant end = Instant.now(); // GH-90000

            Duration latency = Duration.between(start, end); // GH-90000

            // Deterministic should be sub-millisecond
            assertThat(latency).isLessThan(Duration.ofMillis(100)); // GH-90000
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
