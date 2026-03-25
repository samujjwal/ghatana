/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * {@code runCountSnapshot()} method.
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
    void setUp() {
        sloMetrics = new AepSloMetrics(MetricsCollectorFactory.createNoop());
    }

    @Nested
    @DisplayName("runCountSnapshot")
    class RunCountSnapshot {

        @Test
        @DisplayName("initial snapshot has zero runs and zero failure rate")
        void initialSnapshotIsZero() {
            Map<String, Object> snapshot = sloMetrics.runCountSnapshot();
            assertThat((Long) snapshot.get("totalRuns")).isZero();
            assertThat((Long) snapshot.get("failedRuns")).isZero();
            assertThat((Double) snapshot.get("runFailureRate")).isZero();
        }

        @Test
        @DisplayName("records completed runs and updates snapshot")
        void recordsCompletedRuns() {
            sloMetrics.recordRunCompleted("tenant-1", "pipeline-1", 200L);
            sloMetrics.recordRunCompleted("tenant-1", "pipeline-2", 300L);

            Map<String, Object> snapshot = sloMetrics.runCountSnapshot();
            assertThat((Long) snapshot.get("totalRuns")).isEqualTo(2L);
            assertThat((Long) snapshot.get("failedRuns")).isZero();
            assertThat((Double) snapshot.get("runFailureRate")).isZero();
        }

        @Test
        @DisplayName("records failed runs and computes failure rate")
        void recordsFailedRuns() {
            sloMetrics.recordRunCompleted("tenant-1", "pipeline-1", 100L);
            sloMetrics.recordRunFailed("tenant-1", "pipeline-1", 500L, "timeout");

            Map<String, Object> snapshot = sloMetrics.runCountSnapshot();
            assertThat((Long) snapshot.get("totalRuns")).isEqualTo(2L);
            assertThat((Long) snapshot.get("failedRuns")).isEqualTo(1L);
            assertThat((Double) snapshot.get("runFailureRate")).isEqualTo(0.5);
        }

        @Test
        @DisplayName("failure rate is zero when no runs recorded")
        void failureRateZeroWithNoRuns() {
            assertThat((Double) sloMetrics.runCountSnapshot().get("runFailureRate")).isZero();
        }
    }

    @Nested
    @DisplayName("SLO recording methods")
    class SloRecordingMethods {

        @Test
        @DisplayName("recordIntakeLatency does not throw")
        void recordIntakeLatencyDoesNotThrow() {
            Instant received = Instant.now().minusMillis(50);
            Instant processed = Instant.now();
            sloMetrics.recordIntakeLatency(received, processed, "tenant-1");
        }

        @Test
        @DisplayName("recordRunCompleted increments total run counter")
        void recordRunCompletedIncrementsTotal() {
            sloMetrics.recordRunCompleted("tenant-1", "pipeline-1", 100L);
            assertThat((Long) sloMetrics.runCountSnapshot().get("totalRuns")).isEqualTo(1L);
        }

        @Test
        @DisplayName("recordRunFailed increments both total and failed counters")
        void recordRunFailedIncrementsFailedAndTotal() {
            sloMetrics.recordRunFailed("tenant-1", "pipeline-1", 100L, "engine_error");
            Map<String, Object> snap = sloMetrics.runCountSnapshot();
            assertThat((Long) snap.get("totalRuns")).isEqualTo(1L);
            assertThat((Long) snap.get("failedRuns")).isEqualTo(1L);
        }

        @Test
        @DisplayName("recordReviewQueueLatency does not throw")
        void recordReviewQueueLatencyDoesNotThrow() {
            Instant enqueued = Instant.now().minusSeconds(30);
            Instant decided = Instant.now();
            sloMetrics.recordReviewQueueLatency(enqueued, decided, "tenant-1", "POLICY");
        }

        @Test
        @DisplayName("recordPolicyPromotionLatency does not throw")
        void recordPolicyPromotionLatencyDoesNotThrow() {
            Instant approved = Instant.now().minusSeconds(5);
            Instant promoted = Instant.now();
            sloMetrics.recordPolicyPromotionLatency(approved, promoted, "tenant-1", "skill-abc");
        }

        @Test
        @DisplayName("recordReplayAttempt success increments attempt and success counters — no throw")
        void recordReplayAttemptSuccessDoesNotThrow() {
            sloMetrics.recordReplayAttempt(true, "tenant-1", "pipeline-1");
        }

        @Test
        @DisplayName("recordReplayAttempt failure increments attempt and failure counters — no throw")
        void recordReplayAttemptFailureDoesNotThrow() {
            sloMetrics.recordReplayAttempt(false, "tenant-1", "pipeline-1");
        }
    }
}
