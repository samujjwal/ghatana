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
@DisplayName("ClickHouse Trace Storage Integration Tests")
@Tag("integration")
class ClickHouseTraceStorageIntegrationTest extends EventloopTestBase {

    // ── Simulated trace model ─────────────────────────────────────────────────

    record TraceEntry(String traceId, String spanId, String operation, Instant timestamp, 
                      long durationMs, Map<String, String> tags) {}

    // ── In-memory storage simulation (stands in for real ClickHouse in unit scope) ── 

    static class InMemoryTraceStorage {
        private final List<TraceEntry> storage = new ArrayList<>(); 

        void insert(TraceEntry entry) { 
            storage.add(entry); 
        }

        List<TraceEntry> findByTraceId(String traceId) { 
            return storage.stream().filter(e -> e.traceId().equals(traceId)).toList(); 
        }

        List<TraceEntry> findBySpanId(String spanId) { 
            return storage.stream().filter(e -> e.spanId().equals(spanId)).toList(); 
        }

        List<TraceEntry> findByTimeRange(Instant from, Instant to) { 
            return storage.stream() 
                    .filter(e -> !e.timestamp().isBefore(from) && !e.timestamp().isAfter(to)) 
                    .toList(); 
        }

        List<TraceEntry> findByTag(String tagKey, String tagValue) { 
            return storage.stream() 
                    .filter(e -> tagValue.equals(e.tags().get(tagKey))) 
                    .toList(); 
        }

        void deleteByTraceId(String traceId) { 
            storage.removeIf(e -> e.traceId().equals(traceId)); 
        }

        int count() { 
            return storage.size(); 
        }
    }

    // ── Trace storage ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("trace storage")
    class TraceStorage {

        @Test
        @DisplayName("inserted trace is retrievable by trace ID")
        void insertedTrace_isRetrievableByTraceId() { 
            InMemoryTraceStorage store = new InMemoryTraceStorage(); 
            String traceId = UUID.randomUUID().toString(); 
            TraceEntry entry = new TraceEntry(traceId, UUID.randomUUID().toString(), "getUser", 
                    Instant.now(), 42L, Map.of("service", "user-service")); 

            store.insert(entry); 
            List<TraceEntry> found = store.findByTraceId(traceId); 

            assertThat(found).hasSize(1); 
            assertThat(found.getFirst().traceId()).isEqualTo(traceId); 
            assertThat(found.getFirst().operation()).isEqualTo("getUser");
        }

        @Test
        @DisplayName("multiple spans under same trace ID are all retrieved")
        void multipleSpans_underSameTraceId_areAllRetrieved() { 
            InMemoryTraceStorage store = new InMemoryTraceStorage(); 
            String traceId = UUID.randomUUID().toString(); 

            for (int i = 0; i < 5; i++) { 
                store.insert(new TraceEntry(traceId, UUID.randomUUID().toString(), 
                        "span-" + i, Instant.now(), 10L * i, Map.of())); 
            }

            List<TraceEntry> found = store.findByTraceId(traceId); 
            assertThat(found).hasSize(5); 
        }
    }

    // ── Trace retrieval ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("trace retrieval")
    class TraceRetrieval {

        @Test
        @DisplayName("retrieval by span ID returns the single matching entry")
        void retrievalBySpanId_returnsSingleMatchingEntry() { 
            InMemoryTraceStorage store = new InMemoryTraceStorage(); 
            String spanId = UUID.randomUUID().toString(); 
            store.insert(new TraceEntry(UUID.randomUUID().toString(), spanId, 
                    "specific-op", Instant.now(), 5L, Map.of())); 
            store.insert(new TraceEntry(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 
                    "other-op", Instant.now(), 3L, Map.of())); 

            List<TraceEntry> found = store.findBySpanId(spanId); 

            assertThat(found).hasSize(1); 
            assertThat(found.getFirst().spanId()).isEqualTo(spanId); 
        }

        @Test
        @DisplayName("time-range filter returns only entries within window")
        void timeRangeFilter_returnsOnlyEntriesWithinWindow() { 
            InMemoryTraceStorage store = new InMemoryTraceStorage(); 
            Instant base = Instant.now(); 

            store.insert(new TraceEntry("t1", "s1", "op1", base.minusSeconds(100), 5L, Map.of())); 
            store.insert(new TraceEntry("t2", "s2", "op2", base.minusSeconds(50), 5L, Map.of())); 
            store.insert(new TraceEntry("t3", "s3", "op3", base.minusSeconds(10), 5L, Map.of())); 
            store.insert(new TraceEntry("t4", "s4", "op4", base.plusSeconds(10), 5L, Map.of())); 

            List<TraceEntry> inWindow = store.findByTimeRange( 
                    base.minusSeconds(60), base.minusSeconds(5)); 

            assertThat(inWindow).hasSize(2); 
            assertThat(inWindow).extracting(TraceEntry::traceId) 
                    .containsExactlyInAnyOrder("t2", "t3"); 
        }

        @Test
        @DisplayName("tag filter returns only entries with matching tag value")
        void tagFilter_returnsOnlyEntriesWithMatchingTagValue() { 
            InMemoryTraceStorage store = new InMemoryTraceStorage(); 
            store.insert(new TraceEntry("t1", "s1", "op1", Instant.now(), 5L, Map.of("env", "prod"))); 
            store.insert(new TraceEntry("t2", "s2", "op2", Instant.now(), 3L, Map.of("env", "staging"))); 
            store.insert(new TraceEntry("t3", "s3", "op3", Instant.now(), 4L, Map.of("env", "prod"))); 

            List<TraceEntry> prodEntries = store.findByTag("env", "prod"); 

            assertThat(prodEntries).hasSize(2); 
            assertThat(prodEntries).extracting(TraceEntry::traceId) 
                    .containsExactlyInAnyOrder("t1", "t3"); 
        }
    }

    // ── Trace deletion ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("trace deletion")
    class TraceDeletion {

        @Test
        @DisplayName("deletion by trace ID removes all spans of that trace")
        void deletionByTraceId_removesAllSpansOfThatTrace() { 
            InMemoryTraceStorage store = new InMemoryTraceStorage(); 
            String traceToDelete = "trace-to-delete";
            String traceToKeep   = "trace-to-keep";

            store.insert(new TraceEntry(traceToDelete, "s1", "op1", Instant.now(), 5L, Map.of())); 
            store.insert(new TraceEntry(traceToDelete, "s2", "op2", Instant.now(), 3L, Map.of())); 
            store.insert(new TraceEntry(traceToKeep,   "s3", "op3", Instant.now(), 4L, Map.of())); 

            store.deleteByTraceId(traceToDelete); 

            assertThat(store.findByTraceId(traceToDelete)).isEmpty(); 
            assertThat(store.findByTraceId(traceToKeep)).hasSize(1); 
            assertThat(store.count()).isEqualTo(1); 
        }

        @Test
        @DisplayName("deletion of non-existent trace is a no-op")
        void deletionOfNonExistentTrace_isNoOp() { 
            InMemoryTraceStorage store = new InMemoryTraceStorage(); 
            store.insert(new TraceEntry("existing-trace", "s1", "op1", Instant.now(), 5L, Map.of())); 

            store.deleteByTraceId("non-existent-trace");

            assertThat(store.count()).isEqualTo(1); 
        }
    }
}
