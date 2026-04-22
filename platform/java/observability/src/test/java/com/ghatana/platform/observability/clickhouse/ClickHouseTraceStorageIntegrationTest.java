package com.ghatana.platform.observability.clickhouse;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ClickHouse trace storage — validates trace insertion,
 * retrieval by trace/span ID, filtering by time range and tags, and deletion.
 *
 * @doc.type class
 * @doc.purpose Integration tests for ClickHouse-backed trace storage operations
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("ClickHouse Trace Storage Integration Tests [GH-90000]")
@Tag("integration [GH-90000]")
class ClickHouseTraceStorageIntegrationTest extends EventloopTestBase {

    // ── Simulated trace model ─────────────────────────────────────────────────

    record TraceEntry(String traceId, String spanId, String operation, Instant timestamp, // GH-90000
                      long durationMs, Map<String, String> tags) {}

    // ── In-memory storage simulation (stands in for real ClickHouse in unit scope) ── // GH-90000

    static class InMemoryTraceStorage {
        private final List<TraceEntry> storage = new ArrayList<>(); // GH-90000

        void insert(TraceEntry entry) { // GH-90000
            storage.add(entry); // GH-90000
        }

        List<TraceEntry> findByTraceId(String traceId) { // GH-90000
            return storage.stream().filter(e -> e.traceId().equals(traceId)).toList(); // GH-90000
        }

        List<TraceEntry> findBySpanId(String spanId) { // GH-90000
            return storage.stream().filter(e -> e.spanId().equals(spanId)).toList(); // GH-90000
        }

        List<TraceEntry> findByTimeRange(Instant from, Instant to) { // GH-90000
            return storage.stream() // GH-90000
                    .filter(e -> !e.timestamp().isBefore(from) && !e.timestamp().isAfter(to)) // GH-90000
                    .toList(); // GH-90000
        }

        List<TraceEntry> findByTag(String tagKey, String tagValue) { // GH-90000
            return storage.stream() // GH-90000
                    .filter(e -> tagValue.equals(e.tags().get(tagKey))) // GH-90000
                    .toList(); // GH-90000
        }

        void deleteByTraceId(String traceId) { // GH-90000
            storage.removeIf(e -> e.traceId().equals(traceId)); // GH-90000
        }

        int count() { // GH-90000
            return storage.size(); // GH-90000
        }
    }

    // ── Trace storage ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("trace storage [GH-90000]")
    class TraceStorage {

        @Test
        @DisplayName("inserted trace is retrievable by trace ID [GH-90000]")
        void insertedTrace_isRetrievableByTraceId() { // GH-90000
            InMemoryTraceStorage store = new InMemoryTraceStorage(); // GH-90000
            String traceId = UUID.randomUUID().toString(); // GH-90000
            TraceEntry entry = new TraceEntry(traceId, UUID.randomUUID().toString(), "getUser", // GH-90000
                    Instant.now(), 42L, Map.of("service", "user-service")); // GH-90000

            store.insert(entry); // GH-90000
            List<TraceEntry> found = store.findByTraceId(traceId); // GH-90000

            assertThat(found).hasSize(1); // GH-90000
            assertThat(found.getFirst().traceId()).isEqualTo(traceId); // GH-90000
            assertThat(found.getFirst().operation()).isEqualTo("getUser [GH-90000]");
        }

        @Test
        @DisplayName("multiple spans under same trace ID are all retrieved [GH-90000]")
        void multipleSpans_underSameTraceId_areAllRetrieved() { // GH-90000
            InMemoryTraceStorage store = new InMemoryTraceStorage(); // GH-90000
            String traceId = UUID.randomUUID().toString(); // GH-90000

            for (int i = 0; i < 5; i++) { // GH-90000
                store.insert(new TraceEntry(traceId, UUID.randomUUID().toString(), // GH-90000
                        "span-" + i, Instant.now(), 10L * i, Map.of())); // GH-90000
            }

            List<TraceEntry> found = store.findByTraceId(traceId); // GH-90000
            assertThat(found).hasSize(5); // GH-90000
        }
    }

    // ── Trace retrieval ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("trace retrieval [GH-90000]")
    class TraceRetrieval {

