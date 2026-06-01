/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.spi.provider;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        @Test
        void tailFromLatestReceivesOnlyNewEvents() {
            runPromise(() -> store.append(tenant, entry("before-1")));
            runPromise(() -> store.append(tenant, entry("before-2")));

            List<EventEntry> received = new ArrayList<>();
            EventLogStore.Subscription sub = runPromise(() ->
                store.tail(tenant, Offset.of(-1L), received::add));

            assertThat(received).isEmpty();

            runPromise(() -> store.append(tenant, entry("after-1")));
            runPromise(() -> store.append(tenant, entry("after-2")));

            assertThat(received)
                .extracting(EventEntry::eventType)
                .containsExactly("after-1", "after-2");
            sub.cancel();
        }

        @Test
        void tailRuntimeSnapshotExposesSubscriberAndNotificationMetrics() {
            Map<String, Object> before = store.tailRuntimeSnapshot();
            assertThat(before.get("activeSubscribers")).isEqualTo(0);
            assertThat(before.get("totalSubscriptions")).isEqualTo(0L);
            assertThat(before.get("totalNotifications")).isEqualTo(0L);

            EventLogStore.Subscription sub = runPromise(() ->
                store.tail(tenant, Offset.of(-1L), event -> { }));

            Map<String, Object> during = store.tailRuntimeSnapshot();
            assertThat(during.get("activeSubscribers")).isEqualTo(1);
            assertThat(during.get("totalSubscriptions")).isEqualTo(1L);

            runPromise(() -> store.append(tenant, entry("new-event")));

            Map<String, Object> afterEvent = store.tailRuntimeSnapshot();
            assertThat(afterEvent.get("totalNotifications")).isEqualTo(1L);

            sub.cancel();
            Map<String, Object> afterCancel = store.tailRuntimeSnapshot();
            assertThat(afterCancel.get("activeSubscribers")).isEqualTo(0);
        }
    }

    // ─── Checkpoint Management (P3-03) ───────────────────────────────────────

    @Nested
    @DisplayName("checkpoint management")
    class CheckpointManagement {

        @Test
        void readCheckpointReturnsEmptyWhenNotCommitted() {
            var result = runPromise(() -> store.readCheckpoint(tenant, "stream-1", "group-1"));
            assertThat(result).isEmpty();
        }

        @Test
        void commitCheckpointStoresCheckpoint() {
            Offset offset = Offset.of("10");
            var checkpoint = runPromise(() -> 
                store.commitCheckpoint(tenant, "stream-1", "group-1", offset, "idem-key-1"));
            
            assertThat(checkpoint.stream()).isEqualTo("stream-1");
            assertThat(checkpoint.consumerGroup()).isEqualTo("group-1");
            assertThat(checkpoint.offset()).isEqualTo(offset);
            assertThat(checkpoint.idempotencyKey()).isEqualTo("idem-key-1");
        }

        @Test
        void readCheckpointReturnsCommittedCheckpoint() {
            Offset offset = Offset.of("15");
            runPromise(() -> store.commitCheckpoint(tenant, "stream-2", "group-2", offset, "idem-key-2"));
            
            var result = runPromise(() -> store.readCheckpoint(tenant, "stream-2", "group-2"));
            assertThat(result).isPresent();
            assertThat(result.get().offset()).isEqualTo(offset);
        }

        @Test
        void deleteCheckpointRemovesCheckpoint() {
            runPromise(() -> store.commitCheckpoint(tenant, "stream-3", "group-3", Offset.of("20"), "idem-key-3"));
            
            boolean deleted = runPromise(() -> store.deleteCheckpoint(tenant, "stream-3", "group-3"));
            assertThat(deleted).isTrue();
            
            var result = runPromise(() -> store.readCheckpoint(tenant, "stream-3", "group-3"));
            assertThat(result).isEmpty();
        }

        @Test
        void deleteCheckpointReturnsFalseWhenNotExists() {
            boolean deleted = runPromise(() -> store.deleteCheckpoint(tenant, "stream-4", "group-4"));
            assertThat(deleted).isFalse();
        }

        @Test
        void getAllCheckpointsWithMetadataReturnsAllCheckpoints() {
            runPromise(() -> store.commitCheckpoint(tenant, "stream-a", "group-a", Offset.of("1"), "idem-a"));
            runPromise(() -> store.commitCheckpoint(tenant, "stream-b", "group-b", Offset.of("2"), "idem-b"));
            
            var checkpoints = runPromise(() -> store.getAllCheckpointsWithMetadata(tenant));
            assertThat(checkpoints).hasSize(2);
        }

        @Test
        void checkpointsAreTenantIsolated() {
            TenantContext otherTenant = TenantContext.of("tenant-2");
            runPromise(() -> store.commitCheckpoint(tenant, "stream-1", "group-1", Offset.of("10"), "idem-1"));
            
            var otherResult = runPromise(() -> store.readCheckpoint(otherTenant, "stream-1", "group-1"));
            assertThat(otherResult).isEmpty();
        }
    }

    // ─── Replay (P3-03) ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("replay")
    class Replay {

        @Test
        void replayRespectsFromOffset() {
            runPromise(() -> store.append(tenant, entry("E1")));
            runPromise(() -> store.append(tenant, entry("E2")));
            runPromise(() -> store.append(tenant, entry("E3")));
            
            var spec = new com.ghatana.platform.domain.eventstore.EventLogStore.ReplaySpec(
                Offset.of("2"), Offset.of("-1"), List.of());
            var events = runPromise(() -> store.replay(tenant, spec));
            
            assertThat(events).hasSize(2); // E2 and E3
        }

        @Test
        void replayRespectsToOffset() {
            runPromise(() -> store.append(tenant, entry("E1")));
            runPromise(() -> store.append(tenant, entry("E2")));
            runPromise(() -> store.append(tenant, entry("E3")));
            
            var spec = new com.ghatana.platform.domain.eventstore.EventLogStore.ReplaySpec(
                Offset.of("1"), Offset.of("2"), List.of());
            var events = runPromise(() -> store.replay(tenant, spec));
            
            assertThat(events).hasSize(2); // E1 and E2
        }

        @Test
        void replayFiltersByEventType() {
            runPromise(() -> store.append(tenant, entry("TypeA")));
            runPromise(() -> store.append(tenant, entry("TypeB")));
            runPromise(() -> store.append(tenant, entry("TypeA")));
            
            var spec = new com.ghatana.platform.domain.eventstore.EventLogStore.ReplaySpec(
                Offset.of("0"), Offset.of("-1"), List.of("TypeA"));
            var events = runPromise(() -> store.replay(tenant, spec));
            
            assertThat(events).hasSize(2);
            assertThat(events).allMatch(e -> "TypeA".equals(e.eventType()));
        }

        @Test
        void replayWithEmptyEventTypeFilterReturnsAll() {
            runPromise(() -> store.append(tenant, entry("TypeA")));
            runPromise(() -> store.append(tenant, entry("TypeB")));
            
            var spec = new com.ghatana.platform.domain.eventstore.EventLogStore.ReplaySpec(
                Offset.of("0"), Offset.of("-1"), List.of());
            var events = runPromise(() -> store.replay(tenant, spec));
            
            assertThat(events).hasSize(2);
        }

        @Test
        void replayToOffsetNegativeOneReturnsAllEvents() {
            runPromise(() -> store.append(tenant, entry("E1")));
            runPromise(() -> store.append(tenant, entry("E2")));
            
            var spec = new com.ghatana.platform.domain.eventstore.EventLogStore.ReplaySpec(
                Offset.of("0"), Offset.of("-1"), List.of());
            var events = runPromise(() -> store.replay(tenant, spec));
            
            assertThat(events).hasSize(2);
        }
    }

    // ─── Unsubscribe (P3-03) ───────────────────────────────────────────────────

    @Nested
    @DisplayName("unsubscribe")
    class Unsubscribe {

        @Test
        void unsubscribeCompletesSuccessfully() {
            var subscriptionId = new com.ghatana.platform.domain.eventstore.EventLogStore.SubscriptionId("test-sub");
            var result = runPromise(() -> store.unsubscribe(tenant, subscriptionId));
            assertThat(result).isNull(); // Void promise completes with null
        }
    }
}
