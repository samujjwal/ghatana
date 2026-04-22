/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 */

package com.ghatana.agent.deterministic;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Nested;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Comprehensive tests for DeterministicAgent and supporting classes.
 */
@DisplayName("Deterministic Agent [GH-90000]")
class DeterministicAgentTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() { // GH-90000
        ctx = AgentContext.builder() // GH-90000
                .turnId("turn-1 [GH-90000]")
                .agentId("test-agent [GH-90000]")
                .tenantId("test-tenant [GH-90000]")
                .memoryStore(mock(MemoryStore.class)) // GH-90000
                .build(); // GH-90000
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper
    // ═══════════════════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════════════════
    // Operator
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Operator [GH-90000]")
    class OperatorTests {

        @Test void greaterThan() { // GH-90000
            assertThat(Operator.GREATER_THAN.evaluate(10, 5)).isTrue(); // GH-90000
            assertThat(Operator.GREATER_THAN.evaluate(5, 10)).isFalse(); // GH-90000
            assertThat(Operator.GREATER_THAN.evaluate(5, 5)).isFalse(); // GH-90000
        }

        @Test void lessThan() { // GH-90000
            assertThat(Operator.LESS_THAN.evaluate(3, 7)).isTrue(); // GH-90000
            assertThat(Operator.LESS_THAN.evaluate(7, 3)).isFalse(); // GH-90000
        }

        @Test void equals() { // GH-90000
            assertThat(Operator.EQUALS.evaluate("abc", "abc")).isTrue(); // GH-90000
            assertThat(Operator.EQUALS.evaluate(42, 42)).isTrue(); // GH-90000
            assertThat(Operator.EQUALS.evaluate("x", "y")).isFalse(); // GH-90000
            assertThat(Operator.EQUALS.evaluate(null, null)).isTrue(); // GH-90000
            assertThat(Operator.EQUALS.evaluate(null, "x")).isFalse(); // GH-90000
        }

        @Test void contains() { // GH-90000
            assertThat(Operator.CONTAINS.evaluate("hello world", "world")).isTrue(); // GH-90000
            assertThat(Operator.CONTAINS.evaluate("hello", "xyz")).isFalse(); // GH-90000
            assertThat(Operator.CONTAINS.evaluate(null, "x")).isFalse(); // GH-90000
        }

        @Test void regex() { // GH-90000
            assertThat(Operator.REGEX.evaluate("error-123", "error-\\d+")).isTrue(); // GH-90000
            assertThat(Operator.REGEX.evaluate("warn-abc", "error-\\d+")).isFalse(); // GH-90000
        }

        @Test void inOperator() { // GH-90000
            assertThat(Operator.IN.evaluate("a", List.of("a", "b", "c"))).isTrue(); // GH-90000
            assertThat(Operator.IN.evaluate("x", List.of("a", "b", "c"))).isFalse(); // GH-90000
        }

        @Test void isNull() { // GH-90000
            assertThat(Operator.IS_NULL.evaluate(null, null)).isTrue(); // GH-90000
            assertThat(Operator.IS_NULL.evaluate("x", null)).isFalse(); // GH-90000
        }

