/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DataCloudClient checkpoint and replay semantics (P3-05).
 *
 * <p>Validates:
 * <ul>
 *   <li>Checkpoint storage and retrieval</li>
 *   <li>Replay from checkpoint</li>
 *   <li>Exactly-once processing semantics</li>
 *   <li>Multi-stream checkpoint isolation</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Tests for checkpoint/replay functionality
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudClient Checkpoint/Replay Tests")
class DataCloudClientReplayTest extends EventloopTestBase {

    private static final String TEST_TENANT = "test-tenant";
    private static final String TEST_STREAM = "test-stream";
    private static final String OTHER_STREAM = "other-stream";

    @Nested
    @DisplayName("Checkpoint Storage (P3-03: New API with consumer group)")
    class CheckpointStorageTests {

        private static final String CONSUMER_GROUP = "test-consumer-group";

        @Test
        @DisplayName("Store and retrieve checkpoint with consumer group")
        void storeAndRetrieveCheckpoint() {
            DataCloudClient client = DataCloud.forTesting();

            // Store checkpoint at offset 100 with consumer group
            DataCloudClient.Checkpoint stored = runPromise(() -> 
                client.commitCheckpoint(TEST_TENANT, TEST_STREAM, CONSUMER_GROUP, 100L, "idempotency-key-1"));
            assertThat(stored.offset()).isEqualTo(100L);
            assertThat(stored.consumerGroup()).isEqualTo(CONSUMER_GROUP);

            // Retrieve checkpoint
            Optional<DataCloudClient.Checkpoint> checkpoint = runPromise(() -> 
                client.readCheckpoint(TEST_TENANT, TEST_STREAM, CONSUMER_GROUP));
            assertThat(checkpoint).isPresent();
            assertThat(checkpoint.get().offset()).isEqualTo(100L);
            assertThat(checkpoint.get().consumerGroup()).isEqualTo(CONSUMER_GROUP);
        }

        @Test
        @DisplayName("Update existing checkpoint with idempotency")
        void updateExistingCheckpoint() {
            DataCloudClient client = DataCloud.forTesting();

            // Store initial checkpoint
            runPromise(() -> client.commitCheckpoint(TEST_TENANT, TEST_STREAM, CONSUMER_GROUP, 50L, "idempotency-key-1"));

            // Update checkpoint with same idempotency key (idempotent)
            DataCloudClient.Checkpoint updated = runPromise(() -> 
                client.commitCheckpoint(TEST_TENANT, TEST_STREAM, CONSUMER_GROUP, 150L, "idempotency-key-1"));
            assertThat(updated.offset()).isEqualTo(150L);

            // Verify updated value
            Optional<DataCloudClient.Checkpoint> checkpoint = runPromise(() -> 
                client.readCheckpoint(TEST_TENANT, TEST_STREAM, CONSUMER_GROUP));
            assertThat(checkpoint.get().offset()).isEqualTo(150L);
        }

        @Test
        @DisplayName("Delete checkpoint with consumer group")
        void deleteCheckpoint() {
            DataCloudClient client = DataCloud.forTesting();

            // Store checkpoint
            runPromise(() -> client.commitCheckpoint(TEST_TENANT, TEST_STREAM, CONSUMER_GROUP, 100L, "idempotency-key-1"));

            // Delete checkpoint
            Boolean deleted = runPromise(() -> client.deleteCheckpoint(TEST_TENANT, TEST_STREAM, CONSUMER_GROUP));
            assertThat(deleted).isTrue();

            // Verify checkpoint is gone (returns empty)
            Optional<DataCloudClient.Checkpoint> checkpoint = runPromise(() -> 
                client.readCheckpoint(TEST_TENANT, TEST_STREAM, CONSUMER_GROUP));
            assertThat(checkpoint).isEmpty();
        }

