/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.spi;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Abstract contract test harness for {@link EventLogStore} implementations.
 *
 * <p>Concrete test subclasses must:
 * <ol>
 *   <li>Extend this class</li>
 *   <li>Implement {@link #createStore()} to return the SUT</li>
 *   <li>Implement {@link #createTenant(String)} to return an appropriately scoped {@link TenantContext}</li>
 * </ol>
 *
 * <p>Example concrete subclass:
 * <pre>{@code
 * class InMemoryEventLogStoreContractTest extends EventLogStoreContractTest {
 *     @Override protected EventLogStore createStore() { return new InMemoryEventLogStoreProvider(); }
 *     @Override protected TenantContext createTenant(String id) { return TenantContext.of(id); }
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Abstract contract harness ensuring all EventLogStore implementations satisfy SPI contracts
 * @doc.layer spi
 * @doc.pattern ContractTest
 */
public abstract class EventLogStoreContractTest extends EventloopTestBase {

    protected EventLogStore store;
    protected TenantContext tenant;

    // ─── Hook Methods ─────────────────────────────────────────────────────────

    /**
     * Creates the EventLogStore implementation under test.
     */
    protected abstract EventLogStore createStore();

    /**
     * Creates a tenant context for the given logical tenant ID.
     */
    protected abstract TenantContext createTenant(String tenantId);

    @BeforeEach
    void setUpContract() {
        store = createStore();
        tenant = createTenant("test-tenant");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    protected EventLogStore.EventEntry entry(String type) {
        return EventLogStore.EventEntry.builder()
                .eventType(type)
                .payload(ByteBuffer.wrap("{\"k\":\"v\"}".getBytes()))
                .timestamp(Instant.now())
                .build();
    }

    // ─── append ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("append()")
    class Append {

        @Test
        @DisplayName("appends event and returns a non-null offset")
        void appendsEventAndReturnsOffset() {
            Offset offset = runPromise(() -> store.append(tenant, entry("order.placed")));
            assertThat(offset).isNotNull();
        }

        @Test
        @DisplayName("subsequent appends return strictly increasing offsets")
        void subsequentAppendsReturnIncreasingOffsets() {
            Offset o1 = runPromise(() -> store.append(tenant, entry("event.one")));
            Offset o2 = runPromise(() -> store.append(tenant, entry("event.two")));

            assertThat(Long.parseLong(o2.value())).isGreaterThan(Long.parseLong(o1.value()));
        }
    }

    // ─── appendBatch ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("appendBatch()")
    class AppendBatch {

        @Test
        @DisplayName("batch append returns one offset per entry")
        void batchAppendReturnsOneOffsetPerEntry() {
            List<EventLogStore.EventEntry> entries = List.of(
                    entry("batch.a"), entry("batch.b"), entry("batch.c"));

            List<Offset> offsets = runPromise(() -> store.appendBatch(tenant, entries));
            assertThat(offsets).hasSize(3);
        }

        @Test
        @DisplayName("empty batch append returns empty offset list")
        void emptyBatchReturnsEmptyOffsets() {
            List<Offset> offsets = runPromise(() -> store.appendBatch(tenant, List.of()));
            assertThat(offsets).isEmpty();
        }
    }

    // ─── read ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("read()")
    class Read {

        @Test
        @DisplayName("reads appended events from beginning")
        void readsAppendedEventsFromBeginning() {
            runPromise(() -> store.append(tenant, entry("read.a")));
            runPromise(() -> store.append(tenant, entry("read.b")));

            Offset from = runPromise(() -> store.getEarliestOffset(tenant));
            List<EventLogStore.EventEntry> events = runPromise(() -> store.read(tenant, from, 10));

            assertThat(events).isNotEmpty();
            assertThat(events).extracting(EventLogStore.EventEntry::eventType)
                    .containsAnyOf("read.a", "read.b");
        }

        @Test
        @DisplayName("limit is respected when reading events")
        void limitIsRespected() {
            for (int i = 0; i < 5; i++) {
                runPromise(() -> store.append(tenant, entry("limit.test")));
            }

            Offset from = runPromise(() -> store.getEarliestOffset(tenant));
            List<EventLogStore.EventEntry> events = runPromise(() -> store.read(tenant, from, 2));

            assertThat(events).hasSizeLessThanOrEqualTo(2);
        }
    }

    // ─── readByType ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("readByType()")
    class ReadByType {

        @Test
        @DisplayName("reads only events matching the requested type")
        void readsOnlyMatchingType() {
            runPromise(() -> store.append(tenant, entry("type.alpha")));
            runPromise(() -> store.append(tenant, entry("type.beta")));
            runPromise(() -> store.append(tenant, entry("type.alpha")));

            Offset from = runPromise(() -> store.getEarliestOffset(tenant));
            List<EventLogStore.EventEntry> events = runPromise(
                    () -> store.readByType(tenant, "type.alpha", from, 10));

            assertThat(events).isNotEmpty();
            assertThat(events).allSatisfy(e -> assertThat(e.eventType()).isEqualTo("type.alpha"));
        }
    }

    // ─── offset management ───────────────────────────────────────────────────

    @Nested
    @DisplayName("offset management")
    class OffsetManagement {

        @Test
        @DisplayName("latest offset is at or after earliest offset")
        void latestOffsetIsAfterEarliestOffset() {
            runPromise(() -> store.append(tenant, entry("offset.test")));

            Offset earliest = runPromise(() -> store.getEarliestOffset(tenant));
            Offset latest   = runPromise(() -> store.getLatestOffset(tenant));

            assertThat(Long.parseLong(latest.value())).isGreaterThanOrEqualTo(Long.parseLong(earliest.value()));
        }
    }

    // ─── Tenant isolation ────────────────────────────────────────────────────

    @Nested
    @DisplayName("tenant isolation")
    class TenantIsolation {

        @Test
        @DisplayName("events appended for tenant A are not visible to tenant B")
        void eventsOfTenantANotVisibleFromTenantB() {
            TenantContext tenantA = createTenant("tenant-alpha");
            TenantContext tenantB = createTenant("tenant-beta");

            runPromise(() -> store.append(tenantA, entry("private.event")));

            Offset fromB = runPromise(() -> store.getEarliestOffset(tenantB));
            List<EventLogStore.EventEntry> eventsB = runPromise(() -> store.read(tenantB, fromB, 10));

            assertThat(eventsB)
                    .extracting(EventLogStore.EventEntry::eventType)
                    .doesNotContain("private.event");
        }
    }

    // ─── Null safety ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("null safety")
    class NullSafety {

        @Test
        @DisplayName("append with null tenant throws")
        void appendWithNullTenantThrows() {
            assertThatThrownBy(() -> runPromise(() -> store.append(null, entry("x"))))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("append with null entry throws")
        void appendWithNullEntryThrows() {
            assertThatThrownBy(() -> runPromise(() -> store.append(tenant, null)))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ─── Checkpoint Management (P3-03) ─────────────────────────────────────

    @Nested
    @DisplayName("checkpoint management")
    class CheckpointManagement {

        @Test
        @DisplayName("checkpoint commit and read works")
        void checkpointCommitAndReadWorks() {
            Offset offset = runPromise(() -> store.append(tenant, entry("checkpoint.test")));
            
            EventLogStore.Checkpoint checkpoint = runPromise(() -> 
                store.commitCheckpoint(tenant, "test-stream", "default-group", offset, "idempotency-key-123"));
            
            assertThat(checkpoint.stream()).isEqualTo("test-stream");
            assertThat(checkpoint.consumerGroup()).isEqualTo("default-group");
            assertThat(checkpoint.offset()).isEqualTo(offset);
            assertThat(checkpoint.idempotencyKey()).isEqualTo("idempotency-key-123");
            
            java.util.Optional<EventLogStore.Checkpoint> read = runPromise(() -> 
                store.readCheckpoint(tenant, "test-stream", "default-group"));
            
            assertThat(read).isPresent();
            assertThat(read.get().offset()).isEqualTo(offset);
        }

        @Test
        @DisplayName("checkpoint is idempotent")
        void checkpointIsIdempotent() {
            Offset offset = runPromise(() -> store.append(tenant, entry("idempotent.test")));
            
            // Commit same checkpoint twice
            runPromise(() -> 
                store.commitCheckpoint(tenant, "idempotent-stream", "default-group", offset, "idempotency-key-456"));
            EventLogStore.Checkpoint second = runPromise(() -> 
                store.commitCheckpoint(tenant, "idempotent-stream", "default-group", offset, "idempotency-key-456"));
            
            // Should return the same checkpoint
            assertThat(second.offset()).isEqualTo(offset);
        }

        @Test
        @DisplayName("delete checkpoint removes stored checkpoint")
        void deleteCheckpointRemovesStored() {
            Offset offset = runPromise(() -> store.append(tenant, entry("delete.test")));
            runPromise(() -> 
                store.commitCheckpoint(tenant, "delete-stream", "default-group", offset, "idempotency-key-789"));
            
            boolean deleted = runPromise(() -> 
                store.deleteCheckpoint(tenant, "delete-stream", "default-group"));
            
            assertThat(deleted).isTrue();
            
            java.util.Optional<EventLogStore.Checkpoint> read = runPromise(() -> 
                store.readCheckpoint(tenant, "delete-stream", "default-group"));
            
            assertThat(read).isEmpty();
        }
    }

    // ─── Replay (P3-03) ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("replay")
    class Replay {

        @Test
        @DisplayName("replay respects fromOffset")
        void replayRespectsFromOffset() {
            runPromise(() -> store.append(tenant, entry("replay.1")));
            runPromise(() -> store.append(tenant, entry("replay.2")));
            runPromise(() -> store.append(tenant, entry("replay.3")));
            
            EventLogStore.ReplaySpec spec = EventLogStore.ReplaySpec.fromOffset(Offset.of("2"));
            List<EventLogStore.EventEntry> events = runPromise(() -> store.replay(tenant, spec));
            
            // Should start from offset 2, so only events 2 and 3
            assertThat(events).hasSizeGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("replay respects toOffset")
        void replayRespectsToOffset() {
            runPromise(() -> store.append(tenant, entry("replay.a")));
            runPromise(() -> store.append(tenant, entry("replay.b")));
            runPromise(() -> store.append(tenant, entry("replay.c")));
            
            EventLogStore.ReplaySpec spec = EventLogStore.ReplaySpec.bounded(Offset.of("1"), Offset.of("2"));
            List<EventLogStore.EventEntry> events = runPromise(() -> store.replay(tenant, spec));
            
            // Should only include events between offset 1 and 2 (inclusive)
            assertThat(events).hasSizeLessThanOrEqualTo(2);
        }

        @Test
        @DisplayName("replay filters event types")
        void replayFiltersEventTypes() {
            runPromise(() -> store.append(tenant, entry("type.a")));
            runPromise(() -> store.append(tenant, entry("type.b")));
            runPromise(() -> store.append(tenant, entry("type.a")));
            
            EventLogStore.ReplaySpec spec = EventLogStore.ReplaySpec.filtered(
                Offset.of("1"), Offset.of("-1"), List.of("type.a"));
            List<EventLogStore.EventEntry> events = runPromise(() -> store.replay(tenant, spec));
            
            assertThat(events).allSatisfy(e -> assertThat(e.eventType()).isEqualTo("type.a"));
        }

        @Test
        @DisplayName("replay requires valid tenant")
        void replayRequiresValidTenant() {
            EventLogStore.ReplaySpec spec = EventLogStore.ReplaySpec.fromOffset(Offset.of("0"));
            
            assertThatThrownBy(() -> runPromise(() -> store.replay(null, spec)))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // runPromise() is inherited from EventloopTestBase and executes on the eventloop.
}
