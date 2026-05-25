/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud;

import com.ghatana.aep.event.spi.EventCloudCheckpoint;
import com.ghatana.aep.event.spi.EventCloudOffset;
import com.ghatana.aep.event.spi.EventCloudRecord;
import com.ghatana.aep.event.spi.EventCloudSubscription;
import com.ghatana.aep.event.spi.EventCloudWatermark;
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
    void tailsAppendedRecordsWithinTenantPartition() {
        DataCloudEventCloudStore store = store();
        List<EventCloudRecord> tailed = new ArrayList<>();

        EventCloudSubscription subscription = runPromise(() -> store.tail(
            "tenant-a",
            "partition-a",
            new EventCloudOffset("tenant-a", "partition-a", 0L),
            tailed::add));
        runPromise(() -> store.append(event("event-1", "deploy.started", "partition-a")));
        runPromise(() -> store.append(event("event-2", "deploy.started", "partition-b")));
        runPromise(() -> store.append(event("event-3", "deploy.completed", "partition-a")));

        assertThat(subscription.isCancelled()).isFalse();
        assertThat(tailed).extracting(record -> record.event().eventId())
            .containsExactly("event-1", "event-3");

        subscription.cancel();
        assertThat(subscription.isCancelled()).isTrue();
    }

    @Test
    void keepsTenantOffsetsAndReplayIsolated() {
        DataCloudEventCloudStore store = store();
        EventCloudOffset tenantAFirst = runPromise(() -> store.append(event(
            "event-a-1",
            "deploy.started",
            "tenant-a",
            "partition-a",
            "2026-05-23T00:00:00Z")));
        EventCloudOffset tenantBFirst = runPromise(() -> store.append(event(
            "event-b-1",
            "deploy.started",
            "tenant-b",
            "partition-a",
            "2026-05-23T00:00:00Z")));
        EventCloudOffset tenantASecond = runPromise(() -> store.append(event(
            "event-a-2",
            "deploy.completed",
            "tenant-a",
            "partition-a",
            "2026-05-23T00:01:00Z")));
        List<EventCloudRecord> replayed = new ArrayList<>();

        runPromise(() -> store.replay("tenant-a", tenantAFirst, tenantASecond, replayed::add));

        assertThat(tenantAFirst.offset()).isEqualTo(1L);
        assertThat(tenantBFirst.offset()).isEqualTo(1L);
        assertThat(tenantASecond.offset()).isEqualTo(2L);
        assertThat(replayed).extracting(record -> record.event().eventId())
            .containsExactly("event-a-1", "event-a-2");
    }

    @Test
    void persistsCheckpointAndWatermarkProgressAcrossRestart() {
        InMemoryEventLogStoreProvider provider = new InMemoryEventLogStoreProvider();
        DataCloudEventCloudStore store = store(provider);
        EventCloudOffset offset = runPromise(() -> store.append(event("event-1", "metric.error", "partition-a")));
        EventCloudCheckpoint checkpoint = new EventCloudCheckpoint(
            "tenant-a",
            "consumer-1",
            offset,
            Instant.parse("2026-05-23T00:05:00Z"),
            Map.of("mode", "live"));

        runPromise(() -> store.checkpoints().save(checkpoint));
        EventCloudWatermark watermark = runPromise(() -> store.watermark("tenant-a", "partition-a"));

        DataCloudEventCloudStore restartedStore = store(provider);
        assertThat(runPromise(() -> restartedStore.checkpoints().load("tenant-a", "consumer-1")))
            .contains(checkpoint);
        assertThat(watermark.tenantId()).isEqualTo("tenant-a");
        assertThat(watermark.partition()).isEqualTo("partition-a");
        assertThat(watermark.offset().offset()).isGreaterThanOrEqualTo(offset.offset());
    }

    @Test
    void storesLateEventsAndDlqRecordsAsAppendOnlyDataCloudEvents() {
        DataCloudEventCloudStore store = store();
        EventCloudOffset current = runPromise(() -> store.append(event(
            "event-current",
            "metric.current",
            "tenant-a",
            "partition-a",
            "2026-05-23T00:10:00Z")));
        EventCloudOffset late = runPromise(() -> store.append(event(
            "event-late",
            "metric.late",
            "tenant-a",
            "partition-a",
            "2026-05-23T00:01:00Z")));
        EventCloudOffset dlq = runPromise(() -> store.append(event(
            "event-dlq",
            "aep.dlq.recorded",
            "tenant-a",
            "partition-a",
            "2026-05-23T00:11:00Z")));
        List<EventCloudRecord> replayed = new ArrayList<>();

        runPromise(() -> store.replay("tenant-a", current, dlq, replayed::add));

        assertThat(late.offset()).isGreaterThan(current.offset());
        assertThat(replayed).extracting(record -> record.event().eventId())
            .containsExactly("event-current", "event-late", "event-dlq");
        assertThat(replayed).extracting(record -> record.event().eventTime())
            .containsExactly(
                Instant.parse("2026-05-23T00:10:00Z"),
                Instant.parse("2026-05-23T00:01:00Z"),
                Instant.parse("2026-05-23T00:11:00Z"));
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
        return event(eventId, eventType, "tenant-a", partition, "2026-05-23T00:00:00Z");
    }

    private static CanonicalEvent event(
            String eventId,
            String eventType,
            String tenantId,
            String partition,
            String eventTime) {
        return new CanonicalEvent(
            eventId,
            tenantId,
            eventType,
            "1.0.0",
            Instant.parse(eventTime),
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
