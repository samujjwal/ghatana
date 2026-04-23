/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.spi.provider;

import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.EventLogStore.EventEntry;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link InMemoryEventLogStoreProvider}.
 */
@DisplayName("InMemoryEventLogStoreProvider")
class InMemoryEventLogStoreProviderTest extends EventloopTestBase {

    private InMemoryEventLogStoreProvider store;
    private TenantContext tenant;

    @BeforeEach
    void setUp() { // GH-90000
        store = new InMemoryEventLogStoreProvider(); // GH-90000
        tenant = TenantContext.of("tenant-1");
    }

    private EventEntry entry(String type) { // GH-90000
        return EventEntry.builder() // GH-90000
                .eventType(type) // GH-90000
                .payload("{}".getBytes()) // GH-90000
                .timestamp(Instant.now()) // GH-90000
                .build(); // GH-90000
    }

    // ─── Append ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("append")
    class Append {

        @Test
        void firstAppendReturnsOffset1() { // GH-90000
            Offset offset = runPromise(() -> store.append(tenant, entry("OrderCreated")));
            assertThat(offset.value()).isEqualTo("1");
        }

        @Test
        void subsequentAppendsIncrementOffset() { // GH-90000
            runPromise(() -> store.append(tenant, entry("A")));
            Offset second = runPromise(() -> store.append(tenant, entry("B")));
            assertThat(second.value()).isEqualTo("2");
        }

        @Test
        void differentTenantsHaveIndependentOffsets() { // GH-90000
            TenantContext other = TenantContext.of("tenant-2");
            Offset t1 = runPromise(() -> store.append(tenant, entry("X")));
            Offset t2 = runPromise(() -> store.append(other, entry("Y")));
            assertThat(t1.value()).isEqualTo("1");
            assertThat(t2.value()).isEqualTo("1");
        }
    }

    // ─── appendBatch ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("appendBatch")
    class AppendBatch {

        @Test
        void returnsOneOffsetPerEntry() { // GH-90000
            List<EventEntry> entries = List.of(entry("A"), entry("B"), entry("C"));
            List<Offset> offsets = runPromise(() -> store.appendBatch(tenant, entries)); // GH-90000
            assertThat(offsets).hasSize(3); // GH-90000
            assertThat(offsets.get(0).value()).isEqualTo("1");
            assertThat(offsets.get(1).value()).isEqualTo("2");
            assertThat(offsets.get(2).value()).isEqualTo("3");
        }
    }

    // ─── read ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("read")
    class Read {

        @Test
        void readFromBeginning() { // GH-90000
            runPromise(() -> store.append(tenant, entry("A")));
            runPromise(() -> store.append(tenant, entry("B")));

            List<EventEntry> result = runPromise(() -> store.read(tenant, Offset.of(0L), 10)); // GH-90000
            assertThat(result).hasSize(2); // GH-90000
        }

        @Test
        void limitHonoured() { // GH-90000
            for (int i = 0; i < 5; i++) { // GH-90000
                runPromise(() -> store.append(tenant, entry("E")));
            }
            List<EventEntry> result = runPromise(() -> store.read(tenant, Offset.of(0L), 2)); // GH-90000
            assertThat(result).hasSize(2); // GH-90000
        }

