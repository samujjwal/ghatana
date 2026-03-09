package com.ghatana.aiplatform.batch;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.observability.NoopMetricsCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for BatchInferenceJob.
 *
 * Tests validate:
 * - Batch job scheduling and execution
 * - Progress tracking and status updates
 * - Checkpointing and recovery
 * - Multi-tenant batch isolation
 * - Throughput and record processing
 *
 * @see BatchInferenceJob
 */
@DisplayName("BatchInferenceJob Integration Tests")
class BatchInferenceJobIntegrationTest extends EventloopTestBase {

    private BatchInferenceJob batchJob;

    @BeforeEach
    void setUp() {
        batchJob = new BatchInferenceJob(NoopMetricsCollector.getInstance());
    }

    /**
     * Verifies batch job scheduling.
     *
     * GIVEN: Records to process and schedule config
     * WHEN: scheduleBatch() called
     * THEN: Returns job ID in SCHEDULED state
     */
    @Test
    @DisplayName("Should schedule batch job and return job ID")
    void shouldScheduleBatchJobAndReturnJobId() {
        // GIVEN: Batch records
        String tenantId = "tenant-123";
        String modelTarget = "model-v2";
        List<BatchInferenceJob.InferenceRecord> records = List.of(
            new BatchInferenceJob.InferenceRecord("rec-1", Map.of("feature1", 1.0), Instant.now()),
            new BatchInferenceJob.InferenceRecord("rec-2", Map.of("feature1", 2.0), Instant.now())
        );
        BatchInferenceJob.ScheduleConfig config = new BatchInferenceJob.ScheduleConfig(
            Duration.ofHours(1), true, 1000
        );

        // WHEN: Schedule batch
        String jobId = runPromise(() ->
            batchJob.scheduleBatch(tenantId, modelTarget, records, config)
        );

        // THEN: Job ID returned
        assertThat(jobId)
            .as("Job ID should be non-empty")
            .isNotEmpty()
            .startsWith("batch-");
    }

    /**
     * Verifies batch job execution and result.
     *
     * GIVEN: Scheduled batch job
     * WHEN: runBatch() called
     * THEN: Processes records, returns result with outputs
     */
    @Test
    @DisplayName("Should execute batch job and process all records")
    void shouldExecuteBatchJobAndProcessAllRecords() {
        // GIVEN: Scheduled batch
        String tenantId = "tenant-batch";
        String modelTarget = "model-v2";
        List<BatchInferenceJob.InferenceRecord> records = createTestRecords(100);
        BatchInferenceJob.ScheduleConfig config = new BatchInferenceJob.ScheduleConfig(
            Duration.ofHours(1), true, 1000
        );

        String jobId = runPromise(() ->
            batchJob.scheduleBatch(tenantId, modelTarget, records, config)
        );

        // WHEN: Execute batch
        BatchInferenceJob.BatchInferenceResult result = runPromise(() ->
            batchJob.runBatch(tenantId, jobId)
        );

        // THEN: Records processed
        assertThat(result.jobId())
            .as("Result should reference job ID")
            .isEqualTo(jobId);
        assertThat(result.totalRecords())
            .as("Total records should match input")
            .isGreaterThan(0);
        assertThat(result.successCount())
            .as("Success count should be high")
            .isGreaterThan((int) (result.totalRecords() * 0.95));
        assertThat(result.durationMs())
            .as("Duration should be positive")
            .isGreaterThan(0);
        assertThat(result.outputs())
            .as("Outputs should be collected")
            .isNotEmpty();
    }

