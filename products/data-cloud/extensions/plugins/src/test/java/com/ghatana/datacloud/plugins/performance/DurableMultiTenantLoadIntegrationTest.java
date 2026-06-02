package com.ghatana.datacloud.plugins.performance;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.datacloud.plugins.kafka.KafkaEventLogStore;
import com.ghatana.datacloud.plugins.kafka.KafkaEventLogStoreConfig;
import com.ghatana.datacloud.backpressure.BackpressureException;
import com.ghatana.datacloud.backpressure.BackpressureManager;
import com.ghatana.datacloud.plugins.postgres.PostgresEntityStore;
import com.ghatana.datacloud.plugins.postgres.PostgresEntityStoreConfig;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.types.identity.Offset;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.activej.promise.SettableCallback;
import io.activej.promise.SettablePromise;
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
@EnabledIfEnvironmentVariable(named = "DATACLOUD_DURABLE_LOAD_ENABLED", matches = "true") 
@Testcontainers
@DisplayName("Durable Multi-Tenant Load Integration Test")
@SuppressWarnings({"resource", "deprecation"}) 
class DurableMultiTenantLoadIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule()); 
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {}; 

    private static final int TENANT_COUNT = Integer.getInteger("datacloud.load.tenants", 100); 
    private static final int ENTITY_OPS_PER_TENANT = Integer.getInteger("datacloud.load.entityOpsPerTenant", 10); 
    private static final int EVENT_OPS_PER_TENANT = Integer.getInteger("datacloud.load.eventOpsPerTenant", 10); 
    private static final int EVENT_BURST_BATCH_SIZE = Integer.getInteger("datacloud.load.eventBurstBatchSize", 10_000); 
    private static final long TIMEOUT_SECONDS = Long.getLong("datacloud.load.timeoutSeconds", 180L); 
    private static final long MAX_HEAP_DELTA_MB = Long.getLong("datacloud.load.maxHeapDeltaMb", 256L); 
    private static final long MAX_P95_ENTITY_SAVE_MS = Long.getLong("datacloud.load.maxP95EntitySaveMs", 4_000L); 
    private static final long MAX_P95_EVENT_APPEND_MS = Long.getLong("datacloud.load.maxP95EventAppendMs", 2_500L); 
    private static final long MAX_P95_QUERY_MS = Long.getLong("datacloud.load.maxP95QueryMs", 2_500L); 
    private static final long MAX_P99_ENTITY_SAVE_MS = Long.getLong("datacloud.load.maxP99EntitySaveMs", 0L); 
    private static final long MAX_P99_QUERY_MS = Long.getLong("datacloud.load.maxP99QueryMs", 0L); 
    private static final int MIN_P99_SAMPLE_SIZE = Integer.getInteger("datacloud.load.minP99SampleSize", 100); 
    private static final int ITERATIONS = Integer.getInteger("datacloud.load.iterations", 1); 
    private static final int BACKPRESSURE_MAX_CONCURRENT = Integer.getInteger("datacloud.load.backpressure.maxConcurrent", 2);
    private static final int BACKPRESSURE_QUEUE_CAPACITY = Integer.getInteger("datacloud.load.backpressure.queueCapacity", 4);
    private static final int BACKPRESSURE_REQUEST_COUNT = Integer.getInteger("datacloud.load.backpressure.requestCount", 12);
    private static final double MIN_THROUGHPUT_OPS_PER_SECOND = Double.parseDouble( 
        System.getProperty("datacloud.load.minThroughputOpsPerSecond", "0") 
    );
    private static final double MIN_EVENT_BURST_THROUGHPUT_OPS_PER_SECOND = Double.parseDouble( 
        System.getProperty("datacloud.load.minEventBurstThroughputOpsPerSecond", "0") 
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
        createDatabaseRoles(); 
        Flyway.configure() 
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()) 
            .locations("filesystem:" + resolveMigrationDirectory()) 
            .target("10")
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
            .bootstrapServers(normalizeBootstrapServers(KAFKA.getBootstrapServers())) 
            .partitions(1) 
            .replicationFactor((short) 1) 
            .readTimeoutMs(5_000L) 
            .build()); 

        preWarmTenants(); 

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
        double bestEventBurstThroughput = iterationSummaries.stream() 
            .mapToDouble(summary -> ((Number) summary.get("eventBurstThroughputOpsPerSecond")).doubleValue())
            .max() 
            .orElse(0.0d); 

        assertThat(aggregateEntityWrites).isGreaterThanOrEqualTo(minEntityWrites); 
        assertThat(aggregateEventAppends).isGreaterThanOrEqualTo(minEventAppends); 
        assertThat(aggregateQueries).isGreaterThanOrEqualTo(minQueries); 
        assertThat(bestThroughput).isGreaterThanOrEqualTo(MIN_THROUGHPUT_OPS_PER_SECOND); 
        assertThat(bestEventBurstThroughput).isGreaterThanOrEqualTo(MIN_EVENT_BURST_THROUGHPUT_OPS_PER_SECOND); 

        writeMetricsReport( 
            iterationSummaries,
            aggregateEntityWrites,
            aggregateEventAppends,
            aggregateQueries,
            bestThroughput,
            bestEventBurstThroughput
        );
    }

    @Test
    @DisplayName("multi-tenant burst traffic triggers backpressure drops under load")
    void multiTenantBurstTrafficTriggersBackpressureDropsUnderLoad() {
        BackpressureManager manager = BackpressureManager.builder()
            .maxConcurrent(BACKPRESSURE_MAX_CONCURRENT)
            .queueCapacity(BACKPRESSURE_QUEUE_CAPACITY)
            .strategy(BackpressureManager.BackpressureStrategy.DROP)
            .build();

        try {
            List<SettableCallback<Void>> heldPromises = new ArrayList<>();
            List<Promise<Void>> acceptedPromises = new ArrayList<>();
            AtomicLong acceptedRequests = new AtomicLong();

            for (int requestIndex = 0; requestIndex < BACKPRESSURE_REQUEST_COUNT; requestIndex++) {
                final int currentIndex = requestIndex;
                Promise<Void> submitted = manager.execute(BackpressureManager.Priority.NORMAL, () -> {
                    acceptedRequests.incrementAndGet();
                    return Promise.ofCallback(callback -> {
                        if (currentIndex < BACKPRESSURE_MAX_CONCURRENT) {
                            heldPromises.add(callback);
                        } else {
                            callback.set(null);
                        }
                    });
                });

                if (requestIndex < BACKPRESSURE_MAX_CONCURRENT) {
                    acceptedPromises.add(submitted);
                }
            }

            assertThat(acceptedRequests.get()).isEqualTo(BACKPRESSURE_MAX_CONCURRENT);
            assertThat(manager.getMetrics().getDroppedRequests()).isEqualTo(BACKPRESSURE_REQUEST_COUNT - BACKPRESSURE_MAX_CONCURRENT);
            assertThat(manager.getMetrics().getActiveRequests()).isEqualTo(BACKPRESSURE_MAX_CONCURRENT);
            assertThat(manager.getStatus().queuedRequests()).isZero();
            assertThat(manager.isUnderPressure()).isTrue();

            heldPromises.forEach(promise -> promise.set(null));
            runBlocking(() -> Promises.toList(acceptedPromises.stream()));

            assertThat(manager.getMetrics().getActiveRequests()).isZero();
            assertThat(manager.getMetrics().getCompletedRequests()).isEqualTo(BACKPRESSURE_MAX_CONCURRENT);
        } finally {
            manager.shutdown();
        }
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
                final String tenantId = tenantIdFor(tenantIndex); 
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
        long entitySaveP99 = percentile(entitySaveLatenciesMs, 0.99d); 
        long queryP99 = percentile(queryLatenciesMs, 0.99d); 
        double eventBurstThroughputPerSecond = measureEventBurstThroughput(tenantIdFor(0), EVENT_BURST_BATCH_SIZE); 

        assertThat(failures).isEmpty(); 
        assertThat(entityWrites.get()).isEqualTo((long) TENANT_COUNT * ENTITY_OPS_PER_TENANT); 
        assertThat(eventAppends.get()).isEqualTo((long) TENANT_COUNT * EVENT_OPS_PER_TENANT); 
        assertThat(queries.get()).isEqualTo((long) TENANT_COUNT * 2L); 
        assertThat(entitySaveP95).isLessThanOrEqualTo(MAX_P95_ENTITY_SAVE_MS); 
        assertThat(eventAppendP95).isLessThanOrEqualTo(MAX_P95_EVENT_APPEND_MS); 
        assertThat(queryP95).isLessThanOrEqualTo(MAX_P95_QUERY_MS); 
        if (MAX_P99_ENTITY_SAVE_MS > 0 && entitySaveLatenciesMs.size() >= MIN_P99_SAMPLE_SIZE) { 
            assertThat(entitySaveP99).isLessThanOrEqualTo(MAX_P99_ENTITY_SAVE_MS); 
        }
        if (MAX_P99_QUERY_MS > 0 && queryLatenciesMs.size() >= MIN_P99_SAMPLE_SIZE) { 
            assertThat(queryP99).isLessThanOrEqualTo(MAX_P99_QUERY_MS); 
        }
        assertThat(eventBurstThroughputPerSecond).isGreaterThanOrEqualTo(MIN_EVENT_BURST_THROUGHPUT_OPS_PER_SECOND); 
        assertThat(heapDeltaMb).isLessThanOrEqualTo(MAX_HEAP_DELTA_MB); 
        assertThat(throughputPerSecond).isPositive(); 

        try (var connection = POSTGRES.createConnection("");
             var statement = connection.prepareStatement( 
                 "SELECT tenant_id, COUNT(*) AS entity_count FROM entities WHERE collection_name LIKE 'load-orders-%' GROUP BY tenant_id ORDER BY tenant_id" 
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
            Map.entry("entitySaveP99Ms", entitySaveP99), 
            Map.entry("queryP99Ms", queryP99), 
            Map.entry("minP99SampleSize", MIN_P99_SAMPLE_SIZE), 
            Map.entry("entitySaveP99Evaluated", MAX_P99_ENTITY_SAVE_MS > 0 && entitySaveLatenciesMs.size() >= MIN_P99_SAMPLE_SIZE), 
            Map.entry("queryP99Evaluated", MAX_P99_QUERY_MS > 0 && queryLatenciesMs.size() >= MIN_P99_SAMPLE_SIZE), 
            Map.entry("eventBurstBatchSize", EVENT_BURST_BATCH_SIZE), 
            Map.entry("eventBurstThroughputOpsPerSecond", eventBurstThroughputPerSecond), 
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
        double bestThroughput,
        double bestEventBurstThroughput
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
            Map.entry("backpressureMaxConcurrent", BACKPRESSURE_MAX_CONCURRENT),
            Map.entry("backpressureQueueCapacity", BACKPRESSURE_QUEUE_CAPACITY),
            Map.entry("backpressureRequestCount", BACKPRESSURE_REQUEST_COUNT),
            Map.entry("aggregateEntityWrites", aggregateEntityWrites), 
            Map.entry("aggregateEventAppends", aggregateEventAppends), 
            Map.entry("aggregateQueries", aggregateQueries), 
            Map.entry("bestThroughputOpsPerSecond", bestThroughput), 
            Map.entry("bestEventBurstThroughputOpsPerSecond", bestEventBurstThroughput), 
            Map.entry("minThroughputOpsPerSecond", MIN_THROUGHPUT_OPS_PER_SECOND), 
            Map.entry("minEventBurstThroughputOpsPerSecond", MIN_EVENT_BURST_THROUGHPUT_OPS_PER_SECOND), 
            Map.entry("maxP99EntitySaveMs", MAX_P99_ENTITY_SAVE_MS), 
            Map.entry("maxP99QueryMs", MAX_P99_QUERY_MS), 
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

    private void preWarmTenants() {
        ensureTenantTopicsExist();
        for (int tenantIndex = 0; tenantIndex < TENANT_COUNT; tenantIndex++) {
            String tenantId = tenantIdFor(tenantIndex);
            com.ghatana.datacloud.spi.TenantContext spiTenant = com.ghatana.datacloud.spi.TenantContext.of(tenantId);
            com.ghatana.platform.domain.eventstore.TenantContext platformTenant = com.ghatana.platform.domain.eventstore.TenantContext.of(tenantId);
            String warmupEntityId = UUID.nameUUIDFromBytes((tenantId + "-warmup-entity").getBytes(StandardCharsets.UTF_8)).toString();
            runBlocking(() -> entityStore.save(spiTenant, EntityStore.Entity.builder()
                .collection("warmup-orders-" + tenantId)
                .id(warmupEntityId)
                .data(Map.of("tenantTag", tenantId, "warmup", true))
                .build()));
            runBlocking(() -> eventStore.append(platformTenant, EventLogStore.EventEntry.builder()
                .eventType("warmup.event")
                .timestamp(Instant.now())
                .payload(writePayloadBytes(tenantId, -1))
                .headers(Map.of("tenant", tenantId, "warmup", "true"))
                .idempotencyKey(tenantId + "-warmup")
                .build()));
        }
    }

    private void ensureTenantTopicsExist() { 
        Map<String, Object> adminProps = Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, normalizeBootstrapServers(KAFKA.getBootstrapServers())); 
        try (AdminClient adminClient = AdminClient.create(adminProps)) { 
            List<NewTopic> topics = new ArrayList<>(TENANT_COUNT); 
            for (int tenantIndex = 0; tenantIndex < TENANT_COUNT; tenantIndex++) { 
                topics.add(new NewTopic(topicFor(tenantIdFor(tenantIndex)), 1, (short) 1)); 
            }
            CreateTopicsResult result = adminClient.createTopics(topics); 
            result.all().get(30, TimeUnit.SECONDS); 
        } catch (Exception exception) { 
            String message = exception.getMessage(); 
            if (message == null || !message.contains("already exists")) {
                throw new IllegalStateException("failed to pre-create durable-load Kafka topics", exception); 
            }
        }
    }

    private double measureEventBurstThroughput(String tenantId, int eventCount) {
        com.ghatana.platform.domain.eventstore.TenantContext tenant = com.ghatana.platform.domain.eventstore.TenantContext.of(tenantId);
        List<EventLogStore.EventEntry> entries = new ArrayList<>(eventCount);
        for (int index = 0; index < eventCount; index++) {
            entries.add(EventLogStore.EventEntry.builder()
                .eventType("entity.load-burst")
                .timestamp(Instant.now())
                .payload(writePayloadBytes(tenantId, index))
                .headers(Map.of("tenant", tenantId, "phase", "burst"))
                .idempotencyKey(tenantId + "-burst-" + index)
                .build());
        }

        long startedAt = System.nanoTime();
        List<Offset> offsets = runBlocking(() -> eventStore.appendBatch(tenant, entries)); 
        long durationNanos = System.nanoTime() - startedAt; 

        assertThat(offsets).hasSize(eventCount); 
        return eventCount / Math.max(1.0d, durationNanos / 1_000_000_000.0d); 
    }

    private static <T> T runBlocking(Supplier<Promise<T>> operation) { 
        Eventloop eventloop = Eventloop.builder().withCurrentThread().build(); 
        final Object[] result = new Object[1];
        final Throwable[] failure = new Throwable[1];
        eventloop.execute(() -> operation.get().whenComplete((value, error) -> { 
            result[0] = value;
            failure[0] = error;
        }));
        eventloop.run(); 
        if (failure[0] != null) { 
            throw new IllegalStateException("blocking performance helper failed", failure[0]); 
        }
        @SuppressWarnings("unchecked")
        T castResult = (T) result[0]; 
        return castResult;
    }

    private static String topicFor(String tenantId) { 
        return "datacloud." + tenantId + ".events";
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
        com.ghatana.datacloud.spi.TenantContext spiTenant = com.ghatana.datacloud.spi.TenantContext.of(tenantId);
        com.ghatana.platform.domain.eventstore.TenantContext platformTenant = com.ghatana.platform.domain.eventstore.TenantContext.of(tenantId);
        String collection = "load-orders-" + tenantId;
        Promise<Void> chain = Promise.complete();

        for (int index = 0; index < ENTITY_OPS_PER_TENANT; index++) {
            final int entityIndex = index;
            chain = chain.then(() -> measureLatency(
                () -> entityStore.save(spiTenant, EntityStore.Entity.builder()
                    .collection(collection)
                    .id(entityIdFor(tenantId, entityIndex))
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

        chain = chain.then(() -> { 
            List<EventLogStore.EventEntry> entries = new ArrayList<>(EVENT_OPS_PER_TENANT); 
            for (int index = 0; index < EVENT_OPS_PER_TENANT; index++) { 
                entries.add(EventLogStore.EventEntry.builder() 
                    .eventType("entity.load-tested")
                    .timestamp(Instant.now()) 
                    .payload(writePayloadBytes(tenantId, index)) 
                    .headers(Map.of("tenant", tenantId)) 
                    .idempotencyKey(tenantId + "-event-" + index) 
                    .build()); 
            }
            return measureBatchLatencyPerEvent(
                () -> eventStore.appendBatch(platformTenant, entries),
                EVENT_OPS_PER_TENANT,
                eventAppendLatenciesMs
            ).map(offsets -> {
                @SuppressWarnings("unchecked")
                List<Offset> offsetList = (List<Offset>) offsets;
                eventAppends.addAndGet(offsetList.size());
                return (Void) null;
            });
        });

        chain = chain.then(() -> measureLatency(
            () -> entityStore.query(spiTenant, EntityStore.QuerySpec.builder()
                .collection(collection)
                .limit(ENTITY_OPS_PER_TENANT)
                .build()),
            queryLatenciesMs
        ).map(result -> {
            queries.incrementAndGet();
            EntityStore.QueryResult queryResult = (EntityStore.QueryResult) result;
            assertThat(queryResult.entities()).hasSize(ENTITY_OPS_PER_TENANT);
            assertThat(queryResult.entities())
                .allSatisfy(entity -> assertThat(entity.data()).containsEntry("tenantTag", tenantId));
            return (Void) null;
        }));

        chain = chain.then(() -> measureLatency(
            () -> eventStore.readByType(platformTenant, "entity.load-tested", Offset.zero(), EVENT_OPS_PER_TENANT),
            queryLatenciesMs
        ).map(events -> {
            queries.incrementAndGet();
            @SuppressWarnings("unchecked")
            List<EventLogStore.EventEntry> eventList = (List<EventLogStore.EventEntry>) events;
            assertThat(eventList).hasSize(EVENT_OPS_PER_TENANT);
            assertThat(eventList)
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

    private static <T> Promise<T> measureBatchLatencyPerEvent( 
        Supplier<Promise<T>> operation,
        int batchSize,
        Queue<Long> latenciesMs
    ) {
        long startNanos = System.nanoTime(); 
        return operation.get().whenComplete(($, error) -> { 
            long totalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos); 
            long perEventMs = Math.max(1L, totalMs / Math.max(1, batchSize)); 
            for (int index = 0; index < batchSize; index++) { 
                latenciesMs.add(perEventMs); 
            }
        });
    }

    private static String normalizeBootstrapServers(String bootstrapServers) { 
        return bootstrapServers
            .replace("PLAINTEXT://", "") 
            .replace("SSL://", ""); 
    }

    private static void createDatabaseRoles() { 
        String createRolesSql = """
            DO $$
            BEGIN
                IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'application_user') THEN 
                    CREATE ROLE application_user LOGIN;
                END IF;
                IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'admin_user') THEN 
                    CREATE ROLE admin_user LOGIN;
                END IF;
            END
            $$;
            """;

        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) { 
            statement.execute(createRolesSql); 
        } catch (Exception exception) { 
            throw new IllegalStateException("failed to provision durable-load database roles", exception); 
        }
    }

    private static Path resolveMigrationDirectory() { 
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path current = workingDirectory;
        while (current != null) { 
            Path siblingModulePath = current.resolve("platform-launcher")
                .resolve("src").resolve("main").resolve("resources").resolve("db").resolve("migration");
            if (Files.isDirectory(siblingModulePath)) { 
                return siblingModulePath;
            }

            Path monorepoPath = current.resolve("products").resolve("data-cloud")
                .resolve("platform-launcher").resolve("src").resolve("main").resolve("resources").resolve("db").resolve("migration");
            if (Files.isDirectory(monorepoPath)) { 
                return monorepoPath;
            }

            current = current.getParent(); 
        }

        throw new IllegalStateException("could not locate Data Cloud migration directory from " + workingDirectory); 
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

    private static String tenantIdFor(int tenantIndex) { 
        return deterministicUuid("durable-load-tenant-" + tenantIndex); 
    }

    private static String entityIdFor(String tenantId, int entityIndex) { 
        return deterministicUuid(tenantId + ":entity:" + entityIndex); 
    }

    private static String deterministicUuid(String seed) { 
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString(); 
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
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(); 
    }
}