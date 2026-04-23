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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
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
@EnabledIfEnvironmentVariable(named = "DATACLOUD_DURABLE_LOAD_ENABLED", matches = "true") // GH-90000
@Testcontainers
@DisplayName("Durable Multi-Tenant Load Integration Test")
@SuppressWarnings({"resource", "deprecation"}) // GH-90000
class DurableMultiTenantLoadIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule()); // GH-90000
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {}; // GH-90000

    private static final int TENANT_COUNT = Integer.getInteger("datacloud.load.tenants", 100); // GH-90000
    private static final int ENTITY_OPS_PER_TENANT = Integer.getInteger("datacloud.load.entityOpsPerTenant", 10); // GH-90000
    private static final int EVENT_OPS_PER_TENANT = Integer.getInteger("datacloud.load.eventOpsPerTenant", 10); // GH-90000
    private static final int EVENT_BURST_BATCH_SIZE = Integer.getInteger("datacloud.load.eventBurstBatchSize", 10_000); // GH-90000
    private static final long TIMEOUT_SECONDS = Long.getLong("datacloud.load.timeoutSeconds", 180L); // GH-90000
    private static final long MAX_HEAP_DELTA_MB = Long.getLong("datacloud.load.maxHeapDeltaMb", 256L); // GH-90000
    private static final long MAX_P95_ENTITY_SAVE_MS = Long.getLong("datacloud.load.maxP95EntitySaveMs", 4_000L); // GH-90000
    private static final long MAX_P95_EVENT_APPEND_MS = Long.getLong("datacloud.load.maxP95EventAppendMs", 2_500L); // GH-90000
    private static final long MAX_P95_QUERY_MS = Long.getLong("datacloud.load.maxP95QueryMs", 2_500L); // GH-90000
    private static final long MAX_P99_ENTITY_SAVE_MS = Long.getLong("datacloud.load.maxP99EntitySaveMs", 0L); // GH-90000
    private static final long MAX_P99_QUERY_MS = Long.getLong("datacloud.load.maxP99QueryMs", 0L); // GH-90000
    private static final int MIN_P99_SAMPLE_SIZE = Integer.getInteger("datacloud.load.minP99SampleSize", 100); // GH-90000
    private static final int ITERATIONS = Integer.getInteger("datacloud.load.iterations", 1); // GH-90000
    private static final double MIN_THROUGHPUT_OPS_PER_SECOND = Double.parseDouble( // GH-90000
        System.getProperty("datacloud.load.minThroughputOpsPerSecond", "0") // GH-90000
    );
    private static final double MIN_EVENT_BURST_THROUGHPUT_OPS_PER_SECOND = Double.parseDouble( // GH-90000
        System.getProperty("datacloud.load.minEventBurstThroughputOpsPerSecond", "0") // GH-90000
    );
    private static final String METRICS_OUTPUT = System.getProperty("datacloud.load.metricsOutput", ""); // GH-90000

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("datacloud_load_it")
        .withUsername("dc_load")
        .withPassword("dc_load_secret");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
        .withEmbeddedZookeeper(); // GH-90000

    private PostgresEntityStore entityStore;
    private KafkaEventLogStore eventStore;

    @BeforeAll
    static void migrateSchema() { // GH-90000
        createDatabaseRoles(); // GH-90000
        Flyway.configure() // GH-90000
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()) // GH-90000
            .locations("filesystem:" + resolveMigrationDirectory()) // GH-90000
            .target("10")
            .load() // GH-90000
            .migrate(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (entityStore != null) { // GH-90000
            entityStore.close(); // GH-90000
        }
        if (eventStore != null) { // GH-90000
            eventStore.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("mixed CRUD, query, and event append traffic stays tenant-isolated under durable load")
    void mixedCrudQueryAndEventAppendTrafficStaysTenantIsolatedUnderDurableLoad() throws InterruptedException { // GH-90000
        entityStore = new PostgresEntityStore(new PostgresEntityStoreConfig( // GH-90000
            POSTGRES.getJdbcUrl(), // GH-90000
            POSTGRES.getUsername(), // GH-90000
            POSTGRES.getPassword(), // GH-90000
            24,
            2,
            30_000L,
            600_000L,
            1_800_000L
        ));
        eventStore = new KafkaEventLogStore(KafkaEventLogStoreConfig.builder() // GH-90000
            .bootstrapServers(normalizeBootstrapServers(KAFKA.getBootstrapServers())) // GH-90000
            .partitions(1) // GH-90000
            .replicationFactor((short) 1) // GH-90000
            .readTimeoutMs(5_000L) // GH-90000
            .build()); // GH-90000

        preWarmTenants(); // GH-90000

        List<Map<String, Object>> iterationSummaries = new ArrayList<>(); // GH-90000

        for (int iteration = 1; iteration <= ITERATIONS; iteration++) { // GH-90000
            iterationSummaries.add(runSingleIteration(iteration)); // GH-90000
        }

        long minEntityWrites = (long) TENANT_COUNT * ENTITY_OPS_PER_TENANT * ITERATIONS; // GH-90000
        long minEventAppends = (long) TENANT_COUNT * EVENT_OPS_PER_TENANT * ITERATIONS; // GH-90000
        long minQueries = (long) TENANT_COUNT * 2L * ITERATIONS; // GH-90000

        long aggregateEntityWrites = iterationSummaries.stream() // GH-90000
            .mapToLong(summary -> ((Number) summary.get("entityWrites")).longValue())
            .sum(); // GH-90000
        long aggregateEventAppends = iterationSummaries.stream() // GH-90000
            .mapToLong(summary -> ((Number) summary.get("eventAppends")).longValue())
            .sum(); // GH-90000
        long aggregateQueries = iterationSummaries.stream() // GH-90000
            .mapToLong(summary -> ((Number) summary.get("queries")).longValue())
            .sum(); // GH-90000
        double bestThroughput = iterationSummaries.stream() // GH-90000
            .mapToDouble(summary -> ((Number) summary.get("throughputOpsPerSecond")).doubleValue())
            .max() // GH-90000
            .orElse(0.0d); // GH-90000
        double bestEventBurstThroughput = iterationSummaries.stream() // GH-90000
            .mapToDouble(summary -> ((Number) summary.get("eventBurstThroughputOpsPerSecond")).doubleValue())
            .max() // GH-90000
            .orElse(0.0d); // GH-90000

        assertThat(aggregateEntityWrites).isGreaterThanOrEqualTo(minEntityWrites); // GH-90000
        assertThat(aggregateEventAppends).isGreaterThanOrEqualTo(minEventAppends); // GH-90000
        assertThat(aggregateQueries).isGreaterThanOrEqualTo(minQueries); // GH-90000
        assertThat(bestThroughput).isGreaterThanOrEqualTo(MIN_THROUGHPUT_OPS_PER_SECOND); // GH-90000
        assertThat(bestEventBurstThroughput).isGreaterThanOrEqualTo(MIN_EVENT_BURST_THROUGHPUT_OPS_PER_SECOND); // GH-90000

        writeMetricsReport( // GH-90000
            iterationSummaries,
            aggregateEntityWrites,
            aggregateEventAppends,
            aggregateQueries,
            bestThroughput,
            bestEventBurstThroughput
        );
    }

    private Map<String, Object> runSingleIteration(int iteration) throws InterruptedException { // GH-90000

        Queue<Long> entitySaveLatenciesMs = new ConcurrentLinkedQueue<>(); // GH-90000
        Queue<Long> eventAppendLatenciesMs = new ConcurrentLinkedQueue<>(); // GH-90000
        Queue<Long> queryLatenciesMs = new ConcurrentLinkedQueue<>(); // GH-90000
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>()); // GH-90000
        CountDownLatch done = new CountDownLatch(TENANT_COUNT); // GH-90000
        AtomicLong entityWrites = new AtomicLong(); // GH-90000
        AtomicLong eventAppends = new AtomicLong(); // GH-90000
        AtomicLong queries = new AtomicLong(); // GH-90000

        long heapBeforeBytes = usedHeapBytes(); // GH-90000
        long startNanos = System.nanoTime(); // GH-90000

        try (ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor()) { // GH-90000
            for (int tenantIndex = 0; tenantIndex < TENANT_COUNT; tenantIndex++) { // GH-90000
                final String tenantId = tenantIdFor(tenantIndex); // GH-90000
                workers.submit(() -> runTenantScenario( // GH-90000
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

            assertThat(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) // GH-90000
                .as("all tenants should complete within %s seconds", TIMEOUT_SECONDS) // GH-90000
                .isTrue(); // GH-90000
        }

        long durationNanos = System.nanoTime() - startNanos; // GH-90000
        long heapAfterBytes = usedHeapBytes(); // GH-90000
        long heapDeltaMb = Math.max(0L, heapAfterBytes - heapBeforeBytes) / (1024L * 1024L); // GH-90000
        double totalOps = entityWrites.get() + eventAppends.get() + queries.get(); // GH-90000
        double throughputPerSecond = totalOps / Math.max(1.0d, durationNanos / 1_000_000_000.0d); // GH-90000
        long entitySaveP95 = percentile(entitySaveLatenciesMs, 0.95d); // GH-90000
        long eventAppendP95 = percentile(eventAppendLatenciesMs, 0.95d); // GH-90000
        long queryP95 = percentile(queryLatenciesMs, 0.95d); // GH-90000
        long entitySaveP99 = percentile(entitySaveLatenciesMs, 0.99d); // GH-90000
        long queryP99 = percentile(queryLatenciesMs, 0.99d); // GH-90000
        double eventBurstThroughputPerSecond = measureEventBurstThroughput(tenantIdFor(0), EVENT_BURST_BATCH_SIZE); // GH-90000

        assertThat(failures).isEmpty(); // GH-90000
        assertThat(entityWrites.get()).isEqualTo((long) TENANT_COUNT * ENTITY_OPS_PER_TENANT); // GH-90000
        assertThat(eventAppends.get()).isEqualTo((long) TENANT_COUNT * EVENT_OPS_PER_TENANT); // GH-90000
        assertThat(queries.get()).isEqualTo((long) TENANT_COUNT * 2L); // GH-90000
        assertThat(entitySaveP95).isLessThanOrEqualTo(MAX_P95_ENTITY_SAVE_MS); // GH-90000
        assertThat(eventAppendP95).isLessThanOrEqualTo(MAX_P95_EVENT_APPEND_MS); // GH-90000
        assertThat(queryP95).isLessThanOrEqualTo(MAX_P95_QUERY_MS); // GH-90000
        if (MAX_P99_ENTITY_SAVE_MS > 0 && entitySaveLatenciesMs.size() >= MIN_P99_SAMPLE_SIZE) { // GH-90000
            assertThat(entitySaveP99).isLessThanOrEqualTo(MAX_P99_ENTITY_SAVE_MS); // GH-90000
        }
        if (MAX_P99_QUERY_MS > 0 && queryLatenciesMs.size() >= MIN_P99_SAMPLE_SIZE) { // GH-90000
            assertThat(queryP99).isLessThanOrEqualTo(MAX_P99_QUERY_MS); // GH-90000
        }
        assertThat(eventBurstThroughputPerSecond).isGreaterThanOrEqualTo(MIN_EVENT_BURST_THROUGHPUT_OPS_PER_SECOND); // GH-90000
        assertThat(heapDeltaMb).isLessThanOrEqualTo(MAX_HEAP_DELTA_MB); // GH-90000
        assertThat(throughputPerSecond).isPositive(); // GH-90000

        try (var connection = POSTGRES.createConnection("");
             var statement = connection.prepareStatement( // GH-90000
                 "SELECT tenant_id, COUNT(*) AS entity_count FROM entities WHERE collection_name LIKE 'load-orders-%' GROUP BY tenant_id ORDER BY tenant_id" // GH-90000
             );
             var resultSet = statement.executeQuery()) { // GH-90000
            int tenantRows = 0;
            while (resultSet.next()) { // GH-90000
                tenantRows++;
                assertThat(resultSet.getLong("entity_count")).isEqualTo(ENTITY_OPS_PER_TENANT);
            }
            assertThat(tenantRows).isEqualTo(TENANT_COUNT); // GH-90000
        } catch (Exception exception) { // GH-90000
            throw new AssertionError("direct entity-store inspection failed", exception); // GH-90000
        }

        return Map.ofEntries( // GH-90000
            Map.entry("iteration", iteration), // GH-90000
            Map.entry("tenantCount", TENANT_COUNT), // GH-90000
            Map.entry("entityOpsPerTenant", ENTITY_OPS_PER_TENANT), // GH-90000
            Map.entry("eventOpsPerTenant", EVENT_OPS_PER_TENANT), // GH-90000
            Map.entry("entityWrites", entityWrites.get()), // GH-90000
            Map.entry("eventAppends", eventAppends.get()), // GH-90000
            Map.entry("queries", queries.get()), // GH-90000
            Map.entry("heapDeltaMb", heapDeltaMb), // GH-90000
            Map.entry("entitySaveP95Ms", entitySaveP95), // GH-90000
            Map.entry("eventAppendP95Ms", eventAppendP95), // GH-90000
            Map.entry("queryP95Ms", queryP95), // GH-90000
            Map.entry("entitySaveP99Ms", entitySaveP99), // GH-90000
            Map.entry("queryP99Ms", queryP99), // GH-90000
            Map.entry("minP99SampleSize", MIN_P99_SAMPLE_SIZE), // GH-90000
            Map.entry("entitySaveP99Evaluated", MAX_P99_ENTITY_SAVE_MS > 0 && entitySaveLatenciesMs.size() >= MIN_P99_SAMPLE_SIZE), // GH-90000
            Map.entry("queryP99Evaluated", MAX_P99_QUERY_MS > 0 && queryLatenciesMs.size() >= MIN_P99_SAMPLE_SIZE), // GH-90000
            Map.entry("eventBurstBatchSize", EVENT_BURST_BATCH_SIZE), // GH-90000
            Map.entry("eventBurstThroughputOpsPerSecond", eventBurstThroughputPerSecond), // GH-90000
            Map.entry("throughputOpsPerSecond", throughputPerSecond), // GH-90000
            Map.entry("durationSeconds", durationNanos / 1_000_000_000.0d), // GH-90000
            Map.entry("timestamp", Instant.now().toString()) // GH-90000
        );
    }

    private static void writeMetricsReport( // GH-90000
        List<Map<String, Object>> iterationSummaries,
        long aggregateEntityWrites,
        long aggregateEventAppends,
        long aggregateQueries,
        double bestThroughput,
        double bestEventBurstThroughput
    ) {
        if (METRICS_OUTPUT.isBlank()) { // GH-90000
            return;
        }

        Path outputPath = Path.of(METRICS_OUTPUT); // GH-90000
        Map<String, Object> report = Map.ofEntries( // GH-90000
            Map.entry("tenantCount", TENANT_COUNT), // GH-90000
            Map.entry("entityOpsPerTenant", ENTITY_OPS_PER_TENANT), // GH-90000
            Map.entry("eventOpsPerTenant", EVENT_OPS_PER_TENANT), // GH-90000
            Map.entry("iterations", ITERATIONS), // GH-90000
            Map.entry("aggregateEntityWrites", aggregateEntityWrites), // GH-90000
            Map.entry("aggregateEventAppends", aggregateEventAppends), // GH-90000
            Map.entry("aggregateQueries", aggregateQueries), // GH-90000
            Map.entry("bestThroughputOpsPerSecond", bestThroughput), // GH-90000
            Map.entry("bestEventBurstThroughputOpsPerSecond", bestEventBurstThroughput), // GH-90000
            Map.entry("minThroughputOpsPerSecond", MIN_THROUGHPUT_OPS_PER_SECOND), // GH-90000
            Map.entry("minEventBurstThroughputOpsPerSecond", MIN_EVENT_BURST_THROUGHPUT_OPS_PER_SECOND), // GH-90000
            Map.entry("maxP99EntitySaveMs", MAX_P99_ENTITY_SAVE_MS), // GH-90000
            Map.entry("maxP99QueryMs", MAX_P99_QUERY_MS), // GH-90000
            Map.entry("generatedAt", Instant.now().toString()), // GH-90000
            Map.entry("iterationsSummary", iterationSummaries) // GH-90000
        );

        try {
            Files.createDirectories(outputPath.getParent()); // GH-90000
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), report); // GH-90000
        } catch (Exception exception) { // GH-90000
            throw new AssertionError("failed to write durable load metrics report", exception); // GH-90000
        }
    }

    private void preWarmTenants() { // GH-90000
        ensureTenantTopicsExist(); // GH-90000
        for (int tenantIndex = 0; tenantIndex < TENANT_COUNT; tenantIndex++) { // GH-90000
            String tenantId = tenantIdFor(tenantIndex); // GH-90000
            TenantContext tenant = TenantContext.of(tenantId); // GH-90000
            String warmupEntityId = UUID.nameUUIDFromBytes((tenantId + "-warmup-entity").getBytes(StandardCharsets.UTF_8)).toString(); // GH-90000
            runBlocking(() -> entityStore.save(tenant, EntityStore.Entity.builder() // GH-90000
                .collection("warmup-orders-" + tenantId) // GH-90000
                .id(warmupEntityId) // GH-90000
                .data(Map.of("tenantTag", tenantId, "warmup", true)) // GH-90000
                .build())); // GH-90000
            runBlocking(() -> eventStore.append(tenant, EventLogStore.EventEntry.builder() // GH-90000
                .eventType("warmup.event")
                .timestamp(Instant.now()) // GH-90000
                .payload(writePayloadBytes(tenantId, -1)) // GH-90000
                .headers(Map.of("tenant", tenantId, "warmup", "true")) // GH-90000
                .idempotencyKey(tenantId + "-warmup") // GH-90000
                .build())); // GH-90000
        }
    }

    private void ensureTenantTopicsExist() { // GH-90000
        Map<String, Object> adminProps = Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, normalizeBootstrapServers(KAFKA.getBootstrapServers())); // GH-90000
        try (AdminClient adminClient = AdminClient.create(adminProps)) { // GH-90000
            List<NewTopic> topics = new ArrayList<>(TENANT_COUNT); // GH-90000
            for (int tenantIndex = 0; tenantIndex < TENANT_COUNT; tenantIndex++) { // GH-90000
                topics.add(new NewTopic(topicFor(tenantIdFor(tenantIndex)), 1, (short) 1)); // GH-90000
            }
            CreateTopicsResult result = adminClient.createTopics(topics); // GH-90000
            result.all().get(30, TimeUnit.SECONDS); // GH-90000
        } catch (Exception exception) { // GH-90000
            String message = exception.getMessage(); // GH-90000
            if (message == null || !message.contains("already exists")) {
                throw new IllegalStateException("failed to pre-create durable-load Kafka topics", exception); // GH-90000
            }
        }
    }

    private double measureEventBurstThroughput(String tenantId, int eventCount) { // GH-90000
        TenantContext tenant = TenantContext.of(tenantId); // GH-90000
        List<EventLogStore.EventEntry> entries = new ArrayList<>(eventCount); // GH-90000
        for (int index = 0; index < eventCount; index++) { // GH-90000
            entries.add(EventLogStore.EventEntry.builder() // GH-90000
                .eventType("entity.load-burst")
                .timestamp(Instant.now()) // GH-90000
                .payload(writePayloadBytes(tenantId, index)) // GH-90000
                .headers(Map.of("tenant", tenantId, "phase", "burst")) // GH-90000
                .idempotencyKey(tenantId + "-burst-" + index) // GH-90000
                .build()); // GH-90000
        }

        long startedAt = System.nanoTime(); // GH-90000
        List<Offset> offsets = runBlocking(() -> eventStore.appendBatch(tenant, entries)); // GH-90000
        long durationNanos = System.nanoTime() - startedAt; // GH-90000

        assertThat(offsets).hasSize(eventCount); // GH-90000
        return eventCount / Math.max(1.0d, durationNanos / 1_000_000_000.0d); // GH-90000
    }

    private static <T> T runBlocking(Supplier<Promise<T>> operation) { // GH-90000
        Eventloop eventloop = Eventloop.builder().withCurrentThread().build(); // GH-90000
        final Object[] result = new Object[1];
        final Throwable[] failure = new Throwable[1];
        eventloop.execute(() -> operation.get().whenComplete((value, error) -> { // GH-90000
            result[0] = value;
            failure[0] = error;
        }));
        eventloop.run(); // GH-90000
        if (failure[0] != null) { // GH-90000
            throw new IllegalStateException("blocking performance helper failed", failure[0]); // GH-90000
        }
        @SuppressWarnings("unchecked")
        T castResult = (T) result[0]; // GH-90000
        return castResult;
    }

    private static String topicFor(String tenantId) { // GH-90000
        return "datacloud." + tenantId + ".events";
    }

    private void runTenantScenario( // GH-90000
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
        Eventloop eventloop = Eventloop.builder().withCurrentThread().build(); // GH-90000
        eventloop.execute(() -> tenantWorkload( // GH-90000
            tenantId,
            entitySaveLatenciesMs,
            eventAppendLatenciesMs,
            queryLatenciesMs,
            entityWrites,
            eventAppends,
            queries
        ).whenException(failures::add) // GH-90000
            .whenComplete(($, error) -> done.countDown())); // GH-90000
        eventloop.run(); // GH-90000
    }

    private Promise<Void> tenantWorkload( // GH-90000
        String tenantId,
        Queue<Long> entitySaveLatenciesMs,
        Queue<Long> eventAppendLatenciesMs,
        Queue<Long> queryLatenciesMs,
        AtomicLong entityWrites,
        AtomicLong eventAppends,
        AtomicLong queries
    ) {
        TenantContext tenant = TenantContext.of(tenantId); // GH-90000
        String collection = "load-orders-" + tenantId;
        Promise<Void> chain = Promise.complete(); // GH-90000

        for (int index = 0; index < ENTITY_OPS_PER_TENANT; index++) { // GH-90000
            final int entityIndex = index;
            chain = chain.then(() -> measureLatency( // GH-90000
                () -> entityStore.save(tenant, EntityStore.Entity.builder() // GH-90000
                    .collection(collection) // GH-90000
                    .id(entityIdFor(tenantId, entityIndex)) // GH-90000
                    .data(Map.of( // GH-90000
                        "tenantTag", tenantId,
                        "index", entityIndex,
                        "amount", entityIndex * 10,
                        "status", entityIndex % 2 == 0 ? "open" : "closed"
                    ))
                    .build()), // GH-90000
                entitySaveLatenciesMs
            ).map(saved -> { // GH-90000
                entityWrites.incrementAndGet(); // GH-90000
                return (Void) null; // GH-90000
            }));
        }

        chain = chain.then(() -> { // GH-90000
            List<EventLogStore.EventEntry> entries = new ArrayList<>(EVENT_OPS_PER_TENANT); // GH-90000
            for (int index = 0; index < EVENT_OPS_PER_TENANT; index++) { // GH-90000
                entries.add(EventLogStore.EventEntry.builder() // GH-90000
                    .eventType("entity.load-tested")
                    .timestamp(Instant.now()) // GH-90000
                    .payload(writePayloadBytes(tenantId, index)) // GH-90000
                    .headers(Map.of("tenant", tenantId)) // GH-90000
                    .idempotencyKey(tenantId + "-event-" + index) // GH-90000
                    .build()); // GH-90000
            }
            return measureBatchLatencyPerEvent( // GH-90000
                () -> eventStore.appendBatch(tenant, entries), // GH-90000
                EVENT_OPS_PER_TENANT,
                eventAppendLatenciesMs
            ).map(offsets -> { // GH-90000
                eventAppends.addAndGet(offsets.size()); // GH-90000
                return (Void) null; // GH-90000
            });
        });

        chain = chain.then(() -> measureLatency( // GH-90000
            () -> entityStore.query(tenant, EntityStore.QuerySpec.builder() // GH-90000
                .collection(collection) // GH-90000
                .limit(ENTITY_OPS_PER_TENANT) // GH-90000
                .build()), // GH-90000
            queryLatenciesMs
        ).map(result -> { // GH-90000
            queries.incrementAndGet(); // GH-90000
            assertThat(result.entities()).hasSize(ENTITY_OPS_PER_TENANT); // GH-90000
            assertThat(result.entities()) // GH-90000
                .allSatisfy(entity -> assertThat(entity.data()).containsEntry("tenantTag", tenantId)); // GH-90000
            return (Void) null; // GH-90000
        }));

        chain = chain.then(() -> measureLatency( // GH-90000
            () -> eventStore.readByType(tenant, "entity.load-tested", Offset.zero(), EVENT_OPS_PER_TENANT), // GH-90000
            queryLatenciesMs
        ).map(events -> { // GH-90000
            queries.incrementAndGet(); // GH-90000
            assertThat(events).hasSize(EVENT_OPS_PER_TENANT); // GH-90000
            assertThat(events) // GH-90000
                .allSatisfy(event -> assertThat(readPayload(event.payload())).containsEntry("tenantId", tenantId)); // GH-90000
            return (Void) null; // GH-90000
        }));

        return chain;
    }

    private static <T> Promise<T> measureLatency(Supplier<Promise<T>> operation, Queue<Long> latenciesMs) { // GH-90000
        long startNanos = System.nanoTime(); // GH-90000
        return operation.get().whenComplete(($, error) -> latenciesMs.add( // GH-90000
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos) // GH-90000
        ));
    }

    private static <T> Promise<T> measureBatchLatencyPerEvent( // GH-90000
        Supplier<Promise<T>> operation,
        int batchSize,
        Queue<Long> latenciesMs
    ) {
        long startNanos = System.nanoTime(); // GH-90000
        return operation.get().whenComplete(($, error) -> { // GH-90000
            long totalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos); // GH-90000
            long perEventMs = Math.max(1L, totalMs / Math.max(1, batchSize)); // GH-90000
            for (int index = 0; index < batchSize; index++) { // GH-90000
                latenciesMs.add(perEventMs); // GH-90000
            }
        });
    }

    private static String normalizeBootstrapServers(String bootstrapServers) { // GH-90000
        return bootstrapServers
            .replace("PLAINTEXT://", "") // GH-90000
            .replace("SSL://", ""); // GH-90000
    }

    private static void createDatabaseRoles() { // GH-90000
        String createRolesSql = """
            DO $$
            BEGIN
                IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'application_user') THEN // GH-90000
                    CREATE ROLE application_user LOGIN;
                END IF;
                IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'admin_user') THEN // GH-90000
                    CREATE ROLE admin_user LOGIN;
                END IF;
            END
            $$;
            """;

        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) { // GH-90000
            statement.execute(createRolesSql); // GH-90000
        } catch (Exception exception) { // GH-90000
            throw new IllegalStateException("failed to provision durable-load database roles", exception); // GH-90000
        }
    }

    private static Path resolveMigrationDirectory() { // GH-90000
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path current = workingDirectory;
        while (current != null) { // GH-90000
            Path siblingModulePath = current.resolve("platform-launcher")
                .resolve("src").resolve("main").resolve("resources").resolve("db").resolve("migration");
            if (Files.isDirectory(siblingModulePath)) { // GH-90000
                return siblingModulePath;
            }

            Path monorepoPath = current.resolve("products").resolve("data-cloud")
                .resolve("platform-launcher").resolve("src").resolve("main").resolve("resources").resolve("db").resolve("migration");
            if (Files.isDirectory(monorepoPath)) { // GH-90000
                return monorepoPath;
            }

            current = current.getParent(); // GH-90000
        }

        throw new IllegalStateException("could not locate Data Cloud migration directory from " + workingDirectory); // GH-90000
    }

    private static ByteBuffer writePayloadBytes(String tenantId, int eventIndex) { // GH-90000
        try {
            return ByteBuffer.wrap(OBJECT_MAPPER.writeValueAsBytes(Map.of( // GH-90000
                "tenantId", tenantId,
                "index", eventIndex,
                "source", "durable-load-suite"
            )));
        } catch (Exception exception) { // GH-90000
            throw new IllegalStateException("failed to serialize event payload", exception); // GH-90000
        }
    }

    private static Map<String, Object> readPayload(ByteBuffer payload) { // GH-90000
        try {
            ByteBuffer duplicate = payload.duplicate(); // GH-90000
            byte[] bytes = new byte[duplicate.remaining()]; // GH-90000
            duplicate.get(bytes); // GH-90000
            return OBJECT_MAPPER.readValue(bytes, MAP_TYPE); // GH-90000
        } catch (Exception exception) { // GH-90000
            throw new IllegalStateException("failed to deserialize event payload", exception); // GH-90000
        }
    }

    private static String tenantIdFor(int tenantIndex) { // GH-90000
        return deterministicUuid("durable-load-tenant-" + tenantIndex); // GH-90000
    }

    private static String entityIdFor(String tenantId, int entityIndex) { // GH-90000
        return deterministicUuid(tenantId + ":entity:" + entityIndex); // GH-90000
    }

    private static String deterministicUuid(String seed) { // GH-90000
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString(); // GH-90000
    }

    private static long percentile(Queue<Long> values, double percentile) { // GH-90000
        List<Long> sorted = values.stream().sorted(Comparator.naturalOrder()).toList(); // GH-90000
        if (sorted.isEmpty()) { // GH-90000
            return 0L;
        }
        int index = (int) Math.ceil(percentile * sorted.size()) - 1; // GH-90000
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1))); // GH-90000
    }

    private static long usedHeapBytes() { // GH-90000
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(); // GH-90000
    }
}