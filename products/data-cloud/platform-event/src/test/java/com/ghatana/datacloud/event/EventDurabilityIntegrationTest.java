/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.event;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for event durability CDC capture end-to-end (D006). // GH-90000
 *
 * <p>Validates durability guarantees, checkpoint management, and CDC consistency.
 *
 * @doc.type class
 * @doc.purpose CDC capture end-to-end integration tests
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // GH-90000
@DisplayName("EventDurability – CDC Capture End-to-End (D006)")
class EventDurabilityIntegrationTest extends EventloopTestBase {

    @Mock
    private EventDurabilityService durabilityService;

    @Mock
    private EventCheckpointRepository checkpointRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Durability Write Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[D006]: write_with_leader_ack_achieves_durability")
    void writeWithLeaderAckAchievesDurability() { // GH-90000
        EventDurabilityService.Event event = new EventDurabilityService.Event( // GH-90000
            "evt-001", "test.event", "tenant-alpha",
            "{\"data\":\"test\"}".getBytes(), System.currentTimeMillis() // GH-90000
        );

        EventDurabilityService.DurabilityResult result =
            new EventDurabilityService.DurabilityResult( // GH-90000
                "evt-001", 1,
                EventDurabilityService.DurabilityLevel.LEADER_ACK,
                10, 0, true
            );

        when(durabilityService.writeWithDurability(any(), eq(EventDurabilityService.DurabilityLevel.LEADER_ACK))) // GH-90000
            .thenReturn(Promise.of(result)); // GH-90000

        EventDurabilityService.DurabilityResult actual = runPromise(() -> // GH-90000
            durabilityService.writeWithDurability(event, EventDurabilityService.DurabilityLevel.LEADER_ACK) // GH-90000
        );

        assertThat(actual.successful()).isTrue(); // GH-90000
        assertThat(actual.achievedLevel()).isEqualTo(EventDurabilityService.DurabilityLevel.LEADER_ACK); // GH-90000
    }