        @Test void numericStringComparison() { // GH-90000
            assertThat(Operator.GREATER_THAN.evaluate("10", "5")).isTrue(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RuleCondition
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("RuleCondition [GH-90000]")
    class RuleConditionTests {

        @Test void evaluatesSimpleField() { // GH-90000
            RuleCondition c = RuleCondition.gt("amount", 100); // GH-90000
            assertThat(c.evaluate(Map.of("amount", 150))).isTrue(); // GH-90000
            assertThat(c.evaluate(Map.of("amount", 50))).isFalse(); // GH-90000
        }

        @Test void evaluatesNestedField() { // GH-90000
            RuleCondition c = RuleCondition.eq("user.country", "US"); // GH-90000
            Map<String, Object> input = Map.of("user", Map.of("country", "US")); // GH-90000
            assertThat(c.evaluate(input)).isTrue(); // GH-90000
        }

        @Test void convenienceFactories() { // GH-90000
            assertThat(RuleCondition.gte("x", 5).evaluate(Map.of("x", 5))).isTrue(); // GH-90000
            assertThat(RuleCondition.lt("x", 5).evaluate(Map.of("x", 3))).isTrue(); // GH-90000
            assertThat(RuleCondition.lte("x", 5).evaluate(Map.of("x", 5))).isTrue(); // GH-90000
            assertThat(RuleCondition.neq("x", "a").evaluate(Map.of("x", "b"))).isTrue(); // GH-90000
            assertThat(RuleCondition.contains("s", "ell").evaluate(Map.of("s", "hello"))).isTrue(); // GH-90000
            assertThat(RuleCondition.regex("s", "^h.*o$").evaluate(Map.of("s", "hello"))).isTrue(); // GH-90000
            assertThat(RuleCondition.isNull("x [GH-90000]").evaluate(Map.of("y", 1))).isTrue();
            assertThat(RuleCondition.isNotNull("x [GH-90000]").evaluate(Map.of("x", 1))).isTrue();
        }

        @Test void resolveMissingFieldReturnsNull() { // GH-90000
            assertThat(RuleCondition.resolve("missing", Map.of())).isNull(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Rule [GH-90000]")
    class RuleTests {

        @Test void matchesWhenAllConditionsMet() { // GH-90000
            Rule rule = Rule.builder() // GH-90000
                    .id("r1 [GH-90000]").name("High Value [GH-90000]")
                    .condition(RuleCondition.gt("amount", 1000)) // GH-90000
                    .condition(RuleCondition.eq("category", "electronics")) // GH-90000
                    .action("action", "REVIEW") // GH-90000
                    .build(); // GH-90000

            Map<String, Object> input = Map.of("amount", 2000, "category", "electronics"); // GH-90000
            assertThat(rule.matches(input)).isTrue(); // GH-90000
        }

        @Test void doesNotMatchWhenOneConditionFails() { // GH-90000
            Rule rule = Rule.builder() // GH-90000
                    .id("r1 [GH-90000]").name("High Value [GH-90000]")
                    .condition(RuleCondition.gt("amount", 1000)) // GH-90000
                    .condition(RuleCondition.eq("category", "electronics")) // GH-90000
                    .action("action", "REVIEW") // GH-90000
                    .build(); // GH-90000

            assertThat(rule.matches(Map.of("amount", 2000, "category", "food"))).isFalse(); // GH-90000
        }

        @Test void emptyRuleNeverMatches() { // GH-90000
            Rule rule = Rule.builder().id("empty [GH-90000]").name("Empty [GH-90000]").build();
            assertThat(rule.matches(Map.of("x", 1))).isFalse(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RuleEngine
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("RuleEngine [GH-90000]")
    class RuleEngineTests {

        @Test void firstMatchMode() { // GH-90000
            Rule r1 = Rule.builder().id("r1 [GH-90000]").name("Low [GH-90000]").priority(10)
                    .condition(RuleCondition.lt("amount", 50)) // GH-90000
                    .action("tier", "LOW").build(); // GH-90000
            Rule r2 = Rule.builder().id("r2 [GH-90000]").name("High [GH-90000]").priority(20)
                    .condition(RuleCondition.gte("amount", 50)) // GH-90000
                    .action("tier", "HIGH").build(); // GH-90000

            RuleEngine engine = RuleEngine.builder().rule(r1).rule(r2).build(); // GH-90000
            var result = engine.evaluate(Map.of("amount", 100)); // GH-90000

            assertThat(result.isMatched()).isTrue(); // GH-90000
            assertThat(result.getMatchedRules()).hasSize(1); // GH-90000
            assertThat(result.getActions().get("tier [GH-90000]")).isEqualTo("HIGH [GH-90000]");
        }

        @Test void allMatchMode() { // GH-90000
            Rule r1 = Rule.builder().id("r1 [GH-90000]").name("A [GH-90000]").priority(10).terminal(false)
                    .condition(RuleCondition.gt("x", 0)) // GH-90000
                    .action("a", true).build(); // GH-90000
            Rule r2 = Rule.builder().id("r2 [GH-90000]").name("B [GH-90000]").priority(20).terminal(false)
                    .condition(RuleCondition.gt("x", 5)) // GH-90000
                    .action("b", true).build(); // GH-90000

            RuleEngine engine = RuleEngine.builder() // GH-90000
                    .rule(r1).rule(r2).evaluateAll(true).build(); // GH-90000
            var result = engine.evaluate(Map.of("x", 10)); // GH-90000

            assertThat(result.isMatched()).isTrue(); // GH-90000
            assertThat(result.getMatchedRules()).hasSize(2); // GH-90000
            assertThat(result.getActions()).containsKeys("a", "b"); // GH-90000
        }

        @Test void noMatchReturnsEmpty() { // GH-90000
            Rule r = Rule.builder().id("r1 [GH-90000]").name("Never [GH-90000]")
                    .condition(RuleCondition.eq("x", "impossible")) // GH-90000
                    .action("a", true).build(); // GH-90000

            RuleEngine engine = RuleEngine.builder().rule(r).build(); // GH-90000
            var result = engine.evaluate(Map.of("x", "actual")); // GH-90000

            assertThat(result.isMatched()).isFalse(); // GH-90000
            assertThat(result.getMatchedRules()).isEmpty(); // GH-90000
        }

        @Test void priorityOrderIsRespected() { // GH-90000
            Rule low = Rule.builder().id("low [GH-90000]").name("Low [GH-90000]").priority(100)
                    .condition(RuleCondition.gt("x", 0)) // GH-90000
                    .action("winner", "low").build(); // GH-90000
            Rule high = Rule.builder().id("high [GH-90000]").name("High [GH-90000]").priority(1)
                    .condition(RuleCondition.gt("x", 0)) // GH-90000
                    .action("winner", "high").build(); // GH-90000

            RuleEngine engine = RuleEngine.builder().rule(low).rule(high).build(); // GH-90000
            var result = engine.evaluate(Map.of("x", 10)); // GH-90000

            assertThat(result.getActions().get("winner [GH-90000]")).isEqualTo("high [GH-90000]");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ThresholdEvaluator
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ThresholdEvaluator [GH-90000]")
    class ThresholdTests {

        @Test void activatesAboveThreshold() { // GH-90000
            ThresholdEvaluator eval = ThresholdEvaluator.builder() // GH-90000
                    .id("cpu [GH-90000]").field("cpu [GH-90000]").activationThreshold(90.0).upperBound(true).build();

            var r = eval.evaluate(Map.of("cpu", 95.0)); // GH-90000
            assertThat(r.isActive()).isTrue(); // GH-90000
            assertThat(r.isStateChanged()).isTrue(); // GH-90000
        }

        @Test void staysInactiveBelow() { // GH-90000
            ThresholdEvaluator eval = ThresholdEvaluator.builder() // GH-90000
                    .id("cpu [GH-90000]").field("cpu [GH-90000]").activationThreshold(90.0).upperBound(true).build();

            var r = eval.evaluate(Map.of("cpu", 80.0)); // GH-90000
            assertThat(r.isActive()).isFalse(); // GH-90000
        }

        @Test void hysteresisPreventsBouncing() { // GH-90000
            ThresholdEvaluator eval = ThresholdEvaluator.builder() // GH-90000
                    .id("cpu [GH-90000]").field("cpu [GH-90000]")
                    .activationThreshold(90.0) // GH-90000
                    .deactivationThreshold(70.0) // GH-90000
                    .upperBound(true).build(); // GH-90000

            // Activate
            eval.evaluate(Map.of("cpu", 95.0)); // GH-90000
            assertThat(eval.getState()).isEqualTo(ThresholdEvaluator.ThresholdState.ACTIVE); // GH-90000

            // Still active at 80 (above deactivation threshold of 70) // GH-90000
            eval.evaluate(Map.of("cpu", 80.0)); // GH-90000
            assertThat(eval.getState()).isEqualTo(ThresholdEvaluator.ThresholdState.ACTIVE); // GH-90000

            // Deactivates below 70
            eval.evaluate(Map.of("cpu", 65.0)); // GH-90000
            assertThat(eval.getState()).isEqualTo(ThresholdEvaluator.ThresholdState.INACTIVE); // GH-90000
        }

        @Test void lowerBoundThreshold() { // GH-90000
            ThresholdEvaluator eval = ThresholdEvaluator.builder() // GH-90000
                    .id("mem [GH-90000]").field("memory [GH-90000]").activationThreshold(10.0)
                    .upperBound(false).build(); // GH-90000

            assertThat(eval.evaluate(Map.of("memory", 5.0)).isActive()).isTrue(); // GH-90000
            eval.reset(); // GH-90000
            assertThat(eval.evaluate(Map.of("memory", 50.0)).isActive()).isFalse(); // GH-90000
        }

        @Test void nestedFieldResolution() { // GH-90000
            ThresholdEvaluator eval = ThresholdEvaluator.builder() // GH-90000
                    .id("nested [GH-90000]").field("system.cpu.usage [GH-90000]")
                    .activationThreshold(90.0).upperBound(true).build(); // GH-90000

            Map<String, Object> input = Map.of("system", // GH-90000
                    Map.of("cpu", Map.of("usage", 95))); // GH-90000
            assertThat(eval.evaluate(input).isActive()).isTrue(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FiniteStateMachine
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("FiniteStateMachine [GH-90000]")
    class FSMTests {

        private FiniteStateMachine createOrderFSM() { // GH-90000
            FiniteStateMachine.FSMDefinition def = FiniteStateMachine.FSMDefinition.builder() // GH-90000
                    .id("order-fsm [GH-90000]")
                    .name("Order Processing [GH-90000]")
                    .state("PENDING [GH-90000]").state("CONFIRMED [GH-90000]").state("SHIPPED [GH-90000]").state("DELIVERED [GH-90000]")
                    .initialState("PENDING [GH-90000]")
                    .finalState("DELIVERED [GH-90000]")
                    .transition(FiniteStateMachine.FSMDefinition.Transition.builder() // GH-90000
                            .name("confirm [GH-90000]")
                            .fromState("PENDING [GH-90000]").toState("CONFIRMED [GH-90000]")
                            .guard(List.of(RuleCondition.eq("action", "confirm"))) // GH-90000
                            .actions(Map.of("event", "ORDER_CONFIRMED")) // GH-90000
                            .build()) // GH-90000
                    .transition(FiniteStateMachine.FSMDefinition.Transition.builder() // GH-90000
                            .name("ship [GH-90000]")
                            .fromState("CONFIRMED [GH-90000]").toState("SHIPPED [GH-90000]")
                            .guard(List.of(RuleCondition.eq("action", "ship"))) // GH-90000
                            .actions(Map.of("event", "ORDER_SHIPPED")) // GH-90000
                            .build()) // GH-90000
                    .transition(FiniteStateMachine.FSMDefinition.Transition.builder() // GH-90000
                            .name("deliver [GH-90000]")
                            .fromState("SHIPPED [GH-90000]").toState("DELIVERED [GH-90000]")
                            .guard(List.of(RuleCondition.eq("action", "deliver"))) // GH-90000
                            .actions(Map.of("event", "ORDER_DELIVERED")) // GH-90000
                            .build()) // GH-90000
                    .build(); // GH-90000
            return new FiniteStateMachine(def); // GH-90000
        }

        @Test void initialStateIsCorrect() { // GH-90000
            FiniteStateMachine fsm = createOrderFSM(); // GH-90000
            assertThat(fsm.getState("order-1 [GH-90000]")).isEqualTo("PENDING [GH-90000]");
        }

        @Test void transitionsOnMatchingGuard() { // GH-90000
            FiniteStateMachine fsm = createOrderFSM(); // GH-90000
            var result = fsm.process("order-1", Map.of("action", "confirm")); // GH-90000

            assertThat(result.isTransitioned()).isTrue(); // GH-90000
            assertThat(result.getPreviousState()).isEqualTo("PENDING [GH-90000]");
            assertThat(result.getCurrentState()).isEqualTo("CONFIRMED [GH-90000]");
            assertThat(result.getTransitionName()).isEqualTo("confirm [GH-90000]");
            assertThat(result.getActions()).containsEntry("event", "ORDER_CONFIRMED"); // GH-90000
        }

        @Test void noTransitionOnUnmatchedGuard() { // GH-90000
            FiniteStateMachine fsm = createOrderFSM(); // GH-90000
            var result = fsm.process("order-1", Map.of("action", "ship")); // GH-90000

            assertThat(result.isTransitioned()).isFalse(); // GH-90000
            assertThat(result.getCurrentState()).isEqualTo("PENDING [GH-90000]");
        }

        @Test void fullLifecycle() { // GH-90000
            FiniteStateMachine fsm = createOrderFSM(); // GH-90000
            String entity = "order-42";

            fsm.process(entity, Map.of("action", "confirm")); // GH-90000
            fsm.process(entity, Map.of("action", "ship")); // GH-90000
            var result = fsm.process(entity, Map.of("action", "deliver")); // GH-90000

            assertThat(result.getCurrentState()).isEqualTo("DELIVERED [GH-90000]");
            assertThat(result.isFinalState()).isTrue(); // GH-90000
        }

        @Test void independentEntities() { // GH-90000
            FiniteStateMachine fsm = createOrderFSM(); // GH-90000
            fsm.process("a", Map.of("action", "confirm")); // GH-90000

            assertThat(fsm.getState("a [GH-90000]")).isEqualTo("CONFIRMED [GH-90000]");
            assertThat(fsm.getState("b [GH-90000]")).isEqualTo("PENDING [GH-90000]");
        }

        @Test void reset() { // GH-90000
            FiniteStateMachine fsm = createOrderFSM(); // GH-90000
            fsm.process("a", Map.of("action", "confirm")); // GH-90000
            fsm.reset("a [GH-90000]");
            assertThat(fsm.getState("a [GH-90000]")).isEqualTo("PENDING [GH-90000]");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DeterministicAgent (Integration) // GH-90000
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DeterministicAgent Integration [GH-90000]")
    class AgentIntegrationTests {

        @Test void ruleBasedAgent() { // GH-90000
            DeterministicAgent agent = new DeterministicAgent("fraud-rules [GH-90000]");

            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .agentId("fraud-rules [GH-90000]")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .rule(Rule.builder().id("high-value [GH-90000]").name("High Value [GH-90000]")
                            .condition(RuleCondition.gt("amount", 10000)) // GH-90000
                            .action("decision", "BLOCK") // GH-90000
                            .action("reason", "High value transaction") // GH-90000
                            .build()) // GH-90000
                    .rule(Rule.builder().id("low-risk [GH-90000]").name("Low Risk [GH-90000]").priority(200)
                            .condition(RuleCondition.lt("amount", 50)) // GH-90000
                            .action("decision", "ALLOW") // GH-90000
                            .build()) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            AgentResult<Map<String, Object>> result =
                    runOnEventloop(() -> agent.process(ctx, Map.of("amount", 15000))); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).containsEntry("decision", "BLOCK"); // GH-90000
        }

        @Test void ruleBasedAgentWithDefault() { // GH-90000
            DeterministicAgent agent = new DeterministicAgent("default-test [GH-90000]");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .agentId("default-test [GH-90000]")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .rule(Rule.builder().id("never [GH-90000]").name("Never [GH-90000]")
                            .condition(RuleCondition.eq("nope", "nope")) // GH-90000
                            .action("x", 1).build()) // GH-90000
                    .defaultAction("decision", "ALLOW") // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("amount", 500))); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).containsEntry("decision", "ALLOW"); // GH-90000
        }

        @Test void thresholdAgent() { // GH-90000
            DeterministicAgent agent = new DeterministicAgent("threshold-test [GH-90000]");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .agentId("threshold-test [GH-90000]")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .subtype(DeterministicSubtype.THRESHOLD) // GH-90000
                    .threshold(ThresholdEvaluator.builder() // GH-90000
                            .id("cpu [GH-90000]").field("cpu [GH-90000]").activationThreshold(90.0)
                            .upperBound(true).build()) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("cpu", 95.0))); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput().get("threshold.cpu.active [GH-90000]")).isEqualTo(true);
        }

        @Test void fsmAgent() { // GH-90000
            FiniteStateMachine.FSMDefinition def = FiniteStateMachine.FSMDefinition.builder() // GH-90000
                    .id("simple [GH-90000]").name("Simple [GH-90000]")
                    .state("A [GH-90000]").state("B [GH-90000]")
                    .initialState("A [GH-90000]").finalState("B [GH-90000]")
                    .transition(FiniteStateMachine.FSMDefinition.Transition.builder() // GH-90000
                            .name("go [GH-90000]").fromState("A [GH-90000]").toState("B [GH-90000]")
                            .guard(List.of(RuleCondition.eq("action", "go"))) // GH-90000
                            .actions(Map.of("result", "transitioned")) // GH-90000
                            .build()) // GH-90000
                    .build(); // GH-90000

            DeterministicAgent agent = new DeterministicAgent("fsm-test [GH-90000]");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .agentId("fsm-test [GH-90000]")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .subtype(DeterministicSubtype.FSM) // GH-90000
                    .fsmDefinition(def) // GH-90000
                    .fsmEntityKeyField("entityId [GH-90000]")
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            var result = runOnEventloop(() -> // GH-90000
                    agent.process(ctx, Map.of("entityId", "e1", "action", "go"))); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput().get("result [GH-90000]")).isEqualTo("transitioned [GH-90000]");
            assertThat(result.getOutput().get("_fsm.currentState [GH-90000]")).isEqualTo("B [GH-90000]");
        }

        @Test void exactMatchAgent() { // GH-90000
            DeterministicAgent agent = new DeterministicAgent("exact-test [GH-90000]");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .agentId("exact-test [GH-90000]")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .subtype(DeterministicSubtype.EXACT_MATCH) // GH-90000
                    .exactMatchField("country [GH-90000]")
                    .exactMatchEntry("US", Map.of("region", "NA", "currency", "USD")) // GH-90000
                    .exactMatchEntry("JP", Map.of("region", "APAC", "currency", "JPY")) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("country", "US"))); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).containsEntry("region", "NA"); // GH-90000
        }

        @Test void exactMatchMissReturnsSkippedOrDefault() { // GH-90000
            DeterministicAgent agent = new DeterministicAgent("exact-miss [GH-90000]");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .agentId("exact-miss [GH-90000]")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .subtype(DeterministicSubtype.EXACT_MATCH) // GH-90000
                    .exactMatchField("country [GH-90000]")
                    .exactMatchEntry("US", Map.of("region", "NA")) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("country", "XX"))); // GH-90000

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SKIPPED); // GH-90000
        }

        @Test void lifecycleMetrics() { // GH-90000
            DeterministicAgent agent = new DeterministicAgent("metrics-test [GH-90000]");
            DeterministicAgentConfig config = DeterministicAgentConfig.builder() // GH-90000
                    .agentId("metrics-test [GH-90000]")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                    .rule(Rule.builder().id("r1 [GH-90000]").name("Always [GH-90000]")
                            .condition(RuleCondition.isNotNull("x [GH-90000]"))
                            .action("ok", true).build()) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000
            runOnEventloop(() -> agent.process(ctx, Map.of("x", 2))); // GH-90000

            assertThat(agent.getTotalInvocations()).isEqualTo(2); // GH-90000
            assertThat(agent.getSuccessCount()).isEqualTo(2); // GH-90000
        }
    }
}