        @Test
        @DisplayName("Consumer group isolation - checkpoints are independent")
        void consumerGroupIsolation() {
            DataCloudClient client = DataCloud.forTesting();
            String otherConsumerGroup = "other-consumer-group";

            // Store checkpoints for different consumer groups
            runPromise(() -> client.commitCheckpoint(TEST_TENANT, TEST_STREAM, CONSUMER_GROUP, 100L, "idempotency-key-1"));
            runPromise(() -> client.commitCheckpoint(TEST_TENANT, TEST_STREAM, otherConsumerGroup, 200L, "idempotency-key-2"));

            // Verify each consumer group has its own checkpoint
            Optional<DataCloudClient.Checkpoint> checkpoint1 = runPromise(() -> 
                client.readCheckpoint(TEST_TENANT, TEST_STREAM, CONSUMER_GROUP));
            Optional<DataCloudClient.Checkpoint> checkpoint2 = runPromise(() -> 
                client.readCheckpoint(TEST_TENANT, TEST_STREAM, otherConsumerGroup));

            assertThat(checkpoint1.get().offset()).isEqualTo(100L);
            assertThat(checkpoint2.get().offset()).isEqualTo(200L);
        }

        @Test
        @DisplayName("P3-03: Deprecated checkpoint API throws exception")
        void deprecatedCheckpointApiThrowsException() {
            DataCloudClient client = DataCloud.forTesting();

            assertThatThrownBy(() -> runPromise(() -> client.checkpoint(TEST_TENANT, TEST_STREAM, 100L)))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("deprecated");
        }
    }

    @Nested
    @DisplayName("Event Replay (P3-03: New ReplayRequest API)")
    class EventReplayTests {

        @Test
        @DisplayName("Replay events from offset range")
        void replayEventsFromOffsetRange() {
            DataCloudClient client = DataCloud.forTesting();

            // Append some events
            for (int i = 0; i < 10; i++) {
                DataCloudClient.Event event = DataCloudClient.Event.builder()
                    .type("test-event")
                    .payload(Map.of("index", i))
                    .source("test")
                    .build();
                runPromise(() -> client.appendEvent(TEST_TENANT, event));
            }

            // Replay events from offset 3 to 7 using ReplayRequest
            DataCloudClient.ReplayRequest request = DataCloudClient.ReplayRequest.bounded(3L, 7L);
            List<DataCloudClient.Event> events = runPromise(() -> client.replay(TEST_TENANT, request));

            // Should get 5 events (offsets 3, 4, 5, 6, 7)
            assertThat(events).hasSize(5);
        }

        @Test
        @DisplayName("Replay events with filter")
        void replayEventsWithFilter() {
            DataCloudClient client = DataCloud.forTesting();

            // Append events of different types
            DataCloudClient.Event eventA = DataCloudClient.Event.builder()
                .type("type-a")
                .payload(Map.of("data", "a"))
                .source("test")
                .build();
            DataCloudClient.Event eventB = DataCloudClient.Event.builder()
                .type("type-b")
                .payload(Map.of("data", "b"))
                .source("test")
                .build();

            runPromise(() -> client.appendEvent(TEST_TENANT, eventA));
            runPromise(() -> client.appendEvent(TEST_TENANT, eventB));
            runPromise(() -> client.appendEvent(TEST_TENANT, eventA));

            // Replay only type-a events using ReplayRequest
            DataCloudClient.ReplayRequest request = DataCloudClient.ReplayRequest.filtered(0L, 10L, List.of("type-a"));
            List<DataCloudClient.Event> events = runPromise(() -> client.replay(TEST_TENANT, request));

            assertThat(events).hasSize(2);
            assertThat(events.get(0).type()).isEqualTo("type-a");
            assertThat(events.get(1).type()).isEqualTo("type-a");
        }

        @Test
        @DisplayName("P3-03: Replay respects fromOffset")
        void replayRespectsFromOffset() {
            DataCloudClient client = DataCloud.forTesting();

            // Append events
            for (int i = 0; i < 10; i++) {
                DataCloudClient.Event event = DataCloudClient.Event.builder()
                    .type("test-event")
                    .payload(Map.of("index", i))
                    .source("test")
                    .build();
                runPromise(() -> client.appendEvent(TEST_TENANT, event));
            }

            // Replay from offset 5
            DataCloudClient.ReplayRequest request = DataCloudClient.ReplayRequest.fromOffset(5L);
            List<DataCloudClient.Event> events = runPromise(() -> client.replay(TEST_TENANT, request));

            // Should get events from offset 5 onwards (5 events: 5, 6, 7, 8, 9)
            assertThat(events).hasSize(5);
        }