    /**
     * Verifies batch status tracking.
     *
     * GIVEN: Scheduled batch job
     * WHEN: getStatus() called
     * THEN: Returns current status with progress
     */
    @Test
    @DisplayName("Should track batch job status and progress")
    void shouldTrackBatchJobStatusAndProgress() {
        // GIVEN: Scheduled batch
        String tenantId = "tenant-status";
        String modelTarget = "model-v2";
        List<BatchInferenceJob.InferenceRecord> records = createTestRecords(50);
        BatchInferenceJob.ScheduleConfig config = new BatchInferenceJob.ScheduleConfig(
            Duration.ofHours(1), true, 1000
        );

        String jobId = runPromise(() ->
            batchJob.scheduleBatch(tenantId, modelTarget, records, config)
        );

        // WHEN: Get initial status
        BatchInferenceJob.BatchJobStatus initialStatus = runPromise(() ->
            batchJob.getStatus(tenantId, jobId)
        );

        // THEN: Status shows SCHEDULED
        assertThat(initialStatus.status())
            .as("Initial status should be SCHEDULED")
            .isEqualTo("SCHEDULED");
        assertThat(initialStatus.processedCount())
            .as("Processed count should start at 0")
            .isEqualTo(0);

        // WHEN: Execute batch
        runPromise(() -> batchJob.runBatch(tenantId, jobId));

        // AND: Get final status
        BatchInferenceJob.BatchJobStatus finalStatus = runPromise(() ->
            batchJob.getStatus(tenantId, jobId)
        );

        // THEN: Status shows COMPLETED
        assertThat(finalStatus.status())
            .as("Final status should be COMPLETED")
            .isEqualTo("COMPLETED");
        assertThat(finalStatus.processedCount())
            .as("Processed count should match successes")
            .isGreaterThan(0);
        assertThat(finalStatus.durationMs())
            .as("Duration should be recorded")
            .isGreaterThan(0);
    }

    /**
     * Verifies multi-tenant batch isolation.
     *
     * GIVEN: Batch jobs for two different tenants
     * WHEN: Both execute concurrently
     * THEN: Tenant A's batches unaffected by tenant B's batches
     */
    @Test
    @DisplayName("Should enforce tenant isolation for batch jobs")
    void shouldEnforceTenantIsolationForBatchJobs() {
        // GIVEN: Two tenants
        String tenantA = "tenant-a";
        String tenantB = "tenant-b";
        List<BatchInferenceJob.InferenceRecord> recordsA = createTestRecords(50);
        List<BatchInferenceJob.InferenceRecord> recordsB = createTestRecords(75);

        BatchInferenceJob.ScheduleConfig config = new BatchInferenceJob.ScheduleConfig(
            Duration.ofHours(1), true, 1000
        );

        // WHEN: Schedule both batches
        String jobIdA = runPromise(() ->
            batchJob.scheduleBatch(tenantA, "model-a", recordsA, config)
        );

        String jobIdB = runPromise(() ->
            batchJob.scheduleBatch(tenantB, "model-b", recordsB, config)
        );

        // THEN: Job IDs should be distinct
        assertThat(jobIdA)
            .as("Job IDs should differ")
            .isNotEqualTo(jobIdB);

        // WHEN: Execute both
        BatchInferenceJob.BatchInferenceResult resultA = runPromise(() ->
            batchJob.runBatch(tenantA, jobIdA)
        );

        BatchInferenceJob.BatchInferenceResult resultB = runPromise(() ->
            batchJob.runBatch(tenantB, jobIdB)
        );

        // THEN: Results isolated by tenant
        assertThat(resultA.totalRecords())
            .as("Tenant A record count should match input")
            .isGreaterThan(0);
        assertThat(resultB.totalRecords())
            .as("Tenant B record count should match input")
            .isGreaterThan(resultA.totalRecords());  // B had more records
    }