        @Test
        void emptyForUnknownTenant() { // GH-90000
            List<EventEntry> result = runPromise(() -> // GH-90000
                    store.read(TenantContext.of("unknown"), Offset.of(0L), 10));
            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        void readFromMidway() { // GH-90000
            runPromise(() -> store.append(tenant, entry("A")));
            runPromise(() -> store.append(tenant, entry("B")));
            runPromise(() -> store.append(tenant, entry("C")));

            // Read starting from offset 2 — should return entries with offset >= 2
            List<EventEntry> result = runPromise(() -> store.read(tenant, Offset.of(2L), 10)); // GH-90000
            assertThat(result).hasSize(2); // offset 2 and 3 // GH-90000
        }

        @Test
        void nonNumericOffsetThrows() { // GH-90000
            runPromise(() -> store.append(tenant, entry("A")));
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> store.read(tenant, Offset.of("bad"), 10)))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("Offset must be numeric");
        }
    }

    // ─── readByTimeRange ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("readByTimeRange")
    class ReadByTimeRange {

        @Test
        void filtersToRange() { // GH-90000
            Instant base = Instant.parse("2026-01-01T00:00:00Z");
            EventEntry before = EventEntry.builder().eventType("Before")
                    .timestamp(base.minusSeconds(10)).payload("{}".getBytes()).build(); // GH-90000
            EventEntry inside = EventEntry.builder().eventType("Inside")
                    .timestamp(base).payload("{}".getBytes()).build(); // GH-90000
            EventEntry after = EventEntry.builder().eventType("After")
                    .timestamp(base.plusSeconds(100)).payload("{}".getBytes()).build(); // GH-90000

            runPromise(() -> store.append(tenant, before)); // GH-90000
            runPromise(() -> store.append(tenant, inside)); // GH-90000
            runPromise(() -> store.append(tenant, after)); // GH-90000

            List<EventEntry> result = runPromise(() -> // GH-90000
                    store.readByTimeRange(tenant, base, base.plusSeconds(50), 10)); // GH-90000
            assertThat(result).hasSize(1); // GH-90000
            assertThat(result.get(0).eventType()).isEqualTo("Inside");
        }
    }

    // ─── readByType ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("readByType")
    class ReadByType {

        @Test
        void returnsOnlyMatchingType() { // GH-90000
            runPromise(() -> store.append(tenant, entry("TypeA")));
            runPromise(() -> store.append(tenant, entry("TypeB")));
            runPromise(() -> store.append(tenant, entry("TypeA")));

            List<EventEntry> result = runPromise(() -> // GH-90000
                    store.readByType(tenant, "TypeA", Offset.of(0L), 10)); // GH-90000
            assertThat(result).hasSize(2) // GH-90000
                    .allMatch(e -> "TypeA".equals(e.eventType())); // GH-90000
        }
    }

    // ─── getLatestOffset / getEarliestOffset ─────────────────────────────────

    @Nested
    @DisplayName("offset queries")
    class OffsetQueries {

        @Test
        void latestOffsetForEmptyTenantIsZero() { // GH-90000
            Offset latest = runPromise(() -> // GH-90000
                    store.getLatestOffset(TenantContext.of("new-tenant")));
            assertThat(latest.value()).isEqualTo("0");
        }

        @Test
        void latestOffsetReflectsAppendsCount() { // GH-90000
            runPromise(() -> store.append(tenant, entry("A")));
            runPromise(() -> store.append(tenant, entry("B")));
            Offset latest = runPromise(() -> store.getLatestOffset(tenant)); // GH-90000
            assertThat(latest.value()).isEqualTo("2");
        }

        @Test
        void earliestOffsetIsAlwaysZero() { // GH-90000
            runPromise(() -> store.append(tenant, entry("A")));
            Offset earliest = runPromise(() -> store.getEarliestOffset(tenant)); // GH-90000
            assertThat(earliest.value()).isEqualTo("0");
        }
    }

    // ─── tail ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("tail")
    class Tail {

        @Test
        void deliversExistingEntries() { // GH-90000
            runPromise(() -> store.append(tenant, entry("E1")));
            runPromise(() -> store.append(tenant, entry("E2")));

            List<EventEntry> received = new ArrayList<>(); // GH-90000
            EventLogStore.Subscription sub = runPromise(() -> // GH-90000
                    store.tail(tenant, Offset.of(0L), received::add)); // GH-90000

            assertThat(received).hasSize(2); // GH-90000
            assertThat(sub.isCancelled()).isFalse(); // GH-90000
        }

        @Test
        void cancellationStopsDelivery() { // GH-90000
            // Tail from offset -1 means "from latest", no existing entries will be delivered
            List<EventEntry> received = new ArrayList<>(); // GH-90000
            EventLogStore.Subscription sub = runPromise(() -> // GH-90000
                    store.tail(tenant, Offset.of(-1L), received::add)); // GH-90000

            assertThat(received).isEmpty(); // GH-90000
            sub.cancel(); // GH-90000
            assertThat(sub.isCancelled()).isTrue(); // GH-90000
        }

        @Test
        void tailFromEndDeliversNothing() { // GH-90000
            runPromise(() -> store.append(tenant, entry("X")));
            runPromise(() -> store.append(tenant, entry("Y")));

            List<EventEntry> received = new ArrayList<>(); // GH-90000
            // offset == size of list => no entries returned
            runPromise(() -> store.tail(tenant, Offset.of(2L), received::add)); // GH-90000
            assertThat(received).isEmpty(); // GH-90000
        }
    }
}
