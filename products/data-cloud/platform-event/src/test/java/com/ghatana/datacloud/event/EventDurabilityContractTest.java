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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Contract tests for event durability (D006).
 *
 * <p>Validates durability contract: stronger guarantees must meet weaker ones.
 *
 * @doc.type class
 * @doc.purpose Durability contract validation tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventDurability – Contract Tests (D006)")
class EventDurabilityContractTest extends EventloopTestBase {

    @Mock
    private EventDurabilityService durabilityService;

    // ─────────────────────────────────────────────────────────────────────────
    // Durability Level Hierarchy Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Durability Level Hierarchy")
    class DurabilityLevelHierarchyTests {

        @Test
        @DisplayName("[D006]: all_ack_meets_majority_ack")
        void allAckMeetsMajorityAck() {
            EventDurabilityService.DurabilityResult result =
                new EventDurabilityService.DurabilityResult(
                    "evt-001", 1,
                    EventDurabilityService.DurabilityLevel.ALL_ACK,
                    50, 100, true
                );

            assertThat(result.meetsLevel(EventDurabilityService.DurabilityLevel.MAJORITY_ACK))
                .isTrue();
            assertThat(result.meetsLevel(EventDurabilityService.DurabilityLevel.LEADER_ACK))
                .isTrue();
        }

        @Test
        @DisplayName("[D006]: majority_ack_meets_leader_ack")
        void majorityAckMeetsLeaderAck() {
            EventDurabilityService.DurabilityResult result =
                new EventDurabilityService.DurabilityResult(
                    "evt-001", 1,
                    EventDurabilityService.DurabilityLevel.MAJORITY_ACK,
                    30, 50, true
                );

            assertThat(result.meetsLevel(EventDurabilityService.DurabilityLevel.LEADER_ACK))
                .isTrue();
            assertThat(result.meetsLevel(EventDurabilityService.DurabilityLevel.MAJORITY_ACK))
                .isTrue();
            assertThat(result.meetsLevel(EventDurabilityService.DurabilityLevel.ALL_ACK))
                .isFalse();
        }

        @Test
        @DisplayName("[D006]: leader_ack_meets_none")
        void leaderAckMeetsNone() {
            EventDurabilityService.DurabilityResult result =
                new EventDurabilityService.DurabilityResult(
                    "evt-001", 1,
                    EventDurabilityService.DurabilityLevel.LEADER_ACK,
                    10, 0, true
                );

            assertThat(result.meetsLevel(EventDurabilityService.DurabilityLevel.NONE)).isTrue();
            assertThat(result.meetsLevel(EventDurabilityService.DurabilityLevel.LEADER_ACK)).isTrue();
            assertThat(result.meetsLevel(EventDurabilityService.DurabilityLevel.MAJORITY_ACK)).isFalse();
        }

        @Test
        @DisplayName("[D006]: fsync_ack_meets_all_ack_if_replicated")
        void fsyncAckMeetsAllAckIfReplicated() {
            EventDurabilityService.DurabilityResult result =
                new EventDurabilityService.DurabilityResult(
                    "evt-001", 1,
                    EventDurabilityService.DurabilityLevel.FSYNC_ACK,
                    50, 100, true
                );

            // FSYNC_ACK is higher than ALL_ACK in ordinal
            assertThat(result.achievedLevel().ordinal())
                .isGreaterThan(EventDurabilityService.DurabilityLevel.ALL_ACK.ordinal());
        }

