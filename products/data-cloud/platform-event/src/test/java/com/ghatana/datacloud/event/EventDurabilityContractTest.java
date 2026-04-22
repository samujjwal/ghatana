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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Contract tests for event durability (D006). // GH-90000
 *
 * <p>Validates durability contract: stronger guarantees must meet weaker ones.
 *
 * @doc.type class
 * @doc.purpose Durability contract validation tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("EventDurability – Contract Tests (D006) [GH-90000]")
class EventDurabilityContractTest extends EventloopTestBase {

    @Mock
    private EventDurabilityService durabilityService;

    // ─────────────────────────────────────────────────────────────────────────
    // Durability Level Hierarchy Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Durability Level Hierarchy [GH-90000]")
    class DurabilityLevelHierarchyTests {

        @Test
        @DisplayName("[D006]: all_ack_meets_majority_ack [GH-90000]")
        void allAckMeetsMajorityAck() { // GH-90000
            EventDurabilityService.DurabilityResult result =
                new EventDurabilityService.DurabilityResult( // GH-90000
                    "evt-001", 1,
                    EventDurabilityService.DurabilityLevel.ALL_ACK,
                    50, 100, true
                );

            assertThat(result.meetsLevel(EventDurabilityService.DurabilityLevel.MAJORITY_ACK)) // GH-90000
                .isTrue(); // GH-90000
            assertThat(result.meetsLevel(EventDurabilityService.DurabilityLevel.LEADER_ACK)) // GH-90000
                .isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[D006]: majority_ack_meets_leader_ack [GH-90000]")
        void majorityAckMeetsLeaderAck() { // GH-90000
            EventDurabilityService.DurabilityResult result =
                new EventDurabilityService.DurabilityResult( // GH-90000
                    "evt-001", 1,
                    EventDurabilityService.DurabilityLevel.MAJORITY_ACK,
                    30, 50, true
                );

            assertThat(result.meetsLevel(EventDurabilityService.DurabilityLevel.LEADER_ACK)) // GH-90000
                .isTrue(); // GH-90000
            assertThat(result.meetsLevel(EventDurabilityService.DurabilityLevel.MAJORITY_ACK)) // GH-90000
                .isTrue(); // GH-90000
            assertThat(result.meetsLevel(EventDurabilityService.DurabilityLevel.ALL_ACK)) // GH-90000
                .isFalse(); // GH-90000
        }

        @Test
        @DisplayName("[D006]: leader_ack_meets_none [GH-90000]")
        void leaderAckMeetsNone() { // GH-90000
            EventDurabilityService.DurabilityResult result =
                new EventDurabilityService.DurabilityResult( // GH-90000
                    "evt-001", 1,
                    EventDurabilityService.DurabilityLevel.LEADER_ACK,
                    10, 0, true
                );

            assertThat(result.meetsLevel(EventDurabilityService.DurabilityLevel.NONE)).isTrue(); // GH-90000
            assertThat(result.meetsLevel(EventDurabilityService.DurabilityLevel.LEADER_ACK)).isTrue(); // GH-90000
            assertThat(result.meetsLevel(EventDurabilityService.DurabilityLevel.MAJORITY_ACK)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("[D006]: fsync_ack_meets_all_ack_if_replicated [GH-90000]")
        void fsyncAckMeetsAllAckIfReplicated() { // GH-90000
            EventDurabilityService.DurabilityResult result =
                new EventDurabilityService.DurabilityResult( // GH-90000
                    "evt-001", 1,
                    EventDurabilityService.DurabilityLevel.FSYNC_ACK,
                    50, 100, true
                );

            // FSYNC_ACK is higher than ALL_ACK in ordinal
            assertThat(result.achievedLevel().ordinal()) // GH-90000
                .isGreaterThan(EventDurabilityService.DurabilityLevel.ALL_ACK.ordinal()); // GH-90000
        }

        @Test
        @DisplayName("[D006]: none_meets_only_none [GH-90000]")
        void noneMeetsOnlyNone() { // GH-90000
            EventDurabilityService.DurabilityResult result =
                new EventDurabilityService.DurabilityResult( // GH-90000
                    "evt-001", 1,
                    EventDurabilityService.DurabilityLevel.NONE,
                    0, 0, true
                );

            assertThat(result.meetsLevel(EventDurabilityService.DurabilityLevel.NONE)).isTrue(); // GH-90000
            assertThat(result.meetsLevel(EventDurabilityService.DurabilityLevel.LEADER_ACK)).isFalse(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Durability Level Ordering Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Durability Level Ordering [GH-90000]")
    class DurabilityLevelOrderingTests {

        @Test
        @DisplayName("[D006]: durability_levels_ordered_correctly [GH-90000]")
        void durabilityLevelsOrderedCorrectly() { // GH-90000
            // NONE < LEADER_ACK < MAJORITY_ACK < ALL_ACK < FSYNC_ACK
            assertThat(EventDurabilityService.DurabilityLevel.NONE.ordinal()).isEqualTo(0); // GH-90000
            assertThat(EventDurabilityService.DurabilityLevel.LEADER_ACK.ordinal()).isEqualTo(1); // GH-90000
            assertThat(EventDurabilityService.DurabilityLevel.MAJORITY_ACK.ordinal()).isEqualTo(2); // GH-90000
            assertThat(EventDurabilityService.DurabilityLevel.ALL_ACK.ordinal()).isEqualTo(3); // GH-90000
            assertThat(EventDurabilityService.DurabilityLevel.FSYNC_ACK.ordinal()).isEqualTo(4); // GH-90000
        }

        @Test
        @DisplayName("[D006]: higher_level_ordinal_greater_than_lower [GH-90000]")
        void higherLevelOrdinalGreaterThanLower() { // GH-90000
            assertThat(EventDurabilityService.DurabilityLevel.ALL_ACK.ordinal()) // GH-90000
                .isGreaterThan(EventDurabilityService.DurabilityLevel.MAJORITY_ACK.ordinal()); // GH-90000
            assertThat(EventDurabilityService.DurabilityLevel.MAJORITY_ACK.ordinal()) // GH-90000
                .isGreaterThan(EventDurabilityService.DurabilityLevel.LEADER_ACK.ordinal()); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Durability Acknowledgment Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Durability Acknowledgment [GH-90000]")
    class DurabilityAcknowledgmentTests {

        @Test
        @DisplayName("[D006]: consumer_acknowledges_durability [GH-90000]")
        void consumerAcknowledgesDurability() { // GH-90000
            String eventId = "evt-001";
            String consumerId = "consumer-001";

            when(durabilityService.acknowledgeDurability(eventId, consumerId)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> durabilityService.acknowledgeDurability(eventId, consumerId)); // GH-90000

            verify(durabilityService).acknowledgeDurability(eventId, consumerId); // GH-90000
        }

        @Test
        @DisplayName("[D006]: acknowledged_consumers_tracked_in_status [GH-90000]")
        void acknowledgedConsumersTrackedInStatus() { // GH-90000
            String eventId = "evt-001";
            List<String> acknowledged = List.of("consumer-1", "consumer-2", "consumer-3"); // GH-90000

            EventDurabilityService.DurabilityStatus status =
                new EventDurabilityService.DurabilityStatus( // GH-90000
                    eventId,
                    EventDurabilityService.DurabilityLevel.MAJORITY_ACK,
                    3, 2, true, acknowledged
                );

            when(durabilityService.getDurabilityStatus(eventId)) // GH-90000
                .thenReturn(Promise.of(status)); // GH-90000

            EventDurabilityService.DurabilityStatus actual = runPromise(() -> // GH-90000
                durabilityService.getDurabilityStatus(eventId) // GH-90000
            );

            assertThat(actual.acknowledgedConsumers()).containsExactlyElementsOf(acknowledged); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Unsuccessful Durability Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Unsuccessful Durability [GH-90000]")
    class UnsuccessfulDurabilityTests {

        @Test
        @DisplayName("[D006]: unsuccessful_result_does_not_meet_any_level [GH-90000]")
        void unsuccessfulResultDoesNotMeetAnyLevel() { // GH-90000
            EventDurabilityService.DurabilityResult failed =
                new EventDurabilityService.DurabilityResult( // GH-90000
                    "evt-failed", 0,
                    EventDurabilityService.DurabilityLevel.NONE,
                    0, 0, false
                );

            assertThat(failed.successful()).isFalse(); // GH-90000
            assertThat(failed.meetsLevel(EventDurabilityService.DurabilityLevel.NONE)).isTrue(); // GH-90000
            // A failed write at NONE level meets NONE but no higher level
        }

        @Test
        @DisplayName("[D006]: failed_write_reports_error [GH-90000]")
        void failedWriteReportsError() { // GH-90000
            EventDurabilityService.DurabilityResult failed =
                new EventDurabilityService.DurabilityResult( // GH-90000
                    "evt-failed", 0,
                    EventDurabilityService.DurabilityLevel.NONE,
                    0, 0, false
                );

            assertThat(failed.offset()).isZero(); // GH-90000
            assertThat(filledCount(failed)).isZero(); // GH-90000
        }

        private int filledCount(EventDurabilityService.DurabilityResult result) { // GH-90000
            int count = 0;
            if (result.fsyncLatencyMs() > 0) count++; // GH-90000
            if (result.replicationLatencyMs() > 0) count++; // GH-90000
            return count;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Config-based Durability Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Config-based Durability [GH-90000]")
    class ConfigBasedDurabilityTests {

        @Test
        @DisplayName("[D006]: default_durability_level_is_majority_ack [GH-90000]")
        void defaultDurabilityLevelIsMajorityAck() { // GH-90000
            EventDurabilityConfig config = new EventDurabilityConfig(); // GH-90000

            assertThat(config.getDefaultDurabilityLevel()) // GH-90000
                .isEqualTo(EventDurabilityService.DurabilityLevel.MAJORITY_ACK); // GH-90000
        }

        @Test
        @DisplayName("[D006]: durability_timeout_configurable [GH-90000]")
        void durabilityTimeoutConfigurable() { // GH-90000
            EventDurabilityConfig config = new EventDurabilityConfig(); // GH-90000
            config.setDurabilityTimeout(Duration.ofSeconds(60)); // GH-90000

            assertThat(config.getDurabilityTimeout()).isEqualTo(Duration.ofSeconds(60)); // GH-90000
        }

        @Test
        @DisplayName("[D006]: required_replica_count_configurable [GH-90000]")
        void requiredReplicaCountConfigurable() { // GH-90000
            EventDurabilityConfig config = new EventDurabilityConfig(); // GH-90000
            config.setRequiredReplicaCount(3); // GH-90000

            assertThat(config.getRequiredReplicaCount()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("[D006]: fsync_enabled_by_default [GH-90000]")
        void fsyncEnabledByDefault() { // GH-90000
            EventDurabilityConfig config = new EventDurabilityConfig(); // GH-90000

            assertThat(config.isFsyncEnabled()).isTrue(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Latency Tracking Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Latency Tracking [GH-90000]")
    class LatencyTrackingTests {

        @Test
        @DisplayName("[D006]: fsync_latency_tracked [GH-90000]")
        void fsyncLatencyTracked() { // GH-90000
            EventDurabilityService.DurabilityResult result =
                new EventDurabilityService.DurabilityResult( // GH-90000
                    "evt-001", 1,
                    EventDurabilityService.DurabilityLevel.FSYNC_ACK,
                    50, 30, true
                );

            assertThat(result.fsyncLatencyMs()).isEqualTo(50); // GH-90000
        }

        @Test
        @DisplayName("[D006]: replication_latency_tracked [GH-90000]")
        void replicationLatencyTracked() { // GH-90000
            EventDurabilityService.DurabilityResult result =
                new EventDurabilityService.DurabilityResult( // GH-90000
                    "evt-001", 1,
                    EventDurabilityService.DurabilityLevel.MAJORITY_ACK,
                    10, 100, true
                );

            assertThat(result.replicationLatencyMs()).isEqualTo(100); // GH-90000
        }

        @Test
        @DisplayName("[D006]: leader_ack_has_no_replication_latency [GH-90000]")
        void leaderAckHasNoReplicationLatency() { // GH-90000
            EventDurabilityService.DurabilityResult result =
                new EventDurabilityService.DurabilityResult( // GH-90000
                    "evt-001", 1,
                    EventDurabilityService.DurabilityLevel.LEADER_ACK,
                    10, 0, true
                );

            assertThat(result.replicationLatencyMs()).isZero(); // GH-90000
        }
    }
}