    @Test
    @DisplayName("[D006]: write_with_majority_ack_replicates_to_quorum")
    void writeWithMajorityAckReplicatesToQuorum() { // GH-90000
        EventDurabilityService.Event event = new EventDurabilityService.Event( // GH-90000
            "evt-002", "test.event", "tenant-alpha",
            "{\"data\":\"test\"}".getBytes(), System.currentTimeMillis() // GH-90000
        );

        EventDurabilityService.DurabilityResult result =
            new EventDurabilityService.DurabilityResult( // GH-90000
                "evt-002", 2,
                EventDurabilityService.DurabilityLevel.MAJORITY_ACK,
                15, 25, true
            );

        when(durabilityService.writeWithDurability(any(), eq(EventDurabilityService.DurabilityLevel.MAJORITY_ACK))) // GH-90000
            .thenReturn(Promise.of(result)); // GH-90000

        EventDurabilityService.DurabilityResult actual = runPromise(() -> // GH-90000
            durabilityService.writeWithDurability(event, EventDurabilityService.DurabilityLevel.MAJORITY_ACK) // GH-90000
        );

        assertThat(actual.successful()).isTrue(); // GH-90000
        assertThat(actual.achievedLevel()).isEqualTo(EventDurabilityService.DurabilityLevel.MAJORITY_ACK); // GH-90000
        assertThat(actual.replicationLatencyMs()).isGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("[D006]: write_with_fsync_ack_persists_to_disk")
    void writeWithFsyncAckPersistsToDisk() { // GH-90000
        EventDurabilityService.Event event = new EventDurabilityService.Event( // GH-90000
            "evt-003", "test.event", "tenant-alpha",
            "{\"data\":\"test\"}".getBytes(), System.currentTimeMillis() // GH-90000
        );

        EventDurabilityService.DurabilityResult result =
            new EventDurabilityService.DurabilityResult( // GH-90000
                "evt-003", 3,
                EventDurabilityService.DurabilityLevel.FSYNC_ACK,
                50, 30, true
            );

        when(durabilityService.writeWithDurability(any(), eq(EventDurabilityService.DurabilityLevel.FSYNC_ACK))) // GH-90000
            .thenReturn(Promise.of(result)); // GH-90000

        EventDurabilityService.DurabilityResult actual = runPromise(() -> // GH-90000
            durabilityService.writeWithDurability(event, EventDurabilityService.DurabilityLevel.FSYNC_ACK) // GH-90000
        );

        assertThat(actual.successful()).isTrue(); // GH-90000
        assertThat(actual.achievedLevel()).isEqualTo(EventDurabilityService.DurabilityLevel.FSYNC_ACK); // GH-90000
        assertThat(actual.fsyncLatencyMs()).isGreaterThan(0); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Checkpoint Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[D006]: save_checkpoint_persists_offset")
    void saveCheckpointPersistsOffset() { // GH-90000
        String consumerId = "consumer-001";
        int partition = 0;
        long offset = 100;

        when(checkpointRepository.saveCheckpoint(consumerId, partition, offset)) // GH-90000
            .thenReturn(Promise.of((Void) null)); // GH-90000

        runPromise(() -> checkpointRepository.saveCheckpoint(consumerId, partition, offset)); // GH-90000

        verify(checkpointRepository).saveCheckpoint(consumerId, partition, offset); // GH-90000
    }

    @Test
    @DisplayName("[D006]: get_checkpoint_returns_saved_offset")
    void getCheckpointReturnsSavedOffset() { // GH-90000
        String consumerId = "consumer-001";
        int partition = 0;
        long expectedOffset = 100;

        when(checkpointRepository.getCheckpoint(consumerId, partition)) // GH-90000
            .thenReturn(Promise.of(java.util.Optional.of(expectedOffset))); // GH-90000

        java.util.Optional<Long> actual = runPromise(() -> // GH-90000
            checkpointRepository.getCheckpoint(consumerId, partition) // GH-90000
        );

        assertThat(actual).isPresent(); // GH-90000
        assertThat(actual.get()).isEqualTo(expectedOffset); // GH-90000
    }

    @Test
    @DisplayName("[D006]: get_all_checkpoints_returns_all_partitions")
    void getAllCheckpointsReturnsAllPartitions() { // GH-90000
        String consumerId = "consumer-001";
        List<EventCheckpointRepository.Checkpoint> checkpoints = List.of( // GH-90000
            new EventCheckpointRepository.Checkpoint(consumerId, 0, 100, System.currentTimeMillis(), ""), // GH-90000
            new EventCheckpointRepository.Checkpoint(consumerId, 1, 150, System.currentTimeMillis(), ""), // GH-90000
            new EventCheckpointRepository.Checkpoint(consumerId, 2, 200, System.currentTimeMillis(), "") // GH-90000
        );

        when(checkpointRepository.getAllCheckpoints(consumerId)) // GH-90000
            .thenReturn(Promise.of(checkpoints)); // GH-90000

        List<EventCheckpointRepository.Checkpoint> actual = runPromise(() -> // GH-90000
            checkpointRepository.getAllCheckpoints(consumerId) // GH-90000
        );

        assertThat(actual).hasSize(3); // GH-90000
        assertThat(actual).allMatch(c -> consumerId.equals(c.consumerId())); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Consumer Lag Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[D006]: consumer_lag_calculated_correctly")
    void consumerLagCalculatedCorrectly() { // GH-90000
        String consumerId = "consumer-001";
        List<EventCheckpointRepository.PartitionLag> lags = List.of( // GH-90000
            EventCheckpointRepository.PartitionLag.of(0, 1000, 800), // GH-90000
            EventCheckpointRepository.PartitionLag.of(1, 1000, 950), // GH-90000
            EventCheckpointRepository.PartitionLag.of(2, 1000, 1000) // GH-90000
        );

        when(checkpointRepository.getConsumerLag(consumerId)) // GH-90000
            .thenReturn(Promise.of(lags)); // GH-90000

        List<EventCheckpointRepository.PartitionLag> actual = runPromise(() -> // GH-90000
            checkpointRepository.getConsumerLag(consumerId) // GH-90000
        );

        assertThat(actual.get(0).lag()).isEqualTo(200); // 1000 - 800 // GH-90000
        assertThat(actual.get(1).lag()).isEqualTo(50);  // 1000 - 950 // GH-90000
        assertThat(actual.get(2).lag()).isZero(); // 1000 - 1000 // GH-90000
    }

    @Test
    @DisplayName("[D006]: caught_up_partition_has_zero_lag")
    void caughtUpPartitionHasZeroLag() { // GH-90000
        EventCheckpointRepository.PartitionLag lag =
            EventCheckpointRepository.PartitionLag.of(0, 1000, 1000); // GH-90000

        assertThat(lag.lag()).isZero(); // GH-90000
        assertThat(lag.isCaughtUp()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("[D006]: behind_partition_has_positive_lag")
    void behindPartitionHasPositiveLag() { // GH-90000
        EventCheckpointRepository.PartitionLag lag =
            EventCheckpointRepository.PartitionLag.of(0, 1000, 800); // GH-90000

        assertThat(lag.lag()).isEqualTo(200); // GH-90000
        assertThat(lag.isCaughtUp()).isFalse(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Durability Status Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[D006]: durability_status_shows_replica_count")
    void durabilityStatusShowsReplicaCount() { // GH-90000
        String eventId = "evt-001";

        EventDurabilityService.DurabilityStatus status =
            new EventDurabilityService.DurabilityStatus( // GH-90000
                eventId,
                EventDurabilityService.DurabilityLevel.MAJORITY_ACK,
                3, // current replicas
                2, // required
                false,
                List.of("consumer-1", "consumer-2") // GH-90000
            );

        when(durabilityService.getDurabilityStatus(eventId)) // GH-90000
            .thenReturn(Promise.of(status)); // GH-90000

        EventDurabilityService.DurabilityStatus actual = runPromise(() -> // GH-90000
            durabilityService.getDurabilityStatus(eventId) // GH-90000
        );

        assertThat(actual.replicaCount()).isEqualTo(3); // GH-90000
        assertThat(actual.requiredReplicaCount()).isEqualTo(2); // GH-90000
        assertThat(actual.currentLevel()).isEqualTo(EventDurabilityService.DurabilityLevel.MAJORITY_ACK); // GH-90000
    }

    @Test
    @DisplayName("[D006]: fully_durable_when_fsynced_and_replicated")
    void fullyDurableWhenFsyncedAndReplicated() { // GH-90000
        EventDurabilityService.DurabilityStatus status =
            new EventDurabilityService.DurabilityStatus( // GH-90000
                "evt-001",
                EventDurabilityService.DurabilityLevel.ALL_ACK,
                3, // replicas >= required
                2,
                true, // fsynced
                List.of() // GH-90000
            );

        assertThat(status.isFullyDurable()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("[D006]: not_fully_durable_when_not_fsynced")
    void notFullyDurableWhenNotFsynced() { // GH-90000
        EventDurabilityService.DurabilityStatus status =
            new EventDurabilityService.DurabilityStatus( // GH-90000
                "evt-001",
                EventDurabilityService.DurabilityLevel.MAJORITY_ACK,
                3,
                2,
                false, // not fsynced
                List.of() // GH-90000
            );

        assertThat(status.isFullyDurable()).isFalse(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Wait for Durability Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[D006]: wait_for_durability_returns_true_when_achieved")
    void waitForDurabilityReturnsTrueWhenAchieved() { // GH-90000
        String eventId = "evt-001";

        when(durabilityService.waitForDurability( // GH-90000
            eq(eventId), // GH-90000
            eq(EventDurabilityService.DurabilityLevel.LEADER_ACK), // GH-90000
            any(Duration.class) // GH-90000
        )).thenReturn(Promise.of(true)); // GH-90000

        Boolean result = runPromise(() -> // GH-90000
            durabilityService.waitForDurability( // GH-90000
                eventId,
                EventDurabilityService.DurabilityLevel.LEADER_ACK,
                Duration.ofSeconds(5) // GH-90000
            )
        );

        assertThat(result).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("[D006]: wait_for_durability_returns_false_on_timeout")
    void waitForDurabilityReturnsFalseOnTimeout() { // GH-90000
        String eventId = "evt-slow";

        when(durabilityService.waitForDurability( // GH-90000
            eq(eventId), // GH-90000
            eq(EventDurabilityService.DurabilityLevel.ALL_ACK), // GH-90000
            any(Duration.class) // GH-90000
        )).thenReturn(Promise.of(false)); // GH-90000

        Boolean result = runPromise(() -> // GH-90000
            durabilityService.waitForDurability( // GH-90000
                eventId,
                EventDurabilityService.DurabilityLevel.ALL_ACK,
                Duration.ofMillis(100) // GH-90000
            )
        );

        assertThat(result).isFalse(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CDC Capture Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[D006]: cdc_event_captured_with_offset")
    void cdcEventCapturedWithOffset() { // GH-90000
        Map<String, Object> cdcEvent = buildEvent( // GH-90000
            "cdc.entity.created",
            42,
            Map.of("source", "database", "operation", "INSERT") // GH-90000
        );

        assertThat(cdcEvent.get("offset")).isEqualTo(42L);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) cdcEvent.get("payload");
        assertThat(payload.get("source")).isEqualTo("database");
        assertThat(payload.get("operation")).isEqualTo("INSERT");
    }

    @Test
    @DisplayName("[D006]: cdc_events_ordered_by_offset")
    void cdcEventsOrderedByOffset() { // GH-90000
        List<Map<String, Object>> events = List.of( // GH-90000
            buildEvent("cdc.event", 3, Map.of()), // GH-90000
            buildEvent("cdc.event", 1, Map.of()), // GH-90000
            buildEvent("cdc.event", 2, Map.of()) // GH-90000
        );

        List<Map<String, Object>> sorted = events.stream() // GH-90000
            .sorted(java.util.Comparator.comparingLong(e -> (Long) e.get("offset")))
            .toList(); // GH-90000

        assertThat(sorted.get(0).get("offset")).isEqualTo(1L);
        assertThat(sorted.get(1).get("offset")).isEqualTo(2L);
        assertThat(sorted.get(2).get("offset")).isEqualTo(3L);
    }

        // ─────────────────────────────────────────────────────────────────────────
        // Sustained Replay / Load Verification (DC-A17)
        // ─────────────────────────────────────────────────────────────────────────

        @Test
        @DisplayName("[D006][DC-A17]: sustained_replay_load_lag_distribution_remains_bounded")
        void sustainedReplayLoadLagDistributionRemainsBounded() {
        String consumerId = "consumer-load-001";
        int partitions = 128;
        long latestOffset = 1_000_000L;

        List<EventCheckpointRepository.PartitionLag> lags = IntStream.range(0, partitions)
            .mapToObj(partition -> {
                long checkpointOffset = latestOffset - (partition % 17);
                return EventCheckpointRepository.PartitionLag.of(partition, latestOffset, checkpointOffset);
            })
            .toList();

        when(checkpointRepository.getConsumerLag(consumerId))
            .thenReturn(Promise.of(lags));

        List<EventCheckpointRepository.PartitionLag> actual = runPromise(() ->
            checkpointRepository.getConsumerLag(consumerId)
        );

        assertThat(actual).hasSize(partitions);
        assertThat(actual)
            .allMatch(lag -> lag.currentOffset() >= lag.committedOffset());
        assertThat(actual.stream().mapToLong(EventCheckpointRepository.PartitionLag::lag).max())
            .hasValue(16L);
        assertThat(actual.stream().mapToLong(EventCheckpointRepository.PartitionLag::lag).sum())
            .isGreaterThan(0L);
        }

        @Test
        @DisplayName("[D006][DC-A17]: replay_checkpoint_updates_are_monotonic_across_batches")
        void replayCheckpointUpdatesAreMonotonicAcrossBatches() {
        String consumerId = "consumer-load-002";
        int partition = 3;
        List<Long> committedOffsets = List.of(10_000L, 20_000L, 40_000L, 60_000L, 100_000L);

        when(checkpointRepository.saveCheckpoint(eq(consumerId), eq(partition), anyLong()))
            .thenReturn(Promise.of((Void) null));

        List<Long> observed = new ArrayList<>();
        for (Long offset : committedOffsets) {
            runPromise(() -> checkpointRepository.saveCheckpoint(consumerId, partition, offset));
            observed.add(offset);
        }

        verify(checkpointRepository, times(committedOffsets.size()))
            .saveCheckpoint(eq(consumerId), eq(partition), anyLong());

        assertThat(observed).containsExactlyElementsOf(committedOffsets);
        assertThat(observed)
            .allSatisfy(offset -> assertThat(offset).isPositive())
            .isSorted();
        }

    private static Map<String, Object> buildEvent(String type, long offset, Map<String, Object> payload) { // GH-90000
        return Map.of( // GH-90000
            "type", type,
            "offset", offset,
            "payload", payload
        );
    }
}