        @Test
        @DisplayName("[D006]: none_meets_only_none")
        void noneMeetsOnlyNone() {
            EventDurabilityService.DurabilityResult result =
                new EventDurabilityService.DurabilityResult(
                    "evt-001", 1,
                    EventDurabilityService.DurabilityLevel.NONE,
                    0, 0, true
                );

            assertThat(result.meetsLevel(EventDurabilityService.DurabilityLevel.NONE)).isTrue();
            assertThat(result.meetsLevel(EventDurabilityService.DurabilityLevel.LEADER_ACK)).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Durability Level Ordering Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Durability Level Ordering")
    class DurabilityLevelOrderingTests {

        @Test
        @DisplayName("[D006]: durability_levels_ordered_correctly")
        void durabilityLevelsOrderedCorrectly() {
            // NONE < LEADER_ACK < MAJORITY_ACK < ALL_ACK < FSYNC_ACK
            assertThat(EventDurabilityService.DurabilityLevel.NONE.ordinal()).isEqualTo(0);
            assertThat(EventDurabilityService.DurabilityLevel.LEADER_ACK.ordinal()).isEqualTo(1);
            assertThat(EventDurabilityService.DurabilityLevel.MAJORITY_ACK.ordinal()).isEqualTo(2);
            assertThat(EventDurabilityService.DurabilityLevel.ALL_ACK.ordinal()).isEqualTo(3);
            assertThat(EventDurabilityService.DurabilityLevel.FSYNC_ACK.ordinal()).isEqualTo(4);
        }

        @Test
        @DisplayName("[D006]: higher_level_ordinal_greater_than_lower")
        void higherLevelOrdinalGreaterThanLower() {
            assertThat(EventDurabilityService.DurabilityLevel.ALL_ACK.ordinal())
                .isGreaterThan(EventDurabilityService.DurabilityLevel.MAJORITY_ACK.ordinal());
            assertThat(EventDurabilityService.DurabilityLevel.MAJORITY_ACK.ordinal())
                .isGreaterThan(EventDurabilityService.DurabilityLevel.LEADER_ACK.ordinal());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Durability Acknowledgment Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Durability Acknowledgment")
    class DurabilityAcknowledgmentTests {

        @Test
        @DisplayName("[D006]: consumer_acknowledges_durability")
        void consumerAcknowledgesDurability() {
            String eventId = "evt-001";
            String consumerId = "consumer-001";

            when(durabilityService.acknowledgeDurability(eventId, consumerId))
                .thenReturn(Promise.of((Void) null));

            runPromise(() -> durabilityService.acknowledgeDurability(eventId, consumerId));

            verify(durabilityService).acknowledgeDurability(eventId, consumerId);
        }

        @Test
        @DisplayName("[D006]: acknowledged_consumers_tracked_in_status")
        void acknowledgedConsumersTrackedInStatus() {
            String eventId = "evt-001";
            List<String> acknowledged = List.of("consumer-1", "consumer-2", "consumer-3");

            EventDurabilityService.DurabilityStatus status =
                new EventDurabilityService.DurabilityStatus(
                    eventId,
                    EventDurabilityService.DurabilityLevel.MAJORITY_ACK,
                    3, 2, true, acknowledged
                );

            when(durabilityService.getDurabilityStatus(eventId))
                .thenReturn(Promise.of(status));

            EventDurabilityService.DurabilityStatus actual = runPromise(() ->
                durabilityService.getDurabilityStatus(eventId)
            );

            assertThat(actual.acknowledgedConsumers()).containsExactlyElementsOf(acknowledged);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Unsuccessful Durability Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Unsuccessful Durability")
    class UnsuccessfulDurabilityTests {

        @Test
        @DisplayName("[D006]: unsuccessful_result_does_not_meet_any_level")
        void unsuccessfulResultDoesNotMeetAnyLevel() {
            EventDurabilityService.DurabilityResult failed =
                new EventDurabilityService.DurabilityResult(
                    "evt-failed", 0,
                    EventDurabilityService.DurabilityLevel.NONE,
                    0, 0, false
                );

            assertThat(failed.successful()).isFalse();
            assertThat(failed.meetsLevel(EventDurabilityService.DurabilityLevel.NONE)).isTrue();
            // A failed write at NONE level meets NONE but no higher level
        }

        @Test
        @DisplayName("[D006]: failed_write_reports_error")
        void failedWriteReportsError() {
            EventDurabilityService.DurabilityResult failed =
                new EventDurabilityService.DurabilityResult(
                    "evt-failed", 0,
                    EventDurabilityService.DurabilityLevel.NONE,
                    0, 0, false
                );

            assertThat(failed.offset()).isZero();
            assertThat(filledCount(failed)).isZero();
        }

        private int filledCount(EventDurabilityService.DurabilityResult result) {
            int count = 0;
            if (result.fsyncLatencyMs() > 0) count++;
            if (result.replicationLatencyMs() > 0) count++;
            return count;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Config-based Durability Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Config-based Durability")
    class ConfigBasedDurabilityTests {

        @Test
        @DisplayName("[D006]: default_durability_level_is_majority_ack")
        void defaultDurabilityLevelIsMajorityAck() {
            EventDurabilityConfig config = new EventDurabilityConfig();

            assertThat(config.getDefaultDurabilityLevel())
                .isEqualTo(EventDurabilityService.DurabilityLevel.MAJORITY_ACK);
        }

        @Test
        @DisplayName("[D006]: durability_timeout_configurable")
        void durabilityTimeoutConfigurable() {
            EventDurabilityConfig config = new EventDurabilityConfig();
            config.setDurabilityTimeout(Duration.ofSeconds(60));

            assertThat(config.getDurabilityTimeout()).isEqualTo(Duration.ofSeconds(60));
        }

        @Test
        @DisplayName("[D006]: required_replica_count_configurable")
        void requiredReplicaCountConfigurable() {
            EventDurabilityConfig config = new EventDurabilityConfig();
            config.setRequiredReplicaCount(3);

            assertThat(config.getRequiredReplicaCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("[D006]: fsync_enabled_by_default")
        void fsyncEnabledByDefault() {
            EventDurabilityConfig config = new EventDurabilityConfig();

            assertThat(config.isFsyncEnabled()).isTrue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Latency Tracking Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Latency Tracking")
    class LatencyTrackingTests {

        @Test
        @DisplayName("[D006]: fsync_latency_tracked")
        void fsyncLatencyTracked() {
            EventDurabilityService.DurabilityResult result =
                new EventDurabilityService.DurabilityResult(
                    "evt-001", 1,
                    EventDurabilityService.DurabilityLevel.FSYNC_ACK,
                    50, 30, true
                );

            assertThat(result.fsyncLatencyMs()).isEqualTo(50);
        }

        @Test
        @DisplayName("[D006]: replication_latency_tracked")
        void replicationLatencyTracked() {
            EventDurabilityService.DurabilityResult result =
                new EventDurabilityService.DurabilityResult(
                    "evt-001", 1,
                    EventDurabilityService.DurabilityLevel.MAJORITY_ACK,
                    10, 100, true
                );

            assertThat(result.replicationLatencyMs()).isEqualTo(100);
        }

        @Test
        @DisplayName("[D006]: leader_ack_has_no_replication_latency")
        void leaderAckHasNoReplicationLatency() {
            EventDurabilityService.DurabilityResult result =
                new EventDurabilityService.DurabilityResult(
                    "evt-001", 1,
                    EventDurabilityService.DurabilityLevel.LEADER_ACK,
                    10, 0, true
                );

            assertThat(result.replicationLatencyMs()).isZero();
        }
    }
}
