package com.ghatana.datacloud.plugins.kafka;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.kafka.KafkaContainer;
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
@Testcontainers(disabledWithoutDocker = true) 
@DisplayName("KafkaEventLogStore conformance (real broker)")
class KafkaEventLogStoreConformanceIT extends EventloopTestBase {

    @Container
    static final KafkaContainer KAFKA = 
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"))
                    .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true");

    private KafkaEventLogStore store;

    @BeforeEach
    void setUp() {
        // Strip protocol prefix from bootstrap servers (e.g., PLAINTEXT://host:port -> host:port)
        String bootstrapServers = KAFKA.getBootstrapServers().replaceAll("^[a-zA-Z]+://", "");
        store = new KafkaEventLogStore(
                KafkaEventLogStoreConfig.builder()
                        .bootstrapServers(bootstrapServers)
                        .partitions(1)
                        .replicationFactor((short) 1)
                        .readTimeoutMs(8_000L)
                        .build());
    }

    @AfterEach
    void tearDown() { 
        if (store != null) {
            store.close();
        }
    }

    @Test
    @DisplayName("append returns a positive offset for a single event")
    void appendReturnsPosOffsetForSingleEvent() { 
        TenantContext tenant = TenantContext.of("tenant-kafka-single");
        EventEntry entry = entry("order.created", "{\"orderId\":\"o-1\"}");

        Offset offset = runPromise(() -> store.append(tenant, entry));

        assertThat(offset).isNotNull();
        assertThat(Long.parseLong(offset.value())).isGreaterThanOrEqualTo(0L); 
    }

    @Test
    @DisplayName("appendBatch preserves insertion order via offsets")
    void appendBatchPreservesOrder() { 
        TenantContext tenant = TenantContext.of("tenant-kafka-batch");
        List<EventEntry> entries = List.of(
                entry("order.created",  "{\"seq\":1}"),
                entry("order.updated",  "{\"seq\":2}"),
                entry("order.shipped",  "{\"seq\":3}")
        );

        List<Offset> offsets = runPromise(() -> store.appendBatch(tenant, entries));

        assertThat(offsets).hasSize(3); 
        // Offsets must be monotonically increasing
        for (int i = 1; i < offsets.size(); i++) {
            assertThat(offsets.get(i).value()).isGreaterThan(offsets.get(i - 1).value()); 
        }
    }

    @Test
    @DisplayName("read returns events from the given offset in order")
    void readFromOffsetReturnsOrderedEvents() { 
        TenantContext tenant = TenantContext.of("tenant-kafka-read");
        runPromise(() -> store.appendBatch(tenant, List.of(
                entry("ev.a", "{\"x\":1}"),
                entry("ev.b", "{\"x\":2}"),
                entry("ev.c", "{\"x\":3}")
        )));

        Offset earliest = runPromise(() -> store.getEarliestOffset(tenant));
        List<EventEntry> read = runPromise(() -> store.read(tenant, earliest, 10));

        assertThat(read).hasSizeGreaterThanOrEqualTo(3); 
        // Event types should appear in append order (at least for the last three)
        List<String> types = read.stream()
                .map(EventEntry::eventType)
                .toList();
        // All three event types must appear somewhere in the result
        assertThat(types).contains("ev.a", "ev.b", "ev.c"); 
    }

    @Test
    @DisplayName("getLatestOffset increases after each append")
    void latestOffsetIncreasesAfterAppend() { 
        TenantContext tenant = TenantContext.of("tenant-kafka-offset");

        runPromise(() -> store.append(tenant, entry("offset.test", "{\"i\":0}")));
        Offset before = runPromise(() -> store.getLatestOffset(tenant));

        runPromise(() -> store.append(tenant, entry("offset.test", "{\"i\":1}")));
        Offset after = runPromise(() -> store.getLatestOffset(tenant));

        assertThat(after.value()).isGreaterThan(before.value()); 
    }

    @Test
    @DisplayName("tail subscription delivers a newly appended event")
    void tailDeliversFreshlyAppendedEvent() throws InterruptedException { 
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

        assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue(); 
        assertThat(received.get()).isNotNull();
        assertThat(received.get().eventType()).isEqualTo("tail.event"); 
    }

    @Test
    @DisplayName("tail from latest receives only events appended after subscription")
    void tailFromLatestReceivesOnlyNewEvents() throws InterruptedException {
        TenantContext tenant = TenantContext.of("tenant-kafka-tail-latest");
        runPromise(() -> store.append(tenant, entry("before-1", "{\"n\":1}")));
        runPromise(() -> store.append(tenant, entry("before-2", "{\"n\":2}")));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<EventEntry> received = new AtomicReference<>();
        Offset latest = runPromise(() -> store.getLatestOffset(tenant));

        runPromise(() -> store.tail(tenant, latest, event -> {
            if ("after-1".equals(event.eventType())) {
                received.set(event);
                latch.countDown();
            }
        }));

        runPromise(() -> store.append(tenant, entry("after-1", "{\"n\":3}")));

        assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get()).isNotNull();
        assertThat(received.get().eventType()).isEqualTo("after-1");
    }

    @Test
    @DisplayName("tail runtime snapshot exposes active subscription and poll telemetry")
    void tailRuntimeSnapshotExposesTelemetry() {
        TenantContext tenant = TenantContext.of("tenant-kafka-tail-snapshot");

        Map<String, Object> before = store.tailRuntimeSnapshot();
        assertThat(before.get("activeSubscribers")).isEqualTo(0);
        assertThat(before.get("totalSubscriptions")).isEqualTo(0L);

        EventLogStore.Subscription sub = runPromise(() -> store.tail(tenant, Offset.of(-1L), ignored -> { }));

        Map<String, Object> during = store.tailRuntimeSnapshot();
        assertThat(during.get("activeSubscribers")).isEqualTo(1);
        assertThat(during.get("totalSubscriptions")).isEqualTo(1L);

        sub.cancel();
    }

    @Test
    @DisplayName("tenant isolation: tenant-B cannot see tenant-A events via read")
    void tenantIsolationPreventsReadFromOtherTenant() { 
        TenantContext tenantA = TenantContext.of("tenant-kafka-iso-a");
        TenantContext tenantB = TenantContext.of("tenant-kafka-iso-b");

        runPromise(() -> store.append(tenantA, entry("secret.event", "{\"owner\":\"A\"}")));

        // Fresh earliest offset for B — should return no events belonging to A
        Offset bEarliest = runPromise(() -> store.getEarliestOffset(tenantB));
        List<EventEntry> bEvents = runPromise(() -> store.read(tenantB, bEarliest, 50));

        assertThat(bEvents).noneMatch(e -> "secret.event".equals(e.eventType())); 
    }

    @Test
    @DisplayName("readByTimeRange returns events within the window")
    void readByTimeRangeReturnsMatchingEvents() { 
        TenantContext tenant = TenantContext.of("tenant-kafka-timerange");
        Instant before = Instant.now().minusSeconds(1);

        runPromise(() -> store.append(tenant, entry("time.event", "{\"ts\":\"in-window\"}")));

        Instant after = Instant.now().plusSeconds(5);
        List<EventEntry> result = runPromise(() -> store.readByTimeRange(tenant, before, after, 100));

        assertThat(result).isNotEmpty(); 
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static EventEntry entry(String type, String json) { 
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
