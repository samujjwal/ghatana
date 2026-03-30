package com.ghatana.yappc.platform.observability;

import com.ghatana.platform.observability.MetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AgentExecutionMetrics}.
 *
 * <p>Verifies that the correct counters, timers, and error events are emitted
 * for each lifecycle event and that constructor guards reject nulls.
 */
@DisplayName("AgentExecutionMetrics")
@ExtendWith(MockitoExtension.class)
class AgentExecutionMetricsTest {

    @Mock
    private MetricsCollector metricsCollector;

    private MeterRegistry meterRegistry;
    private AgentExecutionMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new AgentExecutionMetrics(metricsCollector, meterRegistry);
    }

    // ─── Constructor guards ────────────────────────────────────────────────

    @Test
    @DisplayName("constructor rejects null MetricsCollector")
    void constructor_rejectsNullMetricsCollector() {
        assertThatThrownBy(() -> new AgentExecutionMetrics(null, meterRegistry))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("constructor rejects null MeterRegistry")
    void constructor_rejectsNullMeterRegistry() {
        assertThatThrownBy(() -> new AgentExecutionMetrics(metricsCollector, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ─── startDispatch ─────────────────────────────────────────────────────

    @Test
    @DisplayName("startDispatch increments dispatched counter and returns a Sample")
    void startDispatch_incrementsCounterAndReturnsSample() {
        Timer.Sample sample = metrics.startDispatch("expert.java", "tenant-1");

        verify(metricsCollector).incrementCounter(
                AgentExecutionMetrics.METRIC_DISPATCHED,
                "agent_id", "expert.java",
                "tenant",   "tenant-1");
        assertThat(sample).isNotNull();
    }

    // ─── recordDispatchSuccess ──────────────────────────────────────────────

    @Test
    @DisplayName("recordDispatchSuccess increments succeeded counter and stops timer")
    void recordDispatchSuccess_incrementsCounterAndStopsTimer() {
        Timer.Sample sample = metrics.startDispatch("code-review-v2", "tenant-a");

        metrics.recordDispatchSuccess(sample, "code-review-v2", "tenant-a");

        verify(metricsCollector).incrementCounter(
                AgentExecutionMetrics.METRIC_SUCCEEDED,
                "agent_id", "code-review-v2",
                "tenant",   "tenant-a");
        // verify at least one timer registered in the registry
        assertThat(meterRegistry.find(AgentExecutionMetrics.METRIC_DURATION).timers()).isNotEmpty();
    }

    // ─── recordDispatchFailure ─────────────────────────────────────────────

    @Test
    @DisplayName("recordDispatchFailure increments failed counter and records error")
    void recordDispatchFailure_withCause_incrementsAndRecordsError() {
        Timer.Sample sample = metrics.startDispatch("scaffold-agent", "tenant-x");
        RuntimeException cause = new RuntimeException("LLM timeout");

        metrics.recordDispatchFailure(sample, "scaffold-agent", "tenant-x", "timeout", cause);

        verify(metricsCollector).incrementCounter(
                AgentExecutionMetrics.METRIC_FAILED,
                "agent_id",   "scaffold-agent",
                "tenant",     "tenant-x",
                "error_type", "timeout");
        verify(metricsCollector).recordError(
                eq(AgentExecutionMetrics.METRIC_FAILED),
                eq(cause),
                any());
    }

    @Test
    @DisplayName("recordDispatchFailure without cause skips recordError")
    void recordDispatchFailure_withoutCause_skipsRecordError() {
        Timer.Sample sample = metrics.startDispatch("agent-x", "t1");

        metrics.recordDispatchFailure(sample, "agent-x", "t1", "validation", null);

        verify(metricsCollector).incrementCounter(
                AgentExecutionMetrics.METRIC_FAILED,
                "agent_id",   "agent-x",
                "tenant",     "t1",
                "error_type", "validation");
        verify(metricsCollector, never()).recordError(anyString(), any(), any());
    }

    @Test
    @DisplayName("recordDispatchFailure with null sample logs a warning and does not throw")
    void recordDispatchFailure_withNullSample_doesNotThrow() {
        // should not throw even with null sample
        metrics.recordDispatchFailure(null, "agent-x", "t1", "unknown", null);

        verify(metricsCollector).incrementCounter(
                AgentExecutionMetrics.METRIC_FAILED,
                "agent_id",   "agent-x",
                "tenant",     "t1",
                "error_type", "unknown");
    }

    // ─── Registry lifecycle ────────────────────────────────────────────────

    @Test
    @DisplayName("recordAgentRegistered increments registered counter")
    void recordAgentRegistered_incrementsCounter() {
        metrics.recordAgentRegistered("new-agent", "tenant-1");

        verify(metricsCollector).incrementCounter(
                AgentExecutionMetrics.METRIC_REGISTERED,
                "agent_id", "new-agent",
                "tenant",   "tenant-1");
    }

    @Test
    @DisplayName("recordAgentUnregistered increments unregistered counter")
    void recordAgentUnregistered_incrementsCounter() {
        metrics.recordAgentUnregistered("old-agent", "tenant-1");

        verify(metricsCollector).incrementCounter(
                AgentExecutionMetrics.METRIC_UNREGISTERED,
                "agent_id", "old-agent",
                "tenant",   "tenant-1");
    }

    // ─── Full round-trip with real registry ───────────────────────────────

    @Test
    @DisplayName("full dispatch success round-trip records duration timer in registry")
    void fullRoundTrip_recordsDurationInRegistry() {
        MeterRegistry real = new SimpleMeterRegistry();
        AgentExecutionMetrics m = new AgentExecutionMetrics(metricsCollector, real);

        Timer.Sample sample = m.startDispatch("expert.java", "t1");
        m.recordDispatchSuccess(sample, "expert.java", "t1");

        assertThat(real.find(AgentExecutionMetrics.METRIC_DURATION)
                .tag("status", "success")
                .timer())
                .isNotNull();
    }
}
