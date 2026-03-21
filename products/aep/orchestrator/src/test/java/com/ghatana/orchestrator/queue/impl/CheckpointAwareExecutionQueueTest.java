/*
 * Copyright (c) 2024 Ghatana Inc.
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
 * executed via {@code runPromise()} to avoid NPE when resolving ActiveJ Promises
 * outside of an event-loop context.
 *
 * @doc.type class
 * @doc.purpose Tests for checkpoint-aware execution queue duplicate prevention and exactly-once semantics
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("CheckpointAwareExecutionQueue")
@ExtendWith(MockitoExtension.class)
class CheckpointAwareExecutionQueueTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-1";

    @Mock
    private CheckpointStore checkpointStore;

    private CheckpointAwareExecutionQueue executionQueue;

    @BeforeEach
    void setUp() {
        executionQueue = new CheckpointAwareExecutionQueue(checkpointStore);
    }

    @Test
    @DisplayName("enqueue() stores job and tracks it")
    void shouldEnqueueNewExecution() {
        String tenantId = TENANT_ID;
        String pipelineId = "test-pipeline";
        Object triggerData = Map.of("event", "trigger");
        String idempotencyKey = "unique-key-123";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(false);
        when(checkpointStore.createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any()))
                .thenReturn(createMockCheckpoint("instance-123", pipelineId, idempotencyKey));

        runPromise(() -> executionQueue.enqueue(tenantId, pipelineId, triggerData, idempotencyKey));

        assertThat(executionQueue.size()).isEqualTo(1);
        assertThat(executionQueue.isEmpty()).isFalse();

        verify(checkpointStore).isDuplicate(tenantId, idempotencyKey);
        verify(checkpointStore).createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any());
    }

    @Test
    @DisplayName("enqueue() is idempotent for duplicate idempotency keys")
    void shouldPreventDuplicateEnqueue() {
        String tenantId = TENANT_ID;
        String pipelineId = "test-pipeline";
        Object triggerData = Map.of("event", "trigger");
        String idempotencyKey = "duplicate-key";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(true);

        runPromise(() -> executionQueue.enqueue(tenantId, pipelineId, triggerData, idempotencyKey));

        assertThat(executionQueue.size()).isEqualTo(0);
        assertThat(executionQueue.isEmpty()).isTrue();

        verify(checkpointStore).isDuplicate(tenantId, idempotencyKey);
        verify(checkpointStore, never()).createExecution(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("poll() returns job when execution is allowed")
    void shouldPollValidJob() {
        String tenantId = TENANT_ID;
        String pipelineId = "test-pipeline";
        String idempotencyKey = "valid-key";
        String instanceId = "instance-123";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(false);
        when(checkpointStore.createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any()))
                .thenReturn(createMockCheckpoint(instanceId, pipelineId, idempotencyKey));
        when(checkpointStore.isExecutionAllowed(anyString())).thenReturn(true);

        runPromise(() -> executionQueue.enqueue(tenantId, pipelineId, Map.of("data", "test"), idempotencyKey));

        List<ExecutionJob> polledJobs = runPromise(() -> executionQueue.poll(1, 0));

        assertThat(polledJobs).isNotNull().hasSize(1);
        ExecutionJob job = polledJobs.get(0);
        assertThat(job.getTenantId()).isEqualTo(tenantId);
        assertThat(job.getPipelineId()).isEqualTo(pipelineId);
        assertThat(job.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(executionQueue.size()).isEqualTo(0);

        verify(checkpointStore).isExecutionAllowed(anyString());
    }

    @Test
    @DisplayName("poll() skips job when checkpoint does not allow execution")
    void shouldSkipJobWithInvalidCheckpoint() {
        String tenantId = TENANT_ID;
        String pipelineId = "test-pipeline";
        String idempotencyKey = "invalid-key";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(false);
        when(checkpointStore.createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any()))
                .thenReturn(createMockCheckpoint("instance-123", pipelineId, idempotencyKey));
        when(checkpointStore.isExecutionAllowed(anyString())).thenReturn(false);

        runPromise(() -> executionQueue.enqueue(tenantId, pipelineId, Map.of("data", "test"), idempotencyKey));

        List<ExecutionJob> polledJobs = runPromise(() -> executionQueue.poll(1, 0));

        assertThat(polledJobs).isNotNull().isEmpty();
        verify(checkpointStore).isExecutionAllowed(anyString());
    }

    @Test
    @DisplayName("poll() skips job when checkpoint is already completed")
    void shouldSkipJobWithCompletedCheckpoint() {
        String tenantId = TENANT_ID;
        String pipelineId = "test-pipeline";
        String idempotencyKey = "completed-key";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(false);
        when(checkpointStore.createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any()))
                .thenReturn(createMockCheckpoint("instance-123", pipelineId, idempotencyKey));
        when(checkpointStore.isExecutionAllowed(anyString())).thenReturn(false);

        runPromise(() -> executionQueue.enqueue(tenantId, pipelineId, Map.of("data", "test"), idempotencyKey));

        List<ExecutionJob> polledJobs = runPromise(() -> executionQueue.poll(1, 0));

        assertThat(polledJobs).isNotNull().isEmpty();
        verify(checkpointStore).isExecutionAllowed(anyString());
    }

    @Test
    @DisplayName("clear() empties the queue")
    void shouldClearQueue() {
        when(checkpointStore.isDuplicate(anyString(), anyString())).thenReturn(false);
        when(checkpointStore.createExecution(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(createMockCheckpoint("instance", "pipeline", "key"));

        runPromise(() -> executionQueue.enqueue(TENANT_ID, "pipeline-1", Map.of(), "key-1"));
        runPromise(() -> executionQueue.enqueue(TENANT_ID, "pipeline-2", Map.of(), "key-2"));
        assertThat(executionQueue.size()).isEqualTo(2);

        runPromise(() -> executionQueue.clear());

        assertThat(executionQueue.size()).isEqualTo(0);
        assertThat(executionQueue.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("poll() after enqueue returns correct job fields")
    void shouldPeekAtNextJob() {
        String tenantId = TENANT_ID;
        String pipelineId = "peek-pipeline";
        String idempotencyKey = "peek-key";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(false);
        when(checkpointStore.createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any()))
                .thenReturn(createMockCheckpoint("instance-123", pipelineId, idempotencyKey));
        when(checkpointStore.isExecutionAllowed(anyString())).thenReturn(true);

        runPromise(() -> executionQueue.enqueue(tenantId, pipelineId, Map.of("data", "peek"), idempotencyKey));

        List<ExecutionJob> polledJobs = runPromise(() -> executionQueue.poll(1, 0));

        assertThat(polledJobs).isNotNull().hasSize(1);
        ExecutionJob polled = polledJobs.get(0);
        assertThat(polled.getTenantId()).isEqualTo(tenantId);
        assertThat(polled.getPipelineId()).isEqualTo(pipelineId);
        assertThat(polled.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(executionQueue.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("getStatistics() reflects current queue state")
    void shouldProvideQueueStatistics() {
        when(checkpointStore.isDuplicate(anyString(), anyString())).thenReturn(false);
        when(checkpointStore.createExecution(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(createMockCheckpoint("instance", "pipeline", "key"));

        runPromise(() -> executionQueue.enqueue(TENANT_ID, "pipeline-1", Map.of(), "key-1"));
        runPromise(() -> executionQueue.enqueue(TENANT_ID, "pipeline-2", Map.of(), "key-2"));
        runPromise(() -> executionQueue.enqueue(TENANT_ID, "pipeline-3", Map.of(), "key-3"));

        CheckpointAwareExecutionQueue.QueueStatistics stats = executionQueue.getStatistics();

        assertThat(stats.getQueueSize()).isEqualTo(3);
        assertThat(stats.getIdempotencyKeyCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("polled job contains non-null jobId and instanceId")
    void shouldCalculateQueuedDuration() {
        String tenantId = TENANT_ID;
        String pipelineId = "duration-pipeline";
        String idempotencyKey = "duration-key";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(false);
        when(checkpointStore.createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any()))
                .thenReturn(createMockCheckpoint("instance-123", pipelineId, idempotencyKey));
        when(checkpointStore.isExecutionAllowed(anyString())).thenReturn(true);

        runPromise(() -> executionQueue.enqueue(tenantId, pipelineId, Map.of("data", "duration"), idempotencyKey));

        List<ExecutionJob> polledJobs = runPromise(() -> executionQueue.poll(1, 0));
        assertThat(polledJobs).hasSize(1);
        ExecutionJob job = polledJobs.get(0);
        assertThat(job.getJobId()).isNotNull().isNotEmpty();
        assertThat(job.getInstanceId()).isNotNull().startsWith(pipelineId + "-");
    }

    @Test
    @DisplayName("enqueue() propagates CheckpointStore exceptions")
    void shouldHandleCheckpointStoreException() {
        String tenantId = TENANT_ID;
        String pipelineId = "error-pipeline";
        String idempotencyKey = "error-key";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(false);
        when(checkpointStore.createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any()))
                .thenThrow(new RuntimeException("Checkpoint store error"));

        assertThatThrownBy(() -> runPromise(
                        () -> executionQueue.enqueue(tenantId, pipelineId, Map.of("data", "error"), idempotencyKey)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Checkpoint store error");

        clearFatalError();
        assertThat(executionQueue.size()).isEqualTo(0);
    }

    private PipelineCheckpoint createMockCheckpoint(String instanceId, String pipelineId, String idempotencyKey) {
        return new PipelineCheckpoint(
                instanceId,
                TENANT_ID,
                pipelineId,
                idempotencyKey,
                PipelineCheckpointStatus.CREATED,
                Map.of("test", "state"),
                Map.of(),
                Instant.now(),
                Instant.now(),
                null,
                null,
                0,
                5);
    }
}
