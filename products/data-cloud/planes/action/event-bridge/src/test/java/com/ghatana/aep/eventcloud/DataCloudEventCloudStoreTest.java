/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud;

import com.ghatana.aep.event.spi.EventCloudCheckpoint;
import com.ghatana.aep.event.spi.EventCloudOffset;
import com.ghatana.aep.event.spi.EventCloudRecord;
import com.ghatana.aep.model.CanonicalEvent;
import com.ghatana.aep.model.PatternPartialMatch;
import com.ghatana.datacloud.spi.EventLogStoreAdapters;
import com.ghatana.datacloud.spi.provider.InMemoryEventLogStoreProvider;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DataCloudEventCloudStoreTest extends EventloopTestBase {

    @Test
    void appendsAndReadsCanonicalEventThroughDataCloudEventLog() {
        DataCloudEventCloudStore store = store();
        CanonicalEvent event = event("event-1", "deploy.started", "partition-a");

        EventCloudOffset offset = runPromise(() -> store.append(event));
        Optional<EventCloudRecord> record = runPromise(() -> store.read(offset));

        assertThat(offset.tenantId()).isEqualTo("tenant-a");
        assertThat(offset.partition()).isEqualTo("partition-a");
        assertThat(offset.offset()).isEqualTo(1L);
        assertThat(record).hasValueSatisfying(value -> {
            assertThat(value.event().eventId()).isEqualTo("event-1");
            assertThat(value.event().eventType()).isEqualTo("deploy.started");
            assertThat(value.event().payload()).containsEntry("service", "checkout");
        });
    }

    @Test
    void replaysOnlyRequestedTenantPartitionAndOffsetRange() {
        DataCloudEventCloudStore store = store();
        EventCloudOffset first = runPromise(() -> store.append(event("event-1", "deploy.started", "partition-a")));
        runPromise(() -> store.append(event("event-2", "ignored.partition", "partition-b")));
        EventCloudOffset third = runPromise(() -> store.append(event("event-3", "service.failed", "partition-a")));
        List<EventCloudRecord> replayed = new ArrayList<>();

        runPromise(() -> store.replay("tenant-a", first, third, replayed::add));

        assertThat(replayed).extracting(record -> record.event().eventId())
            .containsExactly("event-1", "event-3");
    }

    @Test
    void persistsCheckpointsAndPartialMatchesBehindAepSpi() {
        InMemoryEventLogStoreProvider provider = new InMemoryEventLogStoreProvider();
        DataCloudEventCloudStore store = store(provider);
        EventCloudOffset offset = new EventCloudOffset("tenant-a", "partition-a", 42L);
        EventCloudCheckpoint checkpoint = new EventCloudCheckpoint(
            "tenant-a",
            "consumer-1",
            offset,
            Instant.parse("2026-05-23T00:00:00Z"),
            Map.of("mode", "replay"));
        PatternPartialMatch partialMatch = new PatternPartialMatch(
            "partial-1",
            "pattern-1",
            "tenant-a",
            Instant.parse("2026-05-23T00:00:00Z"),
            Instant.parse("2026-05-23T01:00:00Z"),
            List.of(event("event-1", "deploy.started", "partition-a")),
            Map.of("service", "checkout"),
            0.88);

        runPromise(() -> store.checkpoints().save(checkpoint));
        runPromise(() -> store.partialMatches().save(partialMatch));

        assertThat(runPromise(() -> store.checkpoints().load("tenant-a", "consumer-1")))
            .contains(checkpoint);
        assertThat(runPromise(() -> store.partialMatches().loadForPattern("tenant-a", "pattern-1")))
            .containsExactly(partialMatch);

        DataCloudEventCloudStore restartedStore = store(provider);
        assertThat(runPromise(() -> restartedStore.checkpoints().load("tenant-a", "consumer-1")))
            .contains(checkpoint);
        assertThat(runPromise(() -> restartedStore.partialMatches().load("tenant-a", "partial-1")))
            .contains(partialMatch);
    }

    @Test
    void partialMatchDeletePersistsAsStateEvent() {
        InMemoryEventLogStoreProvider provider = new InMemoryEventLogStoreProvider();
        DataCloudEventCloudStore store = store(provider);
        PatternPartialMatch partialMatch = new PatternPartialMatch(
            "partial-1",
            "pattern-1",
            "tenant-a",
            Instant.parse("2026-05-23T00:00:00Z"),
            Instant.parse("2026-05-23T01:00:00Z"),
            List.of(event("event-1", "deploy.started", "partition-a")),
            Map.of("service", "checkout"),
            0.88);

        runPromise(() -> store.partialMatches().save(partialMatch));
        runPromise(() -> store.partialMatches().delete("tenant-a", "partial-1"));

        DataCloudEventCloudStore restartedStore = store(provider);
        assertThat(runPromise(() -> restartedStore.partialMatches().load("tenant-a", "partial-1")))
            .isEmpty();
    }

    private static DataCloudEventCloudStore store() {
        return store(new InMemoryEventLogStoreProvider());
    }

    private static DataCloudEventCloudStore store(InMemoryEventLogStoreProvider provider) {
        return new DataCloudEventCloudStore(
            EventLogStoreAdapters.toPlatformStore(provider));
    }

    private static CanonicalEvent event(String eventId, String eventType, String partition) {
        return new CanonicalEvent(
            eventId,
            "tenant-a",
            eventType,
            "1.0.0",
            Instant.parse("2026-05-23T00:00:00Z"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Map.of("system", "test", "partition", partition),
            List.of("service:checkout"),
            "correlation-1",
            Optional.empty(),
            Map.of("service", "checkout"),
            Map.of("eventDetectionConfidence", 0.99),
            Map.of("source", "unit-test"),
            List.of("internal"),
            eventId + "-idempotency");
    }
}
