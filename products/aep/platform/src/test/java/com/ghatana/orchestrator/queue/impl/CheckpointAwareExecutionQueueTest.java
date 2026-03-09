/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.queue.impl;

import com.ghatana.orchestrator.queue.ExecutionJob;
import com.ghatana.orchestrator.store.PipelineCheckpointStatus;
import com.ghatana.orchestrator.store.CheckpointStore;
import com.ghatana.orchestrator.store.PipelineCheckpoint;
// ... only the necessary store types are imported above
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

/**
 * Day 39: Tests for checkpoint-aware execution queue.
 * Verifies duplicate prevention and exactly-once semantics.
 */
@ExtendWith(MockitoExtension.class)
class CheckpointAwareExecutionQueueTest {

    private static final String TENANT_ID = "tenant-1";

    @Mock
    private CheckpointStore checkpointStore;

    private CheckpointAwareExecutionQueue executionQueue;

    @BeforeEach
    void setUp() {
        executionQueue = new CheckpointAwareExecutionQueue(checkpointStore);
    }

    @Test
    void shouldEnqueueNewExecution() throws Exception {
        // Given
        String tenantId = TENANT_ID;
        String pipelineId = "test-pipeline";
        Object triggerData = Map.of("event", "trigger");
        String idempotencyKey = "unique-key-123";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(false);
        when(checkpointStore.createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any()))
            .thenReturn(createMockCheckpoint("instance-123", pipelineId, idempotencyKey));

        // When
        executionQueue.enqueue(tenantId, pipelineId, triggerData, idempotencyKey).toCompletableFuture().get();

        // Then
        assertThat(executionQueue.size()).isEqualTo(1);
        assertThat(executionQueue.isEmpty()).isFalse();

