/*
 * Copyright (c) 2026 Ghatana Inc. 
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
    void setUp() { 
        store = new InMemoryEventLogStoreProvider(); 
        tenant = TenantContext.of("tenant-1");
    }

    private EventEntry entry(String type) { 
        return EventEntry.builder() 
                .eventType(type) 
                .payload("{}".getBytes()) 
                .timestamp(Instant.now()) 
                .build(); 
    }

    // ─── Append ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("append")
    class Append {

        @Test
        void firstAppendReturnsOffset1() { 
            Offset offset = runPromise(() -> store.append(tenant, entry("OrderCreated")));
            assertThat(offset.value()).isEqualTo("1");
        }

        @Test
        void subsequentAppendsIncrementOffset() { 
            runPromise(() -> store.append(tenant, entry("A")));
            Offset second = runPromise(() -> store.append(tenant, entry("B")));
            assertThat(second.value()).isEqualTo("2");
        }

        @Test
        void differentTenantsHaveIndependentOffsets() { 
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
        void returnsOneOffsetPerEntry() { 
            List<EventEntry> entries = List.of(entry("A"), entry("B"), entry("C"));
            List<Offset> offsets = runPromise(() -> store.appendBatch(tenant, entries)); 
            assertThat(offsets).hasSize(3); 
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
        void readFromBeginning() { 
            runPromise(() -> store.append(tenant, entry("A")));
            runPromise(() -> store.append(tenant, entry("B")));

            List<EventEntry> result = runPromise(() -> store.read(tenant, Offset.of(0L), 10)); 
            assertThat(result).hasSize(2); 
        }

        @Test
        void limitHonoured() { 
            for (int i = 0; i < 5; i++) { 
                runPromise(() -> store.append(tenant, entry("E")));
            }
            List<EventEntry> result = runPromise(() -> store.read(tenant, Offset.of(0L), 2)); 
            assertThat(result).hasSize(2); 
        }

        @Test
        void emptyForUnknownTenant() { 
            List<EventEntry> result = runPromise(() -> 
                    store.read(TenantContext.of("unknown"), Offset.of(0L), 10));
            assertThat(result).isEmpty(); 
        }

        @Test
        void readFromMidway() { 
            runPromise(() -> store.append(tenant, entry("A")));
            runPromise(() -> store.append(tenant, entry("B")));
            runPromise(() -> store.append(tenant, entry("C")));

            // Read starting from offset 2 — should return entries with offset >= 2
            List<EventEntry> result = runPromise(() -> store.read(tenant, Offset.of(2L), 10)); 
            assertThat(result).hasSize(2); // offset 2 and 3 
        }

        @Test
        void nonNumericOffsetThrows() { 
            runPromise(() -> store.append(tenant, entry("A")));
            assertThatThrownBy(() -> 
                    runPromise(() -> store.read(tenant, Offset.of("bad"), 10)))
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("Offset must be numeric");
        }
    }

    // ─── readByTimeRange ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("readByTimeRange")
    class ReadByTimeRange {

        @Test
        void filtersToRange() { 
            Instant base = Instant.parse("2026-01-01T00:00:00Z");
            EventEntry before = EventEntry.builder().eventType("Before")
                    .timestamp(base.minusSeconds(10)).payload("{}".getBytes()).build(); 
            EventEntry inside = EventEntry.builder().eventType("Inside")
                    .timestamp(base).payload("{}".getBytes()).build(); 
            EventEntry after = EventEntry.builder().eventType("After")
                    .timestamp(base.plusSeconds(100)).payload("{}".getBytes()).build(); 

            runPromise(() -> store.append(tenant, before)); 
            runPromise(() -> store.append(tenant, inside)); 
            runPromise(() -> store.append(tenant, after)); 

            List<EventEntry> result = runPromise(() -> 
                    store.readByTimeRange(tenant, base, base.plusSeconds(50), 10)); 
            assertThat(result).hasSize(1); 
            assertThat(result.get(0).eventType()).isEqualTo("Inside");
        }
    }

    // ─── readByType ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("readByType")
    class ReadByType {

        @Test
        void returnsOnlyMatchingType() { 
            runPromise(() -> store.append(tenant, entry("TypeA")));
            runPromise(() -> store.append(tenant, entry("TypeB")));
            runPromise(() -> store.append(tenant, entry("TypeA")));

            List<EventEntry> result = runPromise(() -> 
                    store.readByType(tenant, "TypeA", Offset.of(0L), 10)); 
            assertThat(result).hasSize(2) 
                    .allMatch(e -> "TypeA".equals(e.eventType())); 
        }
    }

    // ─── getLatestOffset / getEarliestOffset ─────────────────────────────────

    @Nested
    @DisplayName("offset queries")
    class OffsetQueries {

        @Test
        void latestOffsetForEmptyTenantIsZero() { 
            Offset latest = runPromise(() -> 
                    store.getLatestOffset(TenantContext.of("new-tenant")));
            assertThat(latest.value()).isEqualTo("0");
        }

        @Test
        void latestOffsetReflectsAppendsCount() { 
            runPromise(() -> store.append(tenant, entry("A")));
            runPromise(() -> store.append(tenant, entry("B")));
            Offset latest = runPromise(() -> store.getLatestOffset(tenant)); 
            assertThat(latest.value()).isEqualTo("2");
        }

        @Test
        void earliestOffsetIsAlwaysZero() { 
            runPromise(() -> store.append(tenant, entry("A")));
            Offset earliest = runPromise(() -> store.getEarliestOffset(tenant)); 
            assertThat(earliest.value()).isEqualTo("0");
        }
    }

    // ─── tail ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("tail")
    class Tail {

        @Test
        void deliversExistingEntries() { 
            runPromise(() -> store.append(tenant, entry("E1")));
            runPromise(() -> store.append(tenant, entry("E2")));

            List<EventEntry> received = new ArrayList<>(); 
            EventLogStore.Subscription sub = runPromise(() -> 
                    store.tail(tenant, Offset.of(0L), received::add)); 

            assertThat(received).hasSize(2); 
            assertThat(sub.isCancelled()).isFalse(); 
        }

        @Test
        void cancellationStopsDelivery() { 
            // Tail from offset -1 means "from latest", no existing entries will be delivered
            List<EventEntry> received = new ArrayList<>(); 
            EventLogStore.Subscription sub = runPromise(() -> 
                    store.tail(tenant, Offset.of(-1L), received::add)); 

            assertThat(received).isEmpty(); 
            sub.cancel(); 
            assertThat(sub.isCancelled()).isTrue(); 
        }

        @Test
        void tailFromEndDeliversNothing() { 
            runPromise(() -> store.append(tenant, entry("X")));
            runPromise(() -> store.append(tenant, entry("Y")));

            List<EventEntry> received = new ArrayList<>(); 
            // offset == size of list => no entries returned
            runPromise(() -> store.tail(tenant, Offset.of(2L), received::add)); 
            assertThat(received).isEmpty(); 
        }
    }
}
