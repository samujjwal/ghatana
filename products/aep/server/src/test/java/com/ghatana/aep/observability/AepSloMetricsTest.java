/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.observability;

import com.ghatana.platform.observability.MetricsCollectorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AepSloMetrics}.
 *
 * <p>Uses the no-op {@link com.ghatana.platform.observability.MetricsCollector} so no
 * Micrometer registry setup is needed — the tests verify counter/gauge state via the
 * {@code runCountSnapshot()} method. // GH-90000
 *
 * @doc.type class
 * @doc.purpose Unit tests for Phase-6 SLO metrics recording
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepSloMetrics")
class AepSloMetricsTest {

    private AepSloMetrics sloMetrics;

    @BeforeEach
    void setUp() { // GH-90000
        sloMetrics = new AepSloMetrics(MetricsCollectorFactory.createNoop()); // GH-90000
    }

    @Nested
    @DisplayName("runCountSnapshot")
    class RunCountSnapshot {

        @Test
        @DisplayName("initial snapshot has zero runs and zero failure rate")
        void initialSnapshotIsZero() { // GH-90000
            Map<String, Object> snapshot = sloMetrics.runCountSnapshot(); // GH-90000
            assertThat((Long) snapshot.get("completedRuns")).isZero();
            assertThat((Long) snapshot.get("totalRuns")).isZero();
            assertThat((Long) snapshot.get("failedRuns")).isZero();
            assertThat((Double) snapshot.get("runSuccessRate")).isZero();
            assertThat((Double) snapshot.get("runFailureRate")).isZero();
        }

        @Test
        @DisplayName("records completed runs and updates snapshot")
        void recordsCompletedRuns() { // GH-90000
            sloMetrics.recordRunCompleted("tenant-1", "pipeline-1", 200L); // GH-90000
            sloMetrics.recordRunCompleted("tenant-1", "pipeline-2", 300L); // GH-90000

            Map<String, Object> snapshot = sloMetrics.runCountSnapshot(); // GH-90000
            assertThat((Long) snapshot.get("completedRuns")).isEqualTo(2L);
            assertThat((Long) snapshot.get("totalRuns")).isEqualTo(2L);
            assertThat((Long) snapshot.get("failedRuns")).isZero();
            assertThat((Double) snapshot.get("runSuccessRate")).isEqualTo(1.0);
            assertThat((Double) snapshot.get("runFailureRate")).isZero();
        }

        @Test
        @DisplayName("records failed runs and computes failure rate")
        void recordsFailedRuns() { // GH-90000
            sloMetrics.recordRunCompleted("tenant-1", "pipeline-1", 100L); // GH-90000
            sloMetrics.recordRunFailed("tenant-1", "pipeline-1", 500L, "timeout"); // GH-90000

            Map<String, Object> snapshot = sloMetrics.runCountSnapshot(); // GH-90000
            assertThat((Long) snapshot.get("completedRuns")).isEqualTo(1L);
            assertThat((Long) snapshot.get("totalRuns")).isEqualTo(2L);
            assertThat((Long) snapshot.get("failedRuns")).isEqualTo(1L);
            assertThat((Double) snapshot.get("runSuccessRate")).isEqualTo(0.5);
            assertThat((Double) snapshot.get("runFailureRate")).isEqualTo(0.5);
        }

        @Test
        @DisplayName("failure rate is zero when no runs recorded")
        void failureRateZeroWithNoRuns() { // GH-90000
            assertThat((Double) sloMetrics.runCountSnapshot().get("runFailureRate")).isZero();
        }
    }

    @Nested
    @DisplayName("replaySnapshot")
    class ReplaySnapshot {

        @Test
        @DisplayName("tracks replay success and failure rates")
        void tracksReplayOutcomes() { // GH-90000
            sloMetrics.recordReplayAttempt(true, "tenant-1", "pipeline-1"); // GH-90000
            sloMetrics.recordReplayAttempt(false, "tenant-1", "pipeline-1"); // GH-90000

            Map<String, Object> snapshot = sloMetrics.replaySnapshot(); // GH-90000
            assertThat((Long) snapshot.get("attempts")).isEqualTo(2L);
            assertThat((Long) snapshot.get("succeeded")).isEqualTo(1L);
            assertThat((Long) snapshot.get("failed")).isEqualTo(1L);
            assertThat((Double) snapshot.get("successRate")).isEqualTo(0.5);
            assertThat((Double) snapshot.get("failureRate")).isEqualTo(0.5);
        }
    }

