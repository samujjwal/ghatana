/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
/**
 * @doc.type class
 * @doc.purpose Tests for AgentRuntimeMetrics — verifies all 11 metrics register and record correctly
 * @doc.layer product
 * @doc.pattern Test
 */
package com.ghatana.agent.runtime.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AgentRuntimeMetrics}.
 */
@DisplayName("AgentRuntimeMetrics")
class AgentRuntimeMetricsTest {

    private MeterRegistry registry;
    private AgentRuntimeMetrics metrics;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new SimpleMeterRegistry(); // GH-90000
        metrics = new AgentRuntimeMetrics(registry); // GH-90000
    }

    // ── Constant inventory ───────────────────────────────────────────────────

    @Nested
    @DisplayName("metric name constants")
    class MetricNameConstants {

        @Test
        @DisplayName("all 11 metric name constants are non-blank")
        void allMetricNamesNonBlank() { // GH-90000
            assertThat(AgentRuntimeMetrics.METRIC_DISPATCH_TOTAL).isNotBlank(); // GH-90000
            assertThat(AgentRuntimeMetrics.METRIC_DISPATCH_DENIED).isNotBlank(); // GH-90000
            assertThat(AgentRuntimeMetrics.METRIC_DISPATCH_DURATION).isNotBlank(); // GH-90000
            assertThat(AgentRuntimeMetrics.METRIC_POLICY_EVAL_TOTAL).isNotBlank(); // GH-90000
            assertThat(AgentRuntimeMetrics.METRIC_POLICY_EVAL_DENIED).isNotBlank(); // GH-90000
            assertThat(AgentRuntimeMetrics.METRIC_INVARIANT_VIOLATION).isNotBlank(); // GH-90000
            assertThat(AgentRuntimeMetrics.METRIC_TOOL_EXEC_TOTAL).isNotBlank(); // GH-90000
            assertThat(AgentRuntimeMetrics.METRIC_TOOL_EXEC_DENIED).isNotBlank(); // GH-90000
            assertThat(AgentRuntimeMetrics.METRIC_TOOL_EXEC_DURATION).isNotBlank(); // GH-90000
            assertThat(AgentRuntimeMetrics.METRIC_MEMORY_ACCESS_TOTAL).isNotBlank(); // GH-90000
            assertThat(AgentRuntimeMetrics.METRIC_TURN_COMPLETED).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("all metric names use ghatana.agent prefix")
        void allMetricNamesHaveGhatanaPrefix() { // GH-90000
            String prefix = "ghatana.agent";
            assertThat(AgentRuntimeMetrics.METRIC_DISPATCH_TOTAL).startsWith(prefix); // GH-90000
            assertThat(AgentRuntimeMetrics.METRIC_DISPATCH_DENIED).startsWith(prefix); // GH-90000
            assertThat(AgentRuntimeMetrics.METRIC_DISPATCH_DURATION).startsWith(prefix); // GH-90000
            assertThat(AgentRuntimeMetrics.METRIC_POLICY_EVAL_TOTAL).startsWith(prefix); // GH-90000
            assertThat(AgentRuntimeMetrics.METRIC_POLICY_EVAL_DENIED).startsWith(prefix); // GH-90000
            assertThat(AgentRuntimeMetrics.METRIC_INVARIANT_VIOLATION).startsWith(prefix); // GH-90000
            assertThat(AgentRuntimeMetrics.METRIC_TOOL_EXEC_TOTAL).startsWith(prefix); // GH-90000
            assertThat(AgentRuntimeMetrics.METRIC_TOOL_EXEC_DENIED).startsWith(prefix); // GH-90000
            assertThat(AgentRuntimeMetrics.METRIC_TOOL_EXEC_DURATION).startsWith(prefix); // GH-90000
            assertThat(AgentRuntimeMetrics.METRIC_MEMORY_ACCESS_TOTAL).startsWith(prefix); // GH-90000
            assertThat(AgentRuntimeMetrics.METRIC_TURN_COMPLETED).startsWith(prefix); // GH-90000
        }

        @Test
        @DisplayName("null registry throws NullPointerException")
        void nullRegistryThrows() { // GH-90000
            assertThatThrownBy(() -> new AgentRuntimeMetrics(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // ── Dispatch metrics ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("dispatch metrics")
    class DispatchMetrics {

        @Test
        @DisplayName("recordDispatch increments dispatch.total counter and records timer")
        void recordDispatchIncrements() { // GH-90000
            metrics.recordDispatch("agent-1", "tenant-1", "SUCCESS", Duration.ofMillis(100)); // GH-90000

            Counter counter = registry.get(AgentRuntimeMetrics.METRIC_DISPATCH_TOTAL) // GH-90000
                    .tags("agentId", "agent-1", "tenantId", "tenant-1", "status", "SUCCESS") // GH-90000
                    .counter(); // GH-90000
            assertThat(counter.count()).isEqualTo(1.0); // GH-90000

            Timer timer = registry.get(AgentRuntimeMetrics.METRIC_DISPATCH_DURATION) // GH-90000
                    .tags("agentId", "agent-1", "tenantId", "tenant-1") // GH-90000
                    .timer(); // GH-90000
            assertThat(timer.count()).isEqualTo(1L); // GH-90000
        }

        @Test
        @DisplayName("recordDispatchDenied increments dispatch.denied counter")
        void recordDispatchDeniedIncrements() { // GH-90000
            metrics.recordDispatchDenied("agent-1", "tenant-1"); // GH-90000
            metrics.recordDispatchDenied("agent-1", "tenant-1"); // GH-90000

            Counter counter = registry.get(AgentRuntimeMetrics.METRIC_DISPATCH_DENIED) // GH-90000
                    .tags("agentId", "agent-1", "tenantId", "tenant-1") // GH-90000
                    .counter(); // GH-90000
            assertThat(counter.count()).isEqualTo(2.0); // GH-90000
        }
    }

    // ── Policy eval metrics ──────────────────────────────────────────────────

    @Nested
    @DisplayName("policy eval metrics")
    class PolicyEvalMetrics {

        @Test
        @DisplayName("recordPolicyEval increments total and NOT denied when allowed")
        void recordPolicyEvalAllowedDoesNotIncrementDenied() { // GH-90000
            metrics.recordPolicyEval("agent-1", "tenant-1", true); // GH-90000

            Counter total = registry.get(AgentRuntimeMetrics.METRIC_POLICY_EVAL_TOTAL) // GH-90000
                    .tags("agentId", "agent-1", "tenantId", "tenant-1") // GH-90000
                    .counter(); // GH-90000
            assertThat(total.count()).isEqualTo(1.0); // GH-90000

            // denied counter should not exist for tags not used
            assertThat(registry.find(AgentRuntimeMetrics.METRIC_POLICY_EVAL_DENIED) // GH-90000
                    .tags("agentId", "agent-1", "tenantId", "tenant-1") // GH-90000
                    .counter()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("recordPolicyEval increments both total and denied when denied")
        void recordPolicyEvalDeniedIncrementsTwo() { // GH-90000
            metrics.recordPolicyEval("agent-2", "tenant-2", false); // GH-90000

            Counter total = registry.get(AgentRuntimeMetrics.METRIC_POLICY_EVAL_TOTAL) // GH-90000
                    .tags("agentId", "agent-2", "tenantId", "tenant-2") // GH-90000
                    .counter(); // GH-90000
            assertThat(total.count()).isEqualTo(1.0); // GH-90000

            Counter denied = registry.get(AgentRuntimeMetrics.METRIC_POLICY_EVAL_DENIED) // GH-90000
                    .tags("agentId", "agent-2", "tenantId", "tenant-2") // GH-90000
                    .counter(); // GH-90000
            assertThat(denied.count()).isEqualTo(1.0); // GH-90000
        }
    }

    // ── Invariant violation metrics ──────────────────────────────────────────

    @Nested
    @DisplayName("invariant violation metrics")
    class InvariantViolationMetrics {

        @Test
        @DisplayName("recordInvariantViolation increments by count")
        void recordInvariantViolationByCount() { // GH-90000
            metrics.recordInvariantViolation("agent-1", "tenant-1", 3); // GH-90000

            Counter counter = registry.get(AgentRuntimeMetrics.METRIC_INVARIANT_VIOLATION) // GH-90000
                    .tags("agentId", "agent-1", "tenantId", "tenant-1") // GH-90000
                    .counter(); // GH-90000
            assertThat(counter.count()).isEqualTo(3.0); // GH-90000
        }
    }

    // ── Tool execution metrics ───────────────────────────────────────────────

    @Nested
    @DisplayName("tool execution metrics")
    class ToolExecutionMetrics {

        @Test
        @DisplayName("recordToolExecution increments total and records timer")
        void recordToolExecutionIncrements() { // GH-90000
            metrics.recordToolExecution("agent-1", "tenant-1", "search-tool", "SUCCESS", Duration.ofMillis(50)); // GH-90000

            Counter counter = registry.get(AgentRuntimeMetrics.METRIC_TOOL_EXEC_TOTAL) // GH-90000
                    .tags("agentId", "agent-1", "tenantId", "tenant-1", "toolId", "search-tool", "status", "SUCCESS") // GH-90000
                    .counter(); // GH-90000
            assertThat(counter.count()).isEqualTo(1.0); // GH-90000

            Timer timer = registry.get(AgentRuntimeMetrics.METRIC_TOOL_EXEC_DURATION) // GH-90000
                    .tags("agentId", "agent-1", "tenantId", "tenant-1", "toolId", "search-tool") // GH-90000
                    .timer(); // GH-90000
            assertThat(timer.count()).isEqualTo(1L); // GH-90000
        }

        @Test
        @DisplayName("recordToolExecutionDenied increments tool.execution.denied counter")
        void recordToolExecutionDeniedIncrements() { // GH-90000
            metrics.recordToolExecutionDenied("agent-1", "tenant-1", "danger-tool"); // GH-90000

            Counter counter = registry.get(AgentRuntimeMetrics.METRIC_TOOL_EXEC_DENIED) // GH-90000
                    .tags("agentId", "agent-1", "tenantId", "tenant-1", "toolId", "danger-tool") // GH-90000
                    .counter(); // GH-90000
            assertThat(counter.count()).isEqualTo(1.0); // GH-90000
        }
    }

    // ── Memory access metrics ────────────────────────────────────────────────

    @Nested
    @DisplayName("memory access metrics")
    class MemoryAccessMetrics {

        @Test
        @DisplayName("recordMemoryAccess increments memory.access.total counter")
        void recordMemoryAccessIncrements() { // GH-90000
            metrics.recordMemoryAccess("agent-1", "tenant-1", "READ"); // GH-90000
            metrics.recordMemoryAccess("agent-1", "tenant-1", "READ"); // GH-90000

            Counter counter = registry.get(AgentRuntimeMetrics.METRIC_MEMORY_ACCESS_TOTAL) // GH-90000
                    .tags("agentId", "agent-1", "tenantId", "tenant-1", "operation", "READ") // GH-90000
                    .counter(); // GH-90000
            assertThat(counter.count()).isEqualTo(2.0); // GH-90000
        }
    }

    // ── Turn completed metrics ───────────────────────────────────────────────

    @Nested
    @DisplayName("turn completed metrics")
    class TurnCompletedMetrics {

        @Test
        @DisplayName("recordTurnCompleted increments turn.completed.total counter")
        void recordTurnCompletedIncrements() { // GH-90000
            metrics.recordTurnCompleted("agent-1", "tenant-1", "SUCCESS"); // GH-90000

            Counter counter = registry.get(AgentRuntimeMetrics.METRIC_TURN_COMPLETED) // GH-90000
                    .tags("agentId", "agent-1", "tenantId", "tenant-1", "status", "SUCCESS") // GH-90000
                    .counter(); // GH-90000
            assertThat(counter.count()).isEqualTo(1.0); // GH-90000
        }
    }

    // ── Registry access ──────────────────────────────────────────────────────

    @Test
    @DisplayName("registry() returns the underlying MeterRegistry")
    void registryReturnsUnderlyingRegistry() { // GH-90000
        assertThat(metrics.registry()).isSameAs(registry); // GH-90000
    }
}
