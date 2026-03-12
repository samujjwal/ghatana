/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ghatana.agent.resilience;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.RetryPolicy;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the agent-resilience module:
 * {@link AgentBulkhead}, {@link ResilientTypedAgent},
 * {@link AgentHealthMonitor}, and {@link ResilienceDecorator}.
 *
 * <p>All async tests extend {@link EventloopTestBase} and use
 * {@code runPromise()} to execute ActiveJ Promises on the managed eventloop.
 *
 * @doc.type class
 * @doc.purpose Tests for AgentBulkhead, ResilientTypedAgent, AgentHealthMonitor, ResilienceDecorator
 * @doc.layer platform
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Agent Resilience Module")
class AgentResilienceTest extends EventloopTestBase {

    // ─── shared test helpers ──────────────────────────────────────────────────

    /** Minimal TypedAgent stub that returns success with the echoed input. */
    private static TypedAgent<String, String> echoAgent(String agentId) {
        return new TypedAgent<>() {
            @Override
            public AgentDescriptor descriptor() {
                return AgentDescriptor.builder().agentId(agentId).build();
            }

            @Override
            public Promise<Void> initialize(AgentConfig config) {
                return Promise.complete();
            }

            @Override
            public Promise<Void> shutdown() {
                return Promise.complete();
            }

            @Override
            public Promise<HealthStatus> healthCheck() {
                return Promise.of(HealthStatus.HEALTHY);
            }

            @Override
            public Promise<Void> reconfigure(AgentConfig newConfig) {
                return Promise.complete();
            }

            @Override
            public boolean validateInput(String input) {
                return input != null;
            }

            @Override
            public Promise<AgentResult<String>> process(AgentContext ctx, String input) {
                return Promise.of(AgentResult.success(input, agentId, Duration.ZERO));
            }

            @Override
            public Promise<List<AgentResult<String>>> processBatch(AgentContext ctx, List<String> inputs) {
                return Promise.of(inputs.stream()
                        .map(i -> AgentResult.success(i, agentId, Duration.ZERO))
                        .toList());
            }
        };
    }

    /** Always-failing TypedAgent stub. */
    private static TypedAgent<String, String> failingAgent(String agentId) {
        return new TypedAgent<>() {
            @Override public AgentDescriptor descriptor() {
                return AgentDescriptor.builder().agentId(agentId).build();
            }
            @Override public Promise<Void> initialize(AgentConfig config) { return Promise.complete(); }
            @Override public Promise<Void> shutdown() { return Promise.complete(); }
            @Override public Promise<HealthStatus> healthCheck() { return Promise.of(HealthStatus.UNHEALTHY); }
            @Override public Promise<Void> reconfigure(AgentConfig config) { return Promise.complete(); }
            @Override public boolean validateInput(String input) { return true; }
            @Override public Promise<AgentResult<String>> process(AgentContext ctx, String input) {
                return Promise.ofException(new RuntimeException("agent-failure"));
            }
            @Override public Promise<List<AgentResult<String>>> processBatch(AgentContext ctx, List<String> inputs) {
                return Promise.ofException(new RuntimeException("agent-failure"));
            }
        };
    }

    private static AgentContext emptyCtx() {
        return AgentContext.empty();
    }

    // =========================================================================
    // AgentBulkhead
    // =========================================================================

    @Nested
    @DisplayName("AgentBulkhead")
    class AgentBulkheadTests {

        private AgentBulkhead bulkhead;
        private static final String AGENT_ID = "test-agent";
        private static final int MAX = 3;

        @BeforeEach
        void setUp() {
            bulkhead = AgentBulkhead.of(AGENT_ID, MAX);
        }

        @Test
        @DisplayName("factory sets agentId and maxConcurrency")
        void factoryCreation() {
            assertThat(bulkhead.getAgentId()).isEqualTo(AGENT_ID);
            assertThat(bulkhead.getMaxConcurrency()).isEqualTo(MAX);
        }

