package com.ghatana.datacloud.plugins.performance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.datacloud.plugins.kafka.KafkaEventLogStore;
import com.ghatana.datacloud.plugins.kafka.KafkaEventLogStoreConfig;
import com.ghatana.datacloud.plugins.postgres.PostgresEntityStore;
import com.ghatana.datacloud.plugins.postgres.PostgresEntityStoreConfig;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Exercises real PostgreSQL and Kafka providers under concurrent multi-tenant load.
 * @doc.layer product
 * @doc.pattern Testcontainers, IntegrationTest
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Durable Multi-Tenant Load Integration Test")
@SuppressWarnings({"resource", "deprecation"})
class DurableMultiTenantLoadIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final int TENANT_COUNT = Integer.getInteger("datacloud.load.tenants", 100);
    private static final int ENTITY_OPS_PER_TENANT = Integer.getInteger("datacloud.load.entityOpsPerTenant", 10);
    private static final int EVENT_OPS_PER_TENANT = Integer.getInteger("datacloud.load.eventOpsPerTenant", 10);
    private static final long TIMEOUT_SECONDS = Long.getLong("datacloud.load.timeoutSeconds", 180L);
    private static final long MAX_HEAP_DELTA_MB = Long.getLong("datacloud.load.maxHeapDeltaMb", 256L);
    private static final long MAX_P95_ENTITY_SAVE_MS = Long.getLong("datacloud.load.maxP95EntitySaveMs", 2_500L);
    private static final long MAX_P95_EVENT_APPEND_MS = Long.getLong("datacloud.load.maxP95EventAppendMs", 2_500L);
    private static final long MAX_P95_QUERY_MS = Long.getLong("datacloud.load.maxP95QueryMs", 2_500L);
    private static final int ITERATIONS = Integer.getInteger("datacloud.load.iterations", 1);
    private static final double MIN_THROUGHPUT_OPS_PER_SECOND = Double.parseDouble(
        System.getProperty("datacloud.load.minThroughputOpsPerSecond", "0")
    );
    private static final String METRICS_OUTPUT = System.getProperty("datacloud.load.metricsOutput", "");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("datacloud_load_it")
        .withUsername("dc_load")
        .withPassword("dc_load_secret");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
        .withEmbeddedZookeeper();

    private PostgresEntityStore entityStore;
    private KafkaEventLogStore eventStore;

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("filesystem:" + Path.of(System.getProperty("user.dir"), "products", "data-cloud", "platform-launcher", "src", "main", "resources", "db", "migration"))
            .load()
            .migrate();
    }

    @AfterEach
    void tearDown() {
        if (entityStore != null) {
            entityStore.close();
        }
        if (eventStore != null) {
            eventStore.close();
        }
    }

    @Test
    @DisplayName("mixed CRUD, query, and event append traffic stays tenant-isolated under durable load")
    void mixedCrudQueryAndEventAppendTrafficStaysTenantIsolatedUnderDurableLoad() throws InterruptedException {
        entityStore = new PostgresEntityStore(new PostgresEntityStoreConfig(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword(),
            24,
            2,
            30_000L,
            600_000L,
            1_800_000L
        ));
        eventStore = new KafkaEventLogStore(KafkaEventLogStoreConfig.builder()
            .bootstrapServers(KAFKA.getBootstrapServers())
            .partitions(1)
            .replicationFactor((short) 1)
            .readTimeoutMs(5_000L)
            .build());

        List<Map<String, Object>> iterationSummaries = new ArrayList<>();

        for (int iteration = 1; iteration <= ITERATIONS; iteration++) {
            iterationSummaries.add(runSingleIteration(iteration));
        }

        long minEntityWrites = (long) TENANT_COUNT * ENTITY_OPS_PER_TENANT * ITERATIONS;
        long minEventAppends = (long) TENANT_COUNT * EVENT_OPS_PER_TENANT * ITERATIONS;
        long minQueries = (long) TENANT_COUNT * 2L * ITERATIONS;

        long aggregateEntityWrites = iterationSummaries.stream()
            .mapToLong(summary -> ((Number) summary.get("entityWrites")).longValue())
            .sum();
        long aggregateEventAppends = iterationSummaries.stream()
            .mapToLong(summary -> ((Number) summary.get("eventAppends")).longValue())
            .sum();
        long aggregateQueries = iterationSummaries.stream()
            .mapToLong(summary -> ((Number) summary.get("queries")).longValue())
            .sum();
        double bestThroughput = iterationSummaries.stream()
            .mapToDouble(summary -> ((Number) summary.get("throughputOpsPerSecond")).doubleValue())
            .max()
            .orElse(0.0d);

        assertThat(aggregateEntityWrites).isGreaterThanOrEqualTo(minEntityWrites);
        assertThat(aggregateEventAppends).isGreaterThanOrEqualTo(minEventAppends);
        assertThat(aggregateQueries).isGreaterThanOrEqualTo(minQueries);
        assertThat(bestThroughput).isGreaterThanOrEqualTo(MIN_THROUGHPUT_OPS_PER_SECOND);

        writeMetricsReport(iterationSummaries, aggregateEntityWrites, aggregateEventAppends, aggregateQueries, bestThroughput);
    }

    private Map<String, Object> runSingleIteration(int iteration) throws InterruptedException {

        Queue<Long> entitySaveLatenciesMs = new ConcurrentLinkedQueue<>();
        Queue<Long> eventAppendLatenciesMs = new ConcurrentLinkedQueue<>();
        Queue<Long> queryLatenciesMs = new ConcurrentLinkedQueue<>();
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(TENANT_COUNT);
        AtomicLong entityWrites = new AtomicLong();
        AtomicLong eventAppends = new AtomicLong();
        AtomicLong queries = new AtomicLong();

        long heapBeforeBytes = usedHeapBytes();
        long startNanos = System.nanoTime();

        try (ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int tenantIndex = 0; tenantIndex < TENANT_COUNT; tenantIndex++) {
                final String tenantId = "load-tenant-" + tenantIndex;
                workers.submit(() -> runTenantScenario(
                    tenantId,
                    entitySaveLatenciesMs,
                    eventAppendLatenciesMs,
                    queryLatenciesMs,
                    entityWrites,
                    eventAppends,
                    queries,
                    failures,
                    done
                ));
            }

            assertThat(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("all tenants should complete within %s seconds", TIMEOUT_SECONDS)
                .isTrue();
        }

        long durationNanos = System.nanoTime() - startNanos;
        long heapAfterBytes = usedHeapBytes();
        long heapDeltaMb = Math.max(0L, heapAfterBytes - heapBeforeBytes) / (1024L * 1024L);
        double totalOps = entityWrites.get() + eventAppends.get() + queries.get();
        double throughputPerSecond = totalOps / Math.max(1.0d, durationNanos / 1_000_000_000.0d);
        long entitySaveP95 = percentile(entitySaveLatenciesMs, 0.95d);
        long eventAppendP95 = percentile(eventAppendLatenciesMs, 0.95d);
        long queryP95 = percentile(queryLatenciesMs, 0.95d);

        assertThat(failures).isEmpty();
        assertThat(entityWrites.get()).isEqualTo((long) TENANT_COUNT * ENTITY_OPS_PER_TENANT);
        assertThat(eventAppends.get()).isEqualTo((long) TENANT_COUNT * EVENT_OPS_PER_TENANT);
        assertThat(queries.get()).isEqualTo((long) TENANT_COUNT * 2L);
        assertThat(entitySaveP95).isLessThanOrEqualTo(MAX_P95_ENTITY_SAVE_MS);
        assertThat(eventAppendP95).isLessThanOrEqualTo(MAX_P95_EVENT_APPEND_MS);
        assertThat(queryP95).isLessThanOrEqualTo(MAX_P95_QUERY_MS);
        assertThat(heapDeltaMb).isLessThanOrEqualTo(MAX_HEAP_DELTA_MB);
        assertThat(throughputPerSecond).isPositive();

        try (var connection = POSTGRES.createConnection("");
             var statement = connection.prepareStatement(
                 "SELECT tenant_id, COUNT(*) AS entity_count FROM entities GROUP BY tenant_id ORDER BY tenant_id"
             );
             var resultSet = statement.executeQuery()) {
            int tenantRows = 0;
            while (resultSet.next()) {
                tenantRows++;
                assertThat(resultSet.getLong("entity_count")).isEqualTo(ENTITY_OPS_PER_TENANT);
            }
            assertThat(tenantRows).isEqualTo(TENANT_COUNT);
        } catch (Exception exception) {
            throw new AssertionError("direct entity-store inspection failed", exception);
        }

        return Map.ofEntries(
            Map.entry("iteration", iteration),
            Map.entry("tenantCount", TENANT_COUNT),
            Map.entry("entityOpsPerTenant", ENTITY_OPS_PER_TENANT),
            Map.entry("eventOpsPerTenant", EVENT_OPS_PER_TENANT),
            Map.entry("entityWrites", entityWrites.get()),
            Map.entry("eventAppends", eventAppends.get()),
            Map.entry("queries", queries.get()),
            Map.entry("heapDeltaMb", heapDeltaMb),
            Map.entry("entitySaveP95Ms", entitySaveP95),
            Map.entry("eventAppendP95Ms", eventAppendP95),
            Map.entry("queryP95Ms", queryP95),
            Map.entry("throughputOpsPerSecond", throughputPerSecond),
            Map.entry("durationSeconds", durationNanos / 1_000_000_000.0d),
            Map.entry("timestamp", Instant.now().toString())
        );
    }

    private static void writeMetricsReport(
        List<Map<String, Object>> iterationSummaries,
        long aggregateEntityWrites,
        long aggregateEventAppends,
        long aggregateQueries,
        double bestThroughput
    ) {
        if (METRICS_OUTPUT.isBlank()) {
            return;
        }

        Path outputPath = Path.of(METRICS_OUTPUT);
        Map<String, Object> report = Map.ofEntries(
            Map.entry("tenantCount", TENANT_COUNT),
            Map.entry("entityOpsPerTenant", ENTITY_OPS_PER_TENANT),
            Map.entry("eventOpsPerTenant", EVENT_OPS_PER_TENANT),
            Map.entry("iterations", ITERATIONS),
            Map.entry("aggregateEntityWrites", aggregateEntityWrites),
            Map.entry("aggregateEventAppends", aggregateEventAppends),
            Map.entry("aggregateQueries", aggregateQueries),
            Map.entry("bestThroughputOpsPerSecond", bestThroughput),
            Map.entry("minThroughputOpsPerSecond", MIN_THROUGHPUT_OPS_PER_SECOND),
            Map.entry("generatedAt", Instant.now().toString()),
            Map.entry("iterationsSummary", iterationSummaries)
        );

        try {
            Files.createDirectories(outputPath.getParent());
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), report);
        } catch (Exception exception) {
            throw new AssertionError("failed to write durable load metrics report", exception);
        }
    }

    private void runTenantScenario(
        String tenantId,
        Queue<Long> entitySaveLatenciesMs,
        Queue<Long> eventAppendLatenciesMs,
        Queue<Long> queryLatenciesMs,
        AtomicLong entityWrites,
        AtomicLong eventAppends,
        AtomicLong queries,
        List<Throwable> failures,
        CountDownLatch done
    ) {
        Eventloop eventloop = Eventloop.builder().withCurrentThread().build();
        eventloop.execute(() -> tenantWorkload(
            tenantId,
            entitySaveLatenciesMs,
            eventAppendLatenciesMs,
            queryLatenciesMs,
            entityWrites,
            eventAppends,
            queries
        ).whenException(failures::add)
            .whenComplete(($, error) -> done.countDown()));
        eventloop.run();
    }

    private Promise<Void> tenantWorkload(
        String tenantId,
        Queue<Long> entitySaveLatenciesMs,
        Queue<Long> eventAppendLatenciesMs,
        Queue<Long> queryLatenciesMs,
        AtomicLong entityWrites,
        AtomicLong eventAppends,
        AtomicLong queries
    ) {
        TenantContext tenant = TenantContext.of(tenantId);
        String collection = "load-orders-" + tenantId;
        Promise<Void> chain = Promise.complete();

        for (int index = 0; index < ENTITY_OPS_PER_TENANT; index++) {
            final int entityIndex = index;
            chain = chain.then(() -> measureLatency(
                () -> entityStore.save(tenant, EntityStore.Entity.builder()
                    .collection(collection)
                    .id(tenantId + "-entity-" + entityIndex)
                    .data(Map.of(
                        "tenantTag", tenantId,
                        "index", entityIndex,
                        "amount", entityIndex * 10,
                        "status", entityIndex % 2 == 0 ? "open" : "closed"
                    ))
                    .build()),
                entitySaveLatenciesMs
            ).map(saved -> {
                entityWrites.incrementAndGet();
                return (Void) null;
            }));
        }

        for (int index = 0; index < EVENT_OPS_PER_TENANT; index++) {
            final int eventIndex = index;
            chain = chain.then(() -> measureLatency(
                () -> eventStore.append(tenant, EventLogStore.EventEntry.builder()
                    .eventType("entity.load-tested")
                    .timestamp(Instant.now())
                    .payload(writePayloadBytes(tenantId, eventIndex))
                    .headers(Map.of("tenant", tenantId))
                    .idempotencyKey(tenantId + "-event-" + eventIndex)
                    .build()),
                eventAppendLatenciesMs
            ).map(offset -> {
                eventAppends.incrementAndGet();
                return (Void) null;
            }));
        }

        chain = chain.then(() -> measureLatency(
            () -> entityStore.query(tenant, EntityStore.QuerySpec.builder()
                .collection(collection)
                .limit(ENTITY_OPS_PER_TENANT)
                .build()),
            queryLatenciesMs
        ).map(result -> {
            queries.incrementAndGet();
            assertThat(result.entities()).hasSize(ENTITY_OPS_PER_TENANT);
            assertThat(result.entities())
                .allSatisfy(entity -> assertThat(entity.data()).containsEntry("tenantTag", tenantId));
            return (Void) null;
        }));

        chain = chain.then(() -> measureLatency(
            () -> eventStore.readByType(tenant, "entity.load-tested", Offset.zero(), EVENT_OPS_PER_TENANT),
            queryLatenciesMs
        ).map(events -> {
            queries.incrementAndGet();
            assertThat(events).hasSize(EVENT_OPS_PER_TENANT);
            assertThat(events)
                .allSatisfy(event -> assertThat(readPayload(event.payload())).containsEntry("tenantId", tenantId));
            return (Void) null;
        }));

        return chain;
    }

    private static <T> Promise<T> measureLatency(Supplier<Promise<T>> operation, Queue<Long> latenciesMs) {
        long startNanos = System.nanoTime();
        return operation.get().whenComplete(($, error) -> latenciesMs.add(
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        ));
    }

    private static ByteBuffer writePayloadBytes(String tenantId, int eventIndex) {
        try {
            return ByteBuffer.wrap(OBJECT_MAPPER.writeValueAsBytes(Map.of(
                "tenantId", tenantId,
                "index", eventIndex,
                "source", "durable-load-suite"
            )));
        } catch (Exception exception) {
            throw new IllegalStateException("failed to serialize event payload", exception);
        }
    }

    private static Map<String, Object> readPayload(ByteBuffer payload) {
        try {
            ByteBuffer duplicate = payload.duplicate();
            byte[] bytes = new byte[duplicate.remaining()];
            duplicate.get(bytes);
            return OBJECT_MAPPER.readValue(bytes, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to deserialize event payload", exception);
        }
    }

    private static long percentile(Queue<Long> values, double percentile) {
        List<Long> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        if (sorted.isEmpty()) {
            return 0L;
        }
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}