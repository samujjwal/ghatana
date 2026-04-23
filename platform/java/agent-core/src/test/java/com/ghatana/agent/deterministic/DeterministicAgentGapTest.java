/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 * Phase 4 — Task 4.1: Gap-filling tests for DeterministicAgent.
 */

package com.ghatana.agent.deterministic;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Gap-filling tests for {@link DeterministicAgent}.
 *
 * <p>Covers areas identified in the Phase 4 test coverage audit:
 * <ul>
 *   <li>PATTERN subtype behaviour (degraded response, not yet wired to NFA)</li> // GH-90000
 *   <li>Multiple rules with non-terminal evaluation and action merging</li>
 *   <li>FSM transition from final state (no further transitions)</li> // GH-90000
 *   <li>Threshold agent with multiple evaluators</li>
 *   <li>Exact match with null field value and default actions</li>
 *   <li>Agent not configured error path</li>
 *   <li>Wrong config type rejection</li>
 * </ul>
 */
@DisplayName("Deterministic Agent — Gap Tests")
class DeterministicAgentGapTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() { // GH-90000
        ctx = AgentContext.builder() // GH-90000
                .turnId("turn-1")
                .agentId("det-gap-test")
                .tenantId("test-tenant")
                .memoryStore(mock(MemoryStore.class)) // GH-90000
                .build(); // GH-90000
    }

    private <T> T runOnEventloop(java.util.function.Supplier<Promise<T>> supplier) { // GH-90000
        AtomicReference<T> result = new AtomicReference<>(); // GH-90000
        AtomicReference<Exception> err = new AtomicReference<>(); // GH-90000
        Eventloop eventloop = Eventloop.builder().withCurrentThread().build(); // GH-90000
        eventloop.post(() -> supplier.get() // GH-90000
                .whenResult(result::set) // GH-90000
                .whenException(err::set)); // GH-90000
        eventloop.run(); // GH-90000
        if (err.get() != null) throw new RuntimeException(err.get()); // GH-90000
        return result.get(); // GH-90000
    }

    private static List<String> outputStringList(Map<String, Object> output, String key) {
        assertThat(output.get(key)).isInstanceOf(List.class);
        List<?> values = (List<?>) output.get(key);
        assertThat(values).allMatch(String.class::isInstance);
        return values.stream().map(String.class::cast).toList();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PATTERN subtype
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PATTERN subtype")
    class PatternSubtypeTests {

        @Test
        void patternSubtypeReturnsDegradedBeforeNfaWiring() { // GH-90000
            DeterministicAgent agent = new DeterministicAgent("pattern-test");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .agentId("pattern-test")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .subtype(DeterministicSubtype.PATTERN) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("event", "login"))); // GH-90000

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DEGRADED); // GH-90000
            assertThat(result.getConfidence()).isLessThan(1.0); // GH-90000
            assertThat(result.getOutput()).containsEntry("event", "login"); // GH-90000
        }

        @Test
        void patternSubtypePreservesInputKeys() { // GH-90000
            DeterministicAgent agent = new DeterministicAgent("pattern-preserve");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .agentId("pattern-preserve")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .subtype(DeterministicSubtype.PATTERN) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            Map<String, Object> input = Map.of("a", 1, "b", "two", "c", 3.0); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, input)); // GH-90000

            assertThat(result.getOutput()).containsAllEntriesOf(input); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Multi-rule evaluation with ALL mode
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Multi-rule ALL-match mode")
    class MultiRuleAllMatchTests {

        @Test
        void allMatchMergesActionsFromMultipleRules() { // GH-90000
            DeterministicAgent agent = new DeterministicAgent("multi-rule");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .agentId("multi-rule")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .evaluateAllRules(true) // GH-90000
                    .rule(Rule.builder().id("r1").name("Flag").priority(10).terminal(false)
                            .condition(RuleCondition.gt("amount", 0)) // GH-90000
                            .action("flagged", true).build()) // GH-90000
                    .rule(Rule.builder().id("r2").name("Source").priority(20).terminal(false)
                            .condition(RuleCondition.eq("source", "api")) // GH-90000
                            .action("channel", "API").build()) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, // GH-90000
                    Map.of("amount", 500, "source", "api"))); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).containsEntry("flagged", true); // GH-90000
            assertThat(result.getOutput()).containsEntry("channel", "API"); // GH-90000
            List<String> matched = outputStringList(result.getOutput(), "_matchedRules");
            assertThat(matched).containsExactlyInAnyOrder("r1", "r2"); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FSM — transition from final state
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("FSM final-state boundary")
    class FSMFinalStateBoundaryTests {

        @Test
        void noTransitionFromFinalState() { // GH-90000
            FiniteStateMachine.FSMDefinition def = FiniteStateMachine.FSMDefinition.builder() // GH-90000
                    .id("two-step").name("TwoStep")
                    .state("START").state("END")
                    .initialState("START").finalState("END")
                    .transition(FiniteStateMachine.FSMDefinition.Transition.builder() // GH-90000
                            .name("finish").fromState("START").toState("END")
                            .guard(List.of(RuleCondition.eq("action", "finish"))) // GH-90000
                            .actions(Map.of("done", true)) // GH-90000
                            .build()) // GH-90000
                    .build(); // GH-90000

            DeterministicAgent agent = new DeterministicAgent("fsm-final");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .agentId("fsm-final")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .subtype(DeterministicSubtype.FSM) // GH-90000
                    .fsmDefinition(def) // GH-90000
                    .fsmEntityKeyField("id")
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000

            // Transition to final state
            runOnEventloop(() -> agent.process(ctx, Map.of("id", "e1", "action", "finish"))); // GH-90000

            // Attempt further transition — should stay in END
            var result = runOnEventloop(() -> agent.process(ctx, // GH-90000
                    Map.of("id", "e1", "action", "finish"))); // GH-90000

            assertThat(result.getOutput().get("_fsm.currentState")).isEqualTo("END");
            assertThat(result.getOutput().get("_fsm.transitioned")).isEqualTo(false);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Threshold — multiple evaluators
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Threshold — multiple evaluators")
    class MultiThresholdTests {

        @Test
        void multipleThresholdsEvaluateIndependently() { // GH-90000
            DeterministicAgent agent = new DeterministicAgent("multi-thresh");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .agentId("multi-thresh")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .subtype(DeterministicSubtype.THRESHOLD) // GH-90000
                    .threshold(ThresholdEvaluator.builder() // GH-90000
                            .id("cpu").field("cpu").activationThreshold(80.0)
                            .upperBound(true).build()) // GH-90000
                    .threshold(ThresholdEvaluator.builder() // GH-90000
                            .id("mem").field("memory").activationThreshold(20.0)
                            .upperBound(false).build()) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, // GH-90000
                    Map.of("cpu", 95.0, "memory", 10.0))); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput().get("threshold.cpu.active")).isEqualTo(true);
            assertThat(result.getOutput().get("threshold.mem.active")).isEqualTo(true);

            List<String> active = outputStringList(result.getOutput(), "_activeThresholds");
            assertThat(active).containsExactlyInAnyOrder("cpu", "mem"); // GH-90000
        }

        @Test
        void thresholdStateChangesTracked() { // GH-90000
            DeterministicAgent agent = new DeterministicAgent("state-changes");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .agentId("state-changes")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .subtype(DeterministicSubtype.THRESHOLD) // GH-90000
                    .threshold(ThresholdEvaluator.builder() // GH-90000
                            .id("temp").field("temperature")
                            .activationThreshold(100.0).upperBound(true).build()) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000

            // First eval: below threshold — inactive, no state change (INACTIVE→INACTIVE) // GH-90000
            var r1 = runOnEventloop(() -> agent.process(ctx, Map.of("temperature", 50.0))); // GH-90000
            List<String> sc1 = outputStringList(r1.getOutput(), "_stateChanges");
            assertThat(sc1).isEmpty(); // GH-90000

            // Second eval: above threshold — active, state change (INACTIVE→ACTIVE) // GH-90000
            var r2 = runOnEventloop(() -> agent.process(ctx, Map.of("temperature", 110.0))); // GH-90000
            List<String> sc2 = outputStringList(r2.getOutput(), "_stateChanges");
            assertThat(sc2).contains("temp");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ExactMatch — null field with default action
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ExactMatch — edge cases")
    class ExactMatchEdgeCaseTests {

        @Test
        void nullFieldValueReturnsDefaultAction() { // GH-90000
            DeterministicAgent agent = new DeterministicAgent("exact-null");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .agentId("exact-null")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .subtype(DeterministicSubtype.EXACT_MATCH) // GH-90000
                    .exactMatchField("country")
                    .exactMatchEntry("US", Map.of("region", "NA")) // GH-90000
                    .defaultAction("region", "UNKNOWN") // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            // Input lacks "country" field entirely
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("name", "test"))); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).containsEntry("region", "UNKNOWN"); // GH-90000
        }

        @Test
        void exactMatchHitIncludesMetadata() { // GH-90000
            DeterministicAgent agent = new DeterministicAgent("exact-meta");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .agentId("exact-meta")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .subtype(DeterministicSubtype.EXACT_MATCH) // GH-90000
                    .exactMatchField("status")
                    .exactMatchEntry("ACTIVE", Map.of("allowed", true)) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("status", "ACTIVE"))); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).containsEntry("_exactMatch.field", "status"); // GH-90000
            assertThat(result.getOutput()).containsEntry("_exactMatch.key", "ACTIVE"); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error paths
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error paths")
    class ErrorPathTests {

        @Test
        void processBeforeConfigureReturnsFailed() { // GH-90000
            DeterministicAgent agent = new DeterministicAgent("unconfigured");
            // Do NOT call initialize

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000

            assertThat(result.isFailed()).isTrue(); // GH-90000
        }

        @Test
        void fsmMissingEntityKeyReturnsFailed() { // GH-90000
            FiniteStateMachine.FSMDefinition def = FiniteStateMachine.FSMDefinition.builder() // GH-90000
                    .id("key-test").name("KeyTest")
                    .state("A").state("B").initialState("A").finalState("B")
                    .transition(FiniteStateMachine.FSMDefinition.Transition.builder() // GH-90000
                            .name("go").fromState("A").toState("B")
                            .guard(List.of(RuleCondition.eq("action", "go"))) // GH-90000
                            .actions(Map.of("ok", true)).build()) // GH-90000
                    .build(); // GH-90000

            DeterministicAgent agent = new DeterministicAgent("fsm-nokey");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .agentId("fsm-nokey")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .subtype(DeterministicSubtype.FSM) // GH-90000
                    .fsmDefinition(def) // GH-90000
                    .fsmEntityKeyField("entityId")
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            // Input lacks entityId field
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("action", "go"))); // GH-90000

            assertThat(result.isFailed()).isTrue(); // GH-90000
        }

        @Test
        void ruleBasedNoMatchAndNoDefaultReturnsSkipped() { // GH-90000
            DeterministicAgent agent = new DeterministicAgent("no-default");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .agentId("no-default")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .rule(Rule.builder().id("r1").name("Never")
                            .condition(RuleCondition.eq("x", "impossible")) // GH-90000
                            .action("a", true).build()) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", "actual"))); // GH-90000

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SKIPPED); // GH-90000
        }
    }
}