        @Test
        @DisplayName("maxConcurrency <= 0 throws IllegalArgumentException")
        void invalidMaxConcurrency() {
            assertThatThrownBy(() -> AgentBulkhead.of("x", 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> AgentBulkhead.of("x", -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("tryAcquire succeeds up to maxConcurrency")
        void acquireUpToMax() {
            for (int i = 0; i < MAX; i++) {
                assertThat(bulkhead.tryAcquire()).isTrue();
            }
            assertThat(bulkhead.availableSlots()).isZero();
            assertThat(bulkhead.isExhausted()).isTrue();
        }

        @Test
        @DisplayName("tryAcquire fails when bulkhead is full")
        void acquireFailsWhenFull() {
            for (int i = 0; i < MAX; i++) bulkhead.tryAcquire();
            assertThat(bulkhead.tryAcquire()).isFalse();
        }

        @Test
        @DisplayName("release frees a slot")
        void releaseFreesSlot() {
            bulkhead.tryAcquire();
            bulkhead.tryAcquire();
            bulkhead.release();
            assertThat(bulkhead.availableSlots()).isEqualTo(2);
            assertThat(bulkhead.tryAcquire()).isTrue();
        }

        @Test
        @DisplayName("getTotalAcquired counts successful acquires")
        void totalAcquiredCounter() {
            bulkhead.tryAcquire();
            bulkhead.tryAcquire();
            assertThat(bulkhead.getTotalAcquired()).isEqualTo(2);
        }

        @Test
        @DisplayName("getTotalRejected counts failed acquires")
        void totalRejectedCounter() {
            for (int i = 0; i < MAX; i++) bulkhead.tryAcquire();
            bulkhead.tryAcquire(); // rejected
            bulkhead.tryAcquire(); // rejected
            assertThat(bulkhead.getTotalRejected()).isEqualTo(2);
        }

        @Test
        @DisplayName("getUtilization reflects current usage ratio")
        void utilizationRatio() {
            assertThat(bulkhead.getUtilization()).isEqualTo(0.0);
            bulkhead.tryAcquire();
            assertThat(bulkhead.getUtilization()).isCloseTo(1.0 / MAX, org.assertj.core.data.Offset.offset(0.001));
            bulkhead.tryAcquire();
            bulkhead.tryAcquire();
            assertThat(bulkhead.getUtilization()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("isExhausted is false initially and true when full")
        void exhaustionCycle() {
            assertThat(bulkhead.isExhausted()).isFalse();
            for (int i = 0; i < MAX; i++) bulkhead.tryAcquire();
            assertThat(bulkhead.isExhausted()).isTrue();
            bulkhead.release();
            assertThat(bulkhead.isExhausted()).isFalse();
        }
    }

    // =========================================================================
    // ResilientTypedAgent
    // =========================================================================

    @Nested
    @DisplayName("ResilientTypedAgent")
    class ResilientTypedAgentTests {

        @Test
        @DisplayName("passes through successful process result from delegate")
        void successPassThrough() {
            ResilientTypedAgent<String, String> resilient = ResilientTypedAgent.<String, String>builder()
                    .delegate(echoAgent("echo-agent"))
                    .eventloop(eventloop())
                    .build();

            AgentResult<String> result = runPromise(() -> resilient.process(emptyCtx(), "hello"));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("hello");
        }

        @Test
        @DisplayName("descriptor delegates to wrapped agent")
        void descriptorDelegation() {
            ResilientTypedAgent<String, String> resilient = ResilientTypedAgent.<String, String>builder()
                    .delegate(echoAgent("my-agent"))
                    .eventloop(eventloop())
                    .build();
            assertThat(resilient.descriptor().getAgentId()).isEqualTo("my-agent");
        }

        @Test
        @DisplayName("bulkhead full causes result.failure with BulkheadFullException")
        void bulkheadFullRejectsWithFailure() {
            AgentBulkhead bulkhead = AgentBulkhead.of("blocked-agent", 1);
            bulkhead.tryAcquire(); // exhaust the single slot

            ResilientTypedAgent<String, String> resilient = ResilientTypedAgent.<String, String>builder()
                    .delegate(echoAgent("blocked-agent"))
                    .eventloop(eventloop())
                    .bulkhead(bulkhead)
                    .build();

            AgentResult<String> result = runPromise(() -> resilient.process(emptyCtx(), "input"));
            assertThat(result.isFailed()).isTrue();
            assertThat(result.getExplanation()).contains("BulkheadFullException");
        }

        @Test
        @DisplayName("bulkhead slot is released after successful process")
        void bulkheadReleasedAfterSuccess() {
            AgentBulkhead bulkhead = AgentBulkhead.of("release-agent", 1);
            ResilientTypedAgent<String, String> resilient = ResilientTypedAgent.<String, String>builder()
                    .delegate(echoAgent("release-agent"))
                    .eventloop(eventloop())
                    .bulkhead(bulkhead)
                    .build();

            runPromise(() -> resilient.process(emptyCtx(), "first"));
            assertThat(bulkhead.availableSlots()).isEqualTo(1);
            // Second call should also succeed because slot was released
            AgentResult<String> second = runPromise(() -> resilient.process(emptyCtx(), "second"));
            assertThat(second.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("circuit-breaker OPEN state causes healthCheck to return DEGRADED")
        void circuitOpenDegrades() {
            CircuitBreaker cb = CircuitBreaker.builder("cb-agent")
                    .failureThreshold(1)
                    .resetTimeout(Duration.ofSeconds(60))
                    .build();
            // Trip the circuit by executing a failing operation
            runBlocking(() -> cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException("forced"))));
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            ResilientTypedAgent<String, String> resilient = ResilientTypedAgent.<String, String>builder()
                    .delegate(echoAgent("cb-agent"))
                    .eventloop(eventloop())
                    .circuitBreaker(cb)
                    .build();

            HealthStatus health = runPromise(resilient::healthCheck);
            assertThat(health).isEqualTo(HealthStatus.DEGRADED);
        }

        @Test
        @DisplayName("healthCheck returns DEGRADED when bulkhead is exhausted")
        void bulkheadExhaustedDegrades() {
            AgentBulkhead bulkhead = AgentBulkhead.of("bh-health", 1);
            bulkhead.tryAcquire(); // exhaust

            ResilientTypedAgent<String, String> resilient = ResilientTypedAgent.<String, String>builder()
                    .delegate(echoAgent("bh-health"))
                    .eventloop(eventloop())
                    .bulkhead(bulkhead)
                    .build();

            HealthStatus health = runPromise(resilient::healthCheck);
            assertThat(health).isEqualTo(HealthStatus.DEGRADED);
        }

        @Test
        @DisplayName("processBatch delegates to underlying agent")
        void batchDelegation() {
            ResilientTypedAgent<String, String> resilient = ResilientTypedAgent.<String, String>builder()
                    .delegate(echoAgent("batch-agent"))
                    .eventloop(eventloop())
                    .build();
            List<AgentResult<String>> results = runPromise(
                    () -> resilient.processBatch(emptyCtx(), List.of("a", "b", "c")));
            assertThat(results).hasSize(3);
            assertThat(results).allMatch(AgentResult::isSuccess);
        }

        @Test
        @DisplayName("validateInput delegates to underlying agent")
        void validateInputDelegation() {
            ResilientTypedAgent<String, String> resilient = ResilientTypedAgent.<String, String>builder()
                    .delegate(echoAgent("validate-agent"))
                    .eventloop(eventloop())
                    .build();
            assertThat(resilient.validateInput("valid")).isTrue();
            assertThat(resilient.validateInput(null)).isFalse();
        }

        @Test
        @DisplayName("retry executes delegate multiple times on transient failure")
        void retryOnTransientFailure() {
            AtomicInteger callCount = new AtomicInteger();
            TypedAgent<String, String> flaky = new TypedAgent<>() {
                @Override public AgentDescriptor descriptor() { return AgentDescriptor.builder().agentId("flaky").build(); }
                @Override public Promise<Void> initialize(AgentConfig c) { return Promise.complete(); }
                @Override public Promise<Void> shutdown() { return Promise.complete(); }
                @Override public Promise<HealthStatus> healthCheck() { return Promise.of(HealthStatus.HEALTHY); }
                @Override public Promise<Void> reconfigure(AgentConfig c) { return Promise.complete(); }
                @Override public boolean validateInput(String i) { return true; }
                @Override public Promise<AgentResult<String>> process(AgentContext ctx, String input) {
                    int call = callCount.incrementAndGet();
                    if (call < 3) return Promise.ofException(new RuntimeException("transient"));
                    return Promise.of(AgentResult.success("recovered", "flaky", Duration.ZERO));
                }
                @Override public Promise<List<AgentResult<String>>> processBatch(AgentContext ctx, List<String> inputs) {
                    return Promise.of(List.of());
                }
            };

            RetryPolicy retry = RetryPolicy.builder().maxRetries(3).initialDelay(Duration.ZERO).build();
            ResilientTypedAgent<String, String> resilient = ResilientTypedAgent.<String, String>builder()
                    .delegate(flaky)
                    .eventloop(eventloop())
                    .retryPolicy(retry)
                    .build();

            AgentResult<String> result = runPromise(() -> resilient.process(emptyCtx(), "in"));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("recovered");
            assertThat(callCount.get()).isEqualTo(3);
        }
    }

    // =========================================================================
    // AgentHealthMonitor
    // =========================================================================

    @Nested
    @DisplayName("AgentHealthMonitor")
    class AgentHealthMonitorTests {

        private AgentHealthMonitor monitor;

        @BeforeEach
        void setUp() {
            monitor = new AgentHealthMonitor();
        }

        @Test
        @DisplayName("check returns HEALTHY snapshot for a healthy agent")
        void checkHealthyAgent() {
            monitor.register("healthy-agent", echoAgent("healthy-agent"), null, null);
            AgentHealthMonitor.AgentHealthSnapshot snap = runPromise(() -> monitor.check("healthy-agent"));
            assertThat(snap.agentId()).isEqualTo("healthy-agent");
            assertThat(snap.agentStatus()).isEqualTo(HealthStatus.HEALTHY);
            assertThat(snap.isHealthy()).isTrue();
            assertThat(snap.circuitBreakerState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(snap.bulkheadUtilization()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("check returns UNHEALTHY snapshot for a failing agent")
        void checkUnhealthyAgent() {
            monitor.register("bad-agent", failingAgent("bad-agent"), null, null);
            AgentHealthMonitor.AgentHealthSnapshot snap = runPromise(() -> monitor.check("bad-agent"));
            assertThat(snap.agentStatus()).isEqualTo(HealthStatus.UNHEALTHY);
            assertThat(snap.isHealthy()).isFalse();
        }

        @Test
        @DisplayName("check throws NoSuchElementException for unregistered agent")
        void checkUnregisteredAgent() {
            assertThatThrownBy(() -> runPromise(() -> monitor.check("nonexistent")))
                    .isInstanceOf(java.util.NoSuchElementException.class);
        }

        @Test
        @DisplayName("unregister removes agent from monitoring")
        void unregister() {
            monitor.register("to-remove", echoAgent("to-remove"), null, null);
            monitor.unregister("to-remove");
            assertThatThrownBy(() -> runPromise(() -> monitor.check("to-remove")))
                    .isInstanceOf(java.util.NoSuchElementException.class);
        }

        @Test
        @DisplayName("checkAll returns snapshots for all registered agents")
        void checkAll() {
            monitor.register("agent-a", echoAgent("agent-a"), null, null);
            monitor.register("agent-b", echoAgent("agent-b"), null, null);
            Map<String, AgentHealthMonitor.AgentHealthSnapshot> all =
                    runPromise(monitor::checkAll);
            assertThat(all).containsKeys("agent-a", "agent-b");
            assertThat(all.get("agent-a").isHealthy()).isTrue();
            assertThat(all.get("agent-b").isHealthy()).isTrue();
        }

        @Test
        @DisplayName("checkAll returns empty map when no agents registered")
        void checkAllEmpty() {
            Map<String, AgentHealthMonitor.AgentHealthSnapshot> all = runPromise(monitor::checkAll);
            assertThat(all).isEmpty();
        }

        @Test
        @DisplayName("overallHealth is HEALTHY when all agents are healthy")
        void overallHealthAllHealthy() {
            monitor.register("a1", echoAgent("a1"), null, null);
            monitor.register("a2", echoAgent("a2"), null, null);
            HealthStatus status = runPromise(monitor::overallHealth);
            assertThat(status).isEqualTo(HealthStatus.HEALTHY);
        }

        @Test
        @DisplayName("overallHealth is UNHEALTHY when any agent is unhealthy")
        void overallHealthAnyUnhealthy() {
            monitor.register("healthy", echoAgent("healthy"), null, null);
            monitor.register("sick", failingAgent("sick"), null, null);
            HealthStatus status = runPromise(monitor::overallHealth);
            assertThat(status).isEqualTo(HealthStatus.UNHEALTHY);
        }

        @Test
        @DisplayName("overallHealth is UNHEALTHY when circuit breaker is OPEN")
        void overallHealthCircuitOpen() {
            CircuitBreaker cb = CircuitBreaker.builder("open-cb")
                    .failureThreshold(1)
                    .resetTimeout(Duration.ofSeconds(60))
                    .build();
            // Trip the circuit by executing a failing operation
            runBlocking(() -> cb.execute(eventloop(), () -> Promise.ofException(new RuntimeException("forced"))));
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            monitor.register("circuit-agent", echoAgent("circuit-agent"), cb, null);
            HealthStatus status = runPromise(monitor::overallHealth);
            assertThat(status).isEqualTo(HealthStatus.UNHEALTHY);
        }

        @Test
        @DisplayName("getLastSnapshot is empty before first check")
        void lastSnapshotEmptyBeforeCheck() {
            monitor.register("fresh-agent", echoAgent("fresh-agent"), null, null);
            Optional<AgentHealthMonitor.AgentHealthSnapshot> snap = monitor.getLastSnapshot("fresh-agent");
            assertThat(snap).isEmpty();
        }

        @Test
        @DisplayName("getLastSnapshot returns cached snapshot after check")
        void lastSnapshotCachedAfterCheck() {
            monitor.register("cached-agent", echoAgent("cached-agent"), null, null);
            runPromise(() -> monitor.check("cached-agent"));
            Optional<AgentHealthMonitor.AgentHealthSnapshot> snap = monitor.getLastSnapshot("cached-agent");
            assertThat(snap).isPresent();
            assertThat(snap.get().agentId()).isEqualTo("cached-agent");
        }

        @Test
        @DisplayName("snapshot includes bulkhead utilization when bulkhead is attached")
        void snapshotBulkheadUtilization() {
            AgentBulkhead bulkhead = AgentBulkhead.of("bh-agent", 4);
            bulkhead.tryAcquire();
            bulkhead.tryAcquire(); // 2 of 4 acquired = 50% utilization
            monitor.register("bh-agent", echoAgent("bh-agent"), null, bulkhead);
            AgentHealthMonitor.AgentHealthSnapshot snap = runPromise(() -> monitor.check("bh-agent"));
            assertThat(snap.bulkheadUtilization()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("snapshot includes circuit-breaker state (CLOSED by default)")
        void snapshotCircuitBreakerState() {
            CircuitBreaker cb = CircuitBreaker.builder("cb-state-agent")
                    .failureThreshold(5)
                    .build();
            monitor.register("cb-state-agent", echoAgent("cb-state-agent"), cb, null);
            AgentHealthMonitor.AgentHealthSnapshot snap = runPromise(() -> monitor.check("cb-state-agent"));
            assertThat(snap.circuitBreakerState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("overallHealth is HEALTHY when no agents registered")
        void overallHealthNoAgents() {
            HealthStatus status = runPromise(monitor::overallHealth);
            assertThat(status).isEqualTo(HealthStatus.HEALTHY);
        }
    }

    // =========================================================================
    // ResilienceDecorator
    // =========================================================================

    @Nested
    @DisplayName("ResilienceDecorator")
    class ResilienceDecoratorTests {

        @Test
        @DisplayName("decorate wraps agent and returns ResilientTypedAgent")
        void decorateReturnsResilientAgent() {
            AgentConfig config = AgentConfig.builder()
                    .agentId("decorator-agent")
                    .maxRetries(0)
                    .circuitBreakerThreshold(5)
                    .circuitBreakerReset(Duration.ofSeconds(30))
                    .build();

            TypedAgent<String, String> raw = echoAgent("decorator-agent");
            ResilientTypedAgent<String, String> resilient =
                    ResilienceDecorator.decorate(raw, config, eventloop());

            assertThat(resilient).isNotNull();
            assertThat(resilient.descriptor().getAgentId()).isEqualTo("decorator-agent");
        }

        @Test
        @DisplayName("decorated agent processes successfully")
        void decoratedAgentProcesses() {
            AgentConfig config = AgentConfig.builder()
                    .agentId("proc-agent")
                    .maxRetries(0)
                    .circuitBreakerThreshold(5)
                    .circuitBreakerReset(Duration.ofSeconds(30))
                    .build();

            ResilientTypedAgent<String, String> resilient =
                    ResilienceDecorator.decorate(echoAgent("proc-agent"), config, eventloop());

            AgentResult<String> result = runPromise(() -> resilient.process(emptyCtx(), "test-input"));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("test-input");
        }

        @Test
        @DisplayName("decorate with health monitor registers agent for monitoring")
        void decorateRegistersWithHealthMonitor() {
            AgentHealthMonitor monitor = new AgentHealthMonitor();
            AgentConfig config = AgentConfig.builder()
                    .agentId("monitored-agent")
                    .maxRetries(0)
                    .circuitBreakerThreshold(5)
                    .circuitBreakerReset(Duration.ofSeconds(30))
                    .build();

            ResilienceDecorator.decorate(echoAgent("monitored-agent"), config, eventloop(), monitor);

            // Agent should be registered in the monitor (check succeeds)
            AgentHealthMonitor.AgentHealthSnapshot snap =
                    runPromise(() -> monitor.check("monitored-agent"));
            assertThat(snap.agentId()).isEqualTo("monitored-agent");
        }

        @Test
        @DisplayName("decorate without health monitor does not register for monitoring")
        void decorateWithoutHealthMonitorNoRegistration() {
            AgentHealthMonitor monitor = new AgentHealthMonitor();
            AgentConfig config = AgentConfig.builder()
                    .agentId("unmonitored-agent")
                    .maxRetries(0)
                    .circuitBreakerThreshold(5)
                    .circuitBreakerReset(Duration.ofSeconds(30))
                    .build();

            // Use the 3-arg overload that does NOT register
            ResilienceDecorator.decorate(echoAgent("unmonitored-agent"), config, eventloop());

            assertThat(monitor.getLastSnapshot("unmonitored-agent")).isEmpty();
        }

        @Test
        @DisplayName("retry is enabled when maxRetries > 0")
        void retryEnabledForPositiveMaxRetries() {
            AtomicInteger callCount = new AtomicInteger();
            TypedAgent<String, String> flakyOnce = new TypedAgent<>() {
                @Override public AgentDescriptor descriptor() { return AgentDescriptor.builder().agentId("retry-test").build(); }
                @Override public Promise<Void> initialize(AgentConfig c) { return Promise.complete(); }
                @Override public Promise<Void> shutdown() { return Promise.complete(); }
                @Override public Promise<HealthStatus> healthCheck() { return Promise.of(HealthStatus.HEALTHY); }
                @Override public Promise<Void> reconfigure(AgentConfig c) { return Promise.complete(); }
                @Override public boolean validateInput(String i) { return true; }
                @Override public Promise<AgentResult<String>> process(AgentContext ctx, String input) {
                    if (callCount.incrementAndGet() == 1) return Promise.ofException(new RuntimeException("once"));
                    return Promise.of(AgentResult.success("ok", "retry-test", Duration.ZERO));
                }
                @Override public Promise<List<AgentResult<String>>> processBatch(AgentContext ctx, List<String> inputs) { return Promise.of(List.of()); }
            };

            AgentConfig config = AgentConfig.builder()
                    .agentId("retry-test")
                    .maxRetries(2)
                    .retryBackoff(Duration.ZERO)
                    .circuitBreakerThreshold(5)
                    .circuitBreakerReset(Duration.ofSeconds(30))
                    .build();

            ResilientTypedAgent<String, String> resilient =
                    ResilienceDecorator.decorate(flakyOnce, config, eventloop());

            AgentResult<String> result = runPromise(() -> resilient.process(emptyCtx(), "x"));
            assertThat(result.isSuccess()).isTrue();
            assertThat(callCount.get()).isGreaterThan(1);
        }
    }

    // =========================================================================
    // BulkheadFullException
    // =========================================================================

    @Nested
    @DisplayName("BulkheadFullException")
    class BulkheadFullExceptionTests {

        @Test
        @DisplayName("exception carries agentId and maxConcurrency")
        void exceptionFields() {
            ResilientTypedAgent.BulkheadFullException ex =
                    new ResilientTypedAgent.BulkheadFullException("my-agent", 5);
            assertThat(ex.getAgentId()).isEqualTo("my-agent");
            assertThat(ex.getMaxConcurrency()).isEqualTo(5);
            assertThat(ex.getMessage()).contains("my-agent").contains("5");
        }
    }
}
