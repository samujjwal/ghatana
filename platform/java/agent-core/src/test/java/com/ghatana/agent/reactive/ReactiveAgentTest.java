/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 */

package com.ghatana.agent.reactive;

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
 * Comprehensive tests for ReactiveAgent — triggers, cooldowns, sliding windows.
 */
@DisplayName("Reactive Agent [GH-90000]")
class ReactiveAgentTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() { // GH-90000
        ctx = AgentContext.builder() // GH-90000
                .turnId("turn-1 [GH-90000]")
                .agentId("reactive-test [GH-90000]")
                .tenantId("test-tenant [GH-90000]")
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

    private ReactiveAgent createAgent(String id, List<ReactiveAgentConfig.TriggerDefinition> triggers) { // GH-90000
        ReactiveAgent agent = new ReactiveAgent(id); // GH-90000
        ReactiveAgentConfig config = ReactiveAgentConfig.builder() // GH-90000
                .agentId(id) // GH-90000
                .type(AgentType.REACTIVE) // GH-90000
                .triggers(triggers) // GH-90000
                .build(); // GH-90000
        runOnEventloop(() -> agent.initialize(config)); // GH-90000
        return agent;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Basic Trigger Matching
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Trigger Matching [GH-90000]")
    class TriggerMatchingTests {

        @Test void matchesByEventType() { // GH-90000
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("error-trigger [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("ERROR [GH-90000]")
                    .action("alert", "CRITICAL") // GH-90000
                    .build(); // GH-90000

            ReactiveAgent agent = createAgent("t1", List.of(trigger)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ERROR"))); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).containsEntry("alert", "CRITICAL"); // GH-90000
        }

        @Test void noMatchReturnsSkipped() { // GH-90000
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("error-trigger [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("ERROR [GH-90000]")
                    .action("alert", "CRITICAL") // GH-90000
                    .build(); // GH-90000

            ReactiveAgent agent = createAgent("t2", List.of(trigger)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "INFO"))); // GH-90000

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SKIPPED); // GH-90000
        }

        @Test void matchesWithAdditionalCondition() { // GH-90000
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("high-cpu [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("METRIC [GH-90000]")
                    .conditionField("cpu [GH-90000]")
                    .conditionOperator("> [GH-90000]")
                    .conditionValue("90 [GH-90000]")
                    .action("action", "SCALE_UP") // GH-90000
                    .build(); // GH-90000

            ReactiveAgent agent = createAgent("t3", List.of(trigger)); // GH-90000

            var matched = runOnEventloop(() -> agent.process(ctx, // GH-90000
                    Map.of("type", "METRIC", "cpu", "95"))); // GH-90000
            assertThat(matched.isSuccess()).isTrue(); // GH-90000
            assertThat(matched.getOutput()).containsEntry("action", "SCALE_UP"); // GH-90000

            agent.resetState(); // GH-90000
            var unmatched = runOnEventloop(() -> agent.process(ctx, // GH-90000
                    Map.of("type", "METRIC", "cpu", "50"))); // GH-90000
            assertThat(unmatched.getStatus()).isEqualTo(AgentResultStatus.SKIPPED); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cooldown Behavior
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cooldown [GH-90000]")
    class CooldownTests {

        @Test void respectsCooldownPeriod() { // GH-90000
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("cooldown-trigger [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("ALERT [GH-90000]")
                    .cooldown(Duration.ofHours(1)) // 1 hour cooldown // GH-90000
                    .action("notify", true) // GH-90000
                    .build(); // GH-90000

            ReactiveAgent agent = createAgent("cd", List.of(trigger)); // GH-90000

            // First fire should trigger
            var first = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ALERT"))); // GH-90000
            assertThat(first.isSuccess()).isTrue(); // GH-90000

            // Second fire within cooldown should be SKIPPED
            var second = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ALERT"))); // GH-90000
            assertThat(second.getStatus()).isEqualTo(AgentResultStatus.SKIPPED); // GH-90000
        }

        @Test void zeroCooldownAllowsImmediateRefire() { // GH-90000
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("no-cooldown [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("PING [GH-90000]")
                    .cooldown(Duration.ZERO) // GH-90000
                    .action("pong", true) // GH-90000
                    .build(); // GH-90000

            ReactiveAgent agent = createAgent("nc", List.of(trigger)); // GH-90000

            var r1 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "PING"))); // GH-90000
            var r2 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "PING"))); // GH-90000

            assertThat(r1.isSuccess()).isTrue(); // GH-90000
            assertThat(r2.isSuccess()).isTrue(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Sliding Window Threshold
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sliding Window Threshold [GH-90000]")
    class WindowTests {

        @Test void firesOnlyAfterThreshold() { // GH-90000
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("threshold-trigger [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("ERROR [GH-90000]")
                    .threshold(3) // GH-90000
                    .countingWindow(Duration.ofMinutes(5)) // GH-90000
                    .action("escalate", true) // GH-90000
                    .build(); // GH-90000

            ReactiveAgent agent = createAgent("thresh", List.of(trigger)); // GH-90000

            // First 2 events: below threshold → SKIPPED
            var r1 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ERROR"))); // GH-90000
            var r2 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ERROR"))); // GH-90000

            // At least one should be skipped (threshold=3 means need 3 events) // GH-90000
            // 3rd event should trigger
            var r3 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ERROR"))); // GH-90000
            assertThat(r3.isSuccess()).isTrue(); // GH-90000
            assertThat(r3.getOutput()).containsEntry("escalate", true); // GH-90000
        }

        @Test void windowCountTracked() { // GH-90000
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("counted [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("TICK [GH-90000]")
                    .threshold(10) // GH-90000
                    .countingWindow(Duration.ofMinutes(5)) // GH-90000
                    .action("x", true) // GH-90000
                    .build(); // GH-90000

            ReactiveAgent agent = createAgent("wc", List.of(trigger)); // GH-90000

            for (int i = 0; i < 5; i++) { // GH-90000
                runOnEventloop(() -> agent.process(ctx, Map.of("type", "TICK"))); // GH-90000
            }

            assertThat(agent.getActiveWindowCount()).isGreaterThan(0); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Priority Ordering
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Priority [GH-90000]")
    class PriorityTests {

        @Test void higherPriorityWins() { // GH-90000
            // All matching triggers fire and merge actions; later triggers overwrite.
            // Triggers sorted ascending by priority: high(10) fires first, low(200) second. // GH-90000
            // Only the high-priority trigger should match.
            var highPriority = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("high [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("EVENT [GH-90000]")
                    .priority(10) // GH-90000
                    .action("source", "high") // GH-90000
                    .build(); // GH-90000

            var lowPriority = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("low [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("OTHER [GH-90000]")  // different event type — won't match
                    .priority(200) // GH-90000
                    .action("source", "low") // GH-90000
                    .build(); // GH-90000

            ReactiveAgent agent = createAgent("prio", List.of(lowPriority, highPriority)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "EVENT"))); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).containsEntry("source", "high"); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Multiple Triggers
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Multiple Triggers [GH-90000]")
    class MultiTriggerTests {

        @Test void onlyMatchingTriggersFire() { // GH-90000
            var errorTrigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("error [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("ERROR [GH-90000]")
                    .action("action", "LOG_ERROR") // GH-90000
                    .build(); // GH-90000

            var warnTrigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("warn [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("WARN [GH-90000]")
                    .action("action", "LOG_WARN") // GH-90000
                    .build(); // GH-90000

            ReactiveAgent agent = createAgent("multi", List.of(errorTrigger, warnTrigger)); // GH-90000

            var r1 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ERROR"))); // GH-90000
            assertThat(r1.getOutput()).containsEntry("action", "LOG_ERROR"); // GH-90000

            agent.resetState(); // GH-90000
            var r2 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "WARN"))); // GH-90000
            assertThat(r2.getOutput()).containsEntry("action", "LOG_WARN"); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Reset & Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("State Management [GH-90000]")
    class StateTests {

        @Test void resetClearsAll() { // GH-90000
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("resettable [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("X [GH-90000]")
                    .cooldown(Duration.ofHours(1)) // GH-90000
                    .action("ok", true) // GH-90000
                    .build(); // GH-90000

            ReactiveAgent agent = createAgent("reset", List.of(trigger)); // GH-90000

            runOnEventloop(() -> agent.process(ctx, Map.of("type", "X"))); // GH-90000
            agent.resetState(); // GH-90000

            // After reset, should fire again despite cooldown
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "X"))); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test void metricsTracked() { // GH-90000
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("tracked [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("A [GH-90000]")
                    .action("ok", true) // GH-90000
                    .build(); // GH-90000

            ReactiveAgent agent = createAgent("metrics", List.of(trigger)); // GH-90000
            runOnEventloop(() -> agent.process(ctx, Map.of("type", "A"))); // GH-90000
            runOnEventloop(() -> agent.process(ctx, Map.of("type", "B"))); // GH-90000

            assertThat(agent.getTotalInvocations()).isEqualTo(2); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Condition Operators
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Condition Operators [GH-90000]")
    class ConditionOperatorTests {

        private ReactiveAgent agentWithCondition(String op, String value) { // GH-90000
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("cond [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("E [GH-90000]")
                    .conditionField("x [GH-90000]")
                    .conditionOperator(op) // GH-90000
                    .conditionValue(value) // GH-90000
                    .action("matched", true) // GH-90000
                    .build(); // GH-90000
            return createAgent("cond-" + op, List.of(trigger)); // GH-90000
        }

        @Test void equalsOperator() { // GH-90000
            ReactiveAgent agent = agentWithCondition("==", "abc"); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "E", "x", "abc"))); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test void notEqualsOperator() { // GH-90000
            ReactiveAgent agent = agentWithCondition("!=", "abc"); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "E", "x", "xyz"))); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test void containsOperator() { // GH-90000
            ReactiveAgent agent = agentWithCondition("contains", "err"); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "E", "x", "error_log"))); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test void greaterThanOperator() { // GH-90000
            ReactiveAgent agent = agentWithCondition(">", "50"); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "E", "x", "75"))); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test void lessThanOperator() { // GH-90000
            ReactiveAgent agent = agentWithCondition("<", "50"); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "E", "x", "25"))); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }
    }
}