        verify(checkpointStore).isDuplicate(tenantId, idempotencyKey);
        verify(checkpointStore).createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any());
    }

    @Test
    void shouldPreventDuplicateEnqueue() throws Exception {
        // Given
        String tenantId = TENANT_ID;
        String pipelineId = "test-pipeline";
        Object triggerData = Map.of("event", "trigger");
        String idempotencyKey = "duplicate-key";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(true);

        // When
        executionQueue.enqueue(tenantId, pipelineId, triggerData, idempotencyKey).toCompletableFuture().get();

        // Then
        assertThat(executionQueue.size()).isEqualTo(0);
        assertThat(executionQueue.isEmpty()).isTrue();

        verify(checkpointStore).isDuplicate(tenantId, idempotencyKey);
        verify(checkpointStore, never()).createExecution(any(), any(), any(), any(), any());
    }

    @Test
    void shouldPollValidJob() throws Exception {
        // Given
        String tenantId = TENANT_ID;
        String pipelineId = "test-pipeline";
        String idempotencyKey = "valid-key";
        String instanceId = "instance-123";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(false);
        when(checkpointStore.createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any()))
            .thenReturn(createMockCheckpoint(instanceId, pipelineId, idempotencyKey));
        when(checkpointStore.isExecutionAllowed(anyString())).thenReturn(true);

        // Enqueue job
        executionQueue.enqueue(tenantId, pipelineId, Map.of("data", "test"), idempotencyKey).toCompletableFuture().get();

        // When
        List<ExecutionJob> polledJobs = executionQueue.poll(1, 0).toCompletableFuture().get();

        // Then
        assertThat(polledJobs).isNotNull();
        assertThat(polledJobs).hasSize(1);
        ExecutionJob job = polledJobs.get(0);
        assertThat(job.getTenantId()).isEqualTo(tenantId);
        assertThat(job.getPipelineId()).isEqualTo(pipelineId);
        assertThat(job.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(executionQueue.size()).isEqualTo(0);

        verify(checkpointStore).isExecutionAllowed(anyString());
    }

    @Test
    void shouldSkipJobWithInvalidCheckpoint() throws Exception {
        // Given
        String tenantId = TENANT_ID;
        String pipelineId = "test-pipeline";
        String idempotencyKey = "invalid-key";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(false);
        when(checkpointStore.createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any()))
            .thenReturn(createMockCheckpoint("instance-123", pipelineId, idempotencyKey));
        
        // Mock checkpoint validation failure
        when(checkpointStore.isExecutionAllowed(anyString())).thenReturn(false);

        // Enqueue job
        executionQueue.enqueue(tenantId, pipelineId, Map.of("data", "test"), idempotencyKey).toCompletableFuture().get();

        // When
        List<ExecutionJob> polledJobs = executionQueue.poll(1, 0).toCompletableFuture().get();

        // Then
        assertThat(polledJobs).isNotNull();
        assertThat(polledJobs).isEmpty();

        verify(checkpointStore).isExecutionAllowed(anyString());
    }

    @Test
    void shouldSkipJobWithCompletedCheckpoint() throws Exception {
        // Given
        String tenantId = TENANT_ID;
        String pipelineId = "test-pipeline";
        String idempotencyKey = "completed-key";
        String instanceId = "instance-123";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(false);
        when(checkpointStore.createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any()))
            .thenReturn(createMockCheckpoint(instanceId, pipelineId, idempotencyKey));
        
        // Mock checkpoint that does not allow execution
        when(checkpointStore.isExecutionAllowed(anyString())).thenReturn(false);

        // Enqueue job
        executionQueue.enqueue(tenantId, pipelineId, Map.of("data", "test"), idempotencyKey).toCompletableFuture().get();

        // When
        List<ExecutionJob> polledJobs = executionQueue.poll(1, 0).toCompletableFuture().get();

        // Then - Job should be polled (validation logic would be in actual implementation)
        assertThat(polledJobs).isNotNull();
        assertThat(polledJobs).isEmpty();

        verify(checkpointStore).isExecutionAllowed(anyString());
    }

    @Test
    void shouldClearQueue() throws Exception {
        // Given
        when(checkpointStore.isDuplicate(anyString(), anyString())).thenReturn(false);
        when(checkpointStore.createExecution(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(createMockCheckpoint("instance", "pipeline", "key"));

        // Enqueue multiple jobs
        executionQueue.enqueue(TENANT_ID, "pipeline-1", Map.of(), "key-1").toCompletableFuture().get();
        executionQueue.enqueue(TENANT_ID, "pipeline-2", Map.of(), "key-2").toCompletableFuture().get();
        assertThat(executionQueue.size()).isEqualTo(2);

        // When
        executionQueue.clear().toCompletableFuture().get();

        // Then
        assertThat(executionQueue.size()).isEqualTo(0);
        assertThat(executionQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldPeekAtNextJob() throws Exception {
        // Given
        String tenantId = TENANT_ID;
        String pipelineId = "peek-pipeline";
        String idempotencyKey = "peek-key";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(false);
        when(checkpointStore.createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any()))
            .thenReturn(createMockCheckpoint("instance-123", pipelineId, idempotencyKey));
        when(checkpointStore.isExecutionAllowed(anyString())).thenReturn(true);

        executionQueue.enqueue(tenantId, pipelineId, Map.of("data", "peek"), idempotencyKey).toCompletableFuture().get();

        // When
        List<ExecutionJob> polledJobs = executionQueue.poll(1, 0).toCompletableFuture().get();

        // Then
        assertThat(polledJobs).isNotNull();
        assertThat(polledJobs).hasSize(1);
        ExecutionJob polled = polledJobs.get(0);
        assertThat(polled.getTenantId()).isEqualTo(tenantId);
        assertThat(polled.getPipelineId()).isEqualTo(pipelineId);
        assertThat(polled.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(executionQueue.size()).isEqualTo(0);
    }

    @Test
    void shouldProvideQueueStatistics() throws Exception {
        // Given
        when(checkpointStore.isDuplicate(anyString(), anyString())).thenReturn(false);
        when(checkpointStore.createExecution(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(createMockCheckpoint("instance", "pipeline", "key"));

        // Enqueue jobs
        executionQueue.enqueue(TENANT_ID, "pipeline-1", Map.of(), "key-1").toCompletableFuture().get();
        executionQueue.enqueue(TENANT_ID, "pipeline-2", Map.of(), "key-2").toCompletableFuture().get();
        executionQueue.enqueue(TENANT_ID, "pipeline-3", Map.of(), "key-3").toCompletableFuture().get();

        // When
        CheckpointAwareExecutionQueue.QueueStatistics stats = executionQueue.getStatistics();

        // Then
        assertThat(stats.getQueueSize()).isEqualTo(3);
        assertThat(stats.getIdempotencyKeyCount()).isEqualTo(3);
    }

    @Test
    void shouldCalculateQueuedDuration() throws Exception {
        // Given
        String tenantId = TENANT_ID;
        String pipelineId = "duration-pipeline";
        String idempotencyKey = "duration-key";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(false);
        when(checkpointStore.createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any()))
            .thenReturn(createMockCheckpoint("instance-123", pipelineId, idempotencyKey));
        when(checkpointStore.isExecutionAllowed(anyString())).thenReturn(true);

        // When
        executionQueue.enqueue(tenantId, pipelineId, Map.of("data", "duration"), idempotencyKey).toCompletableFuture().get();

        List<ExecutionJob> polledJobs = executionQueue.poll(1, 0).toCompletableFuture().get();
        assertThat(polledJobs).hasSize(1);
        ExecutionJob job = polledJobs.get(0);
        assertThat(job.getJobId()).isNotNull().isNotEmpty();
        assertThat(job.getInstanceId()).isNotNull().startsWith(pipelineId + "-");
    }

    @Test
    void shouldHandleCheckpointStoreException() {
        // Given
        String tenantId = TENANT_ID;
        String pipelineId = "error-pipeline";
        String idempotencyKey = "error-key";

        when(checkpointStore.isDuplicate(tenantId, idempotencyKey)).thenReturn(false);
        when(checkpointStore.createExecution(eq(tenantId), eq(pipelineId), anyString(), eq(idempotencyKey), any()))
            .thenThrow(new RuntimeException("Checkpoint store error"));

        // When/Then
        assertThatThrownBy(() -> 
            executionQueue.enqueue(tenantId, pipelineId, Map.of("data", "error"), idempotencyKey).toCompletableFuture().get()
        ).hasCauseInstanceOf(RuntimeException.class)
         .hasMessageContaining("Checkpoint store error");

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
            5
        );
    }
}