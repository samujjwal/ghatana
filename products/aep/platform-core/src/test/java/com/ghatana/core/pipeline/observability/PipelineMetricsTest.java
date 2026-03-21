package com.ghatana.core.pipeline.observability;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.SimpleMetricsCollector;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive tests for PipelineMetrics observability facade.
 *
 * <p>Validates that all pipeline metrics are correctly recorded to the
 * underlying MeterRegistry through the MetricsCollector abstraction.</p>
 *
 * @doc.type test
 * @doc.purpose Validate pipeline observability metrics contract
 * @doc.layer core
 */
@DisplayName("Pipeline Observability Metrics")
class PipelineMetricsTest {

    private MeterRegistry registry;
    private MetricsCollector collector;
    private PipelineMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        collector = new SimpleMetricsCollector(registry);
        metrics = new PipelineMetrics(collector);
    }

    // ════════════════════════════════════════════════════════════════
    // Construction
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("rejects null MetricsCollector")
        void rejectsNullCollector() {
            assertThatThrownBy(() -> new PipelineMetrics(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("MetricsCollector");
        }

        @Test
        @DisplayName("exposes underlying collector")
        void exposesCollector() {
            assertThat(metrics.getCollector()).isSameAs(collector);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Execution metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Execution Metrics")
    class ExecutionMetricsTests {

        @Test
        @DisplayName("records execution started counter")
        void recordsExecutionStarted() {
            metrics.recordExecutionStarted("pipeline-1", "tenant-A");

            Counter counter = registry.find(PipelineMetrics.EXECUTION_COUNT)
                    .tag("pipeline_id", "pipeline-1")
                    .tag("tenant_id", "tenant-A")
                    .tag("status", "started")
                    .counter();

            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("records execution completed with duration")
        void recordsExecutionCompleted() {
            metrics.recordExecutionCompleted("pipeline-1", "tenant-A", 150);

            // Verify success counter
            Counter counter = registry.find(PipelineMetrics.EXECUTION_COUNT)
                    .tag("status", "success")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);

            // Verify timer
            Timer timer = registry.find(PipelineMetrics.EXECUTION_DURATION_MS)
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("records execution failed with error type")
        void recordsExecutionFailed() {
            metrics.recordExecutionFailed("pipeline-1", "tenant-A", "VALIDATION", 50);

            Counter errCounter = registry.find(PipelineMetrics.EXECUTION_ERRORS)
                    .tag("error_type", "VALIDATION")
                    .counter();
            assertThat(errCounter).isNotNull();
            assertThat(errCounter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("multiple executions accumulate")
        void multipleExecutionsAccumulate() {
            metrics.recordExecutionStarted("p1", "t1");
            metrics.recordExecutionStarted("p1", "t1");
            metrics.recordExecutionStarted("p1", "t1");

            Counter counter = registry.find(PipelineMetrics.EXECUTION_COUNT)
                    .tag("status", "started")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(3.0);
        }

        @Test
        @DisplayName("different pipelines create separate counters")
        void differentPipelinesSeparateCounters() {
            metrics.recordExecutionStarted("pipeline-A", "t1");
            metrics.recordExecutionStarted("pipeline-B", "t1");

            Counter counterA = registry.find(PipelineMetrics.EXECUTION_COUNT)
                    .tag("pipeline_id", "pipeline-A")
                    .counter();
            Counter counterB = registry.find(PipelineMetrics.EXECUTION_COUNT)
                    .tag("pipeline_id", "pipeline-B")
                    .counter();

            assertThat(counterA.count()).isEqualTo(1.0);
            assertThat(counterB.count()).isEqualTo(1.0);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Stage metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Stage Metrics")
    class StageMetricsTests {

        @Test
        @DisplayName("records successful stage execution")
        void recordsSuccessfulStage() {
            metrics.recordStageExecution("p1", "stage-1", 25, true);

            Counter counter = registry.find(PipelineMetrics.STAGE_COUNT)
                    .tag("stage_id", "stage-1")
                    .tag("status", "success")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);

            Timer timer = registry.find(PipelineMetrics.STAGE_DURATION_MS)
                    .tag("stage_id", "stage-1")
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("records failed stage with error counter")
        void recordsFailedStage() {
            metrics.recordStageExecution("p1", "stage-2", 10, false);

            Counter errorCounter = registry.find(PipelineMetrics.STAGE_ERRORS)
                    .tag("stage_id", "stage-2")
                    .counter();
            assertThat(errorCounter).isNotNull();
            assertThat(errorCounter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("does not record error counter for successful stage")
        void noErrorCounterForSuccess() {
            metrics.recordStageExecution("p1", "stage-1", 25, true);

            Counter errorCounter = registry.find(PipelineMetrics.STAGE_ERRORS)
                    .tag("stage_id", "stage-1")
                    .counter();
            assertThat(errorCounter).isNull();
        }

        @Test
        @DisplayName("records skipped stage with reason")
        void recordsSkippedStage() {
            metrics.recordStageSkipped("p1", "stage-3", "deadline_exceeded");

            Counter counter = registry.find(PipelineMetrics.STAGE_SKIPPED)
                    .tag("stage_id", "stage-3")
                    .tag("reason", "deadline_exceeded")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Validation metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Validation Metrics")
    class ValidationMetricsTests {

        @Test
        @DisplayName("records successful validation")
        void recordsSuccessfulValidation() {
            metrics.recordValidation("p1", true);

            Counter counter = registry.find(PipelineMetrics.VALIDATION_COUNT)
                    .tag("status", "valid")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);

            // No failure counter for valid pipeline
            Counter failures = registry.find(PipelineMetrics.VALIDATION_FAILURES)
                    .counter();
            assertThat(failures).isNull();
        }

        @Test
        @DisplayName("records failed validation with failure counter")
        void recordsFailedValidation() {
            metrics.recordValidation("p1", false);

            Counter counter = registry.find(PipelineMetrics.VALIDATION_COUNT)
                    .tag("status", "invalid")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);

            Counter failures = registry.find(PipelineMetrics.VALIDATION_FAILURES)
                    .counter();
            assertThat(failures).isNotNull();
            assertThat(failures.count()).isEqualTo(1.0);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Event throughput metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Event Throughput Metrics")
    class EventThroughputMetricsTests {

        @Test
        @DisplayName("records events processed by stage")
        void recordsEventsProcessed() {
            metrics.recordEventsProcessed("p1", "stage-1", 5);

            Counter counter = registry.find(PipelineMetrics.EVENTS_PROCESSED)
                    .tag("pipeline_id", "p1")
                    .tag("stage_id", "stage-1")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("records events emitted by stage")
        void recordsEventsEmitted() {
            metrics.recordEventsEmitted("p1", "stage-1", 3);

            Counter counter = registry.find(PipelineMetrics.EVENTS_EMITTED)
                    .tag("pipeline_id", "p1")
                    .tag("stage_id", "stage-1")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(3.0);
        }

        @Test
        @DisplayName("accumulates events across multiple calls")
        void accumulatesEvents() {
            metrics.recordEventsProcessed("p1", "s1", 10);
            metrics.recordEventsProcessed("p1", "s1", 20);

            Counter counter = registry.find(PipelineMetrics.EVENTS_PROCESSED)
                    .tag("stage_id", "s1")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(30.0);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Checkpoint metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Checkpoint Metrics")
    class CheckpointMetricsTests {

        @Test
        @DisplayName("records checkpoint saved")
        void recordsCheckpointSaved() {
            metrics.recordCheckpointSaved("p1", "t1");

            Counter counter = registry.find(PipelineMetrics.CHECKPOINT_SAVED)
                    .tag("pipeline_id", "p1")
                    .tag("tenant_id", "t1")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("records checkpoint restored")
        void recordsCheckpointRestored() {
            metrics.recordCheckpointRestored("p1", "t1");

            Counter counter = registry.find(PipelineMetrics.CHECKPOINT_RESTORED)
                    .tag("pipeline_id", "p1")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("records checkpoint error with type")
        void recordsCheckpointError() {
            metrics.recordCheckpointError("p1", "IO_FAILURE");

            Counter counter = registry.find(PipelineMetrics.CHECKPOINT_ERRORS)
                    .tag("error_type", "IO_FAILURE")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Multi-tenant isolation
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Multi-Tenant Isolation")
    class MultiTenantTests {

        @Test
        @DisplayName("metrics from different tenants are isolated")
        void metricsIsolatedByTenant() {
            metrics.recordExecutionStarted("p1", "tenant-A");
            metrics.recordExecutionStarted("p1", "tenant-A");
            metrics.recordExecutionStarted("p1", "tenant-B");

            Counter tenantA = registry.find(PipelineMetrics.EXECUTION_COUNT)
                    .tag("tenant_id", "tenant-A")
                    .counter();
            Counter tenantB = registry.find(PipelineMetrics.EXECUTION_COUNT)
                    .tag("tenant_id", "tenant-B")
                    .counter();

            assertThat(tenantA.count()).isEqualTo(2.0);
            assertThat(tenantB.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("completed and failed metrics isolated by tenant")
        void completedFailedIsolatedByTenant() {
            metrics.recordExecutionCompleted("p1", "tenant-A", 100);
            metrics.recordExecutionFailed("p1", "tenant-B", "TIMEOUT", 50);

            Counter successA = registry.find(PipelineMetrics.EXECUTION_COUNT)
                    .tag("tenant_id", "tenant-A")
                    .tag("status", "success")
                    .counter();
            Counter errorsB = registry.find(PipelineMetrics.EXECUTION_ERRORS)
                    .tag("tenant_id", "tenant-B")
                    .counter();

            assertThat(successA.count()).isEqualTo(1.0);
            assertThat(errorsB.count()).isEqualTo(1.0);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Metric naming conventions
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Naming Conventions")
    class NamingConventionTests {

        @Test
        @DisplayName("all constant names follow pipeline.* namespace")
        void allMetricsFollowNamespace() {
            assertThat(PipelineMetrics.EXECUTION_COUNT).startsWith("pipeline.");
            assertThat(PipelineMetrics.EXECUTION_DURATION_MS).startsWith("pipeline.");
            assertThat(PipelineMetrics.EXECUTION_ERRORS).startsWith("pipeline.");
            assertThat(PipelineMetrics.STAGE_COUNT).startsWith("pipeline.");
            assertThat(PipelineMetrics.STAGE_DURATION_MS).startsWith("pipeline.");
            assertThat(PipelineMetrics.STAGE_ERRORS).startsWith("pipeline.");
            assertThat(PipelineMetrics.STAGE_SKIPPED).startsWith("pipeline.");
            assertThat(PipelineMetrics.VALIDATION_COUNT).startsWith("pipeline.");
            assertThat(PipelineMetrics.VALIDATION_FAILURES).startsWith("pipeline.");
            assertThat(PipelineMetrics.EVENTS_PROCESSED).startsWith("pipeline.");
            assertThat(PipelineMetrics.EVENTS_EMITTED).startsWith("pipeline.");
            assertThat(PipelineMetrics.CHECKPOINT_SAVED).startsWith("pipeline.");
            assertThat(PipelineMetrics.CHECKPOINT_RESTORED).startsWith("pipeline.");
            assertThat(PipelineMetrics.CHECKPOINT_ERRORS).startsWith("pipeline.");
        }

        @Test
        @DisplayName("tag keys use snake_case")
        void tagKeysUseSnakeCase() {
            assertThat(PipelineMetrics.TAG_PIPELINE_ID).matches("[a-z_]+");
            assertThat(PipelineMetrics.TAG_TENANT_ID).matches("[a-z_]+");
            assertThat(PipelineMetrics.TAG_STAGE_ID).matches("[a-z_]+");
            assertThat(PipelineMetrics.TAG_STATUS).matches("[a-z_]+");
            assertThat(PipelineMetrics.TAG_ERROR_TYPE).matches("[a-z_]+");
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
            int iterationsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);

            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < iterationsPerThread; i++) {
                            metrics.recordExecutionStarted("p1", "t" + threadId);
                            metrics.recordStageExecution("p1", "s1", 10, true);
                            metrics.recordEventsProcessed("p1", "s1", 1);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Total events processed = threads * iterationsPerThread * 1
            // Using >= because counter accumulation across threads should see all writes
            double totalEvents = 0;
            for (Counter c : registry.find(PipelineMetrics.EVENTS_PROCESSED).counters()) {
                totalEvents += c.count();
            }
            assertThat(totalEvents).isEqualTo((double) threads * iterationsPerThread);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // End-to-end scenario
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("End-to-End Scenarios")
    class EndToEndTests {

        @Test
        @DisplayName("records full pipeline execution lifecycle metrics")
        void fullPipelineLifecycle() {
            String pipelineId = "order-pipeline";
            String tenantId = "acme-corp";

            // Validation
            metrics.recordValidation(pipelineId, true);

            // Execution start
            metrics.recordExecutionStarted(pipelineId, tenantId);

            // Stage 1: success
            metrics.recordStageExecution(pipelineId, "validate", 10, true);
            metrics.recordEventsProcessed(pipelineId, "validate", 1);
            metrics.recordEventsEmitted(pipelineId, "validate", 1);

            // Stage 2: success
            metrics.recordStageExecution(pipelineId, "transform", 25, true);
            metrics.recordEventsProcessed(pipelineId, "transform", 1);
            metrics.recordEventsEmitted(pipelineId, "transform", 3);

            // Stage 3: success
            metrics.recordStageExecution(pipelineId, "enrich", 50, true);
            metrics.recordEventsProcessed(pipelineId, "enrich", 3);
            metrics.recordEventsEmitted(pipelineId, "enrich", 3);

            // Checkpoint
            metrics.recordCheckpointSaved(pipelineId, tenantId);

            // Execution complete
            metrics.recordExecutionCompleted(pipelineId, tenantId, 95);

            // Verify aggregate metrics
            assertThat(registry.find(PipelineMetrics.VALIDATION_COUNT)
                    .tag("status", "valid").counter().count()).isEqualTo(1.0);

            assertThat(registry.find(PipelineMetrics.EXECUTION_COUNT)
                    .tag("status", "started").counter().count()).isEqualTo(1.0);

            assertThat(registry.find(PipelineMetrics.EXECUTION_COUNT)
                    .tag("status", "success").counter().count()).isEqualTo(1.0);

            // 3 stages executed
            double totalStages = 0;
            for (Counter c : registry.find(PipelineMetrics.STAGE_COUNT).counters()) {
                totalStages += c.count();
            }
            assertThat(totalStages).isEqualTo(3.0);

            // Total events: 1 + 1 + 3 = 5
            double totalProcessed = 0;
            for (Counter c : registry.find(PipelineMetrics.EVENTS_PROCESSED).counters()) {
                totalProcessed += c.count();
            }
            assertThat(totalProcessed).isEqualTo(5.0);

            // Total emitted: 1 + 3 + 3 = 7
            double totalEmitted = 0;
            for (Counter c : registry.find(PipelineMetrics.EVENTS_EMITTED).counters()) {
                totalEmitted += c.count();
            }
            assertThat(totalEmitted).isEqualTo(7.0);

            // Checkpoint saved
            assertThat(registry.find(PipelineMetrics.CHECKPOINT_SAVED)
                    .counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("records failed pipeline with partial stage completion")
        void failedPipelinePartialCompletion() {
            String pipelineId = "ingestion-pipeline";
            String tenantId = "beta-corp";

            metrics.recordExecutionStarted(pipelineId, tenantId);

            // Stage 1: success
            metrics.recordStageExecution(pipelineId, "parse", 15, true);

            // Stage 2: failure
            metrics.recordStageExecution(pipelineId, "validate", 5, false);

            // Stage 3: skipped
            metrics.recordStageSkipped(pipelineId, "persist", "upstream_failure");

            // Pipeline failed
            metrics.recordExecutionFailed(pipelineId, tenantId, "STAGE_FAILURE", 25);

            // Verify error count
            assertThat(registry.find(PipelineMetrics.EXECUTION_ERRORS)
                    .tag("error_type", "STAGE_FAILURE").counter().count())
                    .isEqualTo(1.0);

            // Verify skipped stage
            assertThat(registry.find(PipelineMetrics.STAGE_SKIPPED)
                    .tag("reason", "upstream_failure").counter().count())
                    .isEqualTo(1.0);

            // Verify stage error
            assertThat(registry.find(PipelineMetrics.STAGE_ERRORS)
                    .tag("stage_id", "validate").counter().count())
                    .isEqualTo(1.0);
        }
    }
}
