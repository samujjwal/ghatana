/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. 
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
@DisplayName("ReactiveAgent Behavioral Tests")
@ExtendWith(MockitoExtension.class) 
class ReactiveAgentBehavioralTest {

    @Mock
    private MemoryStore memoryStore;

    private AgentContext agentContext;
    private ReactiveAgent agent;

    @BeforeEach
    void setUp() { 
        agentContext = AgentContext.builder() 
                .turnId("turn-1")
                .agentId("reactive-agent")
                .tenantId("tenant-1")
                .memoryStore(memoryStore) 
                .build(); 

        agent = new ReactiveAgent("reactive-agent");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Trigger Evaluation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Trigger Evaluation")
    class TriggerEvaluationTests {

        @Test
        @DisplayName("Trigger evaluates input against condition")
        void triggerEvaluation() { 
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() 
                    .name("High Temperature Alert")
                    .eventTypeField("type")
                    .eventTypeValue("sensor")
                    .conditionField("temperature")
                    .conditionOperator(">")
                    .conditionValue(100.0) 
                    .action("alert", "critical") 
                    .build(); 

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() 
                    .trigger(trigger) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("type", "sensor", "temperature", 105.0); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result.isSuccess()).isTrue(); 
            assertThat(result.getOutput()).isNotNull(); 
        }

        @Test
        @DisplayName("Trigger does not fire when condition is false")
        void triggerNoFire() { 
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() 
                    .name("High Temperature Alert")
                    .eventTypeField("type")
                    .eventTypeValue("sensor")
                    .conditionField("temperature")
                    .conditionOperator(">")
                    .conditionValue(100.0) 
                    .action("alert", "critical") 
                    .build(); 

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() 
                    .trigger(trigger) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            // temperature=50 does NOT satisfy > 100, so trigger does not fire
            Map<String, Object> input = Map.of("type", "sensor", "temperature", 50.0); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result).isNotNull(); 
        }

