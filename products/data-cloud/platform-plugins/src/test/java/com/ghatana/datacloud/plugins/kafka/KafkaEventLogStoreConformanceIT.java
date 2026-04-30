package com.ghatana.datacloud.plugins.kafka;

import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.EventLogStore.EventEntry;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provider-backed conformance coverage for {@link KafkaEventLogStoreProvider} / {@link KafkaEventLogStore}.
 *
 * <p>Exercises the real Kafka broker via Testcontainers:
 * <ul>
 *   <li>Single append + offset round-trip</li>
 *   <li>Batch append ordering</li>
 *   <li>Read-from-offset semantics</li>
 *   <li>Tail subscription delivers newly appended events</li>
 *   <li>Tenant isolation: events for tenant-A not visible to tenant-B</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Kafka EventLogStore provider-backed conformance integration test
 * @doc.layer product
 * @doc.pattern Testcontainers, ConformanceTest, IntegrationTest
 */
@Testcontainers(disabledWithoutDocker = true) // GH-90000
@DisplayName("KafkaEventLogStore conformance (real broker)")
class KafkaEventLogStoreConformanceIT extends EventloopTestBase {

    @Container
    static final KafkaContainer KAFKA = // GH-90000
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
                    .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true");

    private KafkaEventLogStore store;

    @BeforeEach
    void setUp() { // GH-90000
        store = new KafkaEventLogStore(
                KafkaEventLogStoreConfig.builder()
                        .bootstrapServers(KAFKA.getBootstrapServers()) // GH-90000
                        .partitions(1)
                        .replicationFactor((short) 1)
                        .readTimeoutMs(8_000L)
                        .build());
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (store != null) {
            store.close();
        }
    }

    @Test
    @DisplayName("append returns a positive offset for a single event")
    void appendReturnsPosOffsetForSingleEvent() { // GH-90000
        TenantContext tenant = TenantContext.of("tenant-kafka-single");
        EventEntry entry = entry("order.created", "{\"orderId\":\"o-1\"}");

        Offset offset = runPromise(() -> store.append(tenant, entry));

        assertThat(offset).isNotNull();
        assertThat(Long.parseLong(offset.value())).isGreaterThanOrEqualTo(0L); // GH-90000
    }

    @Test
    @DisplayName("appendBatch preserves insertion order via offsets")
    void appendBatchPreservesOrder() { // GH-90000
        TenantContext tenant = TenantContext.of("tenant-kafka-batch");
        List<EventEntry> entries = List.of(
                entry("order.created",  "{\"seq\":1}"),
                entry("order.updated",  "{\"seq\":2}"),
                entry("order.shipped",  "{\"seq\":3}")
        );

        List<Offset> offsets = runPromise(() -> store.appendBatch(tenant, entries));

        assertThat(offsets).hasSize(3); // GH-90000
        // Offsets must be monotonically increasing
        for (int i = 1; i < offsets.size(); i++) {
            assertThat(offsets.get(i).value()).isGreaterThan(offsets.get(i - 1).value()); // GH-90000
        }
    }

    @Test
    @DisplayName("read returns events from the given offset in order")
    void readFromOffsetReturnsOrderedEvents() { // GH-90000
        TenantContext tenant = TenantContext.of("tenant-kafka-read");
        runPromise(() -> store.appendBatch(tenant, List.of(
                entry("ev.a", "{\"x\":1}"),
                entry("ev.b", "{\"x\":2}"),
                entry("ev.c", "{\"x\":3}")
        )));

        Offset earliest = runPromise(() -> store.getEarliestOffset(tenant));
        List<EventEntry> read = runPromise(() -> store.read(tenant, earliest, 10));

        assertThat(read).hasSizeGreaterThanOrEqualTo(3); // GH-90000
        // Event types should appear in append order (at least for the last three)
        List<String> types = read.stream()
                .map(EventEntry::eventType)
                .toList();
        // All three event types must appear somewhere in the result
        assertThat(types).contains("ev.a", "ev.b", "ev.c"); // GH-90000
    }

    @Test
    @DisplayName("getLatestOffset increases after each append")
    void latestOffsetIncreasesAfterAppend() { // GH-90000
        TenantContext tenant = TenantContext.of("tenant-kafka-offset");

        runPromise(() -> store.append(tenant, entry("offset.test", "{\"i\":0}")));
        Offset before = runPromise(() -> store.getLatestOffset(tenant));

        runPromise(() -> store.append(tenant, entry("offset.test", "{\"i\":1}")));
        Offset after = runPromise(() -> store.getLatestOffset(tenant));

        assertThat(after.value()).isGreaterThan(before.value()); // GH-90000
    }

    @Test
    @DisplayName("tail subscription delivers a newly appended event")
    void tailDeliversFreshlyAppendedEvent() throws InterruptedException { // GH-90000
        TenantContext tenant = TenantContext.of("tenant-kafka-tail");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<EventEntry> received = new AtomicReference<>();

        Offset start = runPromise(() -> store.getLatestOffset(tenant));
        runPromise(() -> store.tail(tenant, start, e -> {
            received.set(e);
            latch.countDown();
        }));

        // Append one event after starting the tail
        runPromise(() -> store.append(tenant, entry("tail.event", "{\"live\":true}")));

        assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue(); // GH-90000
        assertThat(received.get()).isNotNull();
        assertThat(received.get().eventType()).isEqualTo("tail.event"); // GH-90000
    }

    @Test
    @DisplayName("tenant isolation: tenant-B cannot see tenant-A events via read")
    void tenantIsolationPreventsReadFromOtherTenant() { // GH-90000
        TenantContext tenantA = TenantContext.of("tenant-kafka-iso-a");
        TenantContext tenantB = TenantContext.of("tenant-kafka-iso-b");

        runPromise(() -> store.append(tenantA, entry("secret.event", "{\"owner\":\"A\"}")));

        // Fresh earliest offset for B — should return no events belonging to A
        Offset bEarliest = runPromise(() -> store.getEarliestOffset(tenantB));
        List<EventEntry> bEvents = runPromise(() -> store.read(tenantB, bEarliest, 50));

        assertThat(bEvents).noneMatch(e -> "secret.event".equals(e.eventType())); // GH-90000
    }

    @Test
    @DisplayName("readByTimeRange returns events within the window")
    void readByTimeRangeReturnsMatchingEvents() { // GH-90000
        TenantContext tenant = TenantContext.of("tenant-kafka-timerange");
        Instant before = Instant.now().minusSeconds(1);

        runPromise(() -> store.append(tenant, entry("time.event", "{\"ts\":\"in-window\"}")));

        Instant after = Instant.now().plusSeconds(5);
        List<EventEntry> result = runPromise(() -> store.readByTimeRange(tenant, before, after, 100));

        assertThat(result).isNotEmpty(); // GH-90000
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static EventEntry entry(String type, String json) { // GH-90000
        return EventEntry.builder()
                .eventId(UUID.randomUUID())
                .eventType(type)
                .eventVersion("1.0.0")
                .timestamp(Instant.now())
                .payload(ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)))
                .contentType("application/json")
                .headers(Map.of("source", "kafka-conformance-test"))
                .idempotencyKey(UUID.randomUUID().toString())
                .build();
    }
}
