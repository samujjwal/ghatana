/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.event.cdc;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Stream accuracy tests for Change Data Capture (CDC) event processing. 
 *
 * <p>Validates that the CDC stream delivers events with correct ordering,
 * no duplication, no data loss, and offset-accurate resumption across
 * simulated source mutations (INSERT, UPDATE, DELETE). 
 *
 * @doc.type    class
 * @doc.purpose CDC stream accuracy: ordering, completeness, deduplication, offset resumption
 * @doc.layer   product
 * @doc.pattern IntegrationTest
 */
@DisplayName("CdcStreamAccuracyTest")
@Tag("cdc")
class CdcStreamAccuracyTest {

    private CdcStream stream;

    @BeforeEach
    void setUp() { 
        stream = new CdcStream(); 
    }

    // ── Event ordering ────────────────────────────────────────────────────────

    @Test
    @DisplayName("events are delivered in offset order")
    void eventsAreDeliveredInOffsetOrder() { 
        stream.publish(CdcEvent.insert("table-a", 3L, Map.of("id", "3"))); 
        stream.publish(CdcEvent.insert("table-a", 1L, Map.of("id", "1"))); 
        stream.publish(CdcEvent.insert("table-a", 2L, Map.of("id", "2"))); 

        List<CdcEvent> consumed = stream.consume("table-a", 0L); 
        assertThat(consumed) 
                .extracting(CdcEvent::offset) 
                .containsExactly(1L, 2L, 3L); 
    }

    @Test
    @DisplayName("all INSERT events are captured")
    void allInsertEventsCaptured() { 
        int count = 50;
        for (int i = 1; i <= count; i++) { 
            stream.publish(CdcEvent.insert("orders", (long) i, Map.of("id", String.valueOf(i)))); 
        }
        assertThat(stream.consume("orders", 0L)).hasSize(count); 
    }

    // ── Event type coverage ───────────────────────────────────────────────────

    @Test
    @DisplayName("INSERT event has correct operation type")
    void insertEventType() { 
        stream.publish(CdcEvent.insert("users", 1L, Map.of("id", "u1"))); 
        CdcEvent event = stream.consume("users", 0L).get(0); 
        assertThat(event.operation()).isEqualTo(CdcOperation.INSERT); 
    }

    @Test
    @DisplayName("UPDATE event carries both before and after payload")
    void updateEventCarriesBeforeAndAfter() { 
        Map<String, String> before = Map.of("status", "PENDING"); 
        Map<String, String> after = Map.of("status", "ACTIVE"); 
        stream.publish(CdcEvent.update("orders", 5L, before, after)); 
        CdcEvent event = stream.consume("orders", 0L).get(0); 
        assertThat(event.operation()).isEqualTo(CdcOperation.UPDATE); 
        assertThat(event.before()).isEqualTo(before); 
        assertThat(event.after()).isEqualTo(after); 
    }

    @Test
    @DisplayName("DELETE event captures the deleted row as before payload")
    void deleteEventCapturesDeletedRow() { 
        Map<String, String> row = Map.of("id", "u7", "name", "Alice"); 
        stream.publish(CdcEvent.delete("users", 9L, row)); 
        CdcEvent event = stream.consume("users", 0L).get(0); 
        assertThat(event.operation()).isEqualTo(CdcOperation.DELETE); 
        assertThat(event.before()).isEqualTo(row); 
        assertThat(event.after()).isNull(); 
    }

    // ── Deduplication ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("duplicate offset from same table is deduplicated")
    void duplicateOffsetIsIgnored() { 
        stream.publish(CdcEvent.insert("products", 10L, Map.of("id", "p1"))); 
        stream.publish(CdcEvent.insert("products", 10L, Map.of("id", "p1-dup"))); 
        assertThat(stream.consume("products", 0L)).hasSize(1); 
    }

    // ── Offset-based resumption ───────────────────────────────────────────────

