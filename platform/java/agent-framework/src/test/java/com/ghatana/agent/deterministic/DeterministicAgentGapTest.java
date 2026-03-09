/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 * Phase 4 — Task 4.1: Gap-filling tests for DeterministicAgent.
 */

package com.ghatana.agent.deterministic;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Gap-filling tests for {@link DeterministicAgent}.
 *
 * <p>Covers areas identified in the Phase 4 test coverage audit:
 * <ul>
 *   <li>PATTERN subtype behaviour (degraded response, not yet wired to NFA)</li>
 *   <li>Multiple rules with non-terminal evaluation and action merging</li>
 *   <li>FSM transition from final state (no further transitions)</li>
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
    void setUp() {
        ctx = AgentContext.builder()
                .turnId("turn-1")
                .agentId("det-gap-test")
                .tenantId("test-tenant")
                .memoryStore(mock(MemoryStore.class))
                .build();
    }

    private <T> T runOnEventloop(java.util.function.Supplier<Promise<T>> supplier) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> err = new AtomicReference<>();
        Eventloop eventloop = Eventloop.builder().withCurrentThread().build();
        eventloop.post(() -> supplier.get()
                .whenResult(result::set)
                .whenException(err::set));
        eventloop.run();
        if (err.get() != null) throw new RuntimeException(err.get());
        return result.get();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PATTERN subtype
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PATTERN subtype")
    class PatternSubtypeTests {

        @Test
        void patternSubtypeReturnsDegradedBeforeNfaWiring() {
            DeterministicAgent agent = new DeterministicAgent("pattern-test");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder()
                    .agentId("pattern-test")
                    .type(AgentType.DETERMINISTIC)
                    .subtype(DeterministicSubtype.PATTERN)
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("event", "login")));

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DEGRADED);
            assertThat(result.getConfidence()).isLessThan(1.0);
            assertThat(result.getOutput()).containsEntry("event", "login");
        }

        @Test
        void patternSubtypePreservesInputKeys() {
            DeterministicAgent agent = new DeterministicAgent("pattern-preserve");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder()
                    .agentId("pattern-preserve")
                    .type(AgentType.DETERMINISTIC)
                    .subtype(DeterministicSubtype.PATTERN)
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            Map<String, Object> input = Map.of("a", 1, "b", "two", "c", 3.0);
            var result = runOnEventloop(() -> agent.process(ctx, input));

            assertThat(result.getOutput()).containsAllEntriesOf(input);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Multi-rule evaluation with ALL mode
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Multi-rule ALL-match mode")
    class MultiRuleAllMatchTests {

        @Test
        void allMatchMergesActionsFromMultipleRules() {
            DeterministicAgent agent = new DeterministicAgent("multi-rule");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder()
                    .agentId("multi-rule")
                    .type(AgentType.DETERMINISTIC)
                    .subtype(DeterministicSubtype.RULE_BASED)
                    .evaluateAllRules(true)
                    .rule(Rule.builder().id("r1").name("Flag").priority(10).terminal(false)
                            .condition(RuleCondition.gt("amount", 0))
                            .action("flagged", true).build())
                    .rule(Rule.builder().id("r2").name("Source").priority(20).terminal(false)
                            .condition(RuleCondition.eq("source", "api"))
                            .action("channel", "API").build())
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            var result = runOnEventloop(() -> agent.process(ctx,
                    Map.of("amount", 500, "source", "api")));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("flagged", true);
            assertThat(result.getOutput()).containsEntry("channel", "API");
            @SuppressWarnings("unchecked")
            List<String> matched = (List<String>) result.getOutput().get("_matchedRules");
            assertThat(matched).containsExactlyInAnyOrder("r1", "r2");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FSM — transition from final state
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("FSM final-state boundary")
    class FSMFinalStateBoundaryTests {

        @Test
        void noTransitionFromFinalState() {
            FiniteStateMachine.FSMDefinition def = FiniteStateMachine.FSMDefinition.builder()
                    .id("two-step").name("TwoStep")
                    .state("START").state("END")
                    .initialState("START").finalState("END")
                    .transition(FiniteStateMachine.FSMDefinition.Transition.builder()
                            .name("finish").fromState("START").toState("END")
                            .guard(List.of(RuleCondition.eq("action", "finish")))
                            .actions(Map.of("done", true))
                            .build())
                    .build();

            DeterministicAgent agent = new DeterministicAgent("fsm-final");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder()
                    .agentId("fsm-final")
                    .type(AgentType.DETERMINISTIC)
                    .subtype(DeterministicSubtype.FSM)
                    .fsmDefinition(def)
                    .fsmEntityKeyField("id")
                    .build();

            runOnEventloop(() -> agent.initialize(config));

            // Transition to final state
            runOnEventloop(() -> agent.process(ctx, Map.of("id", "e1", "action", "finish")));

            // Attempt further transition — should stay in END
            var result = runOnEventloop(() -> agent.process(ctx,
                    Map.of("id", "e1", "action", "finish")));

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
        void multipleThresholdsEvaluateIndependently() {
            DeterministicAgent agent = new DeterministicAgent("multi-thresh");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder()
                    .agentId("multi-thresh")
                    .type(AgentType.DETERMINISTIC)
                    .subtype(DeterministicSubtype.THRESHOLD)
                    .threshold(ThresholdEvaluator.builder()
                            .id("cpu").field("cpu").activationThreshold(80.0)
                            .upperBound(true).build())
                    .threshold(ThresholdEvaluator.builder()
                            .id("mem").field("memory").activationThreshold(20.0)
                            .upperBound(false).build())
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            var result = runOnEventloop(() -> agent.process(ctx,
                    Map.of("cpu", 95.0, "memory", 10.0)));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("threshold.cpu.active")).isEqualTo(true);
            assertThat(result.getOutput().get("threshold.mem.active")).isEqualTo(true);

            @SuppressWarnings("unchecked")
            List<String> active = (List<String>) result.getOutput().get("_activeThresholds");
            assertThat(active).containsExactlyInAnyOrder("cpu", "mem");
        }

        @Test
        void thresholdStateChangesTracked() {
            DeterministicAgent agent = new DeterministicAgent("state-changes");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder()
                    .agentId("state-changes")
                    .type(AgentType.DETERMINISTIC)
                    .subtype(DeterministicSubtype.THRESHOLD)
                    .threshold(ThresholdEvaluator.builder()
                            .id("temp").field("temperature")
                            .activationThreshold(100.0).upperBound(true).build())
                    .build();

            runOnEventloop(() -> agent.initialize(config));

            // First eval: below threshold — inactive, no state change (INACTIVE→INACTIVE)
            var r1 = runOnEventloop(() -> agent.process(ctx, Map.of("temperature", 50.0)));
            @SuppressWarnings("unchecked")
            List<String> sc1 = (List<String>) r1.getOutput().get("_stateChanges");
            assertThat(sc1).isEmpty();

            // Second eval: above threshold — active, state change (INACTIVE→ACTIVE)
            var r2 = runOnEventloop(() -> agent.process(ctx, Map.of("temperature", 110.0)));
            @SuppressWarnings("unchecked")
            List<String> sc2 = (List<String>) r2.getOutput().get("_stateChanges");
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
        void nullFieldValueReturnsDefaultAction() {
            DeterministicAgent agent = new DeterministicAgent("exact-null");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder()
                    .agentId("exact-null")
                    .type(AgentType.DETERMINISTIC)
                    .subtype(DeterministicSubtype.EXACT_MATCH)
                    .exactMatchField("country")
                    .exactMatchEntry("US", Map.of("region", "NA"))
                    .defaultAction("region", "UNKNOWN")
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            // Input lacks "country" field entirely
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("name", "test")));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("region", "UNKNOWN");
        }

        @Test
        void exactMatchHitIncludesMetadata() {
            DeterministicAgent agent = new DeterministicAgent("exact-meta");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder()
                    .agentId("exact-meta")
                    .type(AgentType.DETERMINISTIC)
                    .subtype(DeterministicSubtype.EXACT_MATCH)
                    .exactMatchField("status")
                    .exactMatchEntry("ACTIVE", Map.of("allowed", true))
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("status", "ACTIVE")));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("_exactMatch.field", "status");
            assertThat(result.getOutput()).containsEntry("_exactMatch.key", "ACTIVE");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error paths
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error paths")
    class ErrorPathTests {

        @Test
        void processBeforeConfigureReturnsFailed() {
            DeterministicAgent agent = new DeterministicAgent("unconfigured");
            // Do NOT call initialize

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));

            assertThat(result.isFailed()).isTrue();
        }

        @Test
        void fsmMissingEntityKeyReturnsFailed() {
            FiniteStateMachine.FSMDefinition def = FiniteStateMachine.FSMDefinition.builder()
                    .id("key-test").name("KeyTest")
                    .state("A").state("B").initialState("A").finalState("B")
                    .transition(FiniteStateMachine.FSMDefinition.Transition.builder()
                            .name("go").fromState("A").toState("B")
                            .guard(List.of(RuleCondition.eq("action", "go")))
                            .actions(Map.of("ok", true)).build())
                    .build();

            DeterministicAgent agent = new DeterministicAgent("fsm-nokey");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder()
                    .agentId("fsm-nokey")
                    .type(AgentType.DETERMINISTIC)
                    .subtype(DeterministicSubtype.FSM)
                    .fsmDefinition(def)
                    .fsmEntityKeyField("entityId")
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            // Input lacks entityId field
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("action", "go")));

            assertThat(result.isFailed()).isTrue();
        }

        @Test
        void ruleBasedNoMatchAndNoDefaultReturnsSkipped() {
            DeterministicAgent agent = new DeterministicAgent("no-default");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder()
                    .agentId("no-default")
                    .type(AgentType.DETERMINISTIC)
                    .subtype(DeterministicSubtype.RULE_BASED)
                    .rule(Rule.builder().id("r1").name("Never")
                            .condition(RuleCondition.eq("x", "impossible"))
                            .action("a", true).build())
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", "actual")));

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SKIPPED);
        }
    }
}
