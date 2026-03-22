/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.deterministic;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Nested;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Comprehensive tests for DeterministicAgent and supporting classes.
 */
@DisplayName("Deterministic Agent")
class DeterministicAgentTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() {
        ctx = AgentContext.builder()
                .turnId("turn-1")
                .agentId("test-agent")
                .tenantId("test-tenant")
                .memoryStore(mock(MemoryStore.class))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper
    // ═══════════════════════════════════════════════════════════════════════════

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
    // Operator
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Operator")
    class OperatorTests {

        @Test void greaterThan() {
            assertThat(Operator.GREATER_THAN.evaluate(10, 5)).isTrue();
            assertThat(Operator.GREATER_THAN.evaluate(5, 10)).isFalse();
            assertThat(Operator.GREATER_THAN.evaluate(5, 5)).isFalse();
        }

        @Test void lessThan() {
            assertThat(Operator.LESS_THAN.evaluate(3, 7)).isTrue();
            assertThat(Operator.LESS_THAN.evaluate(7, 3)).isFalse();
        }

        @Test void equals() {
            assertThat(Operator.EQUALS.evaluate("abc", "abc")).isTrue();
            assertThat(Operator.EQUALS.evaluate(42, 42)).isTrue();
            assertThat(Operator.EQUALS.evaluate("x", "y")).isFalse();
            assertThat(Operator.EQUALS.evaluate(null, null)).isTrue();
            assertThat(Operator.EQUALS.evaluate(null, "x")).isFalse();
        }

        @Test void contains() {
            assertThat(Operator.CONTAINS.evaluate("hello world", "world")).isTrue();
            assertThat(Operator.CONTAINS.evaluate("hello", "xyz")).isFalse();
            assertThat(Operator.CONTAINS.evaluate(null, "x")).isFalse();
        }

        @Test void regex() {
            assertThat(Operator.REGEX.evaluate("error-123", "error-\\d+")).isTrue();
            assertThat(Operator.REGEX.evaluate("warn-abc", "error-\\d+")).isFalse();
        }

        @Test void inOperator() {
            assertThat(Operator.IN.evaluate("a", List.of("a", "b", "c"))).isTrue();
            assertThat(Operator.IN.evaluate("x", List.of("a", "b", "c"))).isFalse();
        }

        @Test void isNull() {
            assertThat(Operator.IS_NULL.evaluate(null, null)).isTrue();
            assertThat(Operator.IS_NULL.evaluate("x", null)).isFalse();
        }