        @Test
        @DisplayName("P3-03: Replay respects toOffset")
        void replayRespectsToOffset() {
            DataCloudClient client = DataCloud.forTesting();

            // Append events
            for (int i = 0; i < 10; i++) {
                DataCloudClient.Event event = DataCloudClient.Event.builder()
                    .type("test-event")
                    .payload(Map.of("index", i))
                    .source("test")
                    .build();
                runPromise(() -> client.appendEvent(TEST_TENANT, event));
            }

            // Replay from offset 0 to 4
            DataCloudClient.ReplayRequest request = DataCloudClient.ReplayRequest.bounded(0L, 4L);
            List<DataCloudClient.Event> events = runPromise(() -> client.replay(TEST_TENANT, request));

            // Should get events from offset 0 to 4 (5 events: 0, 1, 2, 3, 4)
            assertThat(events).hasSize(5);
        }
    }

    @Nested
    @DisplayName("Replay from Checkpoint (P3-03: Using new checkpoint API)")
    class ReplayFromCheckpointTests {

        private static final String CONSUMER_GROUP = "replay-consumer-group";

        @Test
        @DisplayName("Replay from last checkpoint")
        void replayFromLastCheckpoint() {
            DataCloudClient client = DataCloud.forTesting();

            // Append events
            for (int i = 0; i < 5; i++) {
                DataCloudClient.Event event = DataCloudClient.Event.builder()
                    .type("test-event")
                    .payload(Map.of("index", i))
                    .source("test")
                    .build();
                runPromise(() -> client.appendEvent(TEST_TENANT, event));
            }

            // Set checkpoint at offset 2 using new API
            runPromise(() -> client.commitCheckpoint(TEST_TENANT, TEST_STREAM, CONSUMER_GROUP, 2L, "idempotency-key-1"));

            // Read checkpoint and replay from it
            Optional<DataCloudClient.Checkpoint> checkpoint = runPromise(() -> 
                client.readCheckpoint(TEST_TENANT, TEST_STREAM, CONSUMER_GROUP));
            assertThat(checkpoint).isPresent();

            DataCloudClient.ReplayRequest request = DataCloudClient.ReplayRequest.fromOffset(checkpoint.get().offset());
            List<DataCloudClient.Event> events = runPromise(() -> client.replay(TEST_TENANT, request));

            // Should get events from offset 2 onwards (3 events: 2, 3, 4)
            assertThat(events).hasSize(3);
        }

