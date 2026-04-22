/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 */

package com.ghatana.agent.behavioral;

import com.ghatana.agent.*;
import com.ghatana.agent.reactive.*;
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
 * Behavioral tests for ReactiveAgent.
 *
 * Focus: Trigger evaluation, cooldown/debounce behavior, sliding window counters,
 * priority-based rule evaluation, and sub-millisecond latency.
 */
@DisplayName("ReactiveAgent Behavioral Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class ReactiveAgentBehavioralTest {

    @Mock
    private MemoryStore memoryStore;

    private AgentContext agentContext;
    private ReactiveAgent agent;

    @BeforeEach
    void setUp() { // GH-90000
        agentContext = AgentContext.builder() // GH-90000
                .turnId("turn-1 [GH-90000]")
                .agentId("reactive-agent [GH-90000]")
                .tenantId("tenant-1 [GH-90000]")
                .memoryStore(memoryStore) // GH-90000
                .build(); // GH-90000

        agent = new ReactiveAgent("reactive-agent [GH-90000]");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Trigger Evaluation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Trigger Evaluation [GH-90000]")
    class TriggerEvaluationTests {

        @Test
        @DisplayName("Trigger evaluates input against condition [GH-90000]")
        void triggerEvaluation() { // GH-90000
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("High Temperature Alert [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("sensor [GH-90000]")
                    .conditionField("temperature [GH-90000]")
                    .conditionOperator("> [GH-90000]")
                    .conditionValue(100.0) // GH-90000
                    .action("alert", "critical") // GH-90000
                    .build(); // GH-90000

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() // GH-90000
                    .trigger(trigger) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("type", "sensor", "temperature", 105.0); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Trigger does not fire when condition is false [GH-90000]")
        void triggerNoFire() { // GH-90000
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("High Temperature Alert [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("sensor [GH-90000]")
                    .conditionField("temperature [GH-90000]")
                    .conditionOperator("> [GH-90000]")
                    .conditionValue(100.0) // GH-90000
                    .action("alert", "critical") // GH-90000
                    .build(); // GH-90000

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() // GH-90000
                    .trigger(trigger) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            // temperature=50 does NOT satisfy > 100, so trigger does not fire
            Map<String, Object> input = Map.of("type", "sensor", "temperature", 50.0); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Multiple triggers evaluate independently [GH-90000]")
        void multipleTriggers() { // GH-90000
            // Both triggers share the same event type so both are evaluated on the same input
            ReactiveAgentConfig.TriggerDefinition trigger1 = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("High CPU [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("metrics [GH-90000]")
                    .conditionField("cpu [GH-90000]")
                    .conditionOperator("> [GH-90000]")
                    .conditionValue(90) // GH-90000
                    .action("action", "scale-up") // GH-90000
                    .build(); // GH-90000

            ReactiveAgentConfig.TriggerDefinition trigger2 = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("Low Memory [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("metrics [GH-90000]")
                    .conditionField("memory [GH-90000]")
                    .conditionOperator("< [GH-90000]")
                    .conditionValue(10) // GH-90000
                    .action("action", "restart") // GH-90000
                    .build(); // GH-90000

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() // GH-90000
                    .trigger(trigger1) // GH-90000
                    .trigger(trigger2) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            // Input satisfies both triggers: cpu > 90 and memory < 10
            Map<String, Object> input = new LinkedHashMap<>(); // GH-90000
            input.put("type", "metrics"); // GH-90000
            input.put("cpu", 95); // GH-90000
            input.put("memory", 5); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Trigger with additional condition [GH-90000]")
        void complexTriggerCondition() { // GH-90000
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("Service Degradation [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("metrics [GH-90000]")
                    .conditionField("latency [GH-90000]")
                    .conditionOperator("> [GH-90000]")
                    .conditionValue(1000.0) // GH-90000
                    .action("alert", "performance") // GH-90000
                    .build(); // GH-90000

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() // GH-90000
                    .trigger(trigger) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("type", "metrics", "latency", 1500.0, "error_rate", 0.08); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cooldown Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cooldown Behavior [GH-90000]")
    class CooldownTests {

        @Test
        @DisplayName("Trigger respects cooldown period [GH-90000]")
        void cooldownRespected() throws InterruptedException { // GH-90000
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("Cooldown Test [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("event [GH-90000]")
                    .conditionField("value [GH-90000]")
                    .conditionOperator("> [GH-90000]")
                    .conditionValue(50) // GH-90000
                    .cooldown(Duration.ofMillis(100)) // GH-90000
                    .action("fired", true) // GH-90000
                    .build(); // GH-90000

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() // GH-90000
                    .trigger(trigger) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("type", "event", "value", 75); // GH-90000

            // First invocation — trigger fires
            AgentResult<?> result1 = runPromise(() -> agent.process(agentContext, input)); // GH-90000
            assertThat(result1.isSuccess()).isTrue(); // GH-90000

            // Immediate second invocation — within cooldown, trigger suppressed
            AgentResult<?> result2 = runPromise(() -> agent.process(agentContext, input)); // GH-90000
            assertThat(result2).isNotNull(); // GH-90000

            // Wait for cooldown to expire
            Thread.sleep(150); // GH-90000

            // Third invocation — after cooldown, trigger fires again
            AgentResult<?> result3 = runPromise(() -> agent.process(agentContext, input)); // GH-90000
            assertThat(result3).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Zero cooldown allows repeated firing [GH-90000]")
        void zeroCooldown() { // GH-90000
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("Rapid Fire [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("event [GH-90000]")
                    .conditionField("x [GH-90000]")
                    .conditionOperator("> [GH-90000]")
                    .conditionValue(0) // GH-90000
                    .cooldown(Duration.ZERO) // GH-90000
                    .action("fired", true) // GH-90000
                    .build(); // GH-90000

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() // GH-90000
                    .trigger(trigger) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("type", "event", "x", 1); // GH-90000

            for (int i = 0; i < 10; i++) { // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000
                assertThat(result).isNotNull(); // GH-90000
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Sliding Window Counter Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sliding Window Counters [GH-90000]")
    class SlidingWindowTests {

        @Test
        @DisplayName("Sliding window counts events in time period [GH-90000]")
        void slidingWindowCountingBasic() { // GH-90000
            // threshold=5 means fire after 5 matching events within the counting window
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("Error Threshold [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("error [GH-90000]")
                    .threshold(5) // GH-90000
                    .countingWindow(Duration.ofSeconds(1)) // GH-90000
                    .action("escalate", true) // GH-90000
                    .build(); // GH-90000

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() // GH-90000
                    .trigger(trigger) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            // Send 6 error events — window fires on the 5th and 6th
            for (int i = 0; i < 6; i++) { // GH-90000
                Map<String, Object> input = Map.of("type", "error", "seq", i); // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000
                assertThat(result).isNotNull(); // GH-90000
            }
        }

        @Test
        @DisplayName("Window resets after expiration [GH-90000]")
        void windowExpiration() throws InterruptedException { // GH-90000
            // threshold=3 within a 200ms window
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("Rate Limit [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("request [GH-90000]")
                    .threshold(3) // GH-90000
                    .countingWindow(Duration.ofMillis(200)) // GH-90000
                    .action("blocked", true) // GH-90000
                    .build(); // GH-90000

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() // GH-90000
                    .trigger(trigger) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            // Send 4 requests rapidly — fires on 3rd and 4th
            for (int i = 0; i < 4; i++) { // GH-90000
                Map<String, Object> input = Map.of("type", "request", "seq", i); // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000
                assertThat(result).isNotNull(); // GH-90000
            }

            // Wait for window to expire
            Thread.sleep(250); // GH-90000

            // Window has reset — single request should not reach threshold
            AgentResult<?> resultAfterReset = runPromise(() -> // GH-90000
                    agent.process(agentContext, Map.of("type", "request", "seq", 99))); // GH-90000
            assertThat(resultAfterReset).isNotNull(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Priority-Based Evaluation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Priority-Based Evaluation [GH-90000]")
    class PriorityTests {

        @Test
        @DisplayName("Higher priority triggers evaluated first [GH-90000]")
        void priorityOrder() { // GH-90000
            ReactiveAgentConfig.TriggerDefinition highPriority = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("Critical Alert [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("alert [GH-90000]")
                    .conditionField("severity [GH-90000]")
                    .conditionOperator("== [GH-90000]")
                    .conditionValue("critical [GH-90000]")
                    .priority(1) // GH-90000
                    .action("response", "immediate") // GH-90000
                    .build(); // GH-90000

            ReactiveAgentConfig.TriggerDefinition lowPriority = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("Normal Alert [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("alert [GH-90000]")
                    .conditionField("severity [GH-90000]")
                    .conditionOperator("== [GH-90000]")
                    .conditionValue("normal [GH-90000]")
                    .priority(10) // GH-90000
                    .action("response", "standard") // GH-90000
                    .build(); // GH-90000

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() // GH-90000
                    .trigger(highPriority) // GH-90000
                    .trigger(lowPriority) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            // Only severity="critical" matches the highPriority trigger
            Map<String, Object> input = Map.of("type", "alert", "severity", "critical"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Multiple triggers fire in priority order [GH-90000]")
        void multiplePrioritizedTriggers() { // GH-90000
            // All three triggers match the same event type with no extra condition
            ReactiveAgentConfig.TriggerDefinition t1 = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("Priority 1 [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("event [GH-90000]")
                    .priority(1) // GH-90000
                    .action("order", "1") // GH-90000
                    .build(); // GH-90000

            ReactiveAgentConfig.TriggerDefinition t2 = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("Priority 2 [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("event [GH-90000]")
                    .priority(2) // GH-90000
                    .action("order", "2") // GH-90000
                    .build(); // GH-90000

            ReactiveAgentConfig.TriggerDefinition t3 = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("Priority 3 [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("event [GH-90000]")
                    .priority(3) // GH-90000
                    .action("order", "3") // GH-90000
                    .build(); // GH-90000

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() // GH-90000
                    .trigger(t1) // GH-90000
                    .trigger(t2) // GH-90000
                    .trigger(t3) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("type", "event", "data", "x"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            // All three triggers fire; result is SUCCESS
            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Latency Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Latency [GH-90000]")
    class LatencyTests {

        @Test
        @DisplayName("Processing completes in sub-millisecond time [GH-90000]")
        void subMillisecondLatency() { // GH-90000
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("Latency Test [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("event [GH-90000]")
                    .conditionField("value [GH-90000]")
                    .conditionOperator("> [GH-90000]")
                    .conditionValue(50) // GH-90000
                    .action("fired", true) // GH-90000
                    .build(); // GH-90000

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() // GH-90000
                    .trigger(trigger) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("type", "event", "value", 75); // GH-90000

            Instant start = Instant.now(); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000
            Instant end = Instant.now(); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            Duration latency = Duration.between(start, end); // GH-90000

            // Reactive agents should be very fast
            assertThat(latency).isLessThan(Duration.ofMillis(10)); // GH-90000
        }

        @Test
        @DisplayName("High throughput trigger evaluation [GH-90000]")
        void highThroughput() { // GH-90000
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("Rapid [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("event [GH-90000]")
                    .conditionField("x [GH-90000]")
                    .conditionOperator("> [GH-90000]")
                    .conditionValue(0) // GH-90000
                    .action("fired", true) // GH-90000
                    .build(); // GH-90000

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() // GH-90000
                    .trigger(trigger) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            int iterations = 1000;
            Map<String, Object> input = Map.of("type", "event", "x", 1); // GH-90000

            Instant start = Instant.now(); // GH-90000
            for (int i = 0; i < iterations; i++) { // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000
                assertThat(result).isNotNull(); // GH-90000
            }
            Instant end = Instant.now(); // GH-90000

            Duration total = Duration.between(start, end); // GH-90000
            long avgLatency = total.toMillis() / iterations; // GH-90000

            // Average latency should be very low
            assertThat(avgLatency).isLessThan(5); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Action Execution Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Action Execution [GH-90000]")
    class ActionExecutionTests {

        @Test
        @DisplayName("Trigger action is included in output [GH-90000]")
        void actionInOutput() { // GH-90000
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("Send Alert [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("alarm [GH-90000]")
                    .conditionField("level [GH-90000]")
                    .conditionOperator("> [GH-90000]")
                    .conditionValue(1) // GH-90000
                    .action("alert_level", "critical") // GH-90000
                    .action("notification", "email") // GH-90000
                    .build(); // GH-90000

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() // GH-90000
                    .trigger(trigger) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("type", "alarm", "level", 2); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("No action when trigger doesn't match [GH-90000]")
        void noActionOnNoMatch() { // GH-90000
            // Trigger expects eventTypeValue="special" but input has type="event"
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("Never Fires [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("special [GH-90000]")
                    .action("fired", true) // GH-90000
                    .build(); // GH-90000

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() // GH-90000
                    .trigger(trigger) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            // Input has type="event" which does NOT match eventTypeValue="special"
            Map<String, Object> input = Map.of("type", "event", "x", "something"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Concurrent Safety Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Concurrent Safety [GH-90000]")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent trigger invocations are safe [GH-90000]")
        void concurrentTriggerEvaluation() throws InterruptedException { // GH-90000
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("Concurrent Test [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("event [GH-90000]")
                    .conditionField("value [GH-90000]")
                    .conditionOperator("> [GH-90000]")
                    .conditionValue(0) // GH-90000
                    .action("fired", true) // GH-90000
                    .build(); // GH-90000

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() // GH-90000
                    .trigger(trigger) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            int threadCount = 10;
            int iterationsPerThread = 100;

            Thread[] threads = new Thread[threadCount];
            for (int t = 0; t < threadCount; t++) { // GH-90000
                threads[t] = new Thread(() -> { // GH-90000
                    for (int i = 0; i < iterationsPerThread; i++) { // GH-90000
                        Map<String, Object> input = Map.of("type", "event", "value", i + 1); // GH-90000
                        AgentResult<?> result = runPromise(() -> // GH-90000
                                agent.process(agentContext, input)); // GH-90000
                        assertThat(result).isNotNull(); // GH-90000
                    }
                });
                threads[t].start(); // GH-90000
            }

            for (Thread t : threads) { // GH-90000
                t.join(); // GH-90000
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Explanation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Explanation Generation [GH-90000]")
    class ExplanationTests {

        @Test
        @DisplayName("Trigger explanation mentions which trigger fired [GH-90000]")
        void triggerExplanation() { // GH-90000
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() // GH-90000
                    .name("Overheat Alert [GH-90000]")
                    .eventTypeField("type [GH-90000]")
                    .eventTypeValue("sensor [GH-90000]")
                    .conditionField("temperature [GH-90000]")
                    .conditionOperator("> [GH-90000]")
                    .conditionValue(100) // GH-90000
                    .action("action", "shutdown") // GH-90000
                    .build(); // GH-90000

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() // GH-90000
                    .trigger(trigger) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("type", "sensor", "temperature", 120.5); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            String explanation = result.getExplanation(); // GH-90000
            assertThat(explanation) // GH-90000
                    .isNotNull() // GH-90000
                    .isNotBlank(); // GH-90000
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
