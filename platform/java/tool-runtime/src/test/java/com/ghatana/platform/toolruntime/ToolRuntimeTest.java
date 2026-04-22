/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("Tool Runtime [GH-90000]")
class ToolRuntimeTest extends EventloopTestBase {

    @Nested
    @DisplayName("NoopToolSandbox [GH-90000]")
    class NoopSandboxTests {

        @Test
        @DisplayName("returns 'noop:<toolName>' for any invocation [GH-90000]")
        void returnsNoopResponse() { // GH-90000
            String result = runPromise(() -> // GH-90000
                NoopToolSandbox.INSTANCE.execute("t1", "agent1", "search", Map.of("q", "hello"))); // GH-90000
            assertThat(result).isEqualTo("noop:search [GH-90000]");
        }

        @Test
        @DisplayName("INSTANCE singleton is not null [GH-90000]")
        void instanceNotNull() { // GH-90000
            assertThat(NoopToolSandbox.INSTANCE).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("InMemoryToolExecutionMonitor [GH-90000]")
    class MonitorTests {

        @Test
        @DisplayName("getStats returns zeroed stats for unknown tool [GH-90000]")
        void zeroStatsForUnknownTool() { // GH-90000
            InMemoryToolExecutionMonitor monitor = new InMemoryToolExecutionMonitor(); // GH-90000
            ToolExecutionStats stats = runPromise(() -> monitor.getStats("t1", "search")); // GH-90000
            assertThat(stats.totalInvocations()).isZero(); // GH-90000
            assertThat(stats.avgDurationMs()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("records one successful execution [GH-90000]")
        void recordSuccess() { // GH-90000
            InMemoryToolExecutionMonitor monitor = new InMemoryToolExecutionMonitor(); // GH-90000
            runBlocking(() -> monitor.record("t1", "agent1", "search", Duration.ofMillis(50), 128, true)); // GH-90000

            ToolExecutionStats stats = runPromise(() -> monitor.getStats("t1", "search")); // GH-90000
            assertThat(stats.totalInvocations()).isEqualTo(1); // GH-90000
            assertThat(stats.successCount()).isEqualTo(1); // GH-90000
            assertThat(stats.failureCount()).isZero(); // GH-90000
            assertThat(stats.avgDurationMs()).isEqualTo(50.0); // GH-90000
        }

        @Test
        @DisplayName("records a failure execution [GH-90000]")
        void recordFailure() { // GH-90000
            InMemoryToolExecutionMonitor monitor = new InMemoryToolExecutionMonitor(); // GH-90000
            runBlocking(() -> monitor.record("t1", "agent1", "search", Duration.ofMillis(10), 0, false)); // GH-90000

            ToolExecutionStats stats = runPromise(() -> monitor.getStats("t1", "search")); // GH-90000
            assertThat(stats.failureCount()).isEqualTo(1); // GH-90000
            assertThat(stats.successRate()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("aggregates multiple executions and computes average [GH-90000]")
        void aggregatesMultipleExecutions() { // GH-90000
            InMemoryToolExecutionMonitor monitor = new InMemoryToolExecutionMonitor(); // GH-90000
            runBlocking(() -> monitor.record("t1", "agent1", "search", Duration.ofMillis(100), 50, true)); // GH-90000
            runBlocking(() -> monitor.record("t1", "agent1", "search", Duration.ofMillis(200), 60, true)); // GH-90000

            ToolExecutionStats stats = runPromise(() -> monitor.getStats("t1", "search")); // GH-90000
            assertThat(stats.totalInvocations()).isEqualTo(2); // GH-90000
            assertThat(stats.avgDurationMs()).isEqualTo(150.0); // GH-90000
            assertThat(stats.successRate()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("tenants are isolated in stats [GH-90000]")
        void tenantsAreIsolated() { // GH-90000
            InMemoryToolExecutionMonitor monitor = new InMemoryToolExecutionMonitor(); // GH-90000
            runBlocking(() -> monitor.record("tenantA", "agent1", "search", Duration.ofMillis(10), 0, true)); // GH-90000

            ToolExecutionStats statsB = runPromise(() -> monitor.getStats("tenantB", "search")); // GH-90000
            assertThat(statsB.totalInvocations()).isZero(); // GH-90000
        }
    }

    @Nested
    @DisplayName("ToolExecutionStats [GH-90000]")
    class StatsTests {

        @Test
        @DisplayName("successRate returns 0.0 when totalInvocations is zero [GH-90000]")
        void successRateZeroWhenNoInvocations() { // GH-90000
            ToolExecutionStats stats = new ToolExecutionStats("t1", "s", 0, 0, 0, 0.0); // GH-90000
            assertThat(stats.successRate()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("successRate computes correctly [GH-90000]")
        void successRateComputed() { // GH-90000
            ToolExecutionStats stats = new ToolExecutionStats("t1", "s", 4, 3, 1, 100.0); // GH-90000
            assertThat(stats.successRate()).isEqualTo(0.75); // GH-90000
        }
    }
}