    @Test
    @DisplayName("consuming from a checkpoint skips already-processed events")
    void consumeFromCheckpointSkipsProcessedEvents() { 
        for (int i = 1; i <= 10; i++) { 
            stream.publish(CdcEvent.insert("logs", (long) i, Map.of("seq", String.valueOf(i)))); 
        }
        List<CdcEvent> page = stream.consume("logs", 5L);  // skip offsets 1–5 
        assertThat(page) 
                .extracting(CdcEvent::offset) 
                .allSatisfy(offset -> assertThat(offset).isGreaterThan(5L)); 
        assertThat(page).hasSize(5); 
    }

    @Test
    @DisplayName("consuming from offset 0 delivers all events")
    void consumeFromZeroDeliversAll() { 
        IntStream.rangeClosed(1, 20).forEach(i -> 
                stream.publish(CdcEvent.insert("metrics", (long) i, Map.of("val", String.valueOf(i))))); 
        assertThat(stream.consume("metrics", 0L)).hasSize(20); 
    }

    // ── Table isolation ───────────────────────────────────────────────────────

    @Test
    @DisplayName("events are partitioned by table — different tables do not share streams")
    void eventsArePartitionedByTable() { 
        stream.publish(CdcEvent.insert("table-alpha", 1L, Map.of("k", "v"))); 
        stream.publish(CdcEvent.insert("table-beta", 1L, Map.of("k", "v"))); 
        assertThat(stream.consume("table-alpha", 0L)).hasSize(1); 
        assertThat(stream.consume("table-beta", 0L)).hasSize(1); 
    }

    @Test
    @DisplayName("consuming from unknown table returns empty list")
    void unknownTableReturnsEmpty() { 
        assertThat(stream.consume("nonexistent-table", 0L)).isEmpty(); 
    }

    // ── Large stream ──────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(ints = {100, 500, 1000}) 
    @DisplayName("stream handles bulk event injection accurately")
    void bulkEventInjection(int count) { 
        for (int i = 1; i <= count; i++) { 
            stream.publish(CdcEvent.insert("bulk", (long) i, Map.of("i", String.valueOf(i)))); 
        }
        List<CdcEvent> events = stream.consume("bulk", 0L); 
        assertThat(events).hasSize(count); 
        // Verify offsets are monotonically increasing
        for (int i = 1; i < events.size(); i++) { 
            assertThat(events.get(i).offset()).isGreaterThan(events.get(i - 1).offset()); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner implementation
    // ─────────────────────────────────────────────────────────────────────────

    enum CdcOperation { INSERT, UPDATE, DELETE }

    record CdcEvent( 
            String table,
            long offset,
            CdcOperation operation,
            Map<String, String> before,
            Map<String, String> after,
            Instant eventTime
    ) {
        static CdcEvent insert(String table, long offset, Map<String, String> row) { 
            return new CdcEvent(table, offset, CdcOperation.INSERT, null, row, Instant.now()); 
        }

        static CdcEvent update(String table, long offset, 
                               Map<String, String> before, Map<String, String> after) {
            return new CdcEvent(table, offset, CdcOperation.UPDATE, before, after, Instant.now()); 
        }

        static CdcEvent delete(String table, long offset, Map<String, String> row) { 
            return new CdcEvent(table, offset, CdcOperation.DELETE, row, null, Instant.now()); 
        }
    }

    static class CdcStream {
        private final Map<String, TreeMap<Long, CdcEvent>> events = new HashMap<>(); 

        void publish(CdcEvent event) { 
            events.computeIfAbsent(event.table(), k -> new TreeMap<>()) 
                    .putIfAbsent(event.offset(), event);  // putIfAbsent handles deduplication 
        }

        List<CdcEvent> consume(String table, long afterOffset) { 
            TreeMap<Long, CdcEvent> tableEvents = events.get(table); 
            if (tableEvents == null) return List.of(); 
            return tableEvents.tailMap(afterOffset + 1) 
                    .values() 
                    .stream() 
                    .toList(); 
        }
    }
}
