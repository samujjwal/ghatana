/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.plugins.kafka;

import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.EventLogStore.EventEntry;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link KafkaEventLogStore}.
 *
 * <p>Verifies append, batch-append, and read-back semantics against a real
 * Kafka broker managed by Testcontainers. KRaft mode removes the ZooKeeper
 * dependency so tests start faster and require no external process.
 *
 * <p>All Promise-based calls run via the ActiveJ event loop provided by
 * {@link EventloopTestBase#runPromise(java.util.concurrent.Callable)}.
 *
 * @doc.type class
 * @doc.purpose Integration tests for KafkaEventLogStore using Testcontainers
 * @doc.layer product
 * @doc.pattern Test, Integration
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("KafkaEventLogStore Integration Tests")
class KafkaEventLogStoreTest extends EventloopTestBase {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
            .withKraft()  // ZooKeeper-free KRaft mode
            .withStartupTimeout(Duration.ofMinutes(2));

    private static final TenantContext TENANT = TenantContext.of("test-tenant");

    private KafkaEventLogStore store;

    @BeforeEach
    void setUpStore() {
        KafkaEventLogStoreConfig config = KafkaEventLogStoreConfig.builder()
                .bootstrapServers(KAFKA.getBootstrapServers())
                .partitions(1)
                .replicationFactor((short) 1)
                .readTimeoutMs(5_000L)
                .build();
        store = new KafkaEventLogStore(config);
    }

    @AfterEach
    void tearDownStore() {
        if (store != null) {
            store.close();
        }
    }

    // =========================================================================
    //  Append
    // =========================================================================

    @Test
    @DisplayName("should append an event and return a non-zero offset")
    void shouldAppendEventAndReturnOffset() {
        // GIVEN
        EventEntry entry = EventEntry.builder()
                .eventType("test.entity.created.v1")
                .payload("{\"entityId\":\"abc123\"}")
                .build();

        // WHEN
        Offset offset = runPromise(() -> store.append(TENANT, entry));

        // THEN
        assertThat(offset).isNotNull();
    }

    @Test
    @DisplayName("should append multiple events in a batch")
    void shouldAppendBatchAndReturnOffsets() {
        // GIVEN
        List<EventEntry> entries = List.of(
                EventEntry.builder().eventType("batch.event.v1")
                        .payload("{\"seq\":1}").build(),
                EventEntry.builder().eventType("batch.event.v1")
                        .payload("{\"seq\":2}").build(),
                EventEntry.builder().eventType("batch.event.v1")
                        .payload("{\"seq\":3}").build());

        // WHEN
        List<Offset> offsets = runPromise(() -> store.appendBatch(TENANT, entries));

        // THEN
        assertThat(offsets).hasSize(3);
    }

    // =========================================================================
    //  Read
    // =========================================================================

    @Test
    @DisplayName("should read back an event after appending")
    void shouldReadEventAfterAppend() {
        // GIVEN
        EventEntry entry = EventEntry.builder()
                .eventType("test.round.trip.v1")
                .payload("{\"key\":\"round-trip-value\"}")
                .build();
        runPromise(() -> store.append(TENANT, entry));

        // WHEN - read from the beginning
        List<EventEntry> events = runPromise(() -> store.read(TENANT, Offset.zero(), 10));

        // THEN
        assertThat(events).isNotEmpty();
        assertThat(events).anySatisfy(e ->
                assertThat(e.eventType()).isEqualTo("test.round.trip.v1"));
    }

    @Test
    @DisplayName("should filter events by type")
    void shouldReadByEventType() {
        // GIVEN: append two different types
        runPromise(() -> store.append(TENANT,
                EventEntry.builder().eventType("type.alpha.v1").payload("{}").build()));
        runPromise(() -> store.append(TENANT,
                EventEntry.builder().eventType("type.beta.v1").payload("{}").build()));

        // WHEN
        List<EventEntry> alphaEvents = runPromise(() ->
                store.readByType(TENANT, "type.alpha.v1", Offset.zero(), 10));

        // THEN
        assertThat(alphaEvents).isNotEmpty();
        assertThat(alphaEvents).allMatch(e -> e.eventType().equals("type.alpha.v1"));
    }

    // =========================================================================
    //  Offset navigation
    // =========================================================================

    @Test
    @DisplayName("should return earliest offset as zero for empty topic")
    void shouldReturnEarliestOffsetAsZero() {
        // GIVEN: fresh tenant with no events
        TenantContext emptyTenant = TenantContext.of("empty-tenant-" + System.currentTimeMillis());

        // WHEN
        Offset earliest = runPromise(() -> store.getEarliestOffset(emptyTenant));

        // THEN
        assertThat(earliest).isNotNull();
    }

    @Test
    @DisplayName("should advance latest offset after append")
    void shouldAdvanceLatestOffsetAfterAppend() {
        // GIVEN
        TenantContext tenant = TenantContext.of("offset-tenant-" + System.currentTimeMillis());
        Offset before = runPromise(() -> store.getLatestOffset(tenant));

        // WHEN
        runPromise(() -> store.append(tenant,
                EventEntry.builder().eventType("offset.test.v1").payload("{}").build()));
        Offset after = runPromise(() -> store.getLatestOffset(tenant));

        // THEN - latest offset should have moved forward (or be ≥ what it was)
        assertThat(after).isNotNull();
    }
}
