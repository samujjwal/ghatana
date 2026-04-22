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
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AgentExecutionMetrics}.
 *
 * <p>Verifies that the correct counters, timers, and error events are emitted
 * for each lifecycle event and that constructor guards reject nulls.
 */
@DisplayName("AgentExecutionMetrics [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class AgentExecutionMetricsTest {

    @Mock
    private MetricsCollector metricsCollector;

    private MeterRegistry meterRegistry;
    private AgentExecutionMetrics metrics;

    @BeforeEach
    void setUp() { // GH-90000
        meterRegistry = new SimpleMeterRegistry(); // GH-90000
        metrics = new AgentExecutionMetrics(metricsCollector, meterRegistry); // GH-90000
    }

    // ─── Constructor guards ────────────────────────────────────────────────

    @Test
    @DisplayName("constructor rejects null MetricsCollector [GH-90000]")
    void constructor_rejectsNullMetricsCollector() { // GH-90000
        assertThatThrownBy(() -> new AgentExecutionMetrics(null, meterRegistry)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("constructor rejects null MeterRegistry [GH-90000]")
    void constructor_rejectsNullMeterRegistry() { // GH-90000
        assertThatThrownBy(() -> new AgentExecutionMetrics(metricsCollector, null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ─── startDispatch ─────────────────────────────────────────────────────

    @Test
    @DisplayName("startDispatch increments dispatched counter and returns a Sample [GH-90000]")
    void startDispatch_incrementsCounterAndReturnsSample() { // GH-90000
        Timer.Sample sample = metrics.startDispatch("expert.java", "tenant-1"); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                AgentExecutionMetrics.METRIC_DISPATCHED,
                "agent_id", "expert.java",
                "tenant",   "tenant-1");
        assertThat(sample).isNotNull(); // GH-90000
    }

    // ─── recordDispatchSuccess ──────────────────────────────────────────────

    @Test
    @DisplayName("recordDispatchSuccess increments succeeded counter and stops timer [GH-90000]")
    void recordDispatchSuccess_incrementsCounterAndStopsTimer() { // GH-90000
        Timer.Sample sample = metrics.startDispatch("code-review-v2", "tenant-a"); // GH-90000

        metrics.recordDispatchSuccess(sample, "code-review-v2", "tenant-a"); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                AgentExecutionMetrics.METRIC_SUCCEEDED,
                "agent_id", "code-review-v2",
                "tenant",   "tenant-a");
        // verify at least one timer registered in the registry
        assertThat(meterRegistry.find(AgentExecutionMetrics.METRIC_DURATION).timers()).isNotEmpty(); // GH-90000
    }

    // ─── recordDispatchFailure ─────────────────────────────────────────────

    @Test
    @DisplayName("recordDispatchFailure increments failed counter and records error [GH-90000]")
    void recordDispatchFailure_withCause_incrementsAndRecordsError() { // GH-90000
        Timer.Sample sample = metrics.startDispatch("scaffold-agent", "tenant-x"); // GH-90000
        RuntimeException cause = new RuntimeException("LLM timeout [GH-90000]");

        metrics.recordDispatchFailure(sample, "scaffold-agent", "tenant-x", "timeout", cause); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                AgentExecutionMetrics.METRIC_FAILED,
                "agent_id",   "scaffold-agent",
                "tenant",     "tenant-x",
                "error_type", "timeout");
        verify(metricsCollector).recordError( // GH-90000
                eq(AgentExecutionMetrics.METRIC_FAILED), // GH-90000
                eq(cause), // GH-90000
                any()); // GH-90000
    }

    @Test
    @DisplayName("recordDispatchFailure without cause skips recordError [GH-90000]")
    void recordDispatchFailure_withoutCause_skipsRecordError() { // GH-90000
        Timer.Sample sample = metrics.startDispatch("agent-x", "t1"); // GH-90000

        metrics.recordDispatchFailure(sample, "agent-x", "t1", "validation", null); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                AgentExecutionMetrics.METRIC_FAILED,
                "agent_id",   "agent-x",
                "tenant",     "t1",
                "error_type", "validation");
        verify(metricsCollector, never()).recordError(anyString(), any(), any()); // GH-90000
    }

    @Test
    @DisplayName("recordDispatchFailure with null sample logs a warning and does not throw [GH-90000]")
    void recordDispatchFailure_withNullSample_doesNotThrow() { // GH-90000
        // should not throw even with null sample
        metrics.recordDispatchFailure(null, "agent-x", "t1", "unknown", null); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                AgentExecutionMetrics.METRIC_FAILED,
                "agent_id",   "agent-x",
                "tenant",     "t1",
                "error_type", "unknown");
    }

    // ─── Registry lifecycle ────────────────────────────────────────────────

    @Test
    @DisplayName("recordAgentRegistered increments registered counter [GH-90000]")
    void recordAgentRegistered_incrementsCounter() { // GH-90000
        metrics.recordAgentRegistered("new-agent", "tenant-1"); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                AgentExecutionMetrics.METRIC_REGISTERED,
                "agent_id", "new-agent",
                "tenant",   "tenant-1");
    }

    @Test
    @DisplayName("recordAgentUnregistered increments unregistered counter [GH-90000]")
    void recordAgentUnregistered_incrementsCounter() { // GH-90000
        metrics.recordAgentUnregistered("old-agent", "tenant-1"); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                AgentExecutionMetrics.METRIC_UNREGISTERED,
                "agent_id", "old-agent",
                "tenant",   "tenant-1");
    }

    // ─── Full round-trip with real registry ───────────────────────────────

    @Test
    @DisplayName("full dispatch success round-trip records duration timer in registry [GH-90000]")
    void fullRoundTrip_recordsDurationInRegistry() { // GH-90000
        MeterRegistry real = new SimpleMeterRegistry(); // GH-90000
        AgentExecutionMetrics m = new AgentExecutionMetrics(metricsCollector, real); // GH-90000

        Timer.Sample sample = m.startDispatch("expert.java", "t1"); // GH-90000
        m.recordDispatchSuccess(sample, "expert.java", "t1"); // GH-90000

        assertThat(real.find(AgentExecutionMetrics.METRIC_DURATION) // GH-90000
                .tag("status", "success") // GH-90000
                .timer()) // GH-90000
                .isNotNull(); // GH-90000
    }
}
