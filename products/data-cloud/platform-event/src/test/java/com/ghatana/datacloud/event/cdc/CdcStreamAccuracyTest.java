/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * Stream accuracy tests for Change Data Capture (CDC) event processing. // GH-90000
 *
 * <p>Validates that the CDC stream delivers events with correct ordering,
 * no duplication, no data loss, and offset-accurate resumption across
 * simulated source mutations (INSERT, UPDATE, DELETE). // GH-90000
 *
 * @doc.type    class
 * @doc.purpose CDC stream accuracy: ordering, completeness, deduplication, offset resumption
 * @doc.layer   product
 * @doc.pattern IntegrationTest
 */
@DisplayName("CdcStreamAccuracyTest [GH-90000]")
@Tag("cdc [GH-90000]")
class CdcStreamAccuracyTest {

    private CdcStream stream;

    @BeforeEach
    void setUp() { // GH-90000
        stream = new CdcStream(); // GH-90000
    }

    // ── Event ordering ────────────────────────────────────────────────────────

    @Test
    @DisplayName("events are delivered in offset order [GH-90000]")
    void eventsAreDeliveredInOffsetOrder() { // GH-90000
        stream.publish(CdcEvent.insert("table-a", 3L, Map.of("id", "3"))); // GH-90000
        stream.publish(CdcEvent.insert("table-a", 1L, Map.of("id", "1"))); // GH-90000
        stream.publish(CdcEvent.insert("table-a", 2L, Map.of("id", "2"))); // GH-90000

        List<CdcEvent> consumed = stream.consume("table-a", 0L); // GH-90000
        assertThat(consumed) // GH-90000
                .extracting(CdcEvent::offset) // GH-90000
                .containsExactly(1L, 2L, 3L); // GH-90000
    }

    @Test
    @DisplayName("all INSERT events are captured [GH-90000]")
    void allInsertEventsCaptured() { // GH-90000
        int count = 50;
        for (int i = 1; i <= count; i++) { // GH-90000
            stream.publish(CdcEvent.insert("orders", (long) i, Map.of("id", String.valueOf(i)))); // GH-90000
        }
        assertThat(stream.consume("orders", 0L)).hasSize(count); // GH-90000
    }

    // ── Event type coverage ───────────────────────────────────────────────────

    @Test
    @DisplayName("INSERT event has correct operation type [GH-90000]")
    void insertEventType() { // GH-90000
        stream.publish(CdcEvent.insert("users", 1L, Map.of("id", "u1"))); // GH-90000
        CdcEvent event = stream.consume("users", 0L).get(0); // GH-90000
        assertThat(event.operation()).isEqualTo(CdcOperation.INSERT); // GH-90000
    }

    @Test
    @DisplayName("UPDATE event carries both before and after payload [GH-90000]")
    void updateEventCarriesBeforeAndAfter() { // GH-90000
        Map<String, String> before = Map.of("status", "PENDING"); // GH-90000
        Map<String, String> after = Map.of("status", "ACTIVE"); // GH-90000
        stream.publish(CdcEvent.update("orders", 5L, before, after)); // GH-90000
        CdcEvent event = stream.consume("orders", 0L).get(0); // GH-90000
        assertThat(event.operation()).isEqualTo(CdcOperation.UPDATE); // GH-90000
        assertThat(event.before()).isEqualTo(before); // GH-90000
        assertThat(event.after()).isEqualTo(after); // GH-90000
    }

    @Test
    @DisplayName("DELETE event captures the deleted row as before payload [GH-90000]")
    void deleteEventCapturesDeletedRow() { // GH-90000
        Map<String, String> row = Map.of("id", "u7", "name", "Alice"); // GH-90000
        stream.publish(CdcEvent.delete("users", 9L, row)); // GH-90000
        CdcEvent event = stream.consume("users", 0L).get(0); // GH-90000
        assertThat(event.operation()).isEqualTo(CdcOperation.DELETE); // GH-90000
        assertThat(event.before()).isEqualTo(row); // GH-90000
        assertThat(event.after()).isNull(); // GH-90000
    }

    // ── Deduplication ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("duplicate offset from same table is deduplicated [GH-90000]")
    void duplicateOffsetIsIgnored() { // GH-90000
        stream.publish(CdcEvent.insert("products", 10L, Map.of("id", "p1"))); // GH-90000
        stream.publish(CdcEvent.insert("products", 10L, Map.of("id", "p1-dup"))); // GH-90000
        assertThat(stream.consume("products", 0L)).hasSize(1); // GH-90000
    }

    // ── Offset-based resumption ───────────────────────────────────────────────

