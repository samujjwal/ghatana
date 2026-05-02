/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("EventRecord and EventBuffer")
@ExtendWith(MockitoExtension.class) 
class EventRecordAndBufferTest {

    // ─── EventRecord ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("EventRecord")
    class EventRecordTest {

        @Test
        void builderCreatesRecord() { 
            Instant now = Instant.now(); 
            EventRecord record = EventRecord.builder() 
                    .tenantId("tenant-1")
                    .streamName("orders")
                    .partitionId(3) 
                    .eventOffset(42L) 
                    .occurrenceTime(now) 
                    .detectionTime(now) 
                    .correlationId("corr-1")
                    .causationId("cause-1")
                    .build(); 

            assertThat(record.getTenantId()).isEqualTo("tenant-1");
            assertThat(record.getStreamName()).isEqualTo("orders");
            assertThat(record.getPartitionId()).isEqualTo(3); 
            assertThat(record.getEventOffset()).isEqualTo(42L); 
            assertThat(record.getCorrelationId()).isEqualTo("corr-1");
            assertThat(record.getCausationId()).isEqualTo("cause-1");
        }

        @Test
        void getRecordTypeIsEvent() { 
            EventRecord record = EventRecord.builder() 
                    .tenantId("t").streamName("s").eventOffset(1L)
                    .occurrenceTime(Instant.now()).detectionTime(Instant.now()) 
                    .build(); 
            assertThat(record.getRecordType()).isEqualTo(RecordType.EVENT); 
        }

        @Test
        void calculatePartition() { 
            assertThat(EventRecord.calculatePartition("key", 4)).isBetween(0, 3); 
            assertThat(EventRecord.calculatePartition(null, 4)).isEqualTo(0); 
            assertThat(EventRecord.calculatePartition("key", 1)).isEqualTo(0); 
        }

        @Test
        void hasIdempotencyKey() { 
            EventRecord withKey = EventRecord.builder() 
                    .tenantId("t").streamName("s").eventOffset(1L)
                    .occurrenceTime(Instant.now()).detectionTime(Instant.now()) 
                    .idempotencyKey("idem-1")
                    .build(); 
            assertThat(withKey.hasIdempotencyKey()).isTrue(); 

            EventRecord withoutKey = EventRecord.builder() 
                    .tenantId("t").streamName("s").eventOffset(1L)
                    .occurrenceTime(Instant.now()).detectionTime(Instant.now()) 
                    .build(); 
            assertThat(withoutKey.hasIdempotencyKey()).isFalse(); 
        }

        @Test
        void toStringContainsKeyFields() { 
            EventRecord record = EventRecord.builder() 
                    .tenantId("t1").streamName("order-stream").eventOffset(7L)
                    .occurrenceTime(Instant.now()).detectionTime(Instant.now()) 
                    .build(); 
            String str = record.toString(); 
            assertThat(str).contains("t1").contains("order-stream").contains("7");
        }
    }

    // ─── EventBuffer ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("EventBuffer state methods")
    class EventBufferTest {

        @Mock
        EventLogStore mockSpillStore;

        @Test
        void newBufferIsEmpty() { 
            EventBuffer buffer = new EventBuffer(mockSpillStore, "test-buf"); 
            assertThat(buffer.isEmpty()).isTrue(); 
            assertThat(buffer.size()).isZero(); 
        }

        @Test
        void offerAndDrain() { 
            EventBuffer buffer = new EventBuffer(mockSpillStore, "test-buf"); 
            com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry entry =
                    com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry.builder() 
                            .eventType("TestEvent")
                            .payload("{}".getBytes()) 
                            .build(); 
            assertThat(buffer.offer(entry)).isTrue(); 
            assertThat(buffer.size()).isEqualTo(1); 

            java.util.List<com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry> drained = buffer.drain(10); 
            assertThat(drained).hasSize(1); 
            assertThat(buffer.isEmpty()).isTrue(); 
        }

        @Test
        void isBelowLowWaterMarkWhenEmpty() { 
            EventBuffer buffer = new EventBuffer(mockSpillStore, "metrics-buf"); 
            // Default low water mark = 2000, empty buffer (0) is below it 
            assertThat(buffer.isBelowLowWaterMark()).isTrue(); 
        }

        @Test
        void statsMapContainsBufferName() { 
            EventBuffer buffer = new EventBuffer(mockSpillStore, "named-buf"); 
            java.util.Map<String, Object> stats = buffer.stats(); 
            assertThat(stats).containsEntry("bufferName", "named-buf"); 
        }

        @Test
        void customCapacityConstructor() { 
            EventBuffer buffer = new EventBuffer(mockSpillStore, "custom", 100, 80, 20); 
            assertThat(buffer.isEmpty()).isTrue(); 
        }

        @Test
        void invalidCapacityThrows() { 
            assertThatThrownBy(() -> new EventBuffer(mockSpillStore, "bad", 0, 0, 0)) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("capacity");
        }
    }
}
