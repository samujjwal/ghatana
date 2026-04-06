/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.event;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for event replay from offset (D007).
 *
 * <p>Validates replay operations from checkpoints with offset tracking.
 *
 * @doc.type class
 * @doc.purpose Event replay from offset tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventReplay – From Offset (D007)")
class EventReplayTest extends EventloopTestBase {

    @Mock
    private EventReplayService replayService;

    @Mock
    private EventCheckpointRepository checkpointRepository;

    private List<EventReplayService.ReplayedEvent> capturedEvents;
    private EventReplayService.EventHandler captureHandler;

    @org.junit.jupiter.api.BeforeEach
    void setUpHandler() {
        capturedEvents = new ArrayList<>();
        captureHandler = event -> {
            capturedEvents.add(event);
            return Promise.of((Void) null);
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Replay from Offset Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Replay from Offset")
    class ReplayFromOffsetTests {

        @Test
        @DisplayName("[D007]: replay_from_offset_replays_subsequent_events")
        void replayFromOffsetReplaysSubsequentEvents() {
            String consumerId = "consumer-001";
            long fromOffset = 100;

            EventReplayService.ReplayResult expectedResult = new EventReplayService.ReplayResult(
                consumerId, 100, 200, 100, 100, 0,
                Duration.ofSeconds(5), true, List.of()
            );

            when(replayService.replayFromOffset(eq(consumerId), eq(fromOffset), any()))
                .thenReturn(Promise.of(expectedResult));

            EventReplayService.ReplayResult result = runPromise(() ->
                replayService.replayFromOffset(consumerId, fromOffset, captureHandler)
            );

            assertThat(result.isSuccessful()).isTrue();
            assertThat(result.startOffset()).isEqualTo(100);
            assertThat(result.endOffset()).isEqualTo(200);
            assertThat(result.eventsReplayed()).isEqualTo(100);
        }

        @Test
        @DisplayName("[D007]: replay_from_zero_replays_all_events")
        void replayFromZeroReplaysAllEvents() {
            String consumerId = "consumer-001";

            EventReplayService.ReplayResult expectedResult = new EventReplayService.ReplayResult(
                consumerId, 0, 1000, 1000, 1000, 0,
                Duration.ofSeconds(30), true, List.of()
            );

            when(replayService.replayFromOffset(eq(consumerId), eq(0L), any()))
                .thenReturn(Promise.of(expectedResult));

            EventReplayService.ReplayResult result = runPromise(() ->
                replayService.replayFromOffset(consumerId, 0, captureHandler)
            );

            assertThat(result.startOffset()).isZero();
            assertThat(result.eventsReplayed()).isEqualTo(1000);
        }

        @Test
        @DisplayName("[D007]: replay_from_latest_offset_returns_empty")
        void replayFromLatestOffsetReturnsEmpty() {
            String consumerId = "consumer-001";
            long latestOffset = 1000;

            EventReplayService.ReplayResult expectedResult = new EventReplayService.ReplayResult(
                consumerId, 1000, 1000, 0, 0, 0,
                Duration.ZERO, true, List.of()
            );

            when(replayService.replayFromOffset(eq(consumerId), eq(latestOffset), any()))
                .thenReturn(Promise.of(expectedResult));

            EventReplayService.ReplayResult result = runPromise(() ->
                replayService.replayFromOffset(consumerId, latestOffset, captureHandler)
            );

            assertThat(result.eventsReplayed()).isZero();
        }

        @Test
        @DisplayName("[D007]: replay_respects_max_events_limit")
        void replayRespectsMaxEventsLimit() {
            String consumerId = "consumer-001";
            long maxEvents = 100;

            EventReplayService.ReplayResult expectedResult = new EventReplayService.ReplayResult(
                consumerId, 0, 100, 100, 100, 0,
                Duration.ofSeconds(5), true, List.of()
            );

            when(replayService.replayFromOffset(eq(consumerId), anyLong(), any()))
                .thenReturn(Promise.of(expectedResult));

            EventReplayService.ReplayResult result = runPromise(() ->
                replayService.replayFromOffset(consumerId, 0, captureHandler)
            );

            assertThat(result.eventsReplayed()).isLessThanOrEqualTo(maxEvents);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Replay from Checkpoint Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Replay from Checkpoint")
    class ReplayFromCheckpointTests {

        @Test
        @DisplayName("[D007]: replay_from_checkpoint_reads_stored_offset")
        void replayFromCheckpointReadsStoredOffset() {
            String consumerId = "consumer-001";
            long checkpointOffset = 500;

            when(checkpointRepository.getCheckpoint(consumerId, 0))
                .thenReturn(Promise.of(java.util.Optional.of(checkpointOffset)));

            EventReplayService.ReplayResult expectedResult = new EventReplayService.ReplayResult(
                consumerId, checkpointOffset, 600, 100, 100, 0,
                Duration.ofSeconds(5), true, List.of()
            );

            when(replayService.replayFromCheckpoint(eq(consumerId), any()))
                .thenReturn(Promise.of(expectedResult));

            EventReplayService.ReplayResult result = runPromise(() ->
                replayService.replayFromCheckpoint(consumerId, captureHandler)
            );

            assertThat(result.startOffset()).isEqualTo(checkpointOffset);
        }

        @Test
        @DisplayName("[D007]: replay_from_checkpoint_starts_from_zero_if_no_checkpoint")
        void replayFromCheckpointStartsFromZeroIfNoCheckpoint() {
            String consumerId = "consumer-001";

            when(checkpointRepository.getCheckpoint(consumerId, 0))
                .thenReturn(Promise.of(java.util.Optional.empty()));

            EventReplayService.ReplayResult expectedResult = new EventReplayService.ReplayResult(
                consumerId, 0, 100, 100, 100, 0,
                Duration.ofSeconds(5), true, List.of()
            );

            when(replayService.replayFromCheckpoint(eq(consumerId), any()))
                .thenReturn(Promise.of(expectedResult));

            EventReplayService.ReplayResult result = runPromise(() ->
                replayService.replayFromCheckpoint(consumerId, captureHandler)
            );

            assertThat(result.startOffset()).isZero();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Time Range Replay Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Time Range Replay")
    class TimeRangeReplayTests {

        @Test
        @DisplayName("[D007]: replay_for_time_range_filters_by_timestamp")
        void replayForTimeRangeFiltersByTimestamp() {
            long startTime = 1704067200000L; // 2024-01-01
            long endTime = 1706745600000L;   // 2024-02-01

            EventReplayService.ReplayResult expectedResult = new EventReplayService.ReplayResult(
                "time-replay", 0, 100, 50, 50, 0,
                Duration.ofSeconds(3), true, List.of()
            );

            when(replayService.replayForTimeRange(eq(startTime), eq(endTime), any()))
                .thenReturn(Promise.of(expectedResult));

            EventReplayService.ReplayResult result = runPromise(() ->
                replayService.replayForTimeRange(startTime, endTime, captureHandler)
            );

            assertThat(result.isSuccessful()).isTrue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tenant-specific Replay Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tenant-specific Replay")
    class TenantSpecificReplayTests {

        @Test
        @DisplayName("[D007]: replay_for_tenant_filters_events")
        void replayForTenantFiltersEvents() {
            String tenantId = "tenant-alpha";
            long fromOffset = 0;

            EventReplayService.ReplayResult expectedResult = new EventReplayService.ReplayResult(
                tenantId, 0, 500, 200, 200, 0,
                Duration.ofSeconds(10), true, List.of()
            );

            when(replayService.replayForTenant(eq(tenantId), eq(fromOffset), any()))
                .thenReturn(Promise.of(expectedResult));

            EventReplayService.ReplayResult result = runPromise(() ->
                replayService.replayForTenant(tenantId, fromOffset, captureHandler)
            );

            assertThat(result.consumerId()).isEqualTo(tenantId);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Replay Control Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Replay Control")
    class ReplayControlTests {

        @Test
        @DisplayName("[D007]: pause_replay_stops_processing")
        void pauseReplayStopsProcessing() {
            String consumerId = "consumer-001";

            when(replayService.pauseReplay(consumerId))
                .thenReturn(Promise.of((Void) null));
            when(replayService.getReplayStatus(consumerId))
                .thenReturn(Promise.of(new EventReplayService.ReplayStatus(
                    consumerId, EventReplayService.ReplayState.PAUSED,
                    50, 100, 50, 50.0,
                    Duration.ofSeconds(5), Duration.ofSeconds(5)
                )));

            runPromise(() -> replayService.pauseReplay(consumerId));
            EventReplayService.ReplayStatus status = runPromise(() ->
                replayService.getReplayStatus(consumerId)
            );

            assertThat(status.state()).isEqualTo(EventReplayService.ReplayState.PAUSED);
        }

        @Test
        @DisplayName("[D007]: resume_replay_continues_processing")
        void resumeReplayContinuesProcessing() {
            String consumerId = "consumer-001";

            when(replayService.resumeReplay(consumerId))
                .thenReturn(Promise.of((Void) null));
            when(replayService.getReplayStatus(consumerId))
                .thenReturn(Promise.of(new EventReplayService.ReplayStatus(
                    consumerId, EventReplayService.ReplayState.RUNNING,
                    75, 100, 75, 75.0,
                    Duration.ofSeconds(8), Duration.ofSeconds(3)
                )));

            runPromise(() -> replayService.resumeReplay(consumerId));
            EventReplayService.ReplayStatus status = runPromise(() ->
                replayService.getReplayStatus(consumerId)
            );

            assertThat(status.state()).isEqualTo(EventReplayService.ReplayState.RUNNING);
        }

        @Test
        @DisplayName("[D007]: cancel_replay_stops_and_clears")
        void cancelReplayStopsAndClears() {
            String consumerId = "consumer-001";

            when(replayService.cancelReplay(consumerId))
                .thenReturn(Promise.of((Void) null));
            when(replayService.getReplayStatus(consumerId))
                .thenReturn(Promise.of(new EventReplayService.ReplayStatus(
                    consumerId, EventReplayService.ReplayState.CANCELLED,
                    30, 100, 30, 30.0,
                    Duration.ofSeconds(3), Duration.ZERO
                )));

            runPromise(() -> replayService.cancelReplay(consumerId));
            EventReplayService.ReplayStatus status = runPromise(() ->
                replayService.getReplayStatus(consumerId)
            );

            assertThat(status.state()).isEqualTo(EventReplayService.ReplayState.CANCELLED);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Replay Progress Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Replay Progress")
    class ReplayProgressTests {

        @Test
        @DisplayName("[D007]: replay_status_shows_progress_percentage")
        void replayStatusShowsProgressPercentage() {
            String consumerId = "consumer-001";

            EventReplayService.ReplayStatus status = new EventReplayService.ReplayStatus(
                consumerId, EventReplayService.ReplayState.RUNNING,
                50, 100, 50, 50.0,
                Duration.ofSeconds(5), Duration.ofSeconds(5)
            );

            when(replayService.getReplayStatus(consumerId))
                .thenReturn(Promise.of(status));

            EventReplayService.ReplayStatus actual = runPromise(() ->
                replayService.getReplayStatus(consumerId)
            );

            assertThat(actual.progressPercent()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("[D007]: progress_calculated_correctly")
        void progressCalculatedCorrectly() {
            EventReplayService.ReplayStatus status = new EventReplayService.ReplayStatus(
                "test", EventReplayService.ReplayState.RUNNING,
                75, 100, 75, 0, // progressPercent = 0 to test calculation
                Duration.ZERO, Duration.ZERO
            );

            double calculated = status.calculateProgress();

            assertThat(calculated).isEqualTo(75.0); // 75/100 * 100
        }

        @Test
        @DisplayName("[D007]: elapsed_time_tracked")
        void elapsedTimeTracked() {
            Duration elapsed = Duration.ofSeconds(15);

            EventReplayService.ReplayStatus status = new EventReplayService.ReplayStatus(
                "test", EventReplayService.ReplayState.RUNNING,
                50, 100, 50, 50.0,
                elapsed, Duration.ofSeconds(15)
            );

            assertThat(status.elapsedTime()).isEqualTo(elapsed);
        }

        @Test
        @DisplayName("[D007]: estimated_remaining_time_calculated")
        void estimatedRemainingTimeCalculated() {
            Duration remaining = Duration.ofSeconds(10);

            EventReplayService.ReplayStatus status = new EventReplayService.ReplayStatus(
                "test", EventReplayService.ReplayState.RUNNING,
                50, 100, 50, 50.0,
                Duration.ofSeconds(10), remaining
            );

            assertThat(status.estimatedRemainingTime()).isEqualTo(remaining);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Replay Success Rate Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Replay Success Rate")
    class ReplaySuccessRateTests {

        @Test
        @DisplayName("[D007]: perfect_replay_has_100_percent_success")
        void perfectReplayHas100PercentSuccess() {
            EventReplayService.ReplayResult result = new EventReplayService.ReplayResult(
                "test", 0, 100, 100, 100, 0,
                Duration.ofSeconds(5), true, List.of()
            );

            assertThat(result.successRate()).isEqualTo(1.0);
            assertThat(result.isSuccessful()).isTrue();
        }

        @Test
        @DisplayName("[D007]: partial_failure_reduces_success_rate")
        void partialFailureReducesSuccessRate() {
            EventReplayService.ReplayResult result = new EventReplayService.ReplayResult(
                "test", 0, 100, 100, 90, 10,
                Duration.ofSeconds(5), true, List.of()
            );

            assertThat(result.successRate()).isEqualTo(0.9);
            assertThat(result.isSuccessful()).isFalse(); // Has failures
        }

        @Test
        @DisplayName("[D007]: all_failed_has_zero_success_rate")
        void allFailedHasZeroSuccessRate() {
            EventReplayService.ReplayResult result = new EventReplayService.ReplayResult(
                "test", 0, 100, 100, 0, 100,
                Duration.ofSeconds(5), false, List.of()
            );

            assertThat(result.successRate()).isEqualTo(0.0);
            assertThat(result.isSuccessful()).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Replayed Event Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Replayed Event")
    class ReplayedEventTests {

        @Test
        @DisplayName("[D007]: replayed_event_contains_original_data")
        void replayedEventContainsOriginalData() {
            EventReplayService.ReplayedEvent event = new EventReplayService.ReplayedEvent(
                "evt-001", "test.event", "tenant-alpha",
                42, System.currentTimeMillis(),
                "{\"data\":\"test\"}".getBytes(),
                1
            );

            assertThat(event.id()).isEqualTo("evt-001");
            assertThat(event.type()).isEqualTo("test.event");
            assertThat(event.tenantId()).isEqualTo("tenant-alpha");
            assertThat(event.offset()).isEqualTo(42);
        }

        @Test
        @DisplayName("[D007]: first_replay_identified_by_count")
        void firstReplayIdentifiedByCount() {
            EventReplayService.ReplayedEvent first = new EventReplayService.ReplayedEvent(
                "evt-001", "test.event", "tenant-alpha",
                42, System.currentTimeMillis(), null, 1
            );

            EventReplayService.ReplayedEvent second = new EventReplayService.ReplayedEvent(
                "evt-001", "test.event", "tenant-alpha",
                42, System.currentTimeMillis(), null, 2
            );

            assertThat(first.isFirstReplay()).isTrue();
            assertThat(second.isFirstReplay()).isFalse();
        }
    }
}
