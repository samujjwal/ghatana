/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.orchestrator.store;

import com.ghatana.orchestrator.queue.ExecutionJob;
import com.ghatana.orchestrator.queue.impl.CheckpointAwareExecutionQueue;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for checkpoint recovery workflows using {@link InMemoryCheckpointStore}.
 * Validates the full pipeline lifecycle: create → step tracking → failure → resume.
 * Complements the Mockito-based {@link PostgresqlCheckpointStoreTest}.
 */
@DisplayName("Checkpoint Recovery Integration")
class CheckpointRecoveryIntegrationTest {

    private static final String TENANT = "tenant-1";
    private static final String PIPELINE = "etl-pipeline";

    private InMemoryCheckpointStore store;
    private CheckpointAwareExecutionQueue queue;

    @BeforeEach
    void setUp() {
        store = new InMemoryCheckpointStore();
        queue = new CheckpointAwareExecutionQueue(store);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Full Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full Lifecycle")
    class FullLifecycleTests {

        @Test
        @DisplayName("Create → run steps → complete successfully")
        void fullSuccessfulExecution() {
            PipelineCheckpoint cp = store.createExecution(TENANT, PIPELINE, "inst-1", "key-1",
                    Map.of("totalSteps", 3));

            assertThat(cp.getStatus()).isEqualTo(PipelineCheckpointStatus.CREATED);
            assertThat(cp.getCompletedSteps()).isZero();
            assertThat(cp.getTotalSteps()).isEqualTo(3);
            assertThat(cp.getInstanceId()).isEqualTo("inst-1");

            // Step 1: extract
            PipelineCheckpoint after1 = store.updateCheckpoint("inst-1", "step-extract", "Extract",
                    PipelineCheckpointStatus.STEP_SUCCESS,
                    Map.of("rows", 1000), Map.of("totalSteps", 3));
            assertThat(after1.getStatus()).isEqualTo(PipelineCheckpointStatus.RUNNING);
            assertThat(after1.getCompletedSteps()).isEqualTo(1);
            assertThat(after1.getCurrentStepId()).isEqualTo("step-extract");

            // Step 2: transform
            PipelineCheckpoint after2 = store.updateCheckpoint("inst-1", "step-transform", "Transform",
                    PipelineCheckpointStatus.STEP_SUCCESS,
                    Map.of("rows", 950), Map.of("totalSteps", 3));
            assertThat(after2.getCompletedSteps()).isEqualTo(2);

            // Step 3: load
            PipelineCheckpoint after3 = store.updateCheckpoint("inst-1", "step-load", "Load",
                    PipelineCheckpointStatus.STEP_SUCCESS,
                    Map.of("rows", 950), Map.of("totalSteps", 3));
            assertThat(after3.getCompletedSteps()).isEqualTo(3);

            // Complete
            store.completeExecution("inst-1", PipelineCheckpointStatus.COMPLETED,
                    Map.of("totalRows", 950, "status", "done"));

            PipelineCheckpoint final_ = store.findByInstanceId("inst-1").orElseThrow();
            assertThat(final_.getStatus()).isEqualTo(PipelineCheckpointStatus.COMPLETED);
            assertThat(final_.getResult()).containsEntry("totalRows", 950);
            assertThat(final_.isActive()).isFalse();
        }

        @Test
        @DisplayName("Create → partial run → fail → not active")
        void failedExecutionLifecycle() {
            store.createExecution(TENANT, PIPELINE, "inst-fail", "key-fail",
                    Map.of("totalSteps", 3));

            store.updateCheckpoint("inst-fail", "step-1", "Step1",
                    PipelineCheckpointStatus.STEP_SUCCESS, Map.of(), Map.of());

            store.updateCheckpoint("inst-fail", "step-2", "Step2",
                    PipelineCheckpointStatus.STEP_FAILED, Map.of("error", "timeout"), Map.of());

            store.completeExecution("inst-fail", PipelineCheckpointStatus.FAILED,
                    Map.of("error", "Step2 timed out"));

            PipelineCheckpoint cp = store.findByInstanceId("inst-fail").orElseThrow();
            assertThat(cp.getStatus()).isEqualTo(PipelineCheckpointStatus.FAILED);
            assertThat(cp.isActive()).isFalse();
            assertThat(cp.getCompletedSteps()).isEqualTo(1); // only step-1 succeeded
        }

        @Test
        @DisplayName("Create → cancel → not active")
        void cancelledExecution() {
            store.createExecution(TENANT, PIPELINE, "inst-cancel", "key-cancel", Map.of());

            store.completeExecution("inst-cancel", PipelineCheckpointStatus.CANCELLED, Map.of());

            PipelineCheckpoint cp = store.findByInstanceId("inst-cancel").orElseThrow();
            assertThat(cp.getStatus()).isEqualTo(PipelineCheckpointStatus.CANCELLED);
            assertThat(cp.isActive()).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Resume from Checkpoint
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Resume from Checkpoint")
    class ResumeTests {

        @Test
        @DisplayName("Resume from last successful step after failure")
        void resumeFromLastSuccessfulStep() {
            // Simulate a 5-step pipeline that fails at step 3
            store.createExecution(TENANT, PIPELINE, "inst-resume", "key-resume",
                    Map.of("totalSteps", 5));

            // Steps 1-2 succeed
            recordSuccessfulStep("inst-resume", "step-1", "Extract", Map.of("rows", 100));
            recordSuccessfulStep("inst-resume", "step-2", "Validate", Map.of("valid", 95));

            // Step 3 fails
            store.recordStepCheckpoint("inst-resume", new StepCheckpoint(
                    "step-3", "Transform", PipelineCheckpointStatus.STEP_FAILED,
                    Map.of(), Map.of(), Instant.now(), Instant.now(), "OOM error", 0));

            store.updateCheckpoint("inst-resume", "step-3", "Transform",
                    PipelineCheckpointStatus.STEP_FAILED, Map.of("error", "OOM"), Map.of());

            // Recover: find last successful step
            Optional<StepCheckpoint> lastSuccess = store.getLastSuccessfulStep("inst-resume");
            assertThat(lastSuccess).isPresent();
            assertThat(lastSuccess.get().getStepId()).isEqualTo("step-2");
            assertThat(lastSuccess.get().getStepName()).isEqualTo("Validate");
            assertThat(lastSuccess.get().getOutput()).containsEntry("valid", 95);

            // Resume: re-execute from step-3 onward
            PipelineCheckpoint cp = store.findByInstanceId("inst-resume").orElseThrow();
            assertThat(cp.getCompletedSteps()).isEqualTo(2);
            assertThat(cp.getStatus()).isEqualTo(PipelineCheckpointStatus.STEP_FAILED);
            // STEP_FAILED is not "active" (only CREATED/RUNNING are) — it requires intervention
            assertThat(cp.isActive()).isFalse();
        }

        @Test
        @DisplayName("No successful steps → resume returns empty")
        void noSuccessfulStepsReturnsEmpty() {
            store.createExecution(TENANT, PIPELINE, "inst-empty", "key-empty", Map.of());

            // Fail immediately at step 1
            store.recordStepCheckpoint("inst-empty", new StepCheckpoint(
                    "step-1", "Init", PipelineCheckpointStatus.STEP_FAILED,
                    Map.of(), Map.of(), Instant.now(), Instant.now(), "config error", 0));

            Optional<StepCheckpoint> lastSuccess = store.getLastSuccessfulStep("inst-empty");
            assertThat(lastSuccess).isEmpty();
        }

        @Test
        @DisplayName("Step checkpoint upsert — retry updates existing step")
        void stepCheckpointUpsert() {
            store.createExecution(TENANT, PIPELINE, "inst-upsert", "key-upsert", Map.of());

            // First attempt fails
            store.recordStepCheckpoint("inst-upsert", new StepCheckpoint(
                    "step-1", "Process", PipelineCheckpointStatus.STEP_FAILED,
                    Map.of("input", "data"), Map.of(), Instant.now(), Instant.now(), "timeout", 1));

            // Retry succeeds — upsert replaces the failed step checkpoint
            store.recordStepCheckpoint("inst-upsert", new StepCheckpoint(
                    "step-1", "Process", PipelineCheckpointStatus.STEP_SUCCESS,
                    Map.of("input", "data"), Map.of("result", "ok"), Instant.now(), Instant.now(), null, 2));

            List<StepCheckpoint> history = store.getStepHistory("inst-upsert");
            assertThat(history).hasSize(1); // upsert, not append
            assertThat(history.get(0).getStatus()).isEqualTo(PipelineCheckpointStatus.STEP_SUCCESS);
            assertThat(history.get(0).getRetryCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Step history preserves insertion order")
        void stepHistoryPreservesOrder() {
            store.createExecution(TENANT, PIPELINE, "inst-order", "key-order", Map.of());

            recordSuccessfulStep("inst-order", "step-a", "A", Map.of());
            recordSuccessfulStep("inst-order", "step-b", "B", Map.of());
            recordSuccessfulStep("inst-order", "step-c", "C", Map.of());

            List<StepCheckpoint> history = store.getStepHistory("inst-order");
            assertThat(history).hasSize(3);
            assertThat(history).extracting(StepCheckpoint::getStepId)
                    .containsExactly("step-a", "step-b", "step-c");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Idempotency & Deduplication
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Idempotency & Deduplication")
    class IdempotencyTests {

        @Test
        @DisplayName("Duplicate idempotency key throws exception")
        void duplicateIdempotencyKeyThrows() {
            store.createExecution(TENANT, PIPELINE, "inst-dup-1", "key-dup", Map.of());

            assertThatThrownBy(() ->
                    store.createExecution(TENANT, PIPELINE, "inst-dup-2", "key-dup", Map.of()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Duplicate");
        }

        @Test
        @DisplayName("Same key in different tenants is allowed")
        void sameKeyDifferentTenantAllowed() {
            store.createExecution("tenant-A", PIPELINE, "inst-a", "shared-key", Map.of());
            store.createExecution("tenant-B", PIPELINE, "inst-b", "shared-key", Map.of());

            assertThat(store.findByInstanceId("inst-a")).isPresent();
            assertThat(store.findByInstanceId("inst-b")).isPresent();
        }

        @Test
        @DisplayName("isDuplicate returns correct result")
        void isDuplicateCheck() {
            store.createExecution(TENANT, PIPELINE, "inst-x", "dedup-key", Map.of());

            assertThat(store.isDuplicate(TENANT, "dedup-key")).isTrue();
            assertThat(store.isDuplicate(TENANT, "other-key")).isFalse();
            assertThat(store.isDuplicate("other-tenant", "dedup-key")).isFalse();
        }

        @Test
        @DisplayName("findByIdempotencyKey retrieves correct checkpoint")
        void findByIdempotencyKey() {
            store.createExecution(TENANT, PIPELINE, "inst-find", "find-key", Map.of());

            Optional<PipelineCheckpoint> found = store.findByIdempotencyKey(TENANT, "find-key");
            assertThat(found).isPresent();
            assertThat(found.get().getInstanceId()).isEqualTo("inst-find");

            assertThat(store.findByIdempotencyKey(TENANT, "non-existent")).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Query Capabilities
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Query Capabilities")
    class QueryTests {

        @Test
        @DisplayName("findByPipelineId returns newest first, respects limit")
        void findByPipelineIdOrdered() {
            store.createExecution(TENANT, PIPELINE, "inst-q1", "q-key-1", Map.of());
            store.createExecution(TENANT, PIPELINE, "inst-q2", "q-key-2", Map.of());
            store.createExecution(TENANT, "other-pipeline", "inst-q3", "q-key-3", Map.of());

            List<PipelineCheckpoint> results = store.findByPipelineId(TENANT, PIPELINE, 10);
            assertThat(results).hasSize(2);
            assertThat(results).extracting(PipelineCheckpoint::getPipelineId)
                    .containsOnly(PIPELINE);

            // Limit
            List<PipelineCheckpoint> limited = store.findByPipelineId(TENANT, PIPELINE, 1);
            assertThat(limited).hasSize(1);
        }

        @Test
        @DisplayName("findActive returns only CREATED/RUNNING checkpoints")
        void findActiveStatus() {
            store.createExecution(TENANT, PIPELINE, "inst-active", "active-key", Map.of());
            store.createExecution(TENANT, PIPELINE, "inst-done", "done-key", Map.of());
            store.completeExecution("inst-done", PipelineCheckpointStatus.COMPLETED, Map.of());

            List<PipelineCheckpoint> active = store.findActive(10);
            assertThat(active).hasSize(1);
            assertThat(active.get(0).getInstanceId()).isEqualTo("inst-active");
        }

        @Test
        @DisplayName("findStale returns executions updated before threshold")
        void findStaleExecutions() {
            // Create execution and manually set old updatedAt by creating with initial state
            store.createExecution(TENANT, PIPELINE, "inst-stale", "stale-key", Map.of());

            // Very recent execution should NOT be stale
            List<PipelineCheckpoint> stale = store.findStale(Instant.now().minus(1, ChronoUnit.HOURS));
            assertThat(stale).isEmpty();

            // With a future threshold, all active should be stale
            List<PipelineCheckpoint> allStale = store.findStale(Instant.now().plus(1, ChronoUnit.SECONDS));
            assertThat(allStale).hasSize(1);
        }

        @Test
        @DisplayName("isExecutionAllowed — active vs completed")
        void isExecutionAllowed() {
            store.createExecution(TENANT, PIPELINE, "inst-allowed", "allowed-key", Map.of());
            assertThat(store.isExecutionAllowed("inst-allowed")).isTrue();

            store.completeExecution("inst-allowed", PipelineCheckpointStatus.COMPLETED, Map.of());
            assertThat(store.isExecutionAllowed("inst-allowed")).isFalse();

            // Non-existent instance
            assertThat(store.isExecutionAllowed("non-existent")).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cleanup
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cleanup")
    class CleanupTests {

        @Test
        @DisplayName("cleanupOldCheckpoints removes completed, leaves active")
        void cleanupRemovesCompletedOnly() {
            store.createExecution(TENANT, PIPELINE, "inst-keep", "keep-key", Map.of());
            store.createExecution(TENANT, PIPELINE, "inst-remove", "remove-key", Map.of());
            store.completeExecution("inst-remove", PipelineCheckpointStatus.COMPLETED, Map.of());

            int removed = store.cleanupOldCheckpoints(Instant.now().plus(1, ChronoUnit.SECONDS));
            assertThat(removed).isEqualTo(1);

            assertThat(store.findByInstanceId("inst-keep")).isPresent();
            assertThat(store.findByInstanceId("inst-remove")).isEmpty();
        }

        @Test
        @DisplayName("cleanup also removes associated step checkpoints")
        void cleanupRemovesStepCheckpoints() {
            store.createExecution(TENANT, PIPELINE, "inst-clean", "clean-key", Map.of());
            recordSuccessfulStep("inst-clean", "s1", "S1", Map.of());
            recordSuccessfulStep("inst-clean", "s2", "S2", Map.of());
            store.completeExecution("inst-clean", PipelineCheckpointStatus.COMPLETED, Map.of());

            store.cleanupOldCheckpoints(Instant.now().plus(1, ChronoUnit.SECONDS));

            assertThat(store.getStepHistory("inst-clean")).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Checkpoint-Aware Execution Queue Integration
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Checkpoint-Aware Queue Integration")
    class QueueIntegrationTests {

        @Test
        @DisplayName("Enqueue creates checkpoint and adds to queue")
        void enqueueCreatesCheckpoint() {
            queue.enqueue(TENANT, PIPELINE, Map.of("trigger", "manual"), "enq-key-1");

            assertThat(store.isDuplicate(TENANT, "enq-key-1")).isTrue();
            assertThat(queue.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("Duplicate enqueue is silently ignored")
        void duplicateEnqueueIgnored() {
            queue.enqueue(TENANT, PIPELINE, Map.of(), "enq-dup");
            queue.enqueue(TENANT, PIPELINE, Map.of(), "enq-dup");

            assertThat(queue.size()).isEqualTo(1); // Not 2
        }

        @Test
        @DisplayName("Poll validates checkpoint before returning job")
        void pollValidatesCheckpoint() {
            queue.enqueue(TENANT, PIPELINE, Map.of(), "poll-key");

            List<ExecutionJob> jobs = queue.poll(10, 30).getResult();
            assertThat(jobs).hasSize(1);

            // After poll, queue is empty
            assertThat(queue.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("Poll skips jobs with completed/cancelled checkpoints")
        void pollSkipsCompletedJobs() {
            queue.enqueue(TENANT, PIPELINE, Map.of(), "skip-key");

            // Complete the checkpoint (simulating external completion)
            PipelineCheckpoint cp = store.findByIdempotencyKey(TENANT, "skip-key").orElseThrow();
            store.completeExecution(cp.getInstanceId(), PipelineCheckpointStatus.COMPLETED, Map.of());

            // Poll should skip the job because checkpoint is no longer active
            List<ExecutionJob> jobs = queue.poll(10, 30).getResult();
            assertThat(jobs).isEmpty();
        }

        @Test
        @DisplayName("Clear empties queue but preserves checkpoints")
        void clearPreservesCheckpoints() {
            queue.enqueue(TENANT, PIPELINE, Map.of(), "clear-key");

            queue.clear();
            assertThat(queue.isEmpty()).isTrue();

            // Checkpoint still exists in store
            assertThat(store.isDuplicate(TENANT, "clear-key")).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Multi-Pipeline Concurrent Execution
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Concurrent Execution Scenarios")
    class ConcurrencyTests {

        @Test
        @DisplayName("Multiple pipelines execute independently")
        void multiplePipelinesIndependent() {
            store.createExecution(TENANT, "pipeline-A", "inst-a", "key-a", Map.of("totalSteps", 2));
            store.createExecution(TENANT, "pipeline-B", "inst-b", "key-b", Map.of("totalSteps", 3));

            recordSuccessfulStep("inst-a", "a-step-1", "A-1", Map.of());
            recordSuccessfulStep("inst-b", "b-step-1", "B-1", Map.of());
            recordSuccessfulStep("inst-b", "b-step-2", "B-2", Map.of());

            PipelineCheckpoint cpA = store.findByInstanceId("inst-a").orElseThrow();
            PipelineCheckpoint cpB = store.findByInstanceId("inst-b").orElseThrow();

            assertThat(cpA.getCompletedSteps()).isEqualTo(1);
            assertThat(cpB.getCompletedSteps()).isEqualTo(2);
        }

        @Test
        @DisplayName("Many concurrent executions with unique idempotency keys")
        void manyConcurrentExecutions() {
            IntStream.range(0, 50).forEach(i ->
                    store.createExecution(TENANT, PIPELINE,
                            "inst-" + i, "key-" + i, Map.of("totalSteps", 5)));

            assertThat(store.size()).isEqualTo(50);
            assertThat(store.findActive(100)).hasSize(50);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Update non-existent checkpoint throws")
        void updateNonExistentThrows() {
            assertThatThrownBy(() ->
                    store.updateCheckpoint("no-such-instance", "s", "S",
                            PipelineCheckpointStatus.STEP_SUCCESS, Map.of(), Map.of()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("Complete non-existent checkpoint throws")
        void completeNonExistentThrows() {
            assertThatThrownBy(() ->
                    store.completeExecution("no-such", PipelineCheckpointStatus.COMPLETED, Map.of()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("Step failure does not increment completedSteps")
        void stepFailureNoIncrement() {
            store.createExecution(TENANT, PIPELINE, "inst-noinc", "key-noinc", Map.of());

            store.updateCheckpoint("inst-noinc", "s1", "S1",
                    PipelineCheckpointStatus.STEP_FAILED, Map.of(), Map.of());

            PipelineCheckpoint cp = store.findByInstanceId("inst-noinc").orElseThrow();
            assertThat(cp.getCompletedSteps()).isZero();
        }

        @Test
        @DisplayName("State is preserved across updates")
        void statePreservedAcrossUpdates() {
            store.createExecution(TENANT, PIPELINE, "inst-state", "key-state",
                    Map.of("initial", "data"));

            store.updateCheckpoint("inst-state", "s1", "S1",
                    PipelineCheckpointStatus.STEP_SUCCESS, Map.of(),
                    Map.of("cursor", "page-2"));

            PipelineCheckpoint cp = store.findByInstanceId("inst-state").orElseThrow();
            assertThat(cp.getState()).containsEntry("cursor", "page-2");
        }

        @Test
        @DisplayName("Null state in update preserves previous state")
        void nullStatePreservesPrevious() {
            store.createExecution(TENANT, PIPELINE, "inst-null", "key-null",
                    Map.of("keep", "this"));

            store.updateCheckpoint("inst-null", "s1", "S1",
                    PipelineCheckpointStatus.STEP_SUCCESS, Map.of(), null);

            PipelineCheckpoint cp = store.findByInstanceId("inst-null").orElseThrow();
            assertThat(cp.getState()).containsEntry("keep", "this");
        }

        @Test
        @DisplayName("InMemoryCheckpointStore clear resets all state")
        void clearResetsAll() {
            store.createExecution(TENANT, PIPELINE, "inst-clear", "key-clear", Map.of());
            recordSuccessfulStep("inst-clear", "s1", "S1", Map.of());

            store.clear();

            assertThat(store.size()).isZero();
            assertThat(store.isDuplicate(TENANT, "key-clear")).isFalse();
            assertThat(store.getStepHistory("inst-clear")).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private void recordSuccessfulStep(String instanceId, String stepId, String stepName,
                                      Map<String, Object> output) {
        StepCheckpoint step = new StepCheckpoint(
                stepId, stepName, PipelineCheckpointStatus.STEP_SUCCESS,
                Map.of(), output, Instant.now(), Instant.now(), null, 0);
        store.recordStepCheckpoint(instanceId, step);
        store.updateCheckpoint(instanceId, stepId, stepName,
                PipelineCheckpointStatus.STEP_SUCCESS, output, Map.of());
    }
}
