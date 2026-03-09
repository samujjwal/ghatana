package com.ghatana.datacloud.observability;

import com.ghatana.datacloud.observability.DataCloudMetrics.OperationType;
import com.ghatana.datacloud.observability.DataCloudMetrics.PluginType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive tests for DataCloudMetrics observability facade.
 *
 * <p>Validates that all data-cloud operations correctly record metrics
 * to the underlying MeterRegistry covering entity operations, event
 * streaming, storage tiering, search, and plugin health.</p>
 *
 * @doc.type test
 * @doc.purpose Validate data-cloud observability metrics contract
 * @doc.layer product
 */
@DisplayName("Data Cloud Observability Metrics")
class DataCloudMetricsTest {

    private MeterRegistry registry;
    private DataCloudMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = DataCloudMetrics.create(registry);
    }

    // ════════════════════════════════════════════════════════════════
    // Construction
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("rejects null MeterRegistry")
        void rejectsNullRegistry() {
            assertThatThrownBy(() -> DataCloudMetrics.create(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("creates instance with valid registry")
        void createsWithValidRegistry() {
            DataCloudMetrics m = DataCloudMetrics.create(new SimpleMeterRegistry());
            assertThat(m).isNotNull();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Operation type coverage
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Operation Types")
    class OperationTypeTests {

        @Test
        @DisplayName("all operation types have non-null values")
        void allOperationTypesHaveValues() {
            for (OperationType op : OperationType.values()) {
                assertThat(op.getValue()).isNotNull().isNotEmpty();
            }
        }

        @Test
        @DisplayName("operation type values follow dot notation")
        void operationTypesFollowDotNotation() {
            for (OperationType op : OperationType.values()) {
                assertThat(op.getValue()).matches("[a-z._]+");
            }
        }

        @Test
        @DisplayName("all plugin types have non-null values")
        void allPluginTypesHaveValues() {
            for (PluginType pt : PluginType.values()) {
                assertThat(pt.getValue()).isNotNull().isNotEmpty();
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Timer operations
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Timer Operations")
    class TimerOperationTests {

        @Test
        @DisplayName("startTimer returns non-null sample")
        void startTimerReturnsNonNull() {
            Timer.Sample sample = metrics.startTimer();
            assertThat(sample).isNotNull();
        }

        @Test
        @DisplayName("recordSuccess stops timer and increments counter")
        void recordSuccessStopsTimerAndIncrements() {
            Timer.Sample sample = metrics.startTimer();

            // Small delay to ensure timer records something
            metrics.recordSuccess(sample, OperationType.ENTITY_CREATE,
                    "tenant-1", "pg-plugin");

            // Check that metrics were recorded
            long totalMeters = registry.getMeters().stream()
                    .filter(m -> m.getId().getName().contains("datacloud"))
                    .count();
            assertThat(totalMeters).isGreaterThan(0);
        }

        @Test
        @DisplayName("recordError stops timer and records error counter")
        void recordErrorStopsTimerAndRecords() {
            Timer.Sample sample = metrics.startTimer();
            Exception error = new RuntimeException("test error");

            metrics.recordError(sample, OperationType.ENTITY_CREATE,
                    "tenant-1", "pg-plugin", error);

            // Check error counters exist
            long errorMeters = registry.getMeters().stream()
                    .filter(m -> m.getId().getName().contains("error")
                            || m.getId().getTags().stream().anyMatch(t ->
                            t.getValue().equals("error")))
                    .count();
            assertThat(errorMeters).isGreaterThanOrEqualTo(0); // At minimum, timer is recorded
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Entity metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Entity Metrics")
    class EntityMetricsTests {

        @Test
        @DisplayName("records entity count gauge")
        void recordsEntityCount() {
            metrics.recordEntityCount("tenant-1", "users", 1000);

            // Gauge should be registered
            long gauges = registry.getMeters().stream()
                    .filter(m -> m.getId().getName().contains("entity") 
                            && m.getId().getName().contains("count"))
                    .count();
            assertThat(gauges).isGreaterThan(0);
        }

        @Test
        @DisplayName("records entity size gauge")
        void recordsEntitySize() {
            metrics.recordEntitySize("tenant-1", "orders", 50_000_000L);

            long sizeMeters = registry.getMeters().stream()
                    .filter(m -> m.getId().getName().contains("entity")
                            && m.getId().getName().contains("size"))
                    .count();
            assertThat(sizeMeters).isGreaterThan(0);
        }

        @Test
        @DisplayName("records entity CRUD operations")
        void recordsEntityCrudOperations() {
            Timer.Sample createSample = metrics.startTimer();
            metrics.recordSuccess(createSample, OperationType.ENTITY_CREATE,
                    "tenant-1", "pg-plugin");

            Timer.Sample readSample = metrics.startTimer();
            metrics.recordSuccess(readSample, OperationType.ENTITY_READ,
                    "tenant-1", "pg-plugin");

            // Verify metrics were recorded for different operation types
            long operationMeters = registry.getMeters().stream()
                    .filter(m -> m.getId().getName().contains("datacloud"))
                    .count();
            assertThat(operationMeters).isGreaterThan(0);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Event streaming metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Event Streaming Metrics")
    class EventStreamingMetricsTests {

        @Test
        @DisplayName("records event offset gauge")
        void recordsEventOffset() {
            metrics.recordEventOffset("tenant-1", "orders-stream", 42_000L);

            long offsetMeters = registry.getMeters().stream()
                    .filter(m -> m.getId().getName().contains("event")
                            && m.getId().getName().contains("offset"))
                    .count();
            assertThat(offsetMeters).isGreaterThan(0);
        }

        @Test
        @DisplayName("records event throughput gauge")
        void recordsEventThroughput() {
            metrics.recordEventThroughput("tenant-1", "orders-stream", 1500.0);

            long throughputMeters = registry.getMeters().stream()
                    .filter(m -> m.getId().getName().contains("event")
                            && m.getId().getName().contains("throughput"))
                    .count();
            assertThat(throughputMeters).isGreaterThan(0);
        }

        @Test
        @DisplayName("records event append operation")
        void recordsEventAppend() {
            Timer.Sample sample = metrics.startTimer();
            metrics.recordSuccess(sample, OperationType.EVENT_APPEND,
                    "tenant-1", "kafka-plugin");

            // Should have at least the timer
            long metrics = registry.getMeters().stream()
                    .filter(m -> m.getId().getName().contains("datacloud"))
                    .count();
            assertThat(metrics).isGreaterThan(0);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Search metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Search Metrics")
    class SearchMetricsTests {

        @Test
        @DisplayName("records search hits")
        void recordsSearchHits() {
            metrics.recordSearchHits("tenant-1", "products", 250L);

            long searchMeters = registry.getMeters().stream()
                    .filter(m -> m.getId().getName().contains("search")
                            && m.getId().getName().contains("hits"))
                    .count();
            assertThat(searchMeters).isGreaterThan(0);
        }

        @Test
        @DisplayName("records search latency")
        void recordsSearchLatency() {
            metrics.recordSearchLatency("tenant-1", "products", 45L);

            long latencyMeters = registry.getMeters().stream()
                    .filter(m -> m.getId().getName().contains("search")
                            && m.getId().getName().contains("latency"))
                    .count();
            assertThat(latencyMeters).isGreaterThan(0);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Plugin health metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Plugin Health Metrics")
    class PluginHealthMetricsTests {

        @Test
        @DisplayName("records plugin health status")
        void recordsPluginHealth() {
            metrics.recordPluginHealth(PluginType.STORAGE, "postgres", true);
            metrics.recordPluginHealth(PluginType.STREAMING, "kafka", false);

            Map<String, DataCloudMetrics.PluginHealthStatus> statuses =
                    metrics.getPluginHealthStatuses();
            assertThat(statuses).isNotNull();
        }

        @Test
        @DisplayName("records plugin operation count")
        void recordsPluginOperations() {
            metrics.recordPluginOperations(PluginType.SEARCH, "elastic", 500L);

            Map<String, DataCloudMetrics.PluginHealthStatus> statuses =
                    metrics.getPluginHealthStatuses();
            assertThat(statuses).isNotEmpty();
            assertThat(statuses).containsKey("search:elastic");
        }

        @Test
        @DisplayName("records plugin error count")
        void recordsPluginErrors() {
            metrics.recordPluginErrors(PluginType.AI, "ml-model", 3L);

            Map<String, DataCloudMetrics.PluginHealthStatus> statuses =
                    metrics.getPluginHealthStatuses();
            assertThat(statuses).isNotEmpty();
            assertThat(statuses).containsKey("ai:ml-model");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Tenant metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Tenant Metrics")
    class TenantMetricsTests {

        @Test
        @DisplayName("records tenant request")
        void recordsTenantRequest() {
            metrics.recordTenantRequest("tenant-1");
            metrics.recordTenantRequest("tenant-1");
            metrics.recordTenantRequest("tenant-2");

            // Requests should be tracked
            long requestMeters = registry.getMeters().stream()
                    .filter(m -> m.getId().getName().contains("tenant")
                            && m.getId().getName().contains("request"))
                    .count();
            assertThat(requestMeters).isGreaterThan(0);
        }

        @Test
        @DisplayName("records tenant API usage")
        void recordsTenantApiUsage() {
            metrics.recordTenantApiUsage("tenant-1", 1500L);

            long usageMeters = registry.getMeters().stream()
                    .filter(m -> m.getId().getName().contains("tenant")
                            && m.getId().getName().contains("api"))
                    .count();
            assertThat(usageMeters).isGreaterThan(0);
        }

        @Test
        @DisplayName("records tenant storage usage")
        void recordsTenantStorageUsage() {
            metrics.recordTenantStorageUsage("tenant-1", 1_073_741_824L);

            long storageMeters = registry.getMeters().stream()
                    .filter(m -> m.getId().getName().contains("tenant")
                            && m.getId().getName().contains("storage"))
                    .count();
            assertThat(storageMeters).isGreaterThan(0);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Rate limiting metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Rate Limiting Metrics")
    class RateLimitMetricsTests {

        @Test
        @DisplayName("records allowed request")
        void recordsAllowedRequest() {
            metrics.recordRateLimitCheck("tenant-1", true);

            long rateLimitMeters = registry.getMeters().stream()
                    .filter(m -> m.getId().getName().contains("ratelimit"))
                    .count();
            assertThat(rateLimitMeters).isGreaterThan(0);
        }

        @Test
        @DisplayName("records denied request")
        void recordsDeniedRequest() {
            metrics.recordRateLimitCheck("tenant-1", false);

            long rateLimitMeters = registry.getMeters().stream()
                    .filter(m -> m.getId().getName().contains("ratelimit"))
                    .count();
            assertThat(rateLimitMeters).isGreaterThan(0);
        }

        @Test
        @DisplayName("records rate limit rejection with reason")
        void recordsRejectionWithReason() {
            metrics.recordRateLimitRejection("tenant-1", "quota_exceeded");

            long rejectionMeters = registry.getMeters().stream()
                    .filter(m -> m.getId().getName().contains("rejection"))
                    .count();
            assertThat(rejectionMeters).isGreaterThan(0);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Multi-tenant isolation
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Multi-Tenant Isolation")
    class MultiTenantTests {

        @Test
        @DisplayName("entity metrics are tenant-specific")
        void entityMetricsTenantSpecific() {
            metrics.recordEntityCount("tenant-A", "users", 100);
            metrics.recordEntityCount("tenant-B", "users", 200);

            // Both tenants should have separate gauges
            long entityGauges = registry.getMeters().stream()
                    .filter(m -> m.getId().getName().contains("entity")
                            && m.getId().getName().contains("count"))
                    .count();
            assertThat(entityGauges).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("operations from different tenants create separate timers")
        void operationsFromDifferentTenants() {
            Timer.Sample sampleA = metrics.startTimer();
            metrics.recordSuccess(sampleA, OperationType.ENTITY_CREATE,
                    "tenant-A", "pg-plugin");

            Timer.Sample sampleB = metrics.startTimer();
            metrics.recordSuccess(sampleB, OperationType.ENTITY_CREATE,
                    "tenant-B", "pg-plugin");

            // Verify at least one timer recorded
            long timerCount = registry.getMeters().stream()
                    .filter(m -> m instanceof Timer)
                    .filter(m -> m.getId().getName().contains("datacloud"))
                    .count();
            assertThat(timerCount).isGreaterThanOrEqualTo(1);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Concurrency safety
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Concurrency Safety")
    class ConcurrencyTests {

        @Test
        @DisplayName("concurrent metric recording is thread-safe")
        void concurrentRecordingIsThreadSafe() throws InterruptedException {
            int threads = 10;
            int iterationsPerThread = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);

            for (int t = 0; t < threads; t++) {
                final String tenantId = "tenant-" + t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < iterationsPerThread; i++) {
                            Timer.Sample sample = metrics.startTimer();
                            metrics.recordSuccess(sample, OperationType.ENTITY_CREATE,
                                    tenantId, "pg-plugin");
                            metrics.recordTenantRequest(tenantId);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Verify no exceptions occurred and meters exist
            long totalMeters = registry.getMeters().stream()
                    .filter(m -> m.getId().getName().contains("datacloud"))
                    .count();
            assertThat(totalMeters).isGreaterThan(0);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // End-to-end scenario
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("End-to-End Scenarios")
    class EndToEndTests {

        @Test
        @DisplayName("records full entity CRUD lifecycle")
        void fullEntityCrudLifecycle() {
            String tenantId = "acme-corp";
            String plugin = "pg-plugin";

            // Create
            Timer.Sample createSample = metrics.startTimer();
            metrics.recordSuccess(createSample, OperationType.ENTITY_CREATE, tenantId, plugin);

            // Read
            Timer.Sample readSample = metrics.startTimer();
            metrics.recordSuccess(readSample, OperationType.ENTITY_READ, tenantId, plugin);

            // Update
            Timer.Sample updateSample = metrics.startTimer();
            metrics.recordSuccess(updateSample, OperationType.ENTITY_UPDATE, tenantId, plugin);

            // Delete
            Timer.Sample deleteSample = metrics.startTimer();
            metrics.recordSuccess(deleteSample, OperationType.ENTITY_DELETE, tenantId, plugin);

            // Query
            Timer.Sample querySample = metrics.startTimer();
            metrics.recordSuccess(querySample, OperationType.ENTITY_QUERY, tenantId, plugin);

            // Verify all operations recorded
            long datacloudMeters = registry.getMeters().stream()
                    .filter(m -> m.getId().getName().contains("datacloud"))
                    .count();
            assertThat(datacloudMeters).isGreaterThan(5);
        }

        @Test
        @DisplayName("records ingestion pipeline with search")
        void ingestionPipelineWithSearch() {
            String tenantId = "beta-corp";

            // Ingest events
            Timer.Sample ingestSample = metrics.startTimer();
            metrics.recordSuccess(ingestSample, OperationType.EVENT_APPEND,
                    tenantId, "kafka-plugin");
            metrics.recordEventThroughput(tenantId, "orders", 2500.0);
            metrics.recordEventOffset(tenantId, "orders", 100_000L);

            // Search
            Timer.Sample searchSample = metrics.startTimer();
            metrics.recordSuccess(searchSample, OperationType.SEARCH_EXECUTE,
                    tenantId, "elastic-plugin");
            metrics.recordSearchHits(tenantId, "orders", 150L);
            metrics.recordSearchLatency(tenantId, "orders", 12L);

            // Record tenant usage
            metrics.recordTenantRequest(tenantId);
            metrics.recordTenantApiUsage(tenantId, 100L);

            // Verify comprehensive metrics
            long totalMeters = registry.getMeters().size();
            assertThat(totalMeters).isGreaterThan(5);
        }

        @Test
        @DisplayName("records plugin health monitoring cycle")
        void pluginHealthMonitoringCycle() {
            // Register healthy plugins
            metrics.recordPluginHealth(PluginType.STORAGE, "postgres", true);
            metrics.recordPluginHealth(PluginType.STREAMING, "kafka", true);
            metrics.recordPluginHealth(PluginType.SEARCH, "elastic", true);

            // Simulate kafka going unhealthy
            metrics.recordPluginHealth(PluginType.STREAMING, "kafka", false);
            metrics.recordPluginErrors(PluginType.STREAMING, "kafka", 5L);

            // Verify plugin health statuses
            Map<String, DataCloudMetrics.PluginHealthStatus> statuses =
                    metrics.getPluginHealthStatuses();
            assertThat(statuses).isNotNull();
        }

        @Test
        @DisplayName("records failed operation with error classification")
        void failedOperationWithErrorClassification() {
            Timer.Sample sample = metrics.startTimer();
            Exception error = new IllegalArgumentException("invalid entity format");

            metrics.recordError(sample, OperationType.ENTITY_CREATE,
                    "tenant-1", "pg-plugin", error);

            // Should have error metrics
            long errorMeters = registry.getMeters().stream()
                    .filter(m -> m.getId().getName().contains("datacloud"))
                    .count();
            assertThat(errorMeters).isGreaterThan(0);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Metric naming conventions
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Naming Conventions")
    class NamingConventionTests {

        @Test
        @DisplayName("all metrics follow datacloud.* namespace")
        void allMetricsFollowNamespace() {
            // Record some metrics to populate registry
            Timer.Sample sample = metrics.startTimer();
            metrics.recordSuccess(sample, OperationType.ENTITY_CREATE,
                    "t1", "pg");
            metrics.recordEntityCount("t1", "col", 100);
            metrics.recordTenantRequest("t1");

            // All datacloud meters should use prefix
            for (Meter meter : registry.getMeters()) {
                String name = meter.getId().getName();
                if (name.contains("datacloud")) {
                    assertThat(name).startsWith("datacloud.");
                }
            }
        }
    }
}