        @Test
        @DisplayName("Replay from beginning when no checkpoint exists")
        void replayFromBeginningWhenNoCheckpoint() {
            DataCloudClient client = DataCloud.forTesting();

            // Append events without setting checkpoint
            for (int i = 0; i < 3; i++) {
                DataCloudClient.Event event = DataCloudClient.Event.builder()
                    .type("test-event")
                    .payload(Map.of("index", i))
                    .source("test")
                    .build();
                runPromise(() -> client.appendEvent(TEST_TENANT, event));
            }

            // Read checkpoint (none exists, should return empty)
            Optional<DataCloudClient.Checkpoint> checkpoint = runPromise(() -> 
                client.readCheckpoint(TEST_TENANT, "new-stream", CONSUMER_GROUP));
            assertThat(checkpoint).isEmpty();

            // Replay from offset 0 (beginning)
            DataCloudClient.ReplayRequest request = DataCloudClient.ReplayRequest.fromOffset(0L);
            List<DataCloudClient.Event> events = runPromise(() -> client.replay(TEST_TENANT, request));

            // Should process all events from beginning
            assertThat(events).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Checkpoint Administration (P3-03: New API with metadata)")
    class CheckpointAdministrationTests {

        @Test
        @DisplayName("Get all checkpoints with metadata for tenant")
        void getAllCheckpoints() {
            DataCloudClient client = DataCloud.forTesting();
            String consumerGroup1 = "consumer-1";
            String consumerGroup2 = "consumer-2";

            // Store multiple checkpoints with consumer groups
            runPromise(() -> client.commitCheckpoint(TEST_TENANT, TEST_STREAM, consumerGroup1, 100L, "idempotency-key-1"));
            runPromise(() -> client.commitCheckpoint(TEST_TENANT, TEST_STREAM, consumerGroup2, 200L, "idempotency-key-2"));

            // Get all checkpoints with metadata
            Map<String, DataCloudClient.Checkpoint> checkpoints = runPromise(() -> client.getAllCheckpoints(TEST_TENANT));

            // Verify all checkpoints are present
            assertThat(checkpoints).hasSize(2);
        }

        @Test
        @DisplayName("Checkpoint lag calculation")
        void checkpointLagCalculation() {
            // Create checkpoint info manually for testing
            DataCloudClient.CheckpointInfo info = new DataCloudClient.CheckpointInfo(
                TEST_STREAM, 50L, 100L, java.time.Instant.now(), "test-consumer"
            );

            // Verify lag calculation
            assertThat(info.lag()).isEqualTo(50L); // 100 - 50
            assertThat(info.isCaughtUp()).isFalse();

            // Create caught-up scenario
            DataCloudClient.CheckpointInfo caughtUp = new DataCloudClient.CheckpointInfo(
                TEST_STREAM, 99L, 100L, java.time.Instant.now(), "test-consumer"
            );
            assertThat(caughtUp.lag()).isEqualTo(1L);
            assertThat(caughtUp.isCaughtUp()).isTrue();
        }

        @Test
        @DisplayName("Stale checkpoint detection")
        void staleCheckpointDetection() {
            java.time.Instant oldTime = java.time.Instant.now().minusSeconds(3600); // 1 hour ago

            DataCloudClient.CheckpointInfo info = new DataCloudClient.CheckpointInfo(
                TEST_STREAM, 50L, 100L, oldTime, "test-consumer"
            );

            // Should be stale with 30 minute threshold
            assertThat(info.isStale(java.time.Duration.ofMinutes(30))).isTrue();

            // Should not be stale with 2 hour threshold
            assertThat(info.isStale(java.time.Duration.ofHours(2))).isFalse();
        }
    }

    @Nested
    @DisplayName("Exactly-Once Semantics (P3-03: Idempotency with consumer groups)")
    class ExactlyOnceSemanticsTests {

        private static final String CONSUMER_GROUP = "idempotency-consumer-group";

        @Test
        @DisplayName("Idempotent checkpoint storage with idempotency key")
        void idempotentCheckpointStorage() {
            DataCloudClient client = DataCloud.forTesting();

            // Store same checkpoint multiple times with same idempotency key
            for (int i = 0; i < 5; i++) {
                DataCloudClient.Checkpoint result = runPromise(() -> 
                    client.commitCheckpoint(TEST_TENANT, TEST_STREAM, CONSUMER_GROUP, 100L, "same-idempotency-key"));
                assertThat(result.offset()).isEqualTo(100L);
            }

            // Checkpoint should still be correct
            Optional<DataCloudClient.Checkpoint> checkpoint = runPromise(() -> 
                client.readCheckpoint(TEST_TENANT, TEST_STREAM, CONSUMER_GROUP));
            assertThat(checkpoint.get().offset()).isEqualTo(100L);
        }

        @Test
        @DisplayName("Tenant isolation for checkpoints")
        void tenantIsolationForCheckpoints() {
            DataCloudClient client = DataCloud.forTesting();
            String otherTenant = "other-tenant";

            // Store checkpoints for different tenants
            runPromise(() -> client.commitCheckpoint(TEST_TENANT, TEST_STREAM, CONSUMER_GROUP, 100L, "idempotency-key-1"));
            runPromise(() -> client.commitCheckpoint(otherTenant, TEST_STREAM, CONSUMER_GROUP, 200L, "idempotency-key-2"));

            // Verify isolation
            Optional<DataCloudClient.Checkpoint> checkpoint1 = runPromise(() -> 
                client.readCheckpoint(TEST_TENANT, TEST_STREAM, CONSUMER_GROUP));
            Optional<DataCloudClient.Checkpoint> checkpoint2 = runPromise(() -> 
                client.readCheckpoint(otherTenant, TEST_STREAM, CONSUMER_GROUP));

            assertThat(checkpoint1.get().offset()).isEqualTo(100L);
            assertThat(checkpoint2.get().offset()).isEqualTo(200L);
        }
    }
}