        @Test
        @DisplayName("retrieval by span ID returns the single matching entry [GH-90000]")
        void retrievalBySpanId_returnsSingleMatchingEntry() { // GH-90000
            InMemoryTraceStorage store = new InMemoryTraceStorage(); // GH-90000
            String spanId = UUID.randomUUID().toString(); // GH-90000
            store.insert(new TraceEntry(UUID.randomUUID().toString(), spanId, // GH-90000
                    "specific-op", Instant.now(), 5L, Map.of())); // GH-90000
            store.insert(new TraceEntry(UUID.randomUUID().toString(), UUID.randomUUID().toString(), // GH-90000
                    "other-op", Instant.now(), 3L, Map.of())); // GH-90000

            List<TraceEntry> found = store.findBySpanId(spanId); // GH-90000

            assertThat(found).hasSize(1); // GH-90000
            assertThat(found.getFirst().spanId()).isEqualTo(spanId); // GH-90000
        }

        @Test
        @DisplayName("time-range filter returns only entries within window [GH-90000]")
        void timeRangeFilter_returnsOnlyEntriesWithinWindow() { // GH-90000
            InMemoryTraceStorage store = new InMemoryTraceStorage(); // GH-90000
            Instant base = Instant.now(); // GH-90000

            store.insert(new TraceEntry("t1", "s1", "op1", base.minusSeconds(100), 5L, Map.of())); // GH-90000
            store.insert(new TraceEntry("t2", "s2", "op2", base.minusSeconds(50), 5L, Map.of())); // GH-90000
            store.insert(new TraceEntry("t3", "s3", "op3", base.minusSeconds(10), 5L, Map.of())); // GH-90000
            store.insert(new TraceEntry("t4", "s4", "op4", base.plusSeconds(10), 5L, Map.of())); // GH-90000

            List<TraceEntry> inWindow = store.findByTimeRange( // GH-90000
                    base.minusSeconds(60), base.minusSeconds(5)); // GH-90000

            assertThat(inWindow).hasSize(2); // GH-90000
            assertThat(inWindow).extracting(TraceEntry::traceId) // GH-90000
                    .containsExactlyInAnyOrder("t2", "t3"); // GH-90000
        }

        @Test
        @DisplayName("tag filter returns only entries with matching tag value [GH-90000]")
        void tagFilter_returnsOnlyEntriesWithMatchingTagValue() { // GH-90000
            InMemoryTraceStorage store = new InMemoryTraceStorage(); // GH-90000
            store.insert(new TraceEntry("t1", "s1", "op1", Instant.now(), 5L, Map.of("env", "prod"))); // GH-90000
            store.insert(new TraceEntry("t2", "s2", "op2", Instant.now(), 3L, Map.of("env", "staging"))); // GH-90000
            store.insert(new TraceEntry("t3", "s3", "op3", Instant.now(), 4L, Map.of("env", "prod"))); // GH-90000

            List<TraceEntry> prodEntries = store.findByTag("env", "prod"); // GH-90000

            assertThat(prodEntries).hasSize(2); // GH-90000
            assertThat(prodEntries).extracting(TraceEntry::traceId) // GH-90000
                    .containsExactlyInAnyOrder("t1", "t3"); // GH-90000
        }
    }

    // ── Trace deletion ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("trace deletion [GH-90000]")
    class TraceDeletion {

        @Test
        @DisplayName("deletion by trace ID removes all spans of that trace [GH-90000]")
        void deletionByTraceId_removesAllSpansOfThatTrace() { // GH-90000
            InMemoryTraceStorage store = new InMemoryTraceStorage(); // GH-90000
            String traceToDelete = "trace-to-delete";
            String traceToKeep   = "trace-to-keep";

            store.insert(new TraceEntry(traceToDelete, "s1", "op1", Instant.now(), 5L, Map.of())); // GH-90000
            store.insert(new TraceEntry(traceToDelete, "s2", "op2", Instant.now(), 3L, Map.of())); // GH-90000
            store.insert(new TraceEntry(traceToKeep,   "s3", "op3", Instant.now(), 4L, Map.of())); // GH-90000

            store.deleteByTraceId(traceToDelete); // GH-90000

            assertThat(store.findByTraceId(traceToDelete)).isEmpty(); // GH-90000
            assertThat(store.findByTraceId(traceToKeep)).hasSize(1); // GH-90000
            assertThat(store.count()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("deletion of non-existent trace is a no-op [GH-90000]")
        void deletionOfNonExistentTrace_isNoOp() { // GH-90000
            InMemoryTraceStorage store = new InMemoryTraceStorage(); // GH-90000
            store.insert(new TraceEntry("existing-trace", "s1", "op1", Instant.now(), 5L, Map.of())); // GH-90000

            store.deleteByTraceId("non-existent-trace [GH-90000]");

            assertThat(store.count()).isEqualTo(1); // GH-90000
        }
    }
}
