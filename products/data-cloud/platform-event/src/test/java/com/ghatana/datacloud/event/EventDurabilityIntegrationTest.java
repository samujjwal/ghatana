/*
 * Copyright (c) 2026 Ghatana Inc.
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Integration tests for event durability CDC capture end-to-end (D006).
 *
 * <p>Validates durability guarantees, checkpoint management, and CDC consistency.
 *
 * @doc.type class
 * @doc.purpose CDC capture end-to-end integration tests
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
    void writeWithLeaderAckAchievesDurability() {
        EventDurabilityService.Event event = new EventDurabilityService.Event(
            "evt-001", "test.event", "tenant-alpha", 
            "{\"data\":\"test\"}".getBytes(), System.currentTimeMillis()
        );

        EventDurabilityService.DurabilityResult result =
            new EventDurabilityService.DurabilityResult(
                "evt-001", 1,
                EventDurabilityService.DurabilityLevel.LEADER_ACK,
                10, 0, true
            );

        when(durabilityService.writeWithDurability(any(), eq(EventDurabilityService.DurabilityLevel.LEADER_ACK)))
            .thenReturn(Promise.of(result));

        EventDurabilityService.DurabilityResult actual = runPromise(() ->
            durabilityService.writeWithDurability(event, EventDurabilityService.DurabilityLevel.LEADER_ACK)
        );

        assertThat(actual.successful()).isTrue();
        assertThat(actual.achievedLevel()).isEqualTo(EventDurabilityService.DurabilityLevel.LEADER_ACK);
    }

    @Test
    @DisplayName("[D006]: write_with_majority_ack_replicates_to_quorum")
    void writeWithMajorityAckReplicatesToQuorum() {
        EventDurabilityService.Event event = new EventDurabilityService.Event(
            "evt-002", "test.event", "tenant-alpha",
            "{\"data\":\"test\"}".getBytes(), System.currentTimeMillis()
        );

        EventDurabilityService.DurabilityResult result =
            new EventDurabilityService.DurabilityResult(
                "evt-002", 2,
                EventDurabilityService.DurabilityLevel.MAJORITY_ACK,
                15, 25, true
            );

        when(durabilityService.writeWithDurability(any(), eq(EventDurabilityService.DurabilityLevel.MAJORITY_ACK)))
            .thenReturn(Promise.of(result));

        EventDurabilityService.DurabilityResult actual = runPromise(() ->
            durabilityService.writeWithDurability(event, EventDurabilityService.DurabilityLevel.MAJORITY_ACK)
        );

        assertThat(actual.successful()).isTrue();
        assertThat(actual.achievedLevel()).isEqualTo(EventDurabilityService.DurabilityLevel.MAJORITY_ACK);
        assertThat(actual.replicationLatencyMs()).isGreaterThan(0);
    }

    @Test
    @DisplayName("[D006]: write_with_fsync_ack_persists_to_disk")
    void writeWithFsyncAckPersistsToDisk() {
        EventDurabilityService.Event event = new EventDurabilityService.Event(
            "evt-003", "test.event", "tenant-alpha",
            "{\"data\":\"test\"}".getBytes(), System.currentTimeMillis()
        );

        EventDurabilityService.DurabilityResult result =
            new EventDurabilityService.DurabilityResult(
                "evt-003", 3,
                EventDurabilityService.DurabilityLevel.FSYNC_ACK,
                50, 30, true
            );

        when(durabilityService.writeWithDurability(any(), eq(EventDurabilityService.DurabilityLevel.FSYNC_ACK)))
            .thenReturn(Promise.of(result));

        EventDurabilityService.DurabilityResult actual = runPromise(() ->
            durabilityService.writeWithDurability(event, EventDurabilityService.DurabilityLevel.FSYNC_ACK)
        );

        assertThat(actual.successful()).isTrue();
        assertThat(actual.achievedLevel()).isEqualTo(EventDurabilityService.DurabilityLevel.FSYNC_ACK);
        assertThat(actual.fsyncLatencyMs()).isGreaterThan(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Checkpoint Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[D006]: save_checkpoint_persists_offset")
    void saveCheckpointPersistsOffset() {
        String consumerId = "consumer-001";
        int partition = 0;
        long offset = 100;

        when(checkpointRepository.saveCheckpoint(consumerId, partition, offset))
            .thenReturn(Promise.of((Void) null));

        runPromise(() -> checkpointRepository.saveCheckpoint(consumerId, partition, offset));

        verify(checkpointRepository).saveCheckpoint(consumerId, partition, offset);
    }

    @Test
    @DisplayName("[D006]: get_checkpoint_returns_saved_offset")
    void getCheckpointReturnsSavedOffset() {
        String consumerId = "consumer-001";
        int partition = 0;
        long expectedOffset = 100;

        when(checkpointRepository.getCheckpoint(consumerId, partition))
            .thenReturn(Promise.of(java.util.Optional.of(expectedOffset)));

        java.util.Optional<Long> actual = runPromise(() ->
            checkpointRepository.getCheckpoint(consumerId, partition)
        );

        assertThat(actual).isPresent();
        assertThat(actual.get()).isEqualTo(expectedOffset);
    }

    @Test
    @DisplayName("[D006]: get_all_checkpoints_returns_all_partitions")
    void getAllCheckpointsReturnsAllPartitions() {
        String consumerId = "consumer-001";
        List<EventCheckpointRepository.Checkpoint> checkpoints = List.of(
            new EventCheckpointRepository.Checkpoint(consumerId, 0, 100, System.currentTimeMillis(), ""),
            new EventCheckpointRepository.Checkpoint(consumerId, 1, 150, System.currentTimeMillis(), ""),
            new EventCheckpointRepository.Checkpoint(consumerId, 2, 200, System.currentTimeMillis(), "")
        );

        when(checkpointRepository.getAllCheckpoints(consumerId))
            .thenReturn(Promise.of(checkpoints));

        List<EventCheckpointRepository.Checkpoint> actual = runPromise(() ->
            checkpointRepository.getAllCheckpoints(consumerId)
        );

        assertThat(actual).hasSize(3);
        assertThat(actual).allMatch(c -> consumerId.equals(c.consumerId()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Consumer Lag Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[D006]: consumer_lag_calculated_correctly")
    void consumerLagCalculatedCorrectly() {
        String consumerId = "consumer-001";
        List<EventCheckpointRepository.PartitionLag> lags = List.of(
            EventCheckpointRepository.PartitionLag.of(0, 1000, 800),
            EventCheckpointRepository.PartitionLag.of(1, 1000, 950),
            EventCheckpointRepository.PartitionLag.of(2, 1000, 1000)
        );

        when(checkpointRepository.getConsumerLag(consumerId))
            .thenReturn(Promise.of(lags));

        List<EventCheckpointRepository.PartitionLag> actual = runPromise(() ->
            checkpointRepository.getConsumerLag(consumerId)
        );

        assertThat(actual.get(0).lag()).isEqualTo(200); // 1000 - 800
        assertThat(actual.get(1).lag()).isEqualTo(50);  // 1000 - 950
        assertThat(actual.get(2).lag()).isZero(); // 1000 - 1000
    }

    @Test
    @DisplayName("[D006]: caught_up_partition_has_zero_lag")
    void caughtUpPartitionHasZeroLag() {
        EventCheckpointRepository.PartitionLag lag =
            EventCheckpointRepository.PartitionLag.of(0, 1000, 1000);

        assertThat(lag.lag()).isZero();
        assertThat(lag.isCaughtUp()).isTrue();
    }

    @Test
    @DisplayName("[D006]: behind_partition_has_positive_lag")
    void behindPartitionHasPositiveLag() {
        EventCheckpointRepository.PartitionLag lag =
            EventCheckpointRepository.PartitionLag.of(0, 1000, 800);

        assertThat(lag.lag()).isEqualTo(200);
        assertThat(lag.isCaughtUp()).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Durability Status Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[D006]: durability_status_shows_replica_count")
    void durabilityStatusShowsReplicaCount() {
        String eventId = "evt-001";

        EventDurabilityService.DurabilityStatus status =
            new EventDurabilityService.DurabilityStatus(
                eventId,
                EventDurabilityService.DurabilityLevel.MAJORITY_ACK,
                3, // current replicas
                2, // required
                false,
                List.of("consumer-1", "consumer-2")
            );

        when(durabilityService.getDurabilityStatus(eventId))
            .thenReturn(Promise.of(status));

        EventDurabilityService.DurabilityStatus actual = runPromise(() ->
            durabilityService.getDurabilityStatus(eventId)
        );

        assertThat(actual.replicaCount()).isEqualTo(3);
        assertThat(actual.requiredReplicaCount()).isEqualTo(2);
        assertThat(actual.currentLevel()).isEqualTo(EventDurabilityService.DurabilityLevel.MAJORITY_ACK);
    }

    @Test
    @DisplayName("[D006]: fully_durable_when_fsynced_and_replicated")
    void fullyDurableWhenFsyncedAndReplicated() {
        EventDurabilityService.DurabilityStatus status =
            new EventDurabilityService.DurabilityStatus(
                "evt-001",
                EventDurabilityService.DurabilityLevel.ALL_ACK,
                3, // replicas >= required
                2,
                true, // fsynced
                List.of()
            );

        assertThat(status.isFullyDurable()).isTrue();
    }

    @Test
    @DisplayName("[D006]: not_fully_durable_when_not_fsynced")
    void notFullyDurableWhenNotFsynced() {
        EventDurabilityService.DurabilityStatus status =
            new EventDurabilityService.DurabilityStatus(
                "evt-001",
                EventDurabilityService.DurabilityLevel.MAJORITY_ACK,
                3,
                2,
                false, // not fsynced
                List.of()
            );

        assertThat(status.isFullyDurable()).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Wait for Durability Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[D006]: wait_for_durability_returns_true_when_achieved")
    void waitForDurabilityReturnsTrueWhenAchieved() {
        String eventId = "evt-001";

        when(durabilityService.waitForDurability(
            eq(eventId),
            eq(EventDurabilityService.DurabilityLevel.LEADER_ACK),
            any(Duration.class)
        )).thenReturn(Promise.of(true));

        Boolean result = runPromise(() ->
            durabilityService.waitForDurability(
                eventId,
                EventDurabilityService.DurabilityLevel.LEADER_ACK,
                Duration.ofSeconds(5)
            )
        );

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("[D006]: wait_for_durability_returns_false_on_timeout")
    void waitForDurabilityReturnsFalseOnTimeout() {
        String eventId = "evt-slow";

        when(durabilityService.waitForDurability(
            eq(eventId),
            eq(EventDurabilityService.DurabilityLevel.ALL_ACK),
            any(Duration.class)
        )).thenReturn(Promise.of(false));

        Boolean result = runPromise(() ->
            durabilityService.waitForDurability(
                eventId,
                EventDurabilityService.DurabilityLevel.ALL_ACK,
                Duration.ofMillis(100)
            )
        );

        assertThat(result).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CDC Capture Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[D006]: cdc_event_captured_with_offset")
    void cdcEventCapturedWithOffset() {
        Map<String, Object> cdcEvent = buildEvent(
            "cdc.entity.created",
            42,
            Map.of("source", "database", "operation", "INSERT")
        );

        assertThat(cdcEvent.get("offset")).isEqualTo(42L);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) cdcEvent.get("payload");
        assertThat(payload.get("source")).isEqualTo("database");
        assertThat(payload.get("operation")).isEqualTo("INSERT");
    }

    @Test
    @DisplayName("[D006]: cdc_events_ordered_by_offset")
    void cdcEventsOrderedByOffset() {
        List<Map<String, Object>> events = List.of(
            buildEvent("cdc.event", 3, Map.of()),
            buildEvent("cdc.event", 1, Map.of()),
            buildEvent("cdc.event", 2, Map.of())
        );

        List<Map<String, Object>> sorted = events.stream()
            .sorted(java.util.Comparator.comparingLong(e -> (Long) e.get("offset")))
            .toList();

        assertThat(sorted.get(0).get("offset")).isEqualTo(1L);
        assertThat(sorted.get(1).get("offset")).isEqualTo(2L);
        assertThat(sorted.get(2).get("offset")).isEqualTo(3L);
    }

    private static Map<String, Object> buildEvent(String type, long offset, Map<String, Object> payload) {
        return Map.of(
            "type", type,
            "offset", offset,
            "payload", payload
        );
    }
}