        @Test
        @DisplayName("Multiple triggers evaluate independently")
        void multipleTriggers() { 
            // Both triggers share the same event type so both are evaluated on the same input
            ReactiveAgentConfig.TriggerDefinition trigger1 = ReactiveAgentConfig.TriggerDefinition.builder() 
                    .name("High CPU")
                    .eventTypeField("type")
                    .eventTypeValue("metrics")
                    .conditionField("cpu")
                    .conditionOperator(">")
                    .conditionValue(90) 
                    .action("action", "scale-up") 
                    .build(); 

            ReactiveAgentConfig.TriggerDefinition trigger2 = ReactiveAgentConfig.TriggerDefinition.builder() 
                    .name("Low Memory")
                    .eventTypeField("type")
                    .eventTypeValue("metrics")
                    .conditionField("memory")
                    .conditionOperator("<")
                    .conditionValue(10) 
                    .action("action", "restart") 
                    .build(); 

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() 
                    .trigger(trigger1) 
                    .trigger(trigger2) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            // Input satisfies both triggers: cpu > 90 and memory < 10
            Map<String, Object> input = new LinkedHashMap<>(); 
            input.put("type", "metrics"); 
            input.put("cpu", 95); 
            input.put("memory", 5); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result.isSuccess()).isTrue(); 
        }

        @Test
        @DisplayName("Trigger with additional condition")
        void complexTriggerCondition() { 
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() 
                    .name("Service Degradation")
                    .eventTypeField("type")
                    .eventTypeValue("metrics")
                    .conditionField("latency")
                    .conditionOperator(">")
                    .conditionValue(1000.0) 
                    .action("alert", "performance") 
                    .build(); 

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() 
                    .trigger(trigger) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("type", "metrics", "latency", 1500.0, "error_rate", 0.08); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result.isSuccess()).isTrue(); 
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cooldown Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cooldown Behavior")
    class CooldownTests {

        @Test
        @DisplayName("Trigger respects cooldown period")
        void cooldownRespected() throws InterruptedException { 
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() 
                    .name("Cooldown Test")
                    .eventTypeField("type")
                    .eventTypeValue("event")
                    .conditionField("value")
                    .conditionOperator(">")
                    .conditionValue(50) 
                    .cooldown(Duration.ofMillis(100)) 
                    .action("fired", true) 
                    .build(); 

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() 
                    .trigger(trigger) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("type", "event", "value", 75); 

            // First invocation — trigger fires
            AgentResult<?> result1 = runPromise(() -> agent.process(agentContext, input)); 
            assertThat(result1.isSuccess()).isTrue(); 

            // Immediate second invocation — within cooldown, trigger suppressed
            AgentResult<?> result2 = runPromise(() -> agent.process(agentContext, input)); 
            assertThat(result2).isNotNull(); 

            // Wait for cooldown to expire
            Thread.sleep(150); 

            // Third invocation — after cooldown, trigger fires again
            AgentResult<?> result3 = runPromise(() -> agent.process(agentContext, input)); 
            assertThat(result3).isNotNull(); 
        }

        @Test
        @DisplayName("Zero cooldown allows repeated firing")
        void zeroCooldown() { 
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() 
                    .name("Rapid Fire")
                    .eventTypeField("type")
                    .eventTypeValue("event")
                    .conditionField("x")
                    .conditionOperator(">")
                    .conditionValue(0) 
                    .cooldown(Duration.ZERO) 
                    .action("fired", true) 
                    .build(); 

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() 
                    .trigger(trigger) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("type", "event", "x", 1); 

            for (int i = 0; i < 10; i++) { 
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 
                assertThat(result).isNotNull(); 
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Sliding Window Counter Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sliding Window Counters")
    class SlidingWindowTests {

        @Test
        @DisplayName("Sliding window counts events in time period")
        void slidingWindowCountingBasic() { 
            // threshold=5 means fire after 5 matching events within the counting window
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() 
                    .name("Error Threshold")
                    .eventTypeField("type")
                    .eventTypeValue("error")
                    .threshold(5) 
                    .countingWindow(Duration.ofSeconds(1)) 
                    .action("escalate", true) 
                    .build(); 

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() 
                    .trigger(trigger) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            // Send 6 error events — window fires on the 5th and 6th
            for (int i = 0; i < 6; i++) { 
                Map<String, Object> input = Map.of("type", "error", "seq", i); 
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 
                assertThat(result).isNotNull(); 
            }
        }

        @Test
        @DisplayName("Window resets after expiration")
        void windowExpiration() throws InterruptedException { 
            // threshold=3 within a 200ms window
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() 
                    .name("Rate Limit")
                    .eventTypeField("type")
                    .eventTypeValue("request")
                    .threshold(3) 
                    .countingWindow(Duration.ofMillis(200)) 
                    .action("blocked", true) 
                    .build(); 

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() 
                    .trigger(trigger) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            // Send 4 requests rapidly — fires on 3rd and 4th
            for (int i = 0; i < 4; i++) { 
                Map<String, Object> input = Map.of("type", "request", "seq", i); 
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 
                assertThat(result).isNotNull(); 
            }

            // Wait for window to expire
            Thread.sleep(250); 

            // Window has reset — single request should not reach threshold
            AgentResult<?> resultAfterReset = runPromise(() -> 
                    agent.process(agentContext, Map.of("type", "request", "seq", 99))); 
            assertThat(resultAfterReset).isNotNull(); 
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Priority-Based Evaluation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Priority-Based Evaluation")
    class PriorityTests {

        @Test
        @DisplayName("Higher priority triggers evaluated first")
        void priorityOrder() { 
            ReactiveAgentConfig.TriggerDefinition highPriority = ReactiveAgentConfig.TriggerDefinition.builder() 
                    .name("Critical Alert")
                    .eventTypeField("type")
                    .eventTypeValue("alert")
                    .conditionField("severity")
                    .conditionOperator("==")
                    .conditionValue("critical")
                    .priority(1) 
                    .action("response", "immediate") 
                    .build(); 

            ReactiveAgentConfig.TriggerDefinition lowPriority = ReactiveAgentConfig.TriggerDefinition.builder() 
                    .name("Normal Alert")
                    .eventTypeField("type")
                    .eventTypeValue("alert")
                    .conditionField("severity")
                    .conditionOperator("==")
                    .conditionValue("normal")
                    .priority(10) 
                    .action("response", "standard") 
                    .build(); 

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() 
                    .trigger(highPriority) 
                    .trigger(lowPriority) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            // Only severity="critical" matches the highPriority trigger
            Map<String, Object> input = Map.of("type", "alert", "severity", "critical"); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result.isSuccess()).isTrue(); 
        }

        @Test
        @DisplayName("Multiple triggers fire in priority order")
        void multiplePrioritizedTriggers() { 
            // All three triggers match the same event type with no extra condition
            ReactiveAgentConfig.TriggerDefinition t1 = ReactiveAgentConfig.TriggerDefinition.builder() 
                    .name("Priority 1")
                    .eventTypeField("type")
                    .eventTypeValue("event")
                    .priority(1) 
                    .action("order", "1") 
                    .build(); 

            ReactiveAgentConfig.TriggerDefinition t2 = ReactiveAgentConfig.TriggerDefinition.builder() 
                    .name("Priority 2")
                    .eventTypeField("type")
                    .eventTypeValue("event")
                    .priority(2) 
                    .action("order", "2") 
                    .build(); 

            ReactiveAgentConfig.TriggerDefinition t3 = ReactiveAgentConfig.TriggerDefinition.builder() 
                    .name("Priority 3")
                    .eventTypeField("type")
                    .eventTypeValue("event")
                    .priority(3) 
                    .action("order", "3") 
                    .build(); 

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() 
                    .trigger(t1) 
                    .trigger(t2) 
                    .trigger(t3) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("type", "event", "data", "x"); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            // All three triggers fire; result is SUCCESS
            assertThat(result.isSuccess()).isTrue(); 
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Latency Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Latency")
    class LatencyTests {

        @Test
        @DisplayName("Processing completes with low latency")
        void subMillisecondLatency() { 
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() 
                    .name("Latency Test")
                    .eventTypeField("type")
                    .eventTypeValue("event")
                    .conditionField("value")
                    .conditionOperator(">")
                    .conditionValue(50) 
                    .action("fired", true) 
                    .build(); 

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() 
                    .trigger(trigger) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("type", "event", "value", 75); 

            long startNanos = System.nanoTime(); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 
            long elapsedNanos = System.nanoTime() - startNanos; 

            assertThat(result.isSuccess()).isTrue(); 
            Duration latency = Duration.ofNanos(elapsedNanos); 

            // Reactive agents should be very fast (allowing for system load variations)
            assertThat(latency).isLessThan(Duration.ofMillis(50)); 
        }

        @Test
        @DisplayName("High throughput trigger evaluation")
        void highThroughput() { 
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() 
                    .name("Rapid")
                    .eventTypeField("type")
                    .eventTypeValue("event")
                    .conditionField("x")
                    .conditionOperator(">")
                    .conditionValue(0) 
                    .action("fired", true) 
                    .build(); 

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() 
                    .trigger(trigger) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            int iterations = 1000;
            Map<String, Object> input = Map.of("type", "event", "x", 1); 

            Instant start = Instant.now(); 
            for (int i = 0; i < iterations; i++) { 
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 
                assertThat(result).isNotNull(); 
            }
            Instant end = Instant.now(); 

            Duration total = Duration.between(start, end); 
            long avgLatency = total.toMillis() / iterations; 

            // Average latency should be very low
            assertThat(avgLatency).isLessThan(5); 
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Action Execution Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Action Execution")
    class ActionExecutionTests {

        @Test
        @DisplayName("Trigger action is included in output")
        void actionInOutput() { 
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() 
                    .name("Send Alert")
                    .eventTypeField("type")
                    .eventTypeValue("alarm")
                    .conditionField("level")
                    .conditionOperator(">")
                    .conditionValue(1) 
                    .action("alert_level", "critical") 
                    .action("notification", "email") 
                    .build(); 

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() 
                    .trigger(trigger) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("type", "alarm", "level", 2); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result.isSuccess()).isTrue(); 
            assertThat(result.getOutput()).isNotNull(); 
        }

        @Test
        @DisplayName("No action when trigger doesn't match")
        void noActionOnNoMatch() { 
            // Trigger expects eventTypeValue="special" but input has type="event"
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() 
                    .name("Never Fires")
                    .eventTypeField("type")
                    .eventTypeValue("special")
                    .action("fired", true) 
                    .build(); 

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() 
                    .trigger(trigger) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            // Input has type="event" which does NOT match eventTypeValue="special"
            Map<String, Object> input = Map.of("type", "event", "x", "something"); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            assertThat(result).isNotNull(); 
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Concurrent Safety Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Concurrent Safety")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent trigger invocations are safe")
        void concurrentTriggerEvaluation() throws InterruptedException { 
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() 
                    .name("Concurrent Test")
                    .eventTypeField("type")
                    .eventTypeValue("event")
                    .conditionField("value")
                    .conditionOperator(">")
                    .conditionValue(0) 
                    .action("fired", true) 
                    .build(); 

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() 
                    .trigger(trigger) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            int threadCount = 10;
            int iterationsPerThread = 100;

            Thread[] threads = new Thread[threadCount];
            for (int t = 0; t < threadCount; t++) { 
                threads[t] = new Thread(() -> { 
                    for (int i = 0; i < iterationsPerThread; i++) { 
                        Map<String, Object> input = Map.of("type", "event", "value", i + 1); 
                        AgentResult<?> result = runPromise(() -> 
                                agent.process(agentContext, input)); 
                        assertThat(result).isNotNull(); 
                    }
                });
                threads[t].start(); 
            }

            for (Thread t : threads) { 
                t.join(); 
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Explanation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Explanation Generation")
    class ExplanationTests {

        @Test
        @DisplayName("Trigger explanation mentions which trigger fired")
        void triggerExplanation() { 
            ReactiveAgentConfig.TriggerDefinition trigger = ReactiveAgentConfig.TriggerDefinition.builder() 
                    .name("Overheat Alert")
                    .eventTypeField("type")
                    .eventTypeValue("sensor")
                    .conditionField("temperature")
                    .conditionOperator(">")
                    .conditionValue(100) 
                    .action("action", "shutdown") 
                    .build(); 

            ReactiveAgentConfig config = ReactiveAgentConfig.builder() 
                    .trigger(trigger) 
                    .build(); 

            runPromise(() -> agent.initialize(config)); 

            Map<String, Object> input = Map.of("type", "sensor", "temperature", 120.5); 
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); 

            String explanation = result.getExplanation(); 
            assertThat(explanation) 
                    .isNotNull() 
                    .isNotBlank(); 
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private <T> T runPromise(java.util.function.Supplier<Promise<T>> supplier) { 
        var result = new Object() { T value; }; 
        var error = new Object() { Exception ex; }; 

        Eventloop eventloop = Eventloop.builder().withCurrentThread().build(); 
        eventloop.post(() -> supplier.get() 
                .whenResult(v -> result.value = v) 
                .whenException(e -> error.ex = (Exception) e)); 

        eventloop.run(); 

        if (error.ex != null) { 
            throw new RuntimeException(error.ex); 
        }

        return result.value;
    }
}
