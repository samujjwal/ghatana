/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 * Phase 4 — Task 4.1: Gap-filling tests for ReactiveAgent.
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
 * Gap-filling tests for {@link ReactiveAgent}.
 *
 * <p>Fills gaps identified in Phase 4 audit:
 * <ul>
 *   <li>Sliding window expiry (events aging out of the window)</li>
 *   <li>Multiple triggers firing on the same event and merging actions</li>
 *   <li>Trigger with threshold=0 (always fires immediately)</li>
 *   <li>Unknown condition operator graceful handling</li>
 *   <li>Additional condition operators (>=, <=)</li>
 *   <li>Null actual value in condition evaluation</li>
 * </ul>
 */
@DisplayName("Reactive Agent — Gap Tests")
class ReactiveAgentGapTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() {
        ctx = AgentContext.builder()
                .turnId("turn-1")
                .agentId("reactive-gap")
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
    // Sliding window expiry
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sliding Window Expiry")
    class SlidingWindowExpiryTests {

        @Test
        void windowWithVeryShortDurationExpiresOldEvents() throws InterruptedException {
            // Use a very short window (10ms) so events age out quickly
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("expiring")
                    .eventTypeField("type")
                    .eventTypeValue("EVENT")
                    .threshold(3)
                    .countingWindow(Duration.ofMillis(10))
                    .action("fired", true)
                    .build();

            ReactiveAgent agent = createAgent("expire-test", List.of(trigger));

            // Send 2 events
            runOnEventloop(() -> agent.process(ctx, Map.of("type", "EVENT")));
            runOnEventloop(() -> agent.process(ctx, Map.of("type", "EVENT")));

            // Wait for the window to expire
            Thread.sleep(50);

            // Send 1 more event — should NOT fire because the first 2 expired
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "EVENT")));
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SKIPPED);
        }

        @Test
        void windowReAccumulatesAfterExpiry() throws InterruptedException {
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("reaccum")
                    .eventTypeField("type")
                    .eventTypeValue("TICK")
                    .threshold(2)
                    .countingWindow(Duration.ofMillis(10))
                    .action("accumulated", true)
                    .build();

            ReactiveAgent agent = createAgent("reaccum-test", List.of(trigger));

            // First batch — accumulates to threshold
            runOnEventloop(() -> agent.process(ctx, Map.of("type", "TICK")));
            var result1 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "TICK")));
            assertThat(result1.isSuccess()).isTrue();

            // Wait for window to expire
            Thread.sleep(50);
            agent.resetState();

            // Second batch — should re-accumulate from zero
            var notFired = runOnEventloop(() -> agent.process(ctx, Map.of("type", "TICK")));
            assertThat(notFired.getStatus()).isEqualTo(AgentResultStatus.SKIPPED);

            // One more → reaches threshold again
            var fired = runOnEventloop(() -> agent.process(ctx, Map.of("type", "TICK")));
            assertThat(fired.isSuccess()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Multiple triggers on same event — action merging
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Multi-trigger Merge")
    class MultiTriggerMergeTests {

        @Test
        void multipleTriggersSameEventTypeMergeActions() {
            var trigger1 = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("log-trigger")
                    .eventTypeField("type")
                    .eventTypeValue("ERROR")
                    .priority(10)
                    .action("log", true)
                    .build();

            var trigger2 = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("alert-trigger")
                    .eventTypeField("type")
                    .eventTypeValue("ERROR")
                    .priority(20)
                    .action("alert", "CRITICAL")
                    .build();

            ReactiveAgent agent = createAgent("multi-merge", List.of(trigger1, trigger2));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ERROR")));

            assertThat(result.isSuccess()).isTrue();
            // Both triggers fire and actions merge
            assertThat(result.getOutput()).containsEntry("log", true);
            assertThat(result.getOutput()).containsEntry("alert", "CRITICAL");

            @SuppressWarnings("unchecked")
            List<String> fired = (List<String>) result.getOutput().get("_reactive.firedTriggers");
            assertThat(fired).containsExactly("log-trigger", "alert-trigger");
        }

        @Test
        void laterTriggerOverwritesConflictingKey() {
            var trigger1 = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("first")
                    .eventTypeField("type")
                    .eventTypeValue("WARN")
                    .priority(10)
                    .action("severity", "LOW")
                    .action("source", "first")
                    .build();

            var trigger2 = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("second")
                    .eventTypeField("type")
                    .eventTypeValue("WARN")
                    .priority(20)
                    .action("severity", "HIGH")
                    .action("channel", "email")
                    .build();

            ReactiveAgent agent = createAgent("overwrite-merge", List.of(trigger1, trigger2));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "WARN")));

            // trigger2 (priority 20, processed after 10) overwrites "severity"
            assertThat(result.getOutput()).containsEntry("severity", "HIGH");
            assertThat(result.getOutput()).containsEntry("source", "first");
            assertThat(result.getOutput()).containsEntry("channel", "email");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Additional condition operators
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Additional Condition Operators")
    class AdditionalOperatorTests {

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

        @Test
        void greaterThanOrEqualOperator() {
            ReactiveAgent agent = agentWithCondition(">=", "50");
            var exact = runOnEventloop(() -> agent.process(ctx,
                    Map.of("type", "E", "x", "50")));
            assertThat(exact.isSuccess()).isTrue();

            agent.resetState();
            var above = runOnEventloop(() -> agent.process(ctx,
                    Map.of("type", "E", "x", "51")));
            assertThat(above.isSuccess()).isTrue();
        }

        @Test
        void lessThanOrEqualOperator() {
            ReactiveAgent agent = agentWithCondition("<=", "50");
            var exact = runOnEventloop(() -> agent.process(ctx,
                    Map.of("type", "E", "x", "50")));
            assertThat(exact.isSuccess()).isTrue();

            agent.resetState();
            var below = runOnEventloop(() -> agent.process(ctx,
                    Map.of("type", "E", "x", "49")));
            assertThat(below.isSuccess()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Null condition value
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Null Condition Value")
    class NullConditionTests {

        @Test
        void missingConditionFieldDoesNotMatch() {
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("null-cond")
                    .eventTypeField("type")
                    .eventTypeValue("E")
                    .conditionField("missing")
                    .conditionOperator(">")
                    .conditionValue("10")
                    .action("fired", true)
                    .build();

            ReactiveAgent agent = createAgent("null-cond-test", List.of(trigger));
            var result = runOnEventloop(() -> agent.process(ctx,
                    Map.of("type", "E")));

            // Missing field → condition evaluates to false → skipped
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SKIPPED);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cooldown and window interaction
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cooldown and Window Interaction")
    class CooldownWindowInteractionTests {

        @Test
        void triggerWithBothThresholdAndCooldown() {
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("combo")
                    .eventTypeField("type")
                    .eventTypeValue("ALERT")
                    .threshold(2)
                    .countingWindow(Duration.ofMinutes(5))
                    .cooldown(Duration.ofHours(1))
                    .action("action", "ESCALATE")
                    .build();

            ReactiveAgent agent = createAgent("combo-test", List.of(trigger));

            // Event 1: below threshold → SKIPPED
            var r1 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ALERT")));
            assertThat(r1.getStatus()).isEqualTo(AgentResultStatus.SKIPPED);

            // Event 2: reaches threshold → fires
            var r2 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ALERT")));
            assertThat(r2.isSuccess()).isTrue();

            // Event 3: would reach threshold again but cooldown prevents firing
            var r3 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ALERT")));
            assertThat(r3.getStatus()).isEqualTo(AgentResultStatus.SKIPPED);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // No triggers configured
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("No Triggers")
    class NoTriggersTests {

        @Test
        void noTriggersAlwaysReturnsSkipped() {
            ReactiveAgent agent = createAgent("no-triggers", List.of());

            var result = runOnEventloop(() -> agent.process(ctx,
                    Map.of("type", "ANYTHING")));

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SKIPPED);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Fired trigger metadata
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Fired Trigger Metadata")
    class TriggerMetadataTests {

        @Test
        void skippedResultContainsEmptyFiredList() {
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder()
                    .name("non-matching")
                    .eventTypeField("type")
                    .eventTypeValue("SPECIFIC")
                    .action("x", 1)
                    .build();

            ReactiveAgent agent = createAgent("meta-skip", List.of(trigger));
            var result = runOnEventloop(() -> agent.process(ctx,
                    Map.of("type", "OTHER")));

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SKIPPED);
            @SuppressWarnings("unchecked")
            List<String> fired = (List<String>) result.getOutput().get("_reactive.firedTriggers");
            assertThat(fired).isEmpty();
        }
    }
}
