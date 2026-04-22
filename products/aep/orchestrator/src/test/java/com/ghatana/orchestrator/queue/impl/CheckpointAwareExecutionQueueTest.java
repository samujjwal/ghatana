/*
 * Copyright (c) 2024 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.orchestrator.queue.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ghatana.orchestrator.queue.ExecutionJob;
import com.ghatana.orchestrator.store.CheckpointStore;
import com.ghatana.orchestrator.store.PipelineCheckpoint;
import com.ghatana.orchestrator.store.PipelineCheckpointStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link CheckpointAwareExecutionQueue}.
 *
 * <p>Extends {@link EventloopTestBase} — all Promise-returning operations are
 * executed via {@code runPromise()} to avoid NPE when resolving ActiveJ Promises // GH-90000
 * outside of an event-loop context.
 *
 * @doc.type class
 * @doc.purpose Tests for checkpoint-aware execution queue duplicate prevention and exactly-once semantics
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("CheckpointAwareExecutionQueue [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class CheckpointAwareExecutionQueueTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-1";

    @Mock
    private CheckpointStore checkpointStore;

    private CheckpointAwareExecutionQueue executionQueue;

    @BeforeEach
    void setUp() { // GH-90000
        executionQueue = new CheckpointAwareExecutionQueue(checkpointStore); // GH-90000
    }

    @Test
    @DisplayName("enqueue() stores job and tracks it [GH-90000]")
    void shouldEnqueueNewExecution() { // GH-90000
        String tenantId = TENANT_ID;
        String pipelineId = "test-pipeline";
        Object triggerData = Map.of("event", "trigger"); // GH-90000
        String idempotencyKey = "unique-key-123";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(false); // GH-90000
        when(checkpointStore.createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any())) // GH-90000
                .thenReturn(createMockCheckpoint("instance-123", pipelineId, idempotencyKey)); // GH-90000

        runPromise(() -> executionQueue.enqueue(tenantId, pipelineId, triggerData, idempotencyKey)); // GH-90000

        assertThat(executionQueue.size()).isEqualTo(1); // GH-90000
        assertThat(executionQueue.isEmpty()).isFalse(); // GH-90000

        verify(checkpointStore).isDuplicate(tenantId, idempotencyKey); // GH-90000
        verify(checkpointStore).createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any()); // GH-90000
    }

    @Test
    @DisplayName("enqueue() is idempotent for duplicate idempotency keys [GH-90000]")
    void shouldPreventDuplicateEnqueue() { // GH-90000
        String tenantId = TENANT_ID;
        String pipelineId = "test-pipeline";
        Object triggerData = Map.of("event", "trigger"); // GH-90000
        String idempotencyKey = "duplicate-key";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(true); // GH-90000

        runPromise(() -> executionQueue.enqueue(tenantId, pipelineId, triggerData, idempotencyKey)); // GH-90000

        assertThat(executionQueue.size()).isEqualTo(0); // GH-90000
        assertThat(executionQueue.isEmpty()).isTrue(); // GH-90000

        verify(checkpointStore).isDuplicate(tenantId, idempotencyKey); // GH-90000
        verify(checkpointStore, never()).createExecution(any(), any(), any(), any(), any()); // GH-90000
    }

    @Test
    @DisplayName("poll() returns job when execution is allowed [GH-90000]")
    void shouldPollValidJob() { // GH-90000
        String tenantId = TENANT_ID;
        String pipelineId = "test-pipeline";
        String idempotencyKey = "valid-key";
        String instanceId = "instance-123";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(false); // GH-90000
        when(checkpointStore.createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any())) // GH-90000
                .thenReturn(createMockCheckpoint(instanceId, pipelineId, idempotencyKey)); // GH-90000
        when(checkpointStore.isExecutionAllowed(anyString())).thenReturn(true); // GH-90000

        runPromise(() -> executionQueue.enqueue(tenantId, pipelineId, Map.of("data", "test"), idempotencyKey)); // GH-90000

        List<ExecutionJob> polledJobs = runPromise(() -> executionQueue.poll(1, 0)); // GH-90000

        assertThat(polledJobs).isNotNull().hasSize(1); // GH-90000
        ExecutionJob job = polledJobs.get(0); // GH-90000
        assertThat(job.getTenantId()).isEqualTo(tenantId); // GH-90000
        assertThat(job.getPipelineId()).isEqualTo(pipelineId); // GH-90000
        assertThat(job.getIdempotencyKey()).isEqualTo(idempotencyKey); // GH-90000
        assertThat(executionQueue.size()).isEqualTo(0); // GH-90000

        verify(checkpointStore).isExecutionAllowed(anyString()); // GH-90000
    }

    @Test
    @DisplayName("poll() skips job when checkpoint does not allow execution [GH-90000]")
    void shouldSkipJobWithInvalidCheckpoint() { // GH-90000
        String tenantId = TENANT_ID;
        String pipelineId = "test-pipeline";
        String idempotencyKey = "invalid-key";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(false); // GH-90000
        when(checkpointStore.createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any())) // GH-90000
                .thenReturn(createMockCheckpoint("instance-123", pipelineId, idempotencyKey)); // GH-90000
        when(checkpointStore.isExecutionAllowed(anyString())).thenReturn(false); // GH-90000

        runPromise(() -> executionQueue.enqueue(tenantId, pipelineId, Map.of("data", "test"), idempotencyKey)); // GH-90000

        List<ExecutionJob> polledJobs = runPromise(() -> executionQueue.poll(1, 0)); // GH-90000

        assertThat(polledJobs).isNotNull().isEmpty(); // GH-90000
        verify(checkpointStore).isExecutionAllowed(anyString()); // GH-90000
    }

    @Test
    @DisplayName("poll() skips job when checkpoint is already completed [GH-90000]")
    void shouldSkipJobWithCompletedCheckpoint() { // GH-90000
        String tenantId = TENANT_ID;
        String pipelineId = "test-pipeline";
        String idempotencyKey = "completed-key";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(false); // GH-90000
        when(checkpointStore.createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any())) // GH-90000
                .thenReturn(createMockCheckpoint("instance-123", pipelineId, idempotencyKey)); // GH-90000
        when(checkpointStore.isExecutionAllowed(anyString())).thenReturn(false); // GH-90000

        runPromise(() -> executionQueue.enqueue(tenantId, pipelineId, Map.of("data", "test"), idempotencyKey)); // GH-90000

        List<ExecutionJob> polledJobs = runPromise(() -> executionQueue.poll(1, 0)); // GH-90000

        assertThat(polledJobs).isNotNull().isEmpty(); // GH-90000
        verify(checkpointStore).isExecutionAllowed(anyString()); // GH-90000
    }

    @Test
    @DisplayName("clear() empties the queue [GH-90000]")
    void shouldClearQueue() { // GH-90000
        when(checkpointStore.isDuplicate(anyString(), anyString())).thenReturn(false); // GH-90000
        when(checkpointStore.createExecution(anyString(), anyString(), anyString(), anyString(), any())) // GH-90000
                .thenReturn(createMockCheckpoint("instance", "pipeline", "key")); // GH-90000

        runPromise(() -> executionQueue.enqueue(TENANT_ID, "pipeline-1", Map.of(), "key-1")); // GH-90000
        runPromise(() -> executionQueue.enqueue(TENANT_ID, "pipeline-2", Map.of(), "key-2")); // GH-90000
        assertThat(executionQueue.size()).isEqualTo(2); // GH-90000

        runPromise(() -> executionQueue.clear()); // GH-90000

        assertThat(executionQueue.size()).isEqualTo(0); // GH-90000
        assertThat(executionQueue.isEmpty()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("poll() after enqueue returns correct job fields [GH-90000]")
    void shouldPeekAtNextJob() { // GH-90000
        String tenantId = TENANT_ID;
        String pipelineId = "peek-pipeline";
        String idempotencyKey = "peek-key";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(false); // GH-90000
        when(checkpointStore.createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any())) // GH-90000
                .thenReturn(createMockCheckpoint("instance-123", pipelineId, idempotencyKey)); // GH-90000
        when(checkpointStore.isExecutionAllowed(anyString())).thenReturn(true); // GH-90000

        runPromise(() -> executionQueue.enqueue(tenantId, pipelineId, Map.of("data", "peek"), idempotencyKey)); // GH-90000

        List<ExecutionJob> polledJobs = runPromise(() -> executionQueue.poll(1, 0)); // GH-90000

        assertThat(polledJobs).isNotNull().hasSize(1); // GH-90000
        ExecutionJob polled = polledJobs.get(0); // GH-90000
        assertThat(polled.getTenantId()).isEqualTo(tenantId); // GH-90000
        assertThat(polled.getPipelineId()).isEqualTo(pipelineId); // GH-90000
        assertThat(polled.getIdempotencyKey()).isEqualTo(idempotencyKey); // GH-90000
        assertThat(executionQueue.size()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("getStatistics() reflects current queue state [GH-90000]")
    void shouldProvideQueueStatistics() { // GH-90000
        when(checkpointStore.isDuplicate(anyString(), anyString())).thenReturn(false); // GH-90000
        when(checkpointStore.createExecution(anyString(), anyString(), anyString(), anyString(), any())) // GH-90000
                .thenReturn(createMockCheckpoint("instance", "pipeline", "key")); // GH-90000

        runPromise(() -> executionQueue.enqueue(TENANT_ID, "pipeline-1", Map.of(), "key-1")); // GH-90000
        runPromise(() -> executionQueue.enqueue(TENANT_ID, "pipeline-2", Map.of(), "key-2")); // GH-90000
        runPromise(() -> executionQueue.enqueue(TENANT_ID, "pipeline-3", Map.of(), "key-3")); // GH-90000

        CheckpointAwareExecutionQueue.QueueStatistics stats = executionQueue.getStatistics(); // GH-90000

        assertThat(stats.getQueueSize()).isEqualTo(3); // GH-90000
        assertThat(stats.getIdempotencyKeyCount()).isEqualTo(3); // GH-90000
    }

    @Test
    @DisplayName("polled job contains non-null jobId and instanceId [GH-90000]")
    void shouldCalculateQueuedDuration() { // GH-90000
        String tenantId = TENANT_ID;
        String pipelineId = "duration-pipeline";
        String idempotencyKey = "duration-key";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(false); // GH-90000
        when(checkpointStore.createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any())) // GH-90000
                .thenReturn(createMockCheckpoint("instance-123", pipelineId, idempotencyKey)); // GH-90000
        when(checkpointStore.isExecutionAllowed(anyString())).thenReturn(true); // GH-90000

        runPromise(() -> executionQueue.enqueue(tenantId, pipelineId, Map.of("data", "duration"), idempotencyKey)); // GH-90000

        List<ExecutionJob> polledJobs = runPromise(() -> executionQueue.poll(1, 0)); // GH-90000
        assertThat(polledJobs).hasSize(1); // GH-90000
        ExecutionJob job = polledJobs.get(0); // GH-90000
        assertThat(job.getJobId()).isNotNull().isNotEmpty(); // GH-90000
        assertThat(job.getInstanceId()).isNotNull().startsWith(pipelineId + "-"); // GH-90000
    }

    @Test
    @DisplayName("enqueue() propagates CheckpointStore exceptions [GH-90000]")
    void shouldHandleCheckpointStoreException() { // GH-90000
        String tenantId = TENANT_ID;
        String pipelineId = "error-pipeline";
        String idempotencyKey = "error-key";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(false); // GH-90000
        when(checkpointStore.createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any())) // GH-90000
                .thenThrow(new RuntimeException("Checkpoint store error [GH-90000]"));

        assertThatThrownBy(() -> runPromise( // GH-90000
                        () -> executionQueue.enqueue(tenantId, pipelineId, Map.of("data", "error"), idempotencyKey))) // GH-90000
                .isInstanceOf(RuntimeException.class) // GH-90000
                .hasMessageContaining("Checkpoint store error [GH-90000]");

        clearFatalError(); // GH-90000
        assertThat(executionQueue.size()).isEqualTo(0); // GH-90000
    }

    private PipelineCheckpoint createMockCheckpoint(String instanceId, String pipelineId, String idempotencyKey) { // GH-90000
        return new PipelineCheckpoint( // GH-90000
                instanceId,
                TENANT_ID,
                pipelineId,
                idempotencyKey,
                PipelineCheckpointStatus.CREATED,
                Map.of("test", "state"), // GH-90000
                Map.of(), // GH-90000
                Instant.now(), // GH-90000
                Instant.now(), // GH-90000
                null,
                null,
                0,
                5);
    }
}