    @Test
    @DisplayName("consuming from a checkpoint skips already-processed events [GH-90000]")
    void consumeFromCheckpointSkipsProcessedEvents() { // GH-90000
        for (int i = 1; i <= 10; i++) { // GH-90000
            stream.publish(CdcEvent.insert("logs", (long) i, Map.of("seq", String.valueOf(i)))); // GH-90000
        }
        List<CdcEvent> page = stream.consume("logs", 5L);  // skip offsets 1–5 // GH-90000
        assertThat(page) // GH-90000
                .extracting(CdcEvent::offset) // GH-90000
                .allSatisfy(offset -> assertThat(offset).isGreaterThan(5L)); // GH-90000
        assertThat(page).hasSize(5); // GH-90000
    }

    @Test
    @DisplayName("consuming from offset 0 delivers all events [GH-90000]")
    void consumeFromZeroDeliversAll() { // GH-90000
        IntStream.rangeClosed(1, 20).forEach(i -> // GH-90000
                stream.publish(CdcEvent.insert("metrics", (long) i, Map.of("val", String.valueOf(i))))); // GH-90000
        assertThat(stream.consume("metrics", 0L)).hasSize(20); // GH-90000
    }

    // ── Table isolation ───────────────────────────────────────────────────────

    @Test
    @DisplayName("events are partitioned by table — different tables do not share streams [GH-90000]")
    void eventsArePartitionedByTable() { // GH-90000
        stream.publish(CdcEvent.insert("table-alpha", 1L, Map.of("k", "v"))); // GH-90000
        stream.publish(CdcEvent.insert("table-beta", 1L, Map.of("k", "v"))); // GH-90000
        assertThat(stream.consume("table-alpha", 0L)).hasSize(1); // GH-90000
        assertThat(stream.consume("table-beta", 0L)).hasSize(1); // GH-90000
    }

    @Test
    @DisplayName("consuming from unknown table returns empty list [GH-90000]")
    void unknownTableReturnsEmpty() { // GH-90000
        assertThat(stream.consume("nonexistent-table", 0L)).isEmpty(); // GH-90000
    }

    // ── Large stream ──────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(ints = {100, 500, 1000}) // GH-90000
    @DisplayName("stream handles bulk event injection accurately [GH-90000]")
    void bulkEventInjection(int count) { // GH-90000
        for (int i = 1; i <= count; i++) { // GH-90000
            stream.publish(CdcEvent.insert("bulk", (long) i, Map.of("i", String.valueOf(i)))); // GH-90000
        }
        List<CdcEvent> events = stream.consume("bulk", 0L); // GH-90000
        assertThat(events).hasSize(count); // GH-90000
        // Verify offsets are monotonically increasing
        for (int i = 1; i < events.size(); i++) { // GH-90000
            assertThat(events.get(i).offset()).isGreaterThan(events.get(i - 1).offset()); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner implementation
    // ─────────────────────────────────────────────────────────────────────────

    enum CdcOperation { INSERT, UPDATE, DELETE }

    record CdcEvent( // GH-90000
            String table,
            long offset,
            CdcOperation operation,
            Map<String, String> before,
            Map<String, String> after,
            Instant eventTime
    ) {
        static CdcEvent insert(String table, long offset, Map<String, String> row) { // GH-90000
            return new CdcEvent(table, offset, CdcOperation.INSERT, null, row, Instant.now()); // GH-90000
        }

        static CdcEvent update(String table, long offset, // GH-90000
                               Map<String, String> before, Map<String, String> after) {
            return new CdcEvent(table, offset, CdcOperation.UPDATE, before, after, Instant.now()); // GH-90000
        }

        static CdcEvent delete(String table, long offset, Map<String, String> row) { // GH-90000
            return new CdcEvent(table, offset, CdcOperation.DELETE, row, null, Instant.now()); // GH-90000
        }
    }

    static class CdcStream {
        private final Map<String, TreeMap<Long, CdcEvent>> events = new HashMap<>(); // GH-90000

        void publish(CdcEvent event) { // GH-90000
            events.computeIfAbsent(event.table(), k -> new TreeMap<>()) // GH-90000
                    .putIfAbsent(event.offset(), event);  // putIfAbsent handles deduplication // GH-90000
        }

        List<CdcEvent> consume(String table, long afterOffset) { // GH-90000
            TreeMap<Long, CdcEvent> tableEvents = events.get(table); // GH-90000
            if (tableEvents == null) return List.of(); // GH-90000
            return tableEvents.tailMap(afterOffset + 1) // GH-90000
                    .values() // GH-90000
                    .stream() // GH-90000
                    .toList(); // GH-90000
        }
    }
}
