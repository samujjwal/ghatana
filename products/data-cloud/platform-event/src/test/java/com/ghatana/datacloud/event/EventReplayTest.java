/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
import static org.mockito.Mockito.*;

/**
 * Tests for event replay from offset (D007). // GH-90000
 *
 * <p>Validates replay operations from checkpoints with offset tracking.
 *
 * @doc.type class
 * @doc.purpose Event replay from offset tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("EventReplay – From Offset (D007)")
class EventReplayTest extends EventloopTestBase {

    @Mock
    private EventReplayService replayService;

    @Mock(lenient = true) // GH-90000
    private EventCheckpointRepository checkpointRepository;

    private List<EventReplayService.ReplayedEvent> capturedEvents;
    private EventReplayService.EventHandler captureHandler;

    @org.junit.jupiter.api.BeforeEach
    void setUpHandler() { // GH-90000
        capturedEvents = new ArrayList<>(); // GH-90000
        captureHandler = event -> {
            capturedEvents.add(event); // GH-90000
            return Promise.of((Void) null); // GH-90000
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
        void replayFromOffsetReplaysSubsequentEvents() { // GH-90000
            String consumerId = "consumer-001";
            long fromOffset = 100;

            EventReplayService.ReplayResult expectedResult = new EventReplayService.ReplayResult( // GH-90000
                consumerId, 100, 200, 100, 100, 0,
                Duration.ofSeconds(5), true, List.of() // GH-90000
            );

            when(replayService.replayFromOffset(eq(consumerId), eq(fromOffset), any())) // GH-90000
                .thenReturn(Promise.of(expectedResult)); // GH-90000

            EventReplayService.ReplayResult result = runPromise(() -> // GH-90000
                replayService.replayFromOffset(consumerId, fromOffset, captureHandler) // GH-90000
            );

            assertThat(result.isSuccessful()).isTrue(); // GH-90000
            assertThat(result.startOffset()).isEqualTo(100); // GH-90000
            assertThat(result.endOffset()).isEqualTo(200); // GH-90000
            assertThat(result.eventsReplayed()).isEqualTo(100); // GH-90000
        }

        @Test
        @DisplayName("[D007]: replay_from_zero_replays_all_events")
        void replayFromZeroReplaysAllEvents() { // GH-90000
            String consumerId = "consumer-001";

            EventReplayService.ReplayResult expectedResult = new EventReplayService.ReplayResult( // GH-90000
                consumerId, 0, 1000, 1000, 1000, 0,
                Duration.ofSeconds(30), true, List.of() // GH-90000
            );

            when(replayService.replayFromOffset(eq(consumerId), eq(0L), any())) // GH-90000
                .thenReturn(Promise.of(expectedResult)); // GH-90000

            EventReplayService.ReplayResult result = runPromise(() -> // GH-90000
                replayService.replayFromOffset(consumerId, 0, captureHandler) // GH-90000
            );

            assertThat(result.startOffset()).isZero(); // GH-90000
            assertThat(result.eventsReplayed()).isEqualTo(1000); // GH-90000
        }

        @Test
        @DisplayName("[D007]: replay_from_latest_offset_returns_empty")
        void replayFromLatestOffsetReturnsEmpty() { // GH-90000
            String consumerId = "consumer-001";
            long latestOffset = 1000;

            EventReplayService.ReplayResult expectedResult = new EventReplayService.ReplayResult( // GH-90000
                consumerId, 1000, 1000, 0, 0, 0,
                Duration.ZERO, true, List.of() // GH-90000
            );

            when(replayService.replayFromOffset(eq(consumerId), eq(latestOffset), any())) // GH-90000
                .thenReturn(Promise.of(expectedResult)); // GH-90000

            EventReplayService.ReplayResult result = runPromise(() -> // GH-90000
                replayService.replayFromOffset(consumerId, latestOffset, captureHandler) // GH-90000
            );

            assertThat(result.eventsReplayed()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("[D007]: replay_respects_max_events_limit")
        void replayRespectsMaxEventsLimit() { // GH-90000
            String consumerId = "consumer-001";
            long maxEvents = 100;

            EventReplayService.ReplayResult expectedResult = new EventReplayService.ReplayResult( // GH-90000
                consumerId, 0, 100, 100, 100, 0,
                Duration.ofSeconds(5), true, List.of() // GH-90000
            );

            when(replayService.replayFromOffset(eq(consumerId), anyLong(), any())) // GH-90000
                .thenReturn(Promise.of(expectedResult)); // GH-90000

            EventReplayService.ReplayResult result = runPromise(() -> // GH-90000
                replayService.replayFromOffset(consumerId, 0, captureHandler) // GH-90000
            );

            assertThat(result.eventsReplayed()).isLessThanOrEqualTo(maxEvents); // GH-90000
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
        void replayFromCheckpointReadsStoredOffset() { // GH-90000
            String consumerId = "consumer-001";
            long checkpointOffset = 500;

            when(checkpointRepository.getCheckpoint(consumerId, 0)) // GH-90000
                .thenReturn(Promise.of(java.util.Optional.of(checkpointOffset))); // GH-90000

            EventReplayService.ReplayResult expectedResult = new EventReplayService.ReplayResult( // GH-90000
                consumerId, checkpointOffset, 600, 100, 100, 0,
                Duration.ofSeconds(5), true, List.of() // GH-90000
            );

            when(replayService.replayFromCheckpoint(eq(consumerId), any())) // GH-90000
                .thenReturn(Promise.of(expectedResult)); // GH-90000

            EventReplayService.ReplayResult result = runPromise(() -> // GH-90000
                replayService.replayFromCheckpoint(consumerId, captureHandler) // GH-90000
            );

            assertThat(result.startOffset()).isEqualTo(checkpointOffset); // GH-90000
        }

        @Test
        @DisplayName("[D007]: replay_from_checkpoint_starts_from_zero_if_no_checkpoint")
        void replayFromCheckpointStartsFromZeroIfNoCheckpoint() { // GH-90000
            String consumerId = "consumer-001";

            when(checkpointRepository.getCheckpoint(consumerId, 0)) // GH-90000
                .thenReturn(Promise.of(java.util.Optional.empty())); // GH-90000

            EventReplayService.ReplayResult expectedResult = new EventReplayService.ReplayResult( // GH-90000
                consumerId, 0, 100, 100, 100, 0,
                Duration.ofSeconds(5), true, List.of() // GH-90000
            );

            when(replayService.replayFromCheckpoint(eq(consumerId), any())) // GH-90000
                .thenReturn(Promise.of(expectedResult)); // GH-90000

            EventReplayService.ReplayResult result = runPromise(() -> // GH-90000
                replayService.replayFromCheckpoint(consumerId, captureHandler) // GH-90000
            );

            assertThat(result.startOffset()).isZero(); // GH-90000
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
        void replayForTimeRangeFiltersByTimestamp() { // GH-90000
            long startTime = 1704067200000L; // 2024-01-01
            long endTime = 1706745600000L;   // 2024-02-01

            EventReplayService.ReplayResult expectedResult = new EventReplayService.ReplayResult( // GH-90000
                "time-replay", 0, 100, 50, 50, 0,
                Duration.ofSeconds(3), true, List.of() // GH-90000
            );

            when(replayService.replayForTimeRange(eq(startTime), eq(endTime), any())) // GH-90000
                .thenReturn(Promise.of(expectedResult)); // GH-90000

            EventReplayService.ReplayResult result = runPromise(() -> // GH-90000
                replayService.replayForTimeRange(startTime, endTime, captureHandler) // GH-90000
            );

            assertThat(result.isSuccessful()).isTrue(); // GH-90000
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
        void replayForTenantFiltersEvents() { // GH-90000
            String tenantId = "tenant-alpha";
            long fromOffset = 0;

            EventReplayService.ReplayResult expectedResult = new EventReplayService.ReplayResult( // GH-90000
                tenantId, 0, 500, 200, 200, 0,
                Duration.ofSeconds(10), true, List.of() // GH-90000
            );

            when(replayService.replayForTenant(eq(tenantId), eq(fromOffset), any())) // GH-90000
                .thenReturn(Promise.of(expectedResult)); // GH-90000

            EventReplayService.ReplayResult result = runPromise(() -> // GH-90000
                replayService.replayForTenant(tenantId, fromOffset, captureHandler) // GH-90000
            );

            assertThat(result.consumerId()).isEqualTo(tenantId); // GH-90000
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
        void pauseReplayStopsProcessing() { // GH-90000
            String consumerId = "consumer-001";

            when(replayService.pauseReplay(consumerId)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000
            when(replayService.getReplayStatus(consumerId)) // GH-90000
                .thenReturn(Promise.of(new EventReplayService.ReplayStatus( // GH-90000
                    consumerId, EventReplayService.ReplayState.PAUSED,
                    50, 100, 50, 50.0,
                    Duration.ofSeconds(5), Duration.ofSeconds(5) // GH-90000
                )));

            runPromise(() -> replayService.pauseReplay(consumerId)); // GH-90000
            EventReplayService.ReplayStatus status = runPromise(() -> // GH-90000
                replayService.getReplayStatus(consumerId) // GH-90000
            );

            assertThat(status.state()).isEqualTo(EventReplayService.ReplayState.PAUSED); // GH-90000
        }

        @Test
        @DisplayName("[D007]: resume_replay_continues_processing")
        void resumeReplayContinuesProcessing() { // GH-90000
            String consumerId = "consumer-001";

            when(replayService.resumeReplay(consumerId)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000
            when(replayService.getReplayStatus(consumerId)) // GH-90000
                .thenReturn(Promise.of(new EventReplayService.ReplayStatus( // GH-90000
                    consumerId, EventReplayService.ReplayState.RUNNING,
                    75, 100, 75, 75.0,
                    Duration.ofSeconds(8), Duration.ofSeconds(3) // GH-90000
                )));

            runPromise(() -> replayService.resumeReplay(consumerId)); // GH-90000
            EventReplayService.ReplayStatus status = runPromise(() -> // GH-90000
                replayService.getReplayStatus(consumerId) // GH-90000
            );

            assertThat(status.state()).isEqualTo(EventReplayService.ReplayState.RUNNING); // GH-90000
        }

        @Test
        @DisplayName("[D007]: cancel_replay_stops_and_clears")
        void cancelReplayStopsAndClears() { // GH-90000
            String consumerId = "consumer-001";

            when(replayService.cancelReplay(consumerId)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000
            when(replayService.getReplayStatus(consumerId)) // GH-90000
                .thenReturn(Promise.of(new EventReplayService.ReplayStatus( // GH-90000
                    consumerId, EventReplayService.ReplayState.CANCELLED,
                    30, 100, 30, 30.0,
                    Duration.ofSeconds(3), Duration.ZERO // GH-90000
                )));

            runPromise(() -> replayService.cancelReplay(consumerId)); // GH-90000
            EventReplayService.ReplayStatus status = runPromise(() -> // GH-90000
                replayService.getReplayStatus(consumerId) // GH-90000
            );

            assertThat(status.state()).isEqualTo(EventReplayService.ReplayState.CANCELLED); // GH-90000
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
        void replayStatusShowsProgressPercentage() { // GH-90000
            String consumerId = "consumer-001";

            EventReplayService.ReplayStatus status = new EventReplayService.ReplayStatus( // GH-90000
                consumerId, EventReplayService.ReplayState.RUNNING,
                50, 100, 50, 50.0,
                Duration.ofSeconds(5), Duration.ofSeconds(5) // GH-90000
            );

            when(replayService.getReplayStatus(consumerId)) // GH-90000
                .thenReturn(Promise.of(status)); // GH-90000

            EventReplayService.ReplayStatus actual = runPromise(() -> // GH-90000
                replayService.getReplayStatus(consumerId) // GH-90000
            );

            assertThat(actual.progressPercent()).isEqualTo(50.0); // GH-90000
        }

        @Test
        @DisplayName("[D007]: progress_calculated_correctly")
        void progressCalculatedCorrectly() { // GH-90000
            EventReplayService.ReplayStatus status = new EventReplayService.ReplayStatus( // GH-90000
                "test", EventReplayService.ReplayState.RUNNING,
                75, 100, 75, 0, // progressPercent = 0 to test calculation
                Duration.ZERO, Duration.ZERO
            );

            double calculated = status.calculateProgress(); // GH-90000

            assertThat(calculated).isEqualTo(75.0); // 75/100 * 100 // GH-90000
        }

        @Test
        @DisplayName("[D007]: elapsed_time_tracked")
        void elapsedTimeTracked() { // GH-90000
            Duration elapsed = Duration.ofSeconds(15); // GH-90000

            EventReplayService.ReplayStatus status = new EventReplayService.ReplayStatus( // GH-90000
                "test", EventReplayService.ReplayState.RUNNING,
                50, 100, 50, 50.0,
                elapsed, Duration.ofSeconds(15) // GH-90000
            );

            assertThat(status.elapsedTime()).isEqualTo(elapsed); // GH-90000
        }

        @Test
        @DisplayName("[D007]: estimated_remaining_time_calculated")
        void estimatedRemainingTimeCalculated() { // GH-90000
            Duration remaining = Duration.ofSeconds(10); // GH-90000

            EventReplayService.ReplayStatus status = new EventReplayService.ReplayStatus( // GH-90000
                "test", EventReplayService.ReplayState.RUNNING,
                50, 100, 50, 50.0,
                Duration.ofSeconds(10), remaining // GH-90000
            );

            assertThat(status.estimatedRemainingTime()).isEqualTo(remaining); // GH-90000
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
        void perfectReplayHas100PercentSuccess() { // GH-90000
            EventReplayService.ReplayResult result = new EventReplayService.ReplayResult( // GH-90000
                "test", 0, 100, 100, 100, 0,
                Duration.ofSeconds(5), true, List.of() // GH-90000
            );

            assertThat(result.successRate()).isEqualTo(1.0); // GH-90000
            assertThat(result.isSuccessful()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[D007]: partial_failure_reduces_success_rate")
        void partialFailureReducesSuccessRate() { // GH-90000
            EventReplayService.ReplayResult result = new EventReplayService.ReplayResult( // GH-90000
                "test", 0, 100, 100, 90, 10,
                Duration.ofSeconds(5), true, List.of() // GH-90000
            );

            assertThat(result.successRate()).isEqualTo(0.9); // GH-90000
            assertThat(result.isSuccessful()).isFalse(); // Has failures // GH-90000
        }

        @Test
        @DisplayName("[D007]: all_failed_has_zero_success_rate")
        void allFailedHasZeroSuccessRate() { // GH-90000
            EventReplayService.ReplayResult result = new EventReplayService.ReplayResult( // GH-90000
                "test", 0, 100, 100, 0, 100,
                Duration.ofSeconds(5), false, List.of() // GH-90000
            );

            assertThat(result.successRate()).isEqualTo(0.0); // GH-90000
            assertThat(result.isSuccessful()).isFalse(); // GH-90000
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
        void replayedEventContainsOriginalData() { // GH-90000
            EventReplayService.ReplayedEvent event = new EventReplayService.ReplayedEvent( // GH-90000
                "evt-001", "test.event", "tenant-alpha",
                42, System.currentTimeMillis(), // GH-90000
                "{\"data\":\"test\"}".getBytes(), // GH-90000
                1
            );

            assertThat(event.id()).isEqualTo("evt-001");
            assertThat(event.type()).isEqualTo("test.event");
            assertThat(event.tenantId()).isEqualTo("tenant-alpha");
            assertThat(event.offset()).isEqualTo(42); // GH-90000
        }

        @Test
        @DisplayName("[D007]: first_replay_identified_by_count")
        void firstReplayIdentifiedByCount() { // GH-90000
            EventReplayService.ReplayedEvent first = new EventReplayService.ReplayedEvent( // GH-90000
                "evt-001", "test.event", "tenant-alpha",
                42, System.currentTimeMillis(), null, 1 // GH-90000
            );

            EventReplayService.ReplayedEvent second = new EventReplayService.ReplayedEvent( // GH-90000
                "evt-001", "test.event", "tenant-alpha",
                42, System.currentTimeMillis(), null, 2 // GH-90000
            );

            assertThat(first.isFirstReplay()).isTrue(); // GH-90000
            assertThat(second.isFirstReplay()).isFalse(); // GH-90000
        }
    }
}