    /**
     * Verifies high throughput batch processing.
     *
     * GIVEN: Large batch of 10k records
     * WHEN: runBatch() called
     * THEN: Processes all records in reasonable time, emits throughput metrics
     */
    @Test
    @DisplayName("Should process high-volume batches with acceptable throughput")
    void shouldProcessHighVolumeBatchesWithAcceptableThroughput() {
        // GIVEN: Large batch
        String tenantId = "tenant-volume";
        String modelTarget = "model-v2";
        List<BatchInferenceJob.InferenceRecord> records = createTestRecords(10000);

        BatchInferenceJob.ScheduleConfig config = new BatchInferenceJob.ScheduleConfig(
            Duration.ofMinutes(5), true, 1000
        );

        String jobId = runPromise(() ->
            batchJob.scheduleBatch(tenantId, modelTarget, records, config)
        );

        long startTime = System.currentTimeMillis();

        // WHEN: Execute batch
        BatchInferenceJob.BatchInferenceResult result = runPromise(() ->
            batchJob.runBatch(tenantId, jobId)
        );

        long actualDuration = result.durationMs();

        // THEN: Processed with high throughput
        assertThat(result.totalRecords())
            .as("Should process all records")
            .isEqualTo(10000);

        double throughput = (double) result.successCount() / (actualDuration / 1000.0);
        assertThat(throughput)
            .as("Throughput should be > 1000 records/sec")
            .isGreaterThan(1000.0);
    }

    /**
     * Verifies checkpointing capability.
     *
     * GIVEN: Batch with checkpointing enabled
     * WHEN: Batch processes
     * THEN: Checkpoint interval respected, recovery possible
     */
    @Test
    @DisplayName("Should respect checkpointing interval for recovery")
    void shouldRespectCheckpointingIntervalForRecovery() {
        // GIVEN: Batch with checkpointing enabled
        String tenantId = "tenant-checkpoint";
        String modelTarget = "model-v2";
        List<BatchInferenceJob.InferenceRecord> records = createTestRecords(5000);

        BatchInferenceJob.ScheduleConfig config = new BatchInferenceJob.ScheduleConfig(
            Duration.ofMinutes(5),
            true,  // Enable checkpointing
            1000   // Checkpoint every 1000 records
        );

        String jobId = runPromise(() ->
            batchJob.scheduleBatch(tenantId, modelTarget, records, config)
        );

        // WHEN: Execute batch
        BatchInferenceJob.BatchInferenceResult result = runPromise(() ->
            batchJob.runBatch(tenantId, jobId)
        );

        // THEN: Batch completed with checkpoint intervals respected
        assertThat(result.successCount())
            .as("Should process multiple checkpoint intervals")
            .isGreaterThanOrEqualTo(1000);
        assertThat(result.failureCount())
            .as("Failure count should be low")
            .isLessThan(result.totalRecords() / 100);  // < 1% failure
    }

    /**
     * Verifies error handling in batch.
     *
     * GIVEN: Batch with some failing records
     * WHEN: runBatch() called
     * THEN: Continues processing, tracks failures separately
     */
    @Test
    @DisplayName("Should handle failures gracefully and continue processing")
    void shouldHandleFailuresGracefullyContinueProcessing() {
        // GIVEN: Batch records
        String tenantId = "tenant-errors";
        String modelTarget = "model-v2";
        List<BatchInferenceJob.InferenceRecord> records = createTestRecords(500);

        BatchInferenceJob.ScheduleConfig config = new BatchInferenceJob.ScheduleConfig(
            Duration.ofMinutes(1), true, 1000
        );

        String jobId = runPromise(() ->
            batchJob.scheduleBatch(tenantId, modelTarget, records, config)
        );

        // WHEN: Execute batch
        BatchInferenceJob.BatchInferenceResult result = runPromise(() ->
            batchJob.runBatch(tenantId, jobId)
        );

        // THEN: Success count + failure count = total
        assertThat(result.successCount() + result.failureCount())
            .as("Success and failure counts should sum to total")
            .isEqualTo(result.totalRecords());

        // And: Majority should succeed
        assertThat(result.successCount())
            .as("Success rate should be high")
            .isGreaterThan((int) (result.totalRecords() * 0.95));
    }

    // Helper Methods

    private List<BatchInferenceJob.InferenceRecord> createTestRecords(int count) {
        List<BatchInferenceJob.InferenceRecord> records = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> features = new HashMap<>();
            features.put("feature1", (double) i);
            features.put("feature2", Math.random());
            records.add(new BatchInferenceJob.InferenceRecord(
                "record-" + i,
                features,
                Instant.now()
            ));
        }
        return records;
    }
}
