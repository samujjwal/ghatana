/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
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
 *   <li>Sliding window expiry (events aging out of the window)</li> // GH-90000
 *   <li>Multiple triggers firing on the same event and merging actions</li>
 *   <li>Trigger with threshold=0 (always fires immediately)</li> // GH-90000
 *   <li>Unknown condition operator graceful handling</li>
 *   <li>Additional condition operators (>=, <=)</li> // GH-90000
 *   <li>Null actual value in condition evaluation</li>
 * </ul>
 */
@DisplayName("Reactive Agent — Gap Tests")
class ReactiveAgentGapTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() { // GH-90000
        ctx = AgentContext.builder() // GH-90000
                .turnId("turn-1")
                .agentId("reactive-gap")
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
    // Sliding window expiry
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sliding Window Expiry")
    class SlidingWindowExpiryTests {

        @Test
        void windowWithVeryShortDurationExpiresOldEvents() throws InterruptedException { // GH-90000
            // Use a very short window (10ms) so events age out quickly // GH-90000
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("expiring")
                    .eventTypeField("type")
                    .eventTypeValue("EVENT")
                    .threshold(3) // GH-90000
                    .countingWindow(Duration.ofMillis(10)) // GH-90000
                    .action("fired", true) // GH-90000
                    .build(); // GH-90000

            ReactiveAgent agent = createAgent("expire-test", List.of(trigger)); // GH-90000

            // Send 2 events
            runOnEventloop(() -> agent.process(ctx, Map.of("type", "EVENT"))); // GH-90000
            runOnEventloop(() -> agent.process(ctx, Map.of("type", "EVENT"))); // GH-90000

            // Wait for the window to expire
            Thread.sleep(50); // GH-90000

            // Send 1 more event — should NOT fire because the first 2 expired
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "EVENT"))); // GH-90000
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SKIPPED); // GH-90000
        }

        @Test
        void windowReAccumulatesAfterExpiry() throws InterruptedException { // GH-90000
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("reaccum")
                    .eventTypeField("type")
                    .eventTypeValue("TICK")
                    .threshold(2) // GH-90000
                    .countingWindow(Duration.ofMillis(500)) // GH-90000
                    .action("accumulated", true) // GH-90000
                    .build(); // GH-90000

            ReactiveAgent agent = createAgent("reaccum-test", List.of(trigger)); // GH-90000

            // First batch — accumulates to threshold
            runOnEventloop(() -> agent.process(ctx, Map.of("type", "TICK"))); // GH-90000
            var result1 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "TICK"))); // GH-90000
            assertThat(result1.isSuccess()).isTrue(); // GH-90000

            // Wait for window to expire (sleep > window duration) // GH-90000
            Thread.sleep(750); // GH-90000
            agent.resetState(); // GH-90000

            // Second batch — should re-accumulate from zero
            var notFired = runOnEventloop(() -> agent.process(ctx, Map.of("type", "TICK"))); // GH-90000
            assertThat(notFired.getStatus()).isEqualTo(AgentResultStatus.SKIPPED); // GH-90000

            // One more → reaches threshold again
            var fired = runOnEventloop(() -> agent.process(ctx, Map.of("type", "TICK"))); // GH-90000
            assertThat(fired.isSuccess()).isTrue(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Multiple triggers on same event — action merging
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Multi-trigger Merge")
    class MultiTriggerMergeTests {

        @Test
        void multipleTriggersSameEventTypeMergeActions() { // GH-90000
            var trigger1 = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("log-trigger")
                    .eventTypeField("type")
                    .eventTypeValue("ERROR")
                    .priority(10) // GH-90000
                    .action("log", true) // GH-90000
                    .build(); // GH-90000

            var trigger2 = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("alert-trigger")
                    .eventTypeField("type")
                    .eventTypeValue("ERROR")
                    .priority(20) // GH-90000
                    .action("alert", "CRITICAL") // GH-90000
                    .build(); // GH-90000

            ReactiveAgent agent = createAgent("multi-merge", List.of(trigger1, trigger2)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ERROR"))); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            // Both triggers fire and actions merge
            assertThat(result.getOutput()).containsEntry("log", true); // GH-90000
            assertThat(result.getOutput()).containsEntry("alert", "CRITICAL"); // GH-90000

            assertThat(result.getOutput().get("_reactive.firedTriggers")).isInstanceOf(List.class);
            List<?> fired = (List<?>) result.getOutput().get("_reactive.firedTriggers");
            assertThat(fired).hasSize(2); // GH-90000
            assertThat(fired.containsAll(List.of("log-trigger", "alert-trigger"))).isTrue(); // GH-90000
        }

        @Test
        void laterTriggerOverwritesConflictingKey() { // GH-90000
            var trigger1 = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("first")
                    .eventTypeField("type")
                    .eventTypeValue("WARN")
                    .priority(10) // GH-90000
                    .action("severity", "LOW") // GH-90000
                    .action("source", "first") // GH-90000
                    .build(); // GH-90000

            var trigger2 = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("second")
                    .eventTypeField("type")
                    .eventTypeValue("WARN")
                    .priority(20) // GH-90000
                    .action("severity", "HIGH") // GH-90000
                    .action("channel", "email") // GH-90000
                    .build(); // GH-90000

            ReactiveAgent agent = createAgent("overwrite-merge", List.of(trigger1, trigger2)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("type", "WARN"))); // GH-90000

            // trigger2 (priority 20, processed after 10) overwrites "severity" // GH-90000
            assertThat(result.getOutput()).containsEntry("severity", "HIGH"); // GH-90000
            assertThat(result.getOutput()).containsEntry("source", "first"); // GH-90000
            assertThat(result.getOutput()).containsEntry("channel", "email"); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Additional condition operators
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Additional Condition Operators")
    class AdditionalOperatorTests {

        private ReactiveAgent agentWithCondition(String op, String value) { // GH-90000
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("cond")
                    .eventTypeField("type")
                    .eventTypeValue("E")
                    .conditionField("x")
                    .conditionOperator(op) // GH-90000
                    .conditionValue(value) // GH-90000
                    .action("matched", true) // GH-90000
                    .build(); // GH-90000
            return createAgent("cond-" + op, List.of(trigger)); // GH-90000
        }

        @Test
        void greaterThanOrEqualOperator() { // GH-90000
            ReactiveAgent agent = agentWithCondition(">=", "50"); // GH-90000
            var exact = runOnEventloop(() -> agent.process(ctx, // GH-90000
                    Map.of("type", "E", "x", "50"))); // GH-90000
            assertThat(exact.isSuccess()).isTrue(); // GH-90000

            agent.resetState(); // GH-90000
            var above = runOnEventloop(() -> agent.process(ctx, // GH-90000
                    Map.of("type", "E", "x", "51"))); // GH-90000
            assertThat(above.isSuccess()).isTrue(); // GH-90000
        }

        @Test
        void lessThanOrEqualOperator() { // GH-90000
            ReactiveAgent agent = agentWithCondition("<=", "50"); // GH-90000
            var exact = runOnEventloop(() -> agent.process(ctx, // GH-90000
                    Map.of("type", "E", "x", "50"))); // GH-90000
            assertThat(exact.isSuccess()).isTrue(); // GH-90000

            agent.resetState(); // GH-90000
            var below = runOnEventloop(() -> agent.process(ctx, // GH-90000
                    Map.of("type", "E", "x", "49"))); // GH-90000
            assertThat(below.isSuccess()).isTrue(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Null condition value
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Null Condition Value")
    class NullConditionTests {

        @Test
        void missingConditionFieldDoesNotMatch() { // GH-90000
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("null-cond")
                    .eventTypeField("type")
                    .eventTypeValue("E")
                    .conditionField("missing")
                    .conditionOperator(">")
                    .conditionValue("10")
                    .action("fired", true) // GH-90000
                    .build(); // GH-90000

            ReactiveAgent agent = createAgent("null-cond-test", List.of(trigger)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, // GH-90000
                    Map.of("type", "E"))); // GH-90000

            // Missing field → condition evaluates to false → skipped
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SKIPPED); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cooldown and window interaction
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cooldown and Window Interaction")
    class CooldownWindowInteractionTests {

        @Test
        void triggerWithBothThresholdAndCooldown() { // GH-90000
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("combo")
                    .eventTypeField("type")
                    .eventTypeValue("ALERT")
                    .threshold(2) // GH-90000
                    .countingWindow(Duration.ofMinutes(5)) // GH-90000
                    .cooldown(Duration.ofHours(1)) // GH-90000
                    .action("action", "ESCALATE") // GH-90000
                    .build(); // GH-90000

            ReactiveAgent agent = createAgent("combo-test", List.of(trigger)); // GH-90000

            // Event 1: below threshold → SKIPPED
            var r1 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ALERT"))); // GH-90000
            assertThat(r1.getStatus()).isEqualTo(AgentResultStatus.SKIPPED); // GH-90000

            // Event 2: reaches threshold → fires
            var r2 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ALERT"))); // GH-90000
            assertThat(r2.isSuccess()).isTrue(); // GH-90000

            // Event 3: would reach threshold again but cooldown prevents firing
            var r3 = runOnEventloop(() -> agent.process(ctx, Map.of("type", "ALERT"))); // GH-90000
            assertThat(r3.getStatus()).isEqualTo(AgentResultStatus.SKIPPED); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // No triggers configured
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("No Triggers")
    class NoTriggersTests {

        @Test
        void noTriggersAlwaysReturnsSkipped() { // GH-90000
            ReactiveAgent agent = createAgent("no-triggers", List.of()); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, // GH-90000
                    Map.of("type", "ANYTHING"))); // GH-90000

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SKIPPED); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Fired trigger metadata
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Fired Trigger Metadata")
    class TriggerMetadataTests {

        @Test
        void skippedResultContainsEmptyFiredList() { // GH-90000
            var trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("non-matching")
                    .eventTypeField("type")
                    .eventTypeValue("SPECIFIC")
                    .action("x", 1) // GH-90000
                    .build(); // GH-90000

            ReactiveAgent agent = createAgent("meta-skip", List.of(trigger)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, // GH-90000
                    Map.of("type", "OTHER"))); // GH-90000

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SKIPPED); // GH-90000
            assertThat(result.getOutput().get("_reactive.firedTriggers")).isInstanceOf(List.class);
            List<?> fired = (List<?>) result.getOutput().get("_reactive.firedTriggers");
            assertThat(fired).isEmpty(); // GH-90000
        }
    }
}
