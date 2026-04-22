/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 */
package com.ghatana.orchestrator.store;

import static org.assertj.core.api.Assertions.*;

import com.ghatana.orchestrator.queue.ExecutionJob;
import com.ghatana.orchestrator.queue.impl.CheckpointAwareExecutionQueue;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;

/**
 * Integration tests for checkpoint recovery workflows using {@link InMemoryCheckpointStore}.
 * Validates the full pipeline lifecycle: create → step tracking → failure → resume.
 * Complements the Mockito-based {@link PostgresqlCheckpointStoreTest}.
 */
@DisplayName("Checkpoint Recovery Integration [GH-90000]")
class CheckpointRecoveryIntegrationTest extends EventloopTestBase {

    private static final String TENANT = "tenant-1";
    private static final String PIPELINE = "etl-pipeline";

    private InMemoryCheckpointStore store;
    private CheckpointAwareExecutionQueue queue;

    @BeforeEach
    void setUp() { // GH-90000
        store = new InMemoryCheckpointStore(); // GH-90000
        queue = new CheckpointAwareExecutionQueue(store); // GH-90000
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Full Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full Lifecycle [GH-90000]")
    class FullLifecycleTests {

        @Test
        @DisplayName("Create → run steps → complete successfully [GH-90000]")
        void fullSuccessfulExecution() { // GH-90000
            PipelineCheckpoint cp = store.createExecution(TENANT, PIPELINE, "inst-1", "key-1", Map.of("totalSteps", 3)); // GH-90000

            assertThat(cp.getStatus()).isEqualTo(PipelineCheckpointStatus.CREATED); // GH-90000
            assertThat(cp.getCompletedSteps()).isZero(); // GH-90000
            assertThat(cp.getTotalSteps()).isEqualTo(3); // GH-90000
            assertThat(cp.getInstanceId()).isEqualTo("inst-1 [GH-90000]");

            // Step 1: extract
            PipelineCheckpoint after1 = store.updateCheckpoint( // GH-90000
                    "inst-1",
                    "step-extract",
                    "Extract",
                    PipelineCheckpointStatus.STEP_SUCCESS,
                    Map.of("rows", 1000), // GH-90000
                    Map.of("totalSteps", 3)); // GH-90000
            assertThat(after1.getStatus()).isEqualTo(PipelineCheckpointStatus.RUNNING); // GH-90000
            assertThat(after1.getCompletedSteps()).isEqualTo(1); // GH-90000
            assertThat(after1.getCurrentStepId()).isEqualTo("step-extract [GH-90000]");

            // Step 2: transform
            PipelineCheckpoint after2 = store.updateCheckpoint( // GH-90000
                    "inst-1",
                    "step-transform",
                    "Transform",
                    PipelineCheckpointStatus.STEP_SUCCESS,
                    Map.of("rows", 950), // GH-90000
                    Map.of("totalSteps", 3)); // GH-90000
            assertThat(after2.getCompletedSteps()).isEqualTo(2); // GH-90000

            // Step 3: load
            PipelineCheckpoint after3 = store.updateCheckpoint( // GH-90000
                    "inst-1",
                    "step-load",
                    "Load",
                    PipelineCheckpointStatus.STEP_SUCCESS,
                    Map.of("rows", 950), // GH-90000
                    Map.of("totalSteps", 3)); // GH-90000
            assertThat(after3.getCompletedSteps()).isEqualTo(3); // GH-90000

            // Complete
            store.completeExecution( // GH-90000
                    "inst-1", PipelineCheckpointStatus.COMPLETED, Map.of("totalRows", 950, "status", "done")); // GH-90000

            PipelineCheckpoint final_ = store.findByInstanceId("inst-1 [GH-90000]").orElseThrow();
            assertThat(final_.getStatus()).isEqualTo(PipelineCheckpointStatus.COMPLETED); // GH-90000
            assertThat(final_.getResult()).containsEntry("totalRows", 950); // GH-90000
            assertThat(final_.isActive()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Create → partial run → fail → not active [GH-90000]")
        void failedExecutionLifecycle() { // GH-90000
            store.createExecution(TENANT, PIPELINE, "inst-fail", "key-fail", Map.of("totalSteps", 3)); // GH-90000

            store.updateCheckpoint( // GH-90000
                    "inst-fail", "step-1", "Step1", PipelineCheckpointStatus.STEP_SUCCESS, Map.of(), Map.of()); // GH-90000

            store.updateCheckpoint( // GH-90000
                    "inst-fail",
                    "step-2",
                    "Step2",
                    PipelineCheckpointStatus.STEP_FAILED,
                    Map.of("error", "timeout"), // GH-90000
                    Map.of()); // GH-90000

            store.completeExecution("inst-fail", PipelineCheckpointStatus.FAILED, Map.of("error", "Step2 timed out")); // GH-90000

            PipelineCheckpoint cp = store.findByInstanceId("inst-fail [GH-90000]").orElseThrow();
            assertThat(cp.getStatus()).isEqualTo(PipelineCheckpointStatus.FAILED); // GH-90000
            assertThat(cp.isActive()).isFalse(); // GH-90000
            assertThat(cp.getCompletedSteps()).isEqualTo(1); // only step-1 succeeded // GH-90000
        }

        @Test
        @DisplayName("Create → cancel → not active [GH-90000]")
        void cancelledExecution() { // GH-90000
            store.createExecution(TENANT, PIPELINE, "inst-cancel", "key-cancel", Map.of()); // GH-90000

            store.completeExecution("inst-cancel", PipelineCheckpointStatus.CANCELLED, Map.of()); // GH-90000

            PipelineCheckpoint cp = store.findByInstanceId("inst-cancel [GH-90000]").orElseThrow();
            assertThat(cp.getStatus()).isEqualTo(PipelineCheckpointStatus.CANCELLED); // GH-90000
            assertThat(cp.isActive()).isFalse(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Resume from Checkpoint
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Resume from Checkpoint [GH-90000]")
    class ResumeTests {

        @Test
        @DisplayName("Resume from last successful step after failure [GH-90000]")
        void resumeFromLastSuccessfulStep() { // GH-90000
            // Simulate a 5-step pipeline that fails at step 3
            store.createExecution(TENANT, PIPELINE, "inst-resume", "key-resume", Map.of("totalSteps", 5)); // GH-90000

            // Steps 1-2 succeed
            recordSuccessfulStep("inst-resume", "step-1", "Extract", Map.of("rows", 100)); // GH-90000
            recordSuccessfulStep("inst-resume", "step-2", "Validate", Map.of("valid", 95)); // GH-90000

            // Step 3 fails
            store.recordStepCheckpoint( // GH-90000
                    "inst-resume",
                    new StepCheckpoint( // GH-90000
                            "step-3",
                            "Transform",
                            PipelineCheckpointStatus.STEP_FAILED,
                            Map.of(), // GH-90000
                            Map.of(), // GH-90000
                            Instant.now(), // GH-90000
                            Instant.now(), // GH-90000
                            "OOM error",
                            0));

            store.updateCheckpoint( // GH-90000
                    "inst-resume",
                    "step-3",
                    "Transform",
                    PipelineCheckpointStatus.STEP_FAILED,
                    Map.of("error", "OOM"), // GH-90000
                    Map.of()); // GH-90000

            // Recover: find last successful step
            Optional<StepCheckpoint> lastSuccess = store.getLastSuccessfulStep("inst-resume [GH-90000]");
            assertThat(lastSuccess).isPresent(); // GH-90000
            assertThat(lastSuccess.get().getStepId()).isEqualTo("step-2 [GH-90000]");
            assertThat(lastSuccess.get().getStepName()).isEqualTo("Validate [GH-90000]");
            assertThat(lastSuccess.get().getOutput()).containsEntry("valid", 95); // GH-90000

            // Resume: re-execute from step-3 onward
            PipelineCheckpoint cp = store.findByInstanceId("inst-resume [GH-90000]").orElseThrow();
            assertThat(cp.getCompletedSteps()).isEqualTo(2); // GH-90000
            assertThat(cp.getStatus()).isEqualTo(PipelineCheckpointStatus.STEP_FAILED); // GH-90000
            // STEP_FAILED is not "active" (only CREATED/RUNNING are) — it requires intervention // GH-90000
            assertThat(cp.isActive()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("No successful steps → resume returns empty [GH-90000]")
        void noSuccessfulStepsReturnsEmpty() { // GH-90000
            store.createExecution(TENANT, PIPELINE, "inst-empty", "key-empty", Map.of()); // GH-90000

            // Fail immediately at step 1
            store.recordStepCheckpoint( // GH-90000
                    "inst-empty",
                    new StepCheckpoint( // GH-90000
                            "step-1",
                            "Init",
                            PipelineCheckpointStatus.STEP_FAILED,
                            Map.of(), // GH-90000
                            Map.of(), // GH-90000
                            Instant.now(), // GH-90000
                            Instant.now(), // GH-90000
                            "config error",
                            0));

            Optional<StepCheckpoint> lastSuccess = store.getLastSuccessfulStep("inst-empty [GH-90000]");
            assertThat(lastSuccess).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Step checkpoint upsert — retry updates existing step [GH-90000]")
        void stepCheckpointUpsert() { // GH-90000
            store.createExecution(TENANT, PIPELINE, "inst-upsert", "key-upsert", Map.of()); // GH-90000

            // First attempt fails
            store.recordStepCheckpoint( // GH-90000
                    "inst-upsert",
                    new StepCheckpoint( // GH-90000
                            "step-1",
                            "Process",
                            PipelineCheckpointStatus.STEP_FAILED,
                            Map.of("input", "data"), // GH-90000
                            Map.of(), // GH-90000
                            Instant.now(), // GH-90000
                            Instant.now(), // GH-90000
                            "timeout",
                            1));

            // Retry succeeds — upsert replaces the failed step checkpoint
            store.recordStepCheckpoint( // GH-90000
                    "inst-upsert",
                    new StepCheckpoint( // GH-90000
                            "step-1",
                            "Process",
                            PipelineCheckpointStatus.STEP_SUCCESS,
                            Map.of("input", "data"), // GH-90000
                            Map.of("result", "ok"), // GH-90000
                            Instant.now(), // GH-90000
                            Instant.now(), // GH-90000
                            null,
                            2));

            List<StepCheckpoint> history = store.getStepHistory("inst-upsert [GH-90000]");
            assertThat(history).hasSize(1); // upsert, not append // GH-90000
            assertThat(history.get(0).getStatus()).isEqualTo(PipelineCheckpointStatus.STEP_SUCCESS); // GH-90000
            assertThat(history.get(0).getRetryCount()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("Step history preserves insertion order [GH-90000]")
        void stepHistoryPreservesOrder() { // GH-90000
            store.createExecution(TENANT, PIPELINE, "inst-order", "key-order", Map.of()); // GH-90000

            recordSuccessfulStep("inst-order", "step-a", "A", Map.of()); // GH-90000
            recordSuccessfulStep("inst-order", "step-b", "B", Map.of()); // GH-90000
            recordSuccessfulStep("inst-order", "step-c", "C", Map.of()); // GH-90000

            List<StepCheckpoint> history = store.getStepHistory("inst-order [GH-90000]");
            assertThat(history).hasSize(3); // GH-90000
            assertThat(history).extracting(StepCheckpoint::getStepId).containsExactly("step-a", "step-b", "step-c"); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Idempotency & Deduplication
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Idempotency & Deduplication [GH-90000]")
    class IdempotencyTests {

        @Test
        @DisplayName("Duplicate idempotency key throws exception [GH-90000]")
        void duplicateIdempotencyKeyThrows() { // GH-90000
            store.createExecution(TENANT, PIPELINE, "inst-dup-1", "key-dup", Map.of()); // GH-90000

            assertThatThrownBy(() -> store.createExecution(TENANT, PIPELINE, "inst-dup-2", "key-dup", Map.of())) // GH-90000
                    .isInstanceOf(RuntimeException.class) // GH-90000
                    .hasMessageContaining("Duplicate [GH-90000]");
        }

        @Test
        @DisplayName("Same key in different tenants is allowed [GH-90000]")
        void sameKeyDifferentTenantAllowed() { // GH-90000
            store.createExecution("tenant-A", PIPELINE, "inst-a", "shared-key", Map.of()); // GH-90000
            store.createExecution("tenant-B", PIPELINE, "inst-b", "shared-key", Map.of()); // GH-90000

            assertThat(store.findByInstanceId("inst-a [GH-90000]")).isPresent();
            assertThat(store.findByInstanceId("inst-b [GH-90000]")).isPresent();
        }

        @Test
        @DisplayName("isDuplicate returns correct result [GH-90000]")
        void isDuplicateCheck() { // GH-90000
            store.createExecution(TENANT, PIPELINE, "inst-x", "dedup-key", Map.of()); // GH-90000

            assertThat(store.isDuplicate(TENANT, "dedup-key")).isTrue(); // GH-90000
            assertThat(store.isDuplicate(TENANT, "other-key")).isFalse(); // GH-90000
            assertThat(store.isDuplicate("other-tenant", "dedup-key")).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("findByIdempotencyKey retrieves correct checkpoint [GH-90000]")
        void findByIdempotencyKey() { // GH-90000
            store.createExecution(TENANT, PIPELINE, "inst-find", "find-key", Map.of()); // GH-90000

            Optional<PipelineCheckpoint> found = store.findByIdempotencyKey(TENANT, "find-key"); // GH-90000
            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().getInstanceId()).isEqualTo("inst-find [GH-90000]");

            assertThat(store.findByIdempotencyKey(TENANT, "non-existent")).isEmpty(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Query Capabilities
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Query Capabilities [GH-90000]")
    class QueryTests {

        @Test
        @DisplayName("findByPipelineId returns newest first, respects limit [GH-90000]")
        void findByPipelineIdOrdered() { // GH-90000
            store.createExecution(TENANT, PIPELINE, "inst-q1", "q-key-1", Map.of()); // GH-90000
            store.createExecution(TENANT, PIPELINE, "inst-q2", "q-key-2", Map.of()); // GH-90000
            store.createExecution(TENANT, "other-pipeline", "inst-q3", "q-key-3", Map.of()); // GH-90000

            List<PipelineCheckpoint> results = store.findByPipelineId(TENANT, PIPELINE, 10); // GH-90000
            assertThat(results).hasSize(2); // GH-90000
            assertThat(results).extracting(PipelineCheckpoint::getPipelineId).containsOnly(PIPELINE); // GH-90000

            // Limit
            List<PipelineCheckpoint> limited = store.findByPipelineId(TENANT, PIPELINE, 1); // GH-90000
            assertThat(limited).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("findActive returns only CREATED/RUNNING checkpoints [GH-90000]")
        void findActiveStatus() { // GH-90000
            store.createExecution(TENANT, PIPELINE, "inst-active", "active-key", Map.of()); // GH-90000
            store.createExecution(TENANT, PIPELINE, "inst-done", "done-key", Map.of()); // GH-90000
            store.completeExecution("inst-done", PipelineCheckpointStatus.COMPLETED, Map.of()); // GH-90000

            List<PipelineCheckpoint> active = store.findActive(10); // GH-90000
            assertThat(active).hasSize(1); // GH-90000
            assertThat(active.get(0).getInstanceId()).isEqualTo("inst-active [GH-90000]");
        }

        @Test
        @DisplayName("findStale returns executions updated before threshold [GH-90000]")
        void findStaleExecutions() { // GH-90000
            // Create execution and manually set old updatedAt by creating with initial state
            store.createExecution(TENANT, PIPELINE, "inst-stale", "stale-key", Map.of()); // GH-90000

            // Very recent execution should NOT be stale
            List<PipelineCheckpoint> stale = store.findStale(Instant.now().minus(1, ChronoUnit.HOURS)); // GH-90000
            assertThat(stale).isEmpty(); // GH-90000

            // With a future threshold, all active should be stale
            List<PipelineCheckpoint> allStale = store.findStale(Instant.now().plus(1, ChronoUnit.SECONDS)); // GH-90000
            assertThat(allStale).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("isExecutionAllowed — active vs completed [GH-90000]")
        void isExecutionAllowed() { // GH-90000
            store.createExecution(TENANT, PIPELINE, "inst-allowed", "allowed-key", Map.of()); // GH-90000
            assertThat(store.isExecutionAllowed("inst-allowed [GH-90000]")).isTrue();

            store.completeExecution("inst-allowed", PipelineCheckpointStatus.COMPLETED, Map.of()); // GH-90000
            assertThat(store.isExecutionAllowed("inst-allowed [GH-90000]")).isFalse();

            // Non-existent instance
            assertThat(store.isExecutionAllowed("non-existent [GH-90000]")).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cleanup
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cleanup [GH-90000]")
    class CleanupTests {

        @Test
        @DisplayName("cleanupOldCheckpoints removes completed, leaves active [GH-90000]")
        void cleanupRemovesCompletedOnly() { // GH-90000
            store.createExecution(TENANT, PIPELINE, "inst-keep", "keep-key", Map.of()); // GH-90000
            store.createExecution(TENANT, PIPELINE, "inst-remove", "remove-key", Map.of()); // GH-90000
            store.completeExecution("inst-remove", PipelineCheckpointStatus.COMPLETED, Map.of()); // GH-90000

            int removed = store.cleanupOldCheckpoints(Instant.now().plus(1, ChronoUnit.SECONDS)); // GH-90000
            assertThat(removed).isEqualTo(1); // GH-90000

            assertThat(store.findByInstanceId("inst-keep [GH-90000]")).isPresent();
            assertThat(store.findByInstanceId("inst-remove [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("cleanup also removes associated step checkpoints [GH-90000]")
        void cleanupRemovesStepCheckpoints() { // GH-90000
            store.createExecution(TENANT, PIPELINE, "inst-clean", "clean-key", Map.of()); // GH-90000
            recordSuccessfulStep("inst-clean", "s1", "S1", Map.of()); // GH-90000
            recordSuccessfulStep("inst-clean", "s2", "S2", Map.of()); // GH-90000
            store.completeExecution("inst-clean", PipelineCheckpointStatus.COMPLETED, Map.of()); // GH-90000

            store.cleanupOldCheckpoints(Instant.now().plus(1, ChronoUnit.SECONDS)); // GH-90000

            assertThat(store.getStepHistory("inst-clean [GH-90000]")).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Checkpoint-Aware Execution Queue Integration
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Checkpoint-Aware Queue Integration [GH-90000]")
    class QueueIntegrationTests {

        @Test
        @DisplayName("Enqueue creates checkpoint and adds to queue [GH-90000]")
        void enqueueCreatesCheckpoint() { // GH-90000
            queue.enqueue(TENANT, PIPELINE, Map.of("trigger", "manual"), "enq-key-1"); // GH-90000

            assertThat(store.isDuplicate(TENANT, "enq-key-1")).isTrue(); // GH-90000
            assertThat(queue.size()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("Duplicate enqueue is silently ignored [GH-90000]")
        void duplicateEnqueueIgnored() { // GH-90000
            queue.enqueue(TENANT, PIPELINE, Map.of(), "enq-dup"); // GH-90000
            queue.enqueue(TENANT, PIPELINE, Map.of(), "enq-dup"); // GH-90000

            assertThat(queue.size()).isEqualTo(1); // Not 2 // GH-90000
        }

        @Test
        @DisplayName("Poll validates checkpoint before returning job [GH-90000]")
        void pollValidatesCheckpoint() { // GH-90000
            queue.enqueue(TENANT, PIPELINE, Map.of(), "poll-key"); // GH-90000

            List<ExecutionJob> jobs = runPromise(() -> queue.poll(10, 30)); // GH-90000
            assertThat(jobs).hasSize(1); // GH-90000

            // After poll, queue is empty
            assertThat(queue.isEmpty()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Poll skips jobs with completed/cancelled checkpoints [GH-90000]")
        void pollSkipsCompletedJobs() { // GH-90000
            queue.enqueue(TENANT, PIPELINE, Map.of(), "skip-key"); // GH-90000

            // Complete the checkpoint (simulating external completion) // GH-90000
            PipelineCheckpoint cp =
                    store.findByIdempotencyKey(TENANT, "skip-key").orElseThrow(); // GH-90000
            store.completeExecution(cp.getInstanceId(), PipelineCheckpointStatus.COMPLETED, Map.of()); // GH-90000

            // Poll should skip the job because checkpoint is no longer active
            List<ExecutionJob> jobs = runPromise(() -> queue.poll(10, 30)); // GH-90000
            assertThat(jobs).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Clear empties queue but preserves checkpoints [GH-90000]")
        void clearPreservesCheckpoints() { // GH-90000
            queue.enqueue(TENANT, PIPELINE, Map.of(), "clear-key"); // GH-90000

            queue.clear(); // GH-90000
            assertThat(queue.isEmpty()).isTrue(); // GH-90000

            // Checkpoint still exists in store
            assertThat(store.isDuplicate(TENANT, "clear-key")).isTrue(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Multi-Pipeline Concurrent Execution
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Concurrent Execution Scenarios [GH-90000]")
    class ConcurrencyTests {

        @Test
        @DisplayName("Multiple pipelines execute independently [GH-90000]")
        void multiplePipelinesIndependent() { // GH-90000
            store.createExecution(TENANT, "pipeline-A", "inst-a", "key-a", Map.of("totalSteps", 2)); // GH-90000
            store.createExecution(TENANT, "pipeline-B", "inst-b", "key-b", Map.of("totalSteps", 3)); // GH-90000

            recordSuccessfulStep("inst-a", "a-step-1", "A-1", Map.of()); // GH-90000
            recordSuccessfulStep("inst-b", "b-step-1", "B-1", Map.of()); // GH-90000
            recordSuccessfulStep("inst-b", "b-step-2", "B-2", Map.of()); // GH-90000

            PipelineCheckpoint cpA = store.findByInstanceId("inst-a [GH-90000]").orElseThrow();
            PipelineCheckpoint cpB = store.findByInstanceId("inst-b [GH-90000]").orElseThrow();

            assertThat(cpA.getCompletedSteps()).isEqualTo(1); // GH-90000
            assertThat(cpB.getCompletedSteps()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("Many concurrent executions with unique idempotency keys [GH-90000]")
        void manyConcurrentExecutions() { // GH-90000
            IntStream.range(0, 50) // GH-90000
                    .forEach(i -> // GH-90000
                            store.createExecution(TENANT, PIPELINE, "inst-" + i, "key-" + i, Map.of("totalSteps", 5))); // GH-90000

            assertThat(store.size()).isEqualTo(50); // GH-90000
            assertThat(store.findActive(100)).hasSize(50); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases [GH-90000]")
    class EdgeCaseTests {

        @Test
        @DisplayName("Update non-existent checkpoint throws [GH-90000]")
        void updateNonExistentThrows() { // GH-90000
            assertThatThrownBy(() -> store.updateCheckpoint( // GH-90000
                            "no-such-instance", "s", "S", PipelineCheckpointStatus.STEP_SUCCESS, Map.of(), Map.of())) // GH-90000
                    .isInstanceOf(RuntimeException.class) // GH-90000
                    .hasMessageContaining("not found [GH-90000]");
        }

        @Test
        @DisplayName("Complete non-existent checkpoint throws [GH-90000]")
        void completeNonExistentThrows() { // GH-90000
            assertThatThrownBy(() -> store.completeExecution("no-such", PipelineCheckpointStatus.COMPLETED, Map.of())) // GH-90000
                    .isInstanceOf(RuntimeException.class) // GH-90000
                    .hasMessageContaining("not found [GH-90000]");
        }

        @Test
        @DisplayName("Step failure does not increment completedSteps [GH-90000]")
        void stepFailureNoIncrement() { // GH-90000
            store.createExecution(TENANT, PIPELINE, "inst-noinc", "key-noinc", Map.of()); // GH-90000

            store.updateCheckpoint("inst-noinc", "s1", "S1", PipelineCheckpointStatus.STEP_FAILED, Map.of(), Map.of()); // GH-90000

            PipelineCheckpoint cp = store.findByInstanceId("inst-noinc [GH-90000]").orElseThrow();
            assertThat(cp.getCompletedSteps()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("State is preserved across updates [GH-90000]")
        void statePreservedAcrossUpdates() { // GH-90000
            store.createExecution(TENANT, PIPELINE, "inst-state", "key-state", Map.of("initial", "data")); // GH-90000

            store.updateCheckpoint( // GH-90000
                    "inst-state",
                    "s1",
                    "S1",
                    PipelineCheckpointStatus.STEP_SUCCESS,
                    Map.of(), // GH-90000
                    Map.of("cursor", "page-2")); // GH-90000

            PipelineCheckpoint cp = store.findByInstanceId("inst-state [GH-90000]").orElseThrow();
            assertThat(cp.getState()).containsEntry("cursor", "page-2"); // GH-90000
        }

        @Test
        @DisplayName("Null state in update preserves previous state [GH-90000]")
        void nullStatePreservesPrevious() { // GH-90000
            store.createExecution(TENANT, PIPELINE, "inst-null", "key-null", Map.of("keep", "this")); // GH-90000

            store.updateCheckpoint("inst-null", "s1", "S1", PipelineCheckpointStatus.STEP_SUCCESS, Map.of(), null); // GH-90000

            PipelineCheckpoint cp = store.findByInstanceId("inst-null [GH-90000]").orElseThrow();
            assertThat(cp.getState()).containsEntry("keep", "this"); // GH-90000
        }

        @Test
        @DisplayName("InMemoryCheckpointStore clear resets all state [GH-90000]")
        void clearResetsAll() { // GH-90000
            store.createExecution(TENANT, PIPELINE, "inst-clear", "key-clear", Map.of()); // GH-90000
            recordSuccessfulStep("inst-clear", "s1", "S1", Map.of()); // GH-90000

            store.clear(); // GH-90000

            assertThat(store.size()).isZero(); // GH-90000
            assertThat(store.isDuplicate(TENANT, "key-clear")).isFalse(); // GH-90000
            assertThat(store.getStepHistory("inst-clear [GH-90000]")).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private void recordSuccessfulStep(String instanceId, String stepId, String stepName, Map<String, Object> output) { // GH-90000
        StepCheckpoint step = new StepCheckpoint( // GH-90000
                stepId,
                stepName,
                PipelineCheckpointStatus.STEP_SUCCESS,
                Map.of(), // GH-90000
                output,
                Instant.now(), // GH-90000
                Instant.now(), // GH-90000
                null,
                0);
        store.recordStepCheckpoint(instanceId, step); // GH-90000
        store.updateCheckpoint(instanceId, stepId, stepName, PipelineCheckpointStatus.STEP_SUCCESS, output, Map.of()); // GH-90000
    }
}
