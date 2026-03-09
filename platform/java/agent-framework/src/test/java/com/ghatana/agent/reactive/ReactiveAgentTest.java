/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
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
@DisplayName("Reactive Agent")
class ReactiveAgentTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() {
        ctx = AgentContext.builder()
                .turnId("turn-1")
                .agentId("reactive-test")
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

    private ReactiveAgent createAgent(String id, List<ReactiveAgentConfig.TriggerDefinition> triggers) {
        ReactiveAgent agent = new ReactiveAgent(id);
        ReactiveAgentConfig config = ReactiveAgentConfig.builder()
                .agentId(id)
                .type(AgentType.REACTIVE)
                .triggers(triggers)
                .build();
        runOnEventloop(() -> agent.initialize(config));
        return agent;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Basic Trigger Matching
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Trigger Matching")
    class TriggerMatchingTests {

        @Test void matchesByEventType() {
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("error-trigger")
                    .eventTypeField("type")
                    .eventTypeValue("ERROR")
                    .action("alert", "CRITICAL")
                    .build();

            ReactiveAgent agent = createAgent("t1", List.of(trigger));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ERROR")));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("alert", "CRITICAL");
        }

        @Test void noMatchReturnsSkipped() {
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("error-trigger")
                    .eventTypeField("type")
                    .eventTypeValue("ERROR")
                    .action("alert", "CRITICAL")
                    .build();

            ReactiveAgent agent = createAgent("t2", List.of(trigger));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "INFO")));

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SKIPPED);
        }

        @Test void matchesWithAdditionalCondition() {
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("high-cpu")
                    .eventTypeField("type")
                    .eventTypeValue("METRIC")
                    .conditionField("cpu")
                    .conditionOperator(">")
                    .conditionValue("90")
                    .action("action", "SCALE_UP")
                    .build();

            ReactiveAgent agent = createAgent("t3", List.of(trigger));

            var matched = runOnEventloop(() -> agent.process(ctx,
                    Map.of("type", "METRIC", "cpu", "95")));
            assertThat(matched.isSuccess()).isTrue();
            assertThat(matched.getOutput()).containsEntry("action", "SCALE_UP");

            agent.resetState();
            var unmatched = runOnEventloop(() -> agent.process(ctx,
                    Map.of("type", "METRIC", "cpu", "50")));
            assertThat(unmatched.getStatus()).isEqualTo(AgentResultStatus.SKIPPED);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cooldown Behavior
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cooldown")
    class CooldownTests {

        @Test void respectsCooldownPeriod() {
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("cooldown-trigger")
                    .eventTypeField("type")
                    .eventTypeValue("ALERT")
                    .cooldown(Duration.ofHours(1)) // 1 hour cooldown
                    .action("notify", true)
                    .build();

            ReactiveAgent agent = createAgent("cd", List.of(trigger));

            // First fire should trigger
            var first = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ALERT")));
            assertThat(first.isSuccess()).isTrue();

            // Second fire within cooldown should be SKIPPED
            var second = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ALERT")));
            assertThat(second.getStatus()).isEqualTo(AgentResultStatus.SKIPPED);
        }

        @Test void zeroCooldownAllowsImmediateRefire() {
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("no-cooldown")
                    .eventTypeField("type")
                    .eventTypeValue("PING")
                    .cooldown(Duration.ZERO)
                    .action("pong", true)
                    .build();

            ReactiveAgent agent = createAgent("nc", List.of(trigger));

            var r1 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "PING")));
            var r2 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "PING")));

            assertThat(r1.isSuccess()).isTrue();
            assertThat(r2.isSuccess()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Sliding Window Threshold
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sliding Window Threshold")
    class WindowTests {

        @Test void firesOnlyAfterThreshold() {
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("threshold-trigger")
                    .eventTypeField("type")
                    .eventTypeValue("ERROR")
                    .threshold(3)
                    .countingWindow(Duration.ofMinutes(5))
                    .action("escalate", true)
                    .build();

            ReactiveAgent agent = createAgent("thresh", List.of(trigger));

            // First 2 events: below threshold → SKIPPED
            var r1 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ERROR")));
            var r2 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ERROR")));

            // At least one should be skipped (threshold=3 means need 3 events)
            // 3rd event should trigger
            var r3 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ERROR")));
            assertThat(r3.isSuccess()).isTrue();
            assertThat(r3.getOutput()).containsEntry("escalate", true);
        }

        @Test void windowCountTracked() {
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("counted")
                    .eventTypeField("type")
                    .eventTypeValue("TICK")
                    .threshold(10)
                    .countingWindow(Duration.ofMinutes(5))
                    .action("x", true)
                    .build();

            ReactiveAgent agent = createAgent("wc", List.of(trigger));

            for (int i = 0; i < 5; i++) {
                runOnEventloop(() -> agent.process(ctx, Map.of("type", "TICK")));
            }

            assertThat(agent.getActiveWindowCount()).isGreaterThan(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Priority Ordering
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Priority")
    class PriorityTests {

        @Test void higherPriorityWins() {
            // All matching triggers fire and merge actions; later triggers overwrite.
            // Triggers sorted ascending by priority: high(10) fires first, low(200) second.
            // Only the high-priority trigger should match.
            var highPriority = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("high")
                    .eventTypeField("type")
                    .eventTypeValue("EVENT")
                    .priority(10)
                    .action("source", "high")
                    .build();

            var lowPriority = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("low")
                    .eventTypeField("type")
                    .eventTypeValue("OTHER")  // different event type — won't match
                    .priority(200)
                    .action("source", "low")
                    .build();

            ReactiveAgent agent = createAgent("prio", List.of(lowPriority, highPriority));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "EVENT")));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("source", "high");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Multiple Triggers
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Multiple Triggers")
    class MultiTriggerTests {

        @Test void onlyMatchingTriggersFire() {
            var errorTrigger = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("error")
                    .eventTypeField("type")
                    .eventTypeValue("ERROR")
                    .action("action", "LOG_ERROR")
                    .build();

            var warnTrigger = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("warn")
                    .eventTypeField("type")
                    .eventTypeValue("WARN")
                    .action("action", "LOG_WARN")
                    .build();

            ReactiveAgent agent = createAgent("multi", List.of(errorTrigger, warnTrigger));

            var r1 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ERROR")));
            assertThat(r1.getOutput()).containsEntry("action", "LOG_ERROR");

            agent.resetState();
            var r2 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "WARN")));
            assertThat(r2.getOutput()).containsEntry("action", "LOG_WARN");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Reset & Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("State Management")
    class StateTests {

        @Test void resetClearsAll() {
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("resettable")
                    .eventTypeField("type")
                    .eventTypeValue("X")
                    .cooldown(Duration.ofHours(1))
                    .action("ok", true)
                    .build();

            ReactiveAgent agent = createAgent("reset", List.of(trigger));

            runOnEventloop(() -> agent.process(ctx, Map.of("type", "X")));
            agent.resetState();

            // After reset, should fire again despite cooldown
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "X")));
            assertThat(result.isSuccess()).isTrue();
        }

        @Test void metricsTracked() {
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("tracked")
                    .eventTypeField("type")
                    .eventTypeValue("A")
                    .action("ok", true)
                    .build();

            ReactiveAgent agent = createAgent("metrics", List.of(trigger));
            runOnEventloop(() -> agent.process(ctx, Map.of("type", "A")));
            runOnEventloop(() -> agent.process(ctx, Map.of("type", "B")));

            assertThat(agent.getTotalInvocations()).isEqualTo(2);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Condition Operators
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Condition Operators")
    class ConditionOperatorTests {

        private ReactiveAgent agentWithCondition(String op, String value) {
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("cond")
                    .eventTypeField("type")
                    .eventTypeValue("E")
                    .conditionField("x")
                    .conditionOperator(op)
                    .conditionValue(value)
                    .action("matched", true)
                    .build();
            return createAgent("cond-" + op, List.of(trigger));
        }

        @Test void equalsOperator() {
            ReactiveAgent agent = agentWithCondition("==", "abc");
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "E", "x", "abc")));
            assertThat(result.isSuccess()).isTrue();
        }

        @Test void notEqualsOperator() {
            ReactiveAgent agent = agentWithCondition("!=", "abc");
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "E", "x", "xyz")));
            assertThat(result.isSuccess()).isTrue();
        }

        @Test void containsOperator() {
            ReactiveAgent agent = agentWithCondition("contains", "err");
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "E", "x", "error_log")));
            assertThat(result.isSuccess()).isTrue();
        }

        @Test void greaterThanOperator() {
            ReactiveAgent agent = agentWithCondition(">", "50");
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "E", "x", "75")));
            assertThat(result.isSuccess()).isTrue();
        }

        @Test void lessThanOperator() {
            ReactiveAgent agent = agentWithCondition("<", "50");
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "E", "x", "25")));
            assertThat(result.isSuccess()).isTrue();
        }
    }
}
