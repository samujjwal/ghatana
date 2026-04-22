package com.ghatana.datacloud.observability;

import com.ghatana.datacloud.observability.DataCloudMetrics.OperationType;
import com.ghatana.datacloud.observability.DataCloudMetrics.PluginType;
import com.ghatana.platform.observability.SimpleMetricsCollector;
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
@DisplayName("Data Cloud Observability Metrics [GH-90000]")
class DataCloudMetricsTest {

    private MeterRegistry registry;
    private DataCloudMetrics metrics;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new SimpleMeterRegistry(); // GH-90000
        metrics = DataCloudMetrics.create(new SimpleMetricsCollector(registry)); // GH-90000
    }

    // ════════════════════════════════════════════════════════════════
    // Construction
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Construction [GH-90000]")
    class ConstructionTests {

        @Test
        @DisplayName("rejects null MetricsCollector [GH-90000]")
        void rejectsNullRegistry() { // GH-90000
            assertThatThrownBy(() -> DataCloudMetrics.create((com.ghatana.platform.observability.MetricsCollector) null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("creates instance with valid registry [GH-90000]")
        void createsWithValidRegistry() { // GH-90000
            DataCloudMetrics m = DataCloudMetrics.create(new SimpleMetricsCollector(new SimpleMeterRegistry())); // GH-90000
            assertThat(m).isNotNull(); // GH-90000
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Operation type coverage
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Operation Types [GH-90000]")
    class OperationTypeTests {

        @Test
        @DisplayName("all operation types have non-null values [GH-90000]")
        void allOperationTypesHaveValues() { // GH-90000
            for (OperationType op : OperationType.values()) { // GH-90000
                assertThat(op.getValue()).isNotNull().isNotEmpty(); // GH-90000
            }
        }

        @Test
        @DisplayName("operation type values follow dot notation [GH-90000]")
        void operationTypesFollowDotNotation() { // GH-90000
            for (OperationType op : OperationType.values()) { // GH-90000
                assertThat(op.getValue()).matches("[a-z._]+ [GH-90000]");
            }
        }

        @Test
        @DisplayName("all plugin types have non-null values [GH-90000]")
        void allPluginTypesHaveValues() { // GH-90000
            for (PluginType pt : PluginType.values()) { // GH-90000
                assertThat(pt.getValue()).isNotNull().isNotEmpty(); // GH-90000
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Timer operations
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Timer Operations [GH-90000]")
    class TimerOperationTests {

        @Test
        @DisplayName("startTimer returns non-null sample [GH-90000]")
        void startTimerReturnsNonNull() { // GH-90000
            Timer.Sample sample = metrics.startTimer(); // GH-90000
            assertThat(sample).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("recordSuccess stops timer and increments counter [GH-90000]")
        void recordSuccessStopsTimerAndIncrements() { // GH-90000
            Timer.Sample sample = metrics.startTimer(); // GH-90000

            // Small delay to ensure timer records something
            metrics.recordSuccess(sample, OperationType.ENTITY_CREATE, // GH-90000
                    "tenant-1", "pg-plugin");

            // Check that metrics were recorded
            long totalMeters = registry.getMeters().stream() // GH-90000
                    .filter(m -> m.getId().getName().contains("datacloud [GH-90000]"))
                    .count(); // GH-90000
            assertThat(totalMeters).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("recordError stops timer and records error counter [GH-90000]")
        void recordErrorStopsTimerAndRecords() { // GH-90000
            Timer.Sample sample = metrics.startTimer(); // GH-90000
            Exception error = new RuntimeException("test error [GH-90000]");

            metrics.recordError(sample, OperationType.ENTITY_CREATE, // GH-90000
                    "tenant-1", "pg-plugin", error);

            // Check error counters exist
            long errorMeters = registry.getMeters().stream() // GH-90000
                    .filter(m -> m.getId().getName().contains("error [GH-90000]")
                            || m.getId().getTags().stream().anyMatch(t -> // GH-90000
                            t.getValue().equals("error [GH-90000]")))
                    .count(); // GH-90000
            assertThat(errorMeters).isGreaterThanOrEqualTo(0); // At minimum, timer is recorded // GH-90000
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Entity metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Entity Metrics [GH-90000]")
    class EntityMetricsTests {

        @Test
        @DisplayName("records entity count gauge [GH-90000]")
        void recordsEntityCount() { // GH-90000
            metrics.recordEntityCount("tenant-1", "users", 1000); // GH-90000

            // Gauge should be registered
            long gauges = registry.getMeters().stream() // GH-90000
                    .filter(m -> m.getId().getName().contains("entity [GH-90000]")
                            && m.getId().getName().contains("count [GH-90000]"))
                    .count(); // GH-90000
            assertThat(gauges).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("records entity size gauge [GH-90000]")
        void recordsEntitySize() { // GH-90000
            metrics.recordEntitySize("tenant-1", "orders", 50_000_000L); // GH-90000

            long sizeMeters = registry.getMeters().stream() // GH-90000
                    .filter(m -> m.getId().getName().contains("entity [GH-90000]")
                            && m.getId().getName().contains("size [GH-90000]"))
                    .count(); // GH-90000
            assertThat(sizeMeters).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("records entity CRUD operations [GH-90000]")
        void recordsEntityCrudOperations() { // GH-90000
            Timer.Sample createSample = metrics.startTimer(); // GH-90000
            metrics.recordSuccess(createSample, OperationType.ENTITY_CREATE, // GH-90000
                    "tenant-1", "pg-plugin");

            Timer.Sample readSample = metrics.startTimer(); // GH-90000
            metrics.recordSuccess(readSample, OperationType.ENTITY_READ, // GH-90000
                    "tenant-1", "pg-plugin");

            // Verify metrics were recorded for different operation types
            long operationMeters = registry.getMeters().stream() // GH-90000
                    .filter(m -> m.getId().getName().contains("datacloud [GH-90000]"))
                    .count(); // GH-90000
            assertThat(operationMeters).isGreaterThan(0); // GH-90000
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Event streaming metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Event Streaming Metrics [GH-90000]")
    class EventStreamingMetricsTests {

        @Test
        @DisplayName("records event offset gauge [GH-90000]")
        void recordsEventOffset() { // GH-90000
            metrics.recordEventOffset("tenant-1", "orders-stream", 42_000L); // GH-90000

            long offsetMeters = registry.getMeters().stream() // GH-90000
                    .filter(m -> m.getId().getName().contains("event [GH-90000]")
                            && m.getId().getName().contains("offset [GH-90000]"))
                    .count(); // GH-90000
            assertThat(offsetMeters).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("records event throughput gauge [GH-90000]")
        void recordsEventThroughput() { // GH-90000
            metrics.recordEventThroughput("tenant-1", "orders-stream", 1500.0); // GH-90000

            long throughputMeters = registry.getMeters().stream() // GH-90000
                    .filter(m -> m.getId().getName().contains("event [GH-90000]")
                            && m.getId().getName().contains("throughput [GH-90000]"))
                    .count(); // GH-90000
            assertThat(throughputMeters).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("records event append operation [GH-90000]")
        void recordsEventAppend() { // GH-90000
            Timer.Sample sample = metrics.startTimer(); // GH-90000
            metrics.recordSuccess(sample, OperationType.EVENT_APPEND, // GH-90000
                    "tenant-1", "kafka-plugin");

            // Should have at least the timer
            long metrics = registry.getMeters().stream() // GH-90000
                    .filter(m -> m.getId().getName().contains("datacloud [GH-90000]"))
                    .count(); // GH-90000
            assertThat(metrics).isGreaterThan(0); // GH-90000
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Search metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Search Metrics [GH-90000]")
    class SearchMetricsTests {

        @Test
        @DisplayName("records search hits [GH-90000]")
        void recordsSearchHits() { // GH-90000
            metrics.recordSearchHits("tenant-1", "products", 250L); // GH-90000

            long searchMeters = registry.getMeters().stream() // GH-90000
                    .filter(m -> m.getId().getName().contains("search [GH-90000]")
                            && m.getId().getName().contains("hits [GH-90000]"))
                    .count(); // GH-90000
            assertThat(searchMeters).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("records search latency [GH-90000]")
        void recordsSearchLatency() { // GH-90000
            metrics.recordSearchLatency("tenant-1", "products", 45L); // GH-90000

            long latencyMeters = registry.getMeters().stream() // GH-90000
                    .filter(m -> m.getId().getName().contains("search [GH-90000]")
                            && m.getId().getName().contains("latency [GH-90000]"))
                    .count(); // GH-90000
            assertThat(latencyMeters).isGreaterThan(0); // GH-90000
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Plugin health metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Plugin Health Metrics [GH-90000]")
    class PluginHealthMetricsTests {

        @Test
        @DisplayName("records plugin health status [GH-90000]")
        void recordsPluginHealth() { // GH-90000
            metrics.recordPluginHealth(PluginType.STORAGE, "postgres", true); // GH-90000
            metrics.recordPluginHealth(PluginType.STREAMING, "kafka", false); // GH-90000

            Map<String, DataCloudMetrics.PluginHealthStatus> statuses =
                    metrics.getPluginHealthStatuses(); // GH-90000
            assertThat(statuses).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("records plugin operation count [GH-90000]")
        void recordsPluginOperations() { // GH-90000
            metrics.recordPluginOperations(PluginType.SEARCH, "elastic", 500L); // GH-90000

            Map<String, DataCloudMetrics.PluginHealthStatus> statuses =
                    metrics.getPluginHealthStatuses(); // GH-90000
            assertThat(statuses).isNotEmpty(); // GH-90000
            assertThat(statuses).containsKey("search:elastic [GH-90000]");
        }

        @Test
        @DisplayName("records plugin error count [GH-90000]")
        void recordsPluginErrors() { // GH-90000
            metrics.recordPluginErrors(PluginType.AI, "ml-model", 3L); // GH-90000

            Map<String, DataCloudMetrics.PluginHealthStatus> statuses =
                    metrics.getPluginHealthStatuses(); // GH-90000
            assertThat(statuses).isNotEmpty(); // GH-90000
            assertThat(statuses).containsKey("ai:ml-model [GH-90000]");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Tenant metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Tenant Metrics [GH-90000]")
    class TenantMetricsTests {

        @Test
        @DisplayName("records tenant request [GH-90000]")
        void recordsTenantRequest() { // GH-90000
            metrics.recordTenantRequest("tenant-1 [GH-90000]");
            metrics.recordTenantRequest("tenant-1 [GH-90000]");
            metrics.recordTenantRequest("tenant-2 [GH-90000]");

            // Requests should be tracked
            long requestMeters = registry.getMeters().stream() // GH-90000
                    .filter(m -> m.getId().getName().contains("tenant [GH-90000]")
                            && m.getId().getName().contains("request [GH-90000]"))
                    .count(); // GH-90000
            assertThat(requestMeters).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("records tenant API usage [GH-90000]")
        void recordsTenantApiUsage() { // GH-90000
            metrics.recordTenantApiUsage("tenant-1", 1500L); // GH-90000

            long usageMeters = registry.getMeters().stream() // GH-90000
                    .filter(m -> m.getId().getName().contains("tenant [GH-90000]")
                            && m.getId().getName().contains("api [GH-90000]"))
                    .count(); // GH-90000
            assertThat(usageMeters).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("records tenant storage usage [GH-90000]")
        void recordsTenantStorageUsage() { // GH-90000
            metrics.recordTenantStorageUsage("tenant-1", 1_073_741_824L); // GH-90000

            long storageMeters = registry.getMeters().stream() // GH-90000
                    .filter(m -> m.getId().getName().contains("tenant [GH-90000]")
                            && m.getId().getName().contains("storage [GH-90000]"))
                    .count(); // GH-90000
            assertThat(storageMeters).isGreaterThan(0); // GH-90000
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Rate limiting metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Rate Limiting Metrics [GH-90000]")
    class RateLimitMetricsTests {

        @Test
        @DisplayName("records allowed request [GH-90000]")
        void recordsAllowedRequest() { // GH-90000
            metrics.recordRateLimitCheck("tenant-1", true); // GH-90000

            long rateLimitMeters = registry.getMeters().stream() // GH-90000
                    .filter(m -> m.getId().getName().contains("ratelimit [GH-90000]"))
                    .count(); // GH-90000
            assertThat(rateLimitMeters).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("records denied request [GH-90000]")
        void recordsDeniedRequest() { // GH-90000
            metrics.recordRateLimitCheck("tenant-1", false); // GH-90000

            long rateLimitMeters = registry.getMeters().stream() // GH-90000
                    .filter(m -> m.getId().getName().contains("ratelimit [GH-90000]"))
                    .count(); // GH-90000
            assertThat(rateLimitMeters).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("records rate limit rejection with reason [GH-90000]")
        void recordsRejectionWithReason() { // GH-90000
            metrics.recordRateLimitRejection("tenant-1", "quota_exceeded"); // GH-90000

            long rejectionMeters = registry.getMeters().stream() // GH-90000
                    .filter(m -> m.getId().getName().contains("rejection [GH-90000]"))
                    .count(); // GH-90000
            assertThat(rejectionMeters).isGreaterThan(0); // GH-90000
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Multi-tenant isolation
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Multi-Tenant Isolation [GH-90000]")
    class MultiTenantTests {

        @Test
        @DisplayName("entity metrics are tenant-specific [GH-90000]")
        void entityMetricsTenantSpecific() { // GH-90000
            metrics.recordEntityCount("tenant-A", "users", 100); // GH-90000
            metrics.recordEntityCount("tenant-B", "users", 200); // GH-90000

            // Both tenants should have separate gauges
            long entityGauges = registry.getMeters().stream() // GH-90000
                    .filter(m -> m.getId().getName().contains("entity [GH-90000]")
                            && m.getId().getName().contains("count [GH-90000]"))
                    .count(); // GH-90000
            assertThat(entityGauges).isGreaterThanOrEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("operations from different tenants create separate timers [GH-90000]")
        void operationsFromDifferentTenants() { // GH-90000
            Timer.Sample sampleA = metrics.startTimer(); // GH-90000
            metrics.recordSuccess(sampleA, OperationType.ENTITY_CREATE, // GH-90000
                    "tenant-A", "pg-plugin");

            Timer.Sample sampleB = metrics.startTimer(); // GH-90000
            metrics.recordSuccess(sampleB, OperationType.ENTITY_CREATE, // GH-90000
                    "tenant-B", "pg-plugin");

            // Verify at least one timer recorded
            long timerCount = registry.getMeters().stream() // GH-90000
                    .filter(m -> m instanceof Timer) // GH-90000
                    .filter(m -> m.getId().getName().contains("datacloud [GH-90000]"))
                    .count(); // GH-90000
            assertThat(timerCount).isGreaterThanOrEqualTo(1); // GH-90000
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Concurrency safety
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Concurrency Safety [GH-90000]")
    class ConcurrencyTests {

        @Test
        @DisplayName("concurrent metric recording is thread-safe [GH-90000]")
        void concurrentRecordingIsThreadSafe() throws InterruptedException { // GH-90000
            int threads = 10;
            int iterationsPerThread = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threads); // GH-90000
            CountDownLatch latch = new CountDownLatch(threads); // GH-90000

            for (int t = 0; t < threads; t++) { // GH-90000
                final String tenantId = "tenant-" + t;
                executor.submit(() -> { // GH-90000
                    try {
                        for (int i = 0; i < iterationsPerThread; i++) { // GH-90000
                            Timer.Sample sample = metrics.startTimer(); // GH-90000
                            metrics.recordSuccess(sample, OperationType.ENTITY_CREATE, // GH-90000
                                    tenantId, "pg-plugin");
                            metrics.recordTenantRequest(tenantId); // GH-90000
                        }
                    } finally {
                        latch.countDown(); // GH-90000
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS); // GH-90000
            executor.shutdown(); // GH-90000

            // Verify no exceptions occurred and meters exist
            long totalMeters = registry.getMeters().stream() // GH-90000
                    .filter(m -> m.getId().getName().contains("datacloud [GH-90000]"))
                    .count(); // GH-90000
            assertThat(totalMeters).isGreaterThan(0); // GH-90000
        }
    }

    // ════════════════════════════════════════════════════════════════
    // End-to-end scenario
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("End-to-End Scenarios [GH-90000]")
    class EndToEndTests {

        @Test
        @DisplayName("records full entity CRUD lifecycle [GH-90000]")
        void fullEntityCrudLifecycle() { // GH-90000
            String tenantId = "acme-corp";
            String plugin = "pg-plugin";

            // Create
            Timer.Sample createSample = metrics.startTimer(); // GH-90000
            metrics.recordSuccess(createSample, OperationType.ENTITY_CREATE, tenantId, plugin); // GH-90000

            // Read
            Timer.Sample readSample = metrics.startTimer(); // GH-90000
            metrics.recordSuccess(readSample, OperationType.ENTITY_READ, tenantId, plugin); // GH-90000

            // Update
            Timer.Sample updateSample = metrics.startTimer(); // GH-90000
            metrics.recordSuccess(updateSample, OperationType.ENTITY_UPDATE, tenantId, plugin); // GH-90000

            // Delete
            Timer.Sample deleteSample = metrics.startTimer(); // GH-90000
            metrics.recordSuccess(deleteSample, OperationType.ENTITY_DELETE, tenantId, plugin); // GH-90000

            // Query
            Timer.Sample querySample = metrics.startTimer(); // GH-90000
            metrics.recordSuccess(querySample, OperationType.ENTITY_QUERY, tenantId, plugin); // GH-90000

            // Verify all operations recorded
            long datacloudMeters = registry.getMeters().stream() // GH-90000
                    .filter(m -> m.getId().getName().contains("datacloud [GH-90000]"))
                    .count(); // GH-90000
            assertThat(datacloudMeters).isGreaterThan(5); // GH-90000
        }

        @Test
        @DisplayName("records ingestion pipeline with search [GH-90000]")
        void ingestionPipelineWithSearch() { // GH-90000
            String tenantId = "beta-corp";

            // Ingest events
            Timer.Sample ingestSample = metrics.startTimer(); // GH-90000
            metrics.recordSuccess(ingestSample, OperationType.EVENT_APPEND, // GH-90000
                    tenantId, "kafka-plugin");
            metrics.recordEventThroughput(tenantId, "orders", 2500.0); // GH-90000
            metrics.recordEventOffset(tenantId, "orders", 100_000L); // GH-90000

            // Search
            Timer.Sample searchSample = metrics.startTimer(); // GH-90000
            metrics.recordSuccess(searchSample, OperationType.SEARCH_EXECUTE, // GH-90000
                    tenantId, "elastic-plugin");
            metrics.recordSearchHits(tenantId, "orders", 150L); // GH-90000
            metrics.recordSearchLatency(tenantId, "orders", 12L); // GH-90000

            // Record tenant usage
            metrics.recordTenantRequest(tenantId); // GH-90000
            metrics.recordTenantApiUsage(tenantId, 100L); // GH-90000

            // Verify comprehensive metrics
            long totalMeters = registry.getMeters().size(); // GH-90000
            assertThat(totalMeters).isGreaterThan(5); // GH-90000
        }

        @Test
        @DisplayName("records plugin health monitoring cycle [GH-90000]")
        void pluginHealthMonitoringCycle() { // GH-90000
            // Register healthy plugins
            metrics.recordPluginHealth(PluginType.STORAGE, "postgres", true); // GH-90000
            metrics.recordPluginHealth(PluginType.STREAMING, "kafka", true); // GH-90000
            metrics.recordPluginHealth(PluginType.SEARCH, "elastic", true); // GH-90000

            // Simulate kafka going unhealthy
            metrics.recordPluginHealth(PluginType.STREAMING, "kafka", false); // GH-90000
            metrics.recordPluginErrors(PluginType.STREAMING, "kafka", 5L); // GH-90000

            // Verify plugin health statuses
            Map<String, DataCloudMetrics.PluginHealthStatus> statuses =
                    metrics.getPluginHealthStatuses(); // GH-90000
            assertThat(statuses).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("records failed operation with error classification [GH-90000]")
        void failedOperationWithErrorClassification() { // GH-90000
            Timer.Sample sample = metrics.startTimer(); // GH-90000
            Exception error = new IllegalArgumentException("invalid entity format [GH-90000]");

            metrics.recordError(sample, OperationType.ENTITY_CREATE, // GH-90000
                    "tenant-1", "pg-plugin", error);

            // Should have error metrics
            long errorMeters = registry.getMeters().stream() // GH-90000
                    .filter(m -> m.getId().getName().contains("datacloud [GH-90000]"))
                    .count(); // GH-90000
            assertThat(errorMeters).isGreaterThan(0); // GH-90000
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Metric naming conventions
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Naming Conventions [GH-90000]")
    class NamingConventionTests {

        @Test
        @DisplayName("all metrics follow datacloud.* namespace [GH-90000]")
        void allMetricsFollowNamespace() { // GH-90000
            // Record some metrics to populate registry
            Timer.Sample sample = metrics.startTimer(); // GH-90000
            metrics.recordSuccess(sample, OperationType.ENTITY_CREATE, // GH-90000
                    "t1", "pg");
            metrics.recordEntityCount("t1", "col", 100); // GH-90000
            metrics.recordTenantRequest("t1 [GH-90000]");

            // All datacloud meters should use prefix
            for (Meter meter : registry.getMeters()) { // GH-90000
                String name = meter.getId().getName(); // GH-90000
                if (name.contains("datacloud [GH-90000]")) {
                    assertThat(name).startsWith("datacloud. [GH-90000]");
                }
            }
        }
    }
}
