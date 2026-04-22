/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud;

import com.ghatana.datacloud.event.buffer.EventBuffer;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link EventRecord} POJO logic and {@link EventBuffer} state methods.
 */
@DisplayName("EventRecord and EventBuffer [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class EventRecordAndBufferTest {

    // ─── EventRecord ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("EventRecord [GH-90000]")
    class EventRecordTest {

        @Test
        void builderCreatesRecord() { // GH-90000
            Instant now = Instant.now(); // GH-90000
            EventRecord record = EventRecord.builder() // GH-90000
                    .tenantId("tenant-1 [GH-90000]")
                    .streamName("orders [GH-90000]")
                    .partitionId(3) // GH-90000
                    .eventOffset(42L) // GH-90000
                    .occurrenceTime(now) // GH-90000
                    .detectionTime(now) // GH-90000
                    .correlationId("corr-1 [GH-90000]")
                    .causationId("cause-1 [GH-90000]")
                    .build(); // GH-90000

            assertThat(record.getTenantId()).isEqualTo("tenant-1 [GH-90000]");
            assertThat(record.getStreamName()).isEqualTo("orders [GH-90000]");
            assertThat(record.getPartitionId()).isEqualTo(3); // GH-90000
            assertThat(record.getEventOffset()).isEqualTo(42L); // GH-90000
            assertThat(record.getCorrelationId()).isEqualTo("corr-1 [GH-90000]");
            assertThat(record.getCausationId()).isEqualTo("cause-1 [GH-90000]");
        }

        @Test
        void getRecordTypeIsEvent() { // GH-90000
            EventRecord record = EventRecord.builder() // GH-90000
                    .tenantId("t [GH-90000]").streamName("s [GH-90000]").eventOffset(1L)
                    .occurrenceTime(Instant.now()).detectionTime(Instant.now()) // GH-90000
                    .build(); // GH-90000
            assertThat(record.getRecordType()).isEqualTo(RecordType.EVENT); // GH-90000
        }

        @Test
        void calculatePartition() { // GH-90000
            assertThat(EventRecord.calculatePartition("key", 4)).isBetween(0, 3); // GH-90000
            assertThat(EventRecord.calculatePartition(null, 4)).isEqualTo(0); // GH-90000
            assertThat(EventRecord.calculatePartition("key", 1)).isEqualTo(0); // GH-90000
        }

        @Test
        void hasIdempotencyKey() { // GH-90000
            EventRecord withKey = EventRecord.builder() // GH-90000
                    .tenantId("t [GH-90000]").streamName("s [GH-90000]").eventOffset(1L)
                    .occurrenceTime(Instant.now()).detectionTime(Instant.now()) // GH-90000
                    .idempotencyKey("idem-1 [GH-90000]")
                    .build(); // GH-90000
            assertThat(withKey.hasIdempotencyKey()).isTrue(); // GH-90000

            EventRecord withoutKey = EventRecord.builder() // GH-90000
                    .tenantId("t [GH-90000]").streamName("s [GH-90000]").eventOffset(1L)
                    .occurrenceTime(Instant.now()).detectionTime(Instant.now()) // GH-90000
                    .build(); // GH-90000
            assertThat(withoutKey.hasIdempotencyKey()).isFalse(); // GH-90000
        }

        @Test
        void toStringContainsKeyFields() { // GH-90000
            EventRecord record = EventRecord.builder() // GH-90000
                    .tenantId("t1 [GH-90000]").streamName("order-stream [GH-90000]").eventOffset(7L)
                    .occurrenceTime(Instant.now()).detectionTime(Instant.now()) // GH-90000
                    .build(); // GH-90000
            String str = record.toString(); // GH-90000
            assertThat(str).contains("t1 [GH-90000]").contains("order-stream [GH-90000]").contains("7 [GH-90000]");
        }
    }

    // ─── EventBuffer ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("EventBuffer state methods [GH-90000]")
    class EventBufferTest {

        @Mock
        EventLogStore mockSpillStore;

        @Test
        void newBufferIsEmpty() { // GH-90000
            EventBuffer buffer = new EventBuffer(mockSpillStore, "test-buf"); // GH-90000
            assertThat(buffer.isEmpty()).isTrue(); // GH-90000
            assertThat(buffer.size()).isZero(); // GH-90000
        }

        @Test
        void offerAndDrain() { // GH-90000
            EventBuffer buffer = new EventBuffer(mockSpillStore, "test-buf"); // GH-90000
            com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry entry =
                    com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry.builder() // GH-90000
                            .eventType("TestEvent [GH-90000]")
                            .payload("{}".getBytes()) // GH-90000
                            .build(); // GH-90000
            assertThat(buffer.offer(entry)).isTrue(); // GH-90000
            assertThat(buffer.size()).isEqualTo(1); // GH-90000

            java.util.List<com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry> drained = buffer.drain(10); // GH-90000
            assertThat(drained).hasSize(1); // GH-90000
            assertThat(buffer.isEmpty()).isTrue(); // GH-90000
        }

        @Test
        void isBelowLowWaterMarkWhenEmpty() { // GH-90000
            EventBuffer buffer = new EventBuffer(mockSpillStore, "metrics-buf"); // GH-90000
            // Default low water mark = 2000, empty buffer (0) is below it // GH-90000
            assertThat(buffer.isBelowLowWaterMark()).isTrue(); // GH-90000
        }

        @Test
        void statsMapContainsBufferName() { // GH-90000
            EventBuffer buffer = new EventBuffer(mockSpillStore, "named-buf"); // GH-90000
            java.util.Map<String, Object> stats = buffer.stats(); // GH-90000
            assertThat(stats).containsEntry("bufferName", "named-buf"); // GH-90000
        }

        @Test
        void customCapacityConstructor() { // GH-90000
            EventBuffer buffer = new EventBuffer(mockSpillStore, "custom", 100, 80, 20); // GH-90000
            assertThat(buffer.isEmpty()).isTrue(); // GH-90000
        }

        @Test
        void invalidCapacityThrows() { // GH-90000
            assertThatThrownBy(() -> new EventBuffer(mockSpillStore, "bad", 0, 0, 0)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("capacity [GH-90000]");
        }
    }
}