        @Test void numericStringComparison() {
            assertThat(Operator.GREATER_THAN.evaluate("10", "5")).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RuleCondition
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("RuleCondition")
    class RuleConditionTests {

        @Test void evaluatesSimpleField() {
            RuleCondition c = RuleCondition.gt("amount", 100);
            assertThat(c.evaluate(Map.of("amount", 150))).isTrue();
            assertThat(c.evaluate(Map.of("amount", 50))).isFalse();
        }

        @Test void evaluatesNestedField() {
            RuleCondition c = RuleCondition.eq("user.country", "US");
            Map<String, Object> input = Map.of("user", Map.of("country", "US"));
            assertThat(c.evaluate(input)).isTrue();
        }

        @Test void convenienceFactories() {
            assertThat(RuleCondition.gte("x", 5).evaluate(Map.of("x", 5))).isTrue();
            assertThat(RuleCondition.lt("x", 5).evaluate(Map.of("x", 3))).isTrue();
            assertThat(RuleCondition.lte("x", 5).evaluate(Map.of("x", 5))).isTrue();
            assertThat(RuleCondition.neq("x", "a").evaluate(Map.of("x", "b"))).isTrue();
            assertThat(RuleCondition.contains("s", "ell").evaluate(Map.of("s", "hello"))).isTrue();
            assertThat(RuleCondition.regex("s", "^h.*o$").evaluate(Map.of("s", "hello"))).isTrue();
            assertThat(RuleCondition.isNull("x").evaluate(Map.of("y", 1))).isTrue();
            assertThat(RuleCondition.isNotNull("x").evaluate(Map.of("x", 1))).isTrue();
        }

        @Test void resolveMissingFieldReturnsNull() {
            assertThat(RuleCondition.resolve("missing", Map.of())).isNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Rule")
    class RuleTests {

        @Test void matchesWhenAllConditionsMet() {
            Rule rule = Rule.builder()
                    .id("r1").name("High Value")
                    .condition(RuleCondition.gt("amount", 1000))
                    .condition(RuleCondition.eq("category", "electronics"))
                    .action("action", "REVIEW")
                    .build();

            Map<String, Object> input = Map.of("amount", 2000, "category", "electronics");
            assertThat(rule.matches(input)).isTrue();
        }

        @Test void doesNotMatchWhenOneConditionFails() {
            Rule rule = Rule.builder()
                    .id("r1").name("High Value")
                    .condition(RuleCondition.gt("amount", 1000))
                    .condition(RuleCondition.eq("category", "electronics"))
                    .action("action", "REVIEW")
                    .build();

            assertThat(rule.matches(Map.of("amount", 2000, "category", "food"))).isFalse();
        }

        @Test void emptyRuleNeverMatches() {
            Rule rule = Rule.builder().id("empty").name("Empty").build();
            assertThat(rule.matches(Map.of("x", 1))).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RuleEngine
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("RuleEngine")
    class RuleEngineTests {

        @Test void firstMatchMode() {
            Rule r1 = Rule.builder().id("r1").name("Low").priority(10)
                    .condition(RuleCondition.lt("amount", 50))
                    .action("tier", "LOW").build();
            Rule r2 = Rule.builder().id("r2").name("High").priority(20)
                    .condition(RuleCondition.gte("amount", 50))
                    .action("tier", "HIGH").build();

            RuleEngine engine = RuleEngine.builder().rule(r1).rule(r2).build();
            var result = engine.evaluate(Map.of("amount", 100));

            assertThat(result.isMatched()).isTrue();
            assertThat(result.getMatchedRules()).hasSize(1);
            assertThat(result.getActions().get("tier")).isEqualTo("HIGH");
        }

        @Test void allMatchMode() {
            Rule r1 = Rule.builder().id("r1").name("A").priority(10).terminal(false)
                    .condition(RuleCondition.gt("x", 0))
                    .action("a", true).build();
            Rule r2 = Rule.builder().id("r2").name("B").priority(20).terminal(false)
                    .condition(RuleCondition.gt("x", 5))
                    .action("b", true).build();

            RuleEngine engine = RuleEngine.builder()
                    .rule(r1).rule(r2).evaluateAll(true).build();
            var result = engine.evaluate(Map.of("x", 10));

            assertThat(result.isMatched()).isTrue();
            assertThat(result.getMatchedRules()).hasSize(2);
            assertThat(result.getActions()).containsKeys("a", "b");
        }

        @Test void noMatchReturnsEmpty() {
            Rule r = Rule.builder().id("r1").name("Never")
                    .condition(RuleCondition.eq("x", "impossible"))
                    .action("a", true).build();

            RuleEngine engine = RuleEngine.builder().rule(r).build();
            var result = engine.evaluate(Map.of("x", "actual"));

            assertThat(result.isMatched()).isFalse();
            assertThat(result.getMatchedRules()).isEmpty();
        }

        @Test void priorityOrderIsRespected() {
            Rule low = Rule.builder().id("low").name("Low").priority(100)
                    .condition(RuleCondition.gt("x", 0))
                    .action("winner", "low").build();
            Rule high = Rule.builder().id("high").name("High").priority(1)
                    .condition(RuleCondition.gt("x", 0))
                    .action("winner", "high").build();

            RuleEngine engine = RuleEngine.builder().rule(low).rule(high).build();
            var result = engine.evaluate(Map.of("x", 10));

            assertThat(result.getActions().get("winner")).isEqualTo("high");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ThresholdEvaluator
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ThresholdEvaluator")
    class ThresholdTests {

        @Test void activatesAboveThreshold() {
            ThresholdEvaluator eval = ThresholdEvaluator.builder()
                    .id("cpu").field("cpu").activationThreshold(90.0).upperBound(true).build();

            var r = eval.evaluate(Map.of("cpu", 95.0));
            assertThat(r.isActive()).isTrue();
            assertThat(r.isStateChanged()).isTrue();
        }

        @Test void staysInactiveBelow() {
            ThresholdEvaluator eval = ThresholdEvaluator.builder()
                    .id("cpu").field("cpu").activationThreshold(90.0).upperBound(true).build();

            var r = eval.evaluate(Map.of("cpu", 80.0));
            assertThat(r.isActive()).isFalse();
        }

        @Test void hysteresisPreventsBouncing() {
            ThresholdEvaluator eval = ThresholdEvaluator.builder()
                    .id("cpu").field("cpu")
                    .activationThreshold(90.0)
                    .deactivationThreshold(70.0)
                    .upperBound(true).build();

            // Activate
            eval.evaluate(Map.of("cpu", 95.0));
            assertThat(eval.getState()).isEqualTo(ThresholdEvaluator.ThresholdState.ACTIVE);

            // Still active at 80 (above deactivation threshold of 70)
            eval.evaluate(Map.of("cpu", 80.0));
            assertThat(eval.getState()).isEqualTo(ThresholdEvaluator.ThresholdState.ACTIVE);

            // Deactivates below 70
            eval.evaluate(Map.of("cpu", 65.0));
            assertThat(eval.getState()).isEqualTo(ThresholdEvaluator.ThresholdState.INACTIVE);
        }

        @Test void lowerBoundThreshold() {
            ThresholdEvaluator eval = ThresholdEvaluator.builder()
                    .id("mem").field("memory").activationThreshold(10.0)
                    .upperBound(false).build();

            assertThat(eval.evaluate(Map.of("memory", 5.0)).isActive()).isTrue();
            eval.reset();
            assertThat(eval.evaluate(Map.of("memory", 50.0)).isActive()).isFalse();
        }

        @Test void nestedFieldResolution() {
            ThresholdEvaluator eval = ThresholdEvaluator.builder()
                    .id("nested").field("system.cpu.usage")
                    .activationThreshold(90.0).upperBound(true).build();

            Map<String, Object> input = Map.of("system",
                    Map.of("cpu", Map.of("usage", 95)));
            assertThat(eval.evaluate(input).isActive()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FiniteStateMachine
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("FiniteStateMachine")
    class FSMTests {

        private FiniteStateMachine createOrderFSM() {
            FiniteStateMachine.FSMDefinition def = FiniteStateMachine.FSMDefinition.builder()
                    .id("order-fsm")
                    .name("Order Processing")
                    .state("PENDING").state("CONFIRMED").state("SHIPPED").state("DELIVERED")
                    .initialState("PENDING")
                    .finalState("DELIVERED")
                    .transition(FiniteStateMachine.FSMDefinition.Transition.builder()
                            .name("confirm")
                            .fromState("PENDING").toState("CONFIRMED")
                            .guard(List.of(RuleCondition.eq("action", "confirm")))
                            .actions(Map.of("event", "ORDER_CONFIRMED"))
                            .build())
                    .transition(FiniteStateMachine.FSMDefinition.Transition.builder()
                            .name("ship")
                            .fromState("CONFIRMED").toState("SHIPPED")
                            .guard(List.of(RuleCondition.eq("action", "ship")))
                            .actions(Map.of("event", "ORDER_SHIPPED"))
                            .build())
                    .transition(FiniteStateMachine.FSMDefinition.Transition.builder()
                            .name("deliver")
                            .fromState("SHIPPED").toState("DELIVERED")
                            .guard(List.of(RuleCondition.eq("action", "deliver")))
                            .actions(Map.of("event", "ORDER_DELIVERED"))
                            .build())
                    .build();
            return new FiniteStateMachine(def);
        }

        @Test void initialStateIsCorrect() {
            FiniteStateMachine fsm = createOrderFSM();
            assertThat(fsm.getState("order-1")).isEqualTo("PENDING");
        }

        @Test void transitionsOnMatchingGuard() {
            FiniteStateMachine fsm = createOrderFSM();
            var result = fsm.process("order-1", Map.of("action", "confirm"));

            assertThat(result.isTransitioned()).isTrue();
            assertThat(result.getPreviousState()).isEqualTo("PENDING");
            assertThat(result.getCurrentState()).isEqualTo("CONFIRMED");
            assertThat(result.getTransitionName()).isEqualTo("confirm");
            assertThat(result.getActions()).containsEntry("event", "ORDER_CONFIRMED");
        }

        @Test void noTransitionOnUnmatchedGuard() {
            FiniteStateMachine fsm = createOrderFSM();
            var result = fsm.process("order-1", Map.of("action", "ship"));

            assertThat(result.isTransitioned()).isFalse();
            assertThat(result.getCurrentState()).isEqualTo("PENDING");
        }

        @Test void fullLifecycle() {
            FiniteStateMachine fsm = createOrderFSM();
            String entity = "order-42";

            fsm.process(entity, Map.of("action", "confirm"));
            fsm.process(entity, Map.of("action", "ship"));
            var result = fsm.process(entity, Map.of("action", "deliver"));

            assertThat(result.getCurrentState()).isEqualTo("DELIVERED");
            assertThat(result.isFinalState()).isTrue();
        }

        @Test void independentEntities() {
            FiniteStateMachine fsm = createOrderFSM();
            fsm.process("a", Map.of("action", "confirm"));

            assertThat(fsm.getState("a")).isEqualTo("CONFIRMED");
            assertThat(fsm.getState("b")).isEqualTo("PENDING");
        }

        @Test void reset() {
            FiniteStateMachine fsm = createOrderFSM();
            fsm.process("a", Map.of("action", "confirm"));
            fsm.reset("a");
            assertThat(fsm.getState("a")).isEqualTo("PENDING");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DeterministicAgent (Integration)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DeterministicAgent Integration")
    class AgentIntegrationTests {

        @Test void ruleBasedAgent() {
            DeterministicAgent agent = new DeterministicAgent("fraud-rules");

            DeterministicAgentConfig config = DeterministicAgentConfig.builder()
                    .agentId("fraud-rules")
                    .type(AgentType.DETERMINISTIC)
                    .subtype(DeterministicSubtype.RULE_BASED)
                    .rule(Rule.builder().id("high-value").name("High Value")
                            .condition(RuleCondition.gt("amount", 10000))
                            .action("decision", "BLOCK")
                            .action("reason", "High value transaction")
                            .build())
                    .rule(Rule.builder().id("low-risk").name("Low Risk").priority(200)
                            .condition(RuleCondition.lt("amount", 50))
                            .action("decision", "ALLOW")
                            .build())
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            AgentResult<Map<String, Object>> result =
                    runOnEventloop(() -> agent.process(ctx, Map.of("amount", 15000)));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("decision", "BLOCK");
        }

        @Test void ruleBasedAgentWithDefault() {
            DeterministicAgent agent = new DeterministicAgent("default-test");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder()
                    .agentId("default-test")
                    .type(AgentType.DETERMINISTIC)
                    .subtype(DeterministicSubtype.RULE_BASED)
                    .rule(Rule.builder().id("never").name("Never")
                            .condition(RuleCondition.eq("nope", "nope"))
                            .action("x", 1).build())
                    .defaultAction("decision", "ALLOW")
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("amount", 500)));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("decision", "ALLOW");
        }

        @Test void thresholdAgent() {
            DeterministicAgent agent = new DeterministicAgent("threshold-test");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder()
                    .agentId("threshold-test")
                    .type(AgentType.DETERMINISTIC)
                    .subtype(DeterministicSubtype.THRESHOLD)
                    .threshold(ThresholdEvaluator.builder()
                            .id("cpu").field("cpu").activationThreshold(90.0)
                            .upperBound(true).build())
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("cpu", 95.0)));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("threshold.cpu.active")).isEqualTo(true);
        }

        @Test void fsmAgent() {
            FiniteStateMachine.FSMDefinition def = FiniteStateMachine.FSMDefinition.builder()
                    .id("simple").name("Simple")
                    .state("A").state("B")
                    .initialState("A").finalState("B")
                    .transition(FiniteStateMachine.FSMDefinition.Transition.builder()
                            .name("go").fromState("A").toState("B")
                            .guard(List.of(RuleCondition.eq("action", "go")))
                            .actions(Map.of("result", "transitioned"))
                            .build())
                    .build();

            DeterministicAgent agent = new DeterministicAgent("fsm-test");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder()
                    .agentId("fsm-test")
                    .type(AgentType.DETERMINISTIC)
                    .subtype(DeterministicSubtype.FSM)
                    .fsmDefinition(def)
                    .fsmEntityKeyField("entityId")
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            var result = runOnEventloop(() ->
                    agent.process(ctx, Map.of("entityId", "e1", "action", "go")));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("result")).isEqualTo("transitioned");
            assertThat(result.getOutput().get("_fsm.currentState")).isEqualTo("B");
        }

        @Test void exactMatchAgent() {
            DeterministicAgent agent = new DeterministicAgent("exact-test");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder()
                    .agentId("exact-test")
                    .type(AgentType.DETERMINISTIC)
                    .subtype(DeterministicSubtype.EXACT_MATCH)
                    .exactMatchField("country")
                    .exactMatchEntry("US", Map.of("region", "NA", "currency", "USD"))
                    .exactMatchEntry("JP", Map.of("region", "APAC", "currency", "JPY"))
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("country", "US")));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("region", "NA");
        }

        @Test void exactMatchMissReturnsSkippedOrDefault() {
            DeterministicAgent agent = new DeterministicAgent("exact-miss");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder()
                    .agentId("exact-miss")
                    .type(AgentType.DETERMINISTIC)
                    .subtype(DeterministicSubtype.EXACT_MATCH)
                    .exactMatchField("country")
                    .exactMatchEntry("US", Map.of("region", "NA"))
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("country", "XX")));

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SKIPPED);
        }

        @Test void lifecycleMetrics() {
            DeterministicAgent agent = new DeterministicAgent("metrics-test");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder()
                    .agentId("metrics-test")
                    .type(AgentType.DETERMINISTIC)
                    .subtype(DeterministicSubtype.RULE_BASED)
                    .rule(Rule.builder().id("r1").name("Always")
                            .condition(RuleCondition.isNotNull("x"))
                            .action("ok", true).build())
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));
            runOnEventloop(() -> agent.process(ctx, Map.of("x", 2)));

            assertThat(agent.getTotalInvocations()).isEqualTo(2);
            assertThat(agent.getSuccessCount()).isEqualTo(2);
        }
    }
}
