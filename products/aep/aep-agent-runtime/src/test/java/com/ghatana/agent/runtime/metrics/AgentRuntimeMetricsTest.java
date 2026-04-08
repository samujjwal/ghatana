/*
 * Copyright (c) 2026 Ghatana Inc.
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
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AgentRuntimeMetrics(registry);
    }

    // ── Constant inventory ───────────────────────────────────────────────────

    @Nested
    @DisplayName("metric name constants")
    class MetricNameConstants {

        @Test
        @DisplayName("all 11 metric name constants are non-blank")
        void allMetricNamesNonBlank() {
            assertThat(AgentRuntimeMetrics.METRIC_DISPATCH_TOTAL).isNotBlank();
            assertThat(AgentRuntimeMetrics.METRIC_DISPATCH_DENIED).isNotBlank();
            assertThat(AgentRuntimeMetrics.METRIC_DISPATCH_DURATION).isNotBlank();
            assertThat(AgentRuntimeMetrics.METRIC_POLICY_EVAL_TOTAL).isNotBlank();
            assertThat(AgentRuntimeMetrics.METRIC_POLICY_EVAL_DENIED).isNotBlank();
            assertThat(AgentRuntimeMetrics.METRIC_INVARIANT_VIOLATION).isNotBlank();
            assertThat(AgentRuntimeMetrics.METRIC_TOOL_EXEC_TOTAL).isNotBlank();
            assertThat(AgentRuntimeMetrics.METRIC_TOOL_EXEC_DENIED).isNotBlank();
            assertThat(AgentRuntimeMetrics.METRIC_TOOL_EXEC_DURATION).isNotBlank();
            assertThat(AgentRuntimeMetrics.METRIC_MEMORY_ACCESS_TOTAL).isNotBlank();
            assertThat(AgentRuntimeMetrics.METRIC_TURN_COMPLETED).isNotBlank();
        }

        @Test
        @DisplayName("all metric names use ghatana.agent prefix")
        void allMetricNamesHaveGhatanaPrefix() {
            String prefix = "ghatana.agent";
            assertThat(AgentRuntimeMetrics.METRIC_DISPATCH_TOTAL).startsWith(prefix);
            assertThat(AgentRuntimeMetrics.METRIC_DISPATCH_DENIED).startsWith(prefix);
            assertThat(AgentRuntimeMetrics.METRIC_DISPATCH_DURATION).startsWith(prefix);
            assertThat(AgentRuntimeMetrics.METRIC_POLICY_EVAL_TOTAL).startsWith(prefix);
            assertThat(AgentRuntimeMetrics.METRIC_POLICY_EVAL_DENIED).startsWith(prefix);
            assertThat(AgentRuntimeMetrics.METRIC_INVARIANT_VIOLATION).startsWith(prefix);
            assertThat(AgentRuntimeMetrics.METRIC_TOOL_EXEC_TOTAL).startsWith(prefix);
            assertThat(AgentRuntimeMetrics.METRIC_TOOL_EXEC_DENIED).startsWith(prefix);
            assertThat(AgentRuntimeMetrics.METRIC_TOOL_EXEC_DURATION).startsWith(prefix);
            assertThat(AgentRuntimeMetrics.METRIC_MEMORY_ACCESS_TOTAL).startsWith(prefix);
            assertThat(AgentRuntimeMetrics.METRIC_TURN_COMPLETED).startsWith(prefix);
        }

        @Test
        @DisplayName("null registry throws NullPointerException")
        void nullRegistryThrows() {
            assertThatThrownBy(() -> new AgentRuntimeMetrics(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ── Dispatch metrics ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("dispatch metrics")
    class DispatchMetrics {

        @Test
        @DisplayName("recordDispatch increments dispatch.total counter and records timer")
        void recordDispatchIncrements() {
            metrics.recordDispatch("agent-1", "tenant-1", "SUCCESS", Duration.ofMillis(100));

            Counter counter = registry.get(AgentRuntimeMetrics.METRIC_DISPATCH_TOTAL)
                    .tags("agentId", "agent-1", "tenantId", "tenant-1", "status", "SUCCESS")
                    .counter();
            assertThat(counter.count()).isEqualTo(1.0);

            Timer timer = registry.get(AgentRuntimeMetrics.METRIC_DISPATCH_DURATION)
                    .tags("agentId", "agent-1", "tenantId", "tenant-1")
                    .timer();
            assertThat(timer.count()).isEqualTo(1L);
        }

        @Test
        @DisplayName("recordDispatchDenied increments dispatch.denied counter")
        void recordDispatchDeniedIncrements() {
            metrics.recordDispatchDenied("agent-1", "tenant-1");
            metrics.recordDispatchDenied("agent-1", "tenant-1");

            Counter counter = registry.get(AgentRuntimeMetrics.METRIC_DISPATCH_DENIED)
                    .tags("agentId", "agent-1", "tenantId", "tenant-1")
                    .counter();
            assertThat(counter.count()).isEqualTo(2.0);
        }
    }

    // ── Policy eval metrics ──────────────────────────────────────────────────

    @Nested
    @DisplayName("policy eval metrics")
    class PolicyEvalMetrics {

        @Test
        @DisplayName("recordPolicyEval increments total and NOT denied when allowed")
        void recordPolicyEvalAllowedDoesNotIncrementDenied() {
            metrics.recordPolicyEval("agent-1", "tenant-1", true);

            Counter total = registry.get(AgentRuntimeMetrics.METRIC_POLICY_EVAL_TOTAL)
                    .tags("agentId", "agent-1", "tenantId", "tenant-1")
                    .counter();
            assertThat(total.count()).isEqualTo(1.0);

            // denied counter should not exist for tags not used
            assertThat(registry.find(AgentRuntimeMetrics.METRIC_POLICY_EVAL_DENIED)
                    .tags("agentId", "agent-1", "tenantId", "tenant-1")
                    .counter()).isNull();
        }

        @Test
        @DisplayName("recordPolicyEval increments both total and denied when denied")
        void recordPolicyEvalDeniedIncrementsTwo() {
            metrics.recordPolicyEval("agent-2", "tenant-2", false);

            Counter total = registry.get(AgentRuntimeMetrics.METRIC_POLICY_EVAL_TOTAL)
                    .tags("agentId", "agent-2", "tenantId", "tenant-2")
                    .counter();
            assertThat(total.count()).isEqualTo(1.0);

            Counter denied = registry.get(AgentRuntimeMetrics.METRIC_POLICY_EVAL_DENIED)
                    .tags("agentId", "agent-2", "tenantId", "tenant-2")
                    .counter();
            assertThat(denied.count()).isEqualTo(1.0);
        }
    }

    // ── Invariant violation metrics ──────────────────────────────────────────

    @Nested
    @DisplayName("invariant violation metrics")
    class InvariantViolationMetrics {

        @Test
        @DisplayName("recordInvariantViolation increments by count")
        void recordInvariantViolationByCount() {
            metrics.recordInvariantViolation("agent-1", "tenant-1", 3);

            Counter counter = registry.get(AgentRuntimeMetrics.METRIC_INVARIANT_VIOLATION)
                    .tags("agentId", "agent-1", "tenantId", "tenant-1")
                    .counter();
            assertThat(counter.count()).isEqualTo(3.0);
        }
    }

    // ── Tool execution metrics ───────────────────────────────────────────────

    @Nested
    @DisplayName("tool execution metrics")
    class ToolExecutionMetrics {

        @Test
        @DisplayName("recordToolExecution increments total and records timer")
        void recordToolExecutionIncrements() {
            metrics.recordToolExecution("agent-1", "tenant-1", "search-tool", "SUCCESS", Duration.ofMillis(50));

            Counter counter = registry.get(AgentRuntimeMetrics.METRIC_TOOL_EXEC_TOTAL)
                    .tags("agentId", "agent-1", "tenantId", "tenant-1", "toolId", "search-tool", "status", "SUCCESS")
                    .counter();
            assertThat(counter.count()).isEqualTo(1.0);

            Timer timer = registry.get(AgentRuntimeMetrics.METRIC_TOOL_EXEC_DURATION)
                    .tags("agentId", "agent-1", "tenantId", "tenant-1", "toolId", "search-tool")
                    .timer();
            assertThat(timer.count()).isEqualTo(1L);
        }

        @Test
        @DisplayName("recordToolExecutionDenied increments tool.execution.denied counter")
        void recordToolExecutionDeniedIncrements() {
            metrics.recordToolExecutionDenied("agent-1", "tenant-1", "danger-tool");

            Counter counter = registry.get(AgentRuntimeMetrics.METRIC_TOOL_EXEC_DENIED)
                    .tags("agentId", "agent-1", "tenantId", "tenant-1", "toolId", "danger-tool")
                    .counter();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }

    // ── Memory access metrics ────────────────────────────────────────────────

    @Nested
    @DisplayName("memory access metrics")
    class MemoryAccessMetrics {

        @Test
        @DisplayName("recordMemoryAccess increments memory.access.total counter")
        void recordMemoryAccessIncrements() {
            metrics.recordMemoryAccess("agent-1", "tenant-1", "READ");
            metrics.recordMemoryAccess("agent-1", "tenant-1", "READ");

            Counter counter = registry.get(AgentRuntimeMetrics.METRIC_MEMORY_ACCESS_TOTAL)
                    .tags("agentId", "agent-1", "tenantId", "tenant-1", "operation", "READ")
                    .counter();
            assertThat(counter.count()).isEqualTo(2.0);
        }
    }

    // ── Turn completed metrics ───────────────────────────────────────────────

    @Nested
    @DisplayName("turn completed metrics")
    class TurnCompletedMetrics {

        @Test
        @DisplayName("recordTurnCompleted increments turn.completed.total counter")
        void recordTurnCompletedIncrements() {
            metrics.recordTurnCompleted("agent-1", "tenant-1", "SUCCESS");

            Counter counter = registry.get(AgentRuntimeMetrics.METRIC_TURN_COMPLETED)
                    .tags("agentId", "agent-1", "tenantId", "tenant-1", "status", "SUCCESS")
                    .counter();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }

    // ── Registry access ──────────────────────────────────────────────────────

    @Test
    @DisplayName("registry() returns the underlying MeterRegistry")
    void registryReturnsUnderlyingRegistry() {
        assertThat(metrics.registry()).isSameAs(registry);
    }
}