    @Nested
    @DisplayName("agentExecutionSnapshot")
    class AgentExecutionSnapshot {

        @Test
        @DisplayName("tracks agent execution success and failure rates")
        void tracksAgentExecutionOutcomes() { // GH-90000
            sloMetrics.recordAgentExecutionSuccess("tenant-1", "agent-1", 125L); // GH-90000
            sloMetrics.recordAgentExecutionFailure( // GH-90000
                "tenant-1",
                "agent-1",
                "AGENT_EXECUTION_TIMEOUT",
                "transient",
                true,
                240L);

            Map<String, Object> snapshot = sloMetrics.agentExecutionSnapshot(); // GH-90000
            assertThat((Long) snapshot.get("attempts")).isEqualTo(2L);
            assertThat((Long) snapshot.get("succeeded")).isEqualTo(1L);
            assertThat((Long) snapshot.get("failed")).isEqualTo(1L);
            assertThat((Double) snapshot.get("successRate")).isEqualTo(0.5);
            assertThat((Double) snapshot.get("failureRate")).isEqualTo(0.5);
        }
    }

    @Nested
    @DisplayName("SLO recording methods")
    class SloRecordingMethods {

        @Test
        @DisplayName("recordIntakeLatency does not throw")
        void recordIntakeLatencyDoesNotThrow() { // GH-90000
            Instant received = Instant.now().minusMillis(50); // GH-90000
            Instant processed = Instant.now(); // GH-90000
            sloMetrics.recordIntakeLatency(received, processed, "tenant-1"); // GH-90000
        }

        @Test
        @DisplayName("recordRunCompleted increments total run counter")
        void recordRunCompletedIncrementsTotal() { // GH-90000
            sloMetrics.recordRunCompleted("tenant-1", "pipeline-1", 100L); // GH-90000
            assertThat((Long) sloMetrics.runCountSnapshot().get("totalRuns")).isEqualTo(1L);
        }

        @Test
        @DisplayName("recordRunFailed increments both total and failed counters")
        void recordRunFailedIncrementsFailedAndTotal() { // GH-90000
            sloMetrics.recordRunFailed("tenant-1", "pipeline-1", 100L, "engine_error"); // GH-90000
            Map<String, Object> snap = sloMetrics.runCountSnapshot(); // GH-90000
            assertThat((Long) snap.get("totalRuns")).isEqualTo(1L);
            assertThat((Long) snap.get("failedRuns")).isEqualTo(1L);
        }

        @Test
        @DisplayName("recordReviewQueueLatency does not throw")
        void recordReviewQueueLatencyDoesNotThrow() { // GH-90000
            Instant enqueued = Instant.now().minusSeconds(30); // GH-90000
            Instant decided = Instant.now(); // GH-90000
            sloMetrics.recordReviewQueueLatency(enqueued, decided, "tenant-1", "POLICY"); // GH-90000
        }

        @Test
        @DisplayName("recordPolicyPromotionLatency does not throw")
        void recordPolicyPromotionLatencyDoesNotThrow() { // GH-90000
            Instant approved = Instant.now().minusSeconds(5); // GH-90000
            Instant promoted = Instant.now(); // GH-90000
            sloMetrics.recordPolicyPromotionLatency(approved, promoted, "tenant-1", "skill-abc"); // GH-90000
        }

        @Test
        @DisplayName("recordReplayAttempt success increments attempt and success counters — no throw")
        void recordReplayAttemptSuccessDoesNotThrow() { // GH-90000
            sloMetrics.recordReplayAttempt(true, "tenant-1", "pipeline-1"); // GH-90000
        }

        @Test
        @DisplayName("recordReplayAttempt failure increments attempt and failure counters — no throw")
        void recordReplayAttemptFailureDoesNotThrow() { // GH-90000
            sloMetrics.recordReplayAttempt(false, "tenant-1", "pipeline-1"); // GH-90000
        }

        @Test
        @DisplayName("recordAgentExecutionSuccess records metrics without throwing")
        void recordAgentExecutionSuccessDoesNotThrow() { // GH-90000
            sloMetrics.recordAgentExecutionSuccess("tenant-1", "agent-1", 125L); // GH-90000
        }

        @Test
        @DisplayName("recordAgentExecutionFailure records categorized metrics without throwing")
        void recordAgentExecutionFailureDoesNotThrow() { // GH-90000
            sloMetrics.recordAgentExecutionFailure( // GH-90000
                "tenant-1",
                "agent-1",
                "AGENT_EXECUTION_TIMEOUT",
                "transient",
                true,
                240L);
        }
    }
}
