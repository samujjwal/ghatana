/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for tool-runtime: {@link NoopToolSandbox}, {@link InMemoryToolExecutionMonitor},
 * and {@link ToolExecutionStats}.
 */
@DisplayName("Tool Runtime")
class ToolRuntimeTest extends EventloopTestBase {

    @Nested
    @DisplayName("NoopToolSandbox")
    class NoopSandboxTests {

        @Test
        @DisplayName("returns 'noop:<toolName>' for any invocation")
        void returnsNoopResponse() {
            String result = runPromise(() ->
                NoopToolSandbox.INSTANCE.execute("t1", "agent1", "search", Map.of("q", "hello")));
            assertThat(result).isEqualTo("noop:search");
        }

        @Test
        @DisplayName("INSTANCE singleton is not null")
        void instanceNotNull() {
            assertThat(NoopToolSandbox.INSTANCE).isNotNull();
        }
    }

    @Nested
    @DisplayName("InMemoryToolExecutionMonitor")
    class MonitorTests {

        @Test
        @DisplayName("getStats returns zeroed stats for unknown tool")
        void zeroStatsForUnknownTool() {
            InMemoryToolExecutionMonitor monitor = new InMemoryToolExecutionMonitor();
            ToolExecutionStats stats = runPromise(() -> monitor.getStats("t1", "search"));
            assertThat(stats.totalInvocations()).isZero();
            assertThat(stats.avgDurationMs()).isZero();
        }

        @Test
        @DisplayName("records one successful execution")
        void recordSuccess() {
            InMemoryToolExecutionMonitor monitor = new InMemoryToolExecutionMonitor();
            runBlocking(() -> monitor.record("t1", "agent1", "search", Duration.ofMillis(50), 128, true));

            ToolExecutionStats stats = runPromise(() -> monitor.getStats("t1", "search"));
            assertThat(stats.totalInvocations()).isEqualTo(1);
            assertThat(stats.successCount()).isEqualTo(1);
            assertThat(stats.failureCount()).isZero();
            assertThat(stats.avgDurationMs()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("records a failure execution")
        void recordFailure() {
            InMemoryToolExecutionMonitor monitor = new InMemoryToolExecutionMonitor();
            runBlocking(() -> monitor.record("t1", "agent1", "search", Duration.ofMillis(10), 0, false));

            ToolExecutionStats stats = runPromise(() -> monitor.getStats("t1", "search"));
            assertThat(stats.failureCount()).isEqualTo(1);
            assertThat(stats.successRate()).isZero();
        }

        @Test
        @DisplayName("aggregates multiple executions and computes average")
        void aggregatesMultipleExecutions() {
            InMemoryToolExecutionMonitor monitor = new InMemoryToolExecutionMonitor();
            runBlocking(() -> monitor.record("t1", "agent1", "search", Duration.ofMillis(100), 50, true));
            runBlocking(() -> monitor.record("t1", "agent1", "search", Duration.ofMillis(200), 60, true));

            ToolExecutionStats stats = runPromise(() -> monitor.getStats("t1", "search"));
            assertThat(stats.totalInvocations()).isEqualTo(2);
            assertThat(stats.avgDurationMs()).isEqualTo(150.0);
            assertThat(stats.successRate()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("tenants are isolated in stats")
        void tenantsAreIsolated() {
            InMemoryToolExecutionMonitor monitor = new InMemoryToolExecutionMonitor();
            runBlocking(() -> monitor.record("tenantA", "agent1", "search", Duration.ofMillis(10), 0, true));

            ToolExecutionStats statsB = runPromise(() -> monitor.getStats("tenantB", "search"));
            assertThat(statsB.totalInvocations()).isZero();
        }
    }

    @Nested
    @DisplayName("ToolExecutionStats")
    class StatsTests {

        @Test
        @DisplayName("successRate returns 0.0 when totalInvocations is zero")
        void successRateZeroWhenNoInvocations() {
            ToolExecutionStats stats = new ToolExecutionStats("t1", "s", 0, 0, 0, 0.0);
            assertThat(stats.successRate()).isZero();
        }

        @Test
        @DisplayName("successRate computes correctly")
        void successRateComputed() {
            ToolExecutionStats stats = new ToolExecutionStats("t1", "s", 4, 3, 1, 100.0);
            assertThat(stats.successRate()).isEqualTo(0.75);
        }
    }
}
