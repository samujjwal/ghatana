/*
 * Copyright (c) 2024 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.orchestrator.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Day 39: Unit tests for PostgreSQL checkpoint store implementation.
 * Tests duplicate prevention, checkpoint management, and exactly-once semantics.
 */
@ExtendWith(MockitoExtension.class) // GH-90000
class PostgresqlCheckpointStoreTest {

    private static final String TENANT_ID = "tenant-1";

    @Mock
    private PipelineCheckpointRepository pipelineRepository;

    @Mock
    private StepCheckpointRepository stepRepository;

    private PostgresqlCheckpointStore checkpointStore;

    @BeforeEach
    void setUp() { // GH-90000
        checkpointStore = new PostgresqlCheckpointStore(pipelineRepository, stepRepository); // GH-90000
    }

    @Test
    void shouldCreateExecutionCheckpoint() { // GH-90000
        // Given
        String tenantId = TENANT_ID;
        String pipelineId = "test-pipeline";
        String instanceId = "instance-123";
        String idempotencyKey = "key-456";
        Map<String, Object> initialState = Map.of("input", "test"); // GH-90000

        PipelineCheckpointEntity savedEntity = createTestEntity(instanceId, pipelineId, idempotencyKey); // GH-90000
        when(pipelineRepository.existsByIdempotencyKey(tenantId, idempotencyKey)) // GH-90000
                .thenReturn(false); // GH-90000
        when(pipelineRepository.save(any(PipelineCheckpointEntity.class))).thenReturn(savedEntity); // GH-90000

        // When
        PipelineCheckpoint checkpoint =
                checkpointStore.createExecution(tenantId, pipelineId, instanceId, idempotencyKey, initialState); // GH-90000

        // Then
        assertThat(checkpoint).isNotNull(); // GH-90000
        assertThat(checkpoint.getInstanceId()).isEqualTo(instanceId); // GH-90000
        assertThat(checkpoint.getPipelineId()).isEqualTo(pipelineId); // GH-90000
        assertThat(checkpoint.getIdempotencyKey()).isEqualTo(idempotencyKey); // GH-90000
        assertThat(checkpoint.getStatus()).isEqualTo(PipelineCheckpointStatus.CREATED); // GH-90000

        verify(pipelineRepository).existsByIdempotencyKey(tenantId, idempotencyKey); // GH-90000
        verify(pipelineRepository).save(any(PipelineCheckpointEntity.class)); // GH-90000
    }

    @Test
    void shouldPreventDuplicateExecution() { // GH-90000
        // Given
        String tenantId = TENANT_ID;
        String idempotencyKey = "duplicate-key";
        when(pipelineRepository.existsByIdempotencyKey(tenantId, idempotencyKey)) // GH-90000
                .thenReturn(true); // GH-90000

        // When/Then
        assertThatThrownBy(() -> // GH-90000
                        checkpointStore.createExecution(tenantId, "pipeline", "instance", idempotencyKey, Map.of())) // GH-90000
                .isInstanceOf(RuntimeException.class) // GH-90000
                .hasMessageContaining("already exists [GH-90000]");

        verify(pipelineRepository).existsByIdempotencyKey(tenantId, idempotencyKey); // GH-90000
        verify(pipelineRepository, never()).save(any()); // GH-90000
    }

    @Test
    void shouldUpdateCheckpointWithStepProgress() { // GH-90000
        // Given
        String instanceId = "instance-123";
        String stepId = "step-1";
        String stepName = "Test Step";
        PipelineCheckpointStatus status = PipelineCheckpointStatus.STEP_SUCCESS;
        Map<String, Object> result = Map.of("output", "success"); // GH-90000
        Map<String, Object> state = Map.of("progress", 50); // GH-90000

        PipelineCheckpointEntity existingEntity = createTestEntity(instanceId, "pipeline", "key"); // GH-90000
        existingEntity.setCompletedSteps(0); // GH-90000
        PipelineCheckpointEntity updatedEntity = createTestEntity(instanceId, "pipeline", "key"); // GH-90000
        updatedEntity.setCompletedSteps(1); // GH-90000
        updatedEntity.setCurrentStepId(stepId); // GH-90000
        updatedEntity.setCurrentStepName(stepName); // GH-90000

        when(pipelineRepository.findById(instanceId)).thenReturn(Optional.of(existingEntity)); // GH-90000
        when(pipelineRepository.save(any(PipelineCheckpointEntity.class))).thenReturn(updatedEntity); // GH-90000

        // When
        PipelineCheckpoint checkpoint =
                checkpointStore.updateCheckpoint(instanceId, stepId, stepName, status, result, state); // GH-90000

        // Then
        assertThat(checkpoint.getCurrentStepId()).isEqualTo(stepId); // GH-90000
        assertThat(checkpoint.getCurrentStepName()).isEqualTo(stepName); // GH-90000
        assertThat(checkpoint.getCompletedSteps()).isEqualTo(1); // GH-90000

        verify(pipelineRepository).findById(instanceId); // GH-90000
        verify(pipelineRepository).save(any(PipelineCheckpointEntity.class)); // GH-90000
    }

    @Test
    void shouldThrowExceptionWhenUpdatingNonExistentCheckpoint() { // GH-90000
        // Given
        String instanceId = "non-existent";
        when(pipelineRepository.findById(instanceId)).thenReturn(Optional.empty()); // GH-90000

        // When/Then
        assertThatThrownBy(() -> checkpointStore.updateCheckpoint( // GH-90000
                        instanceId, "step", "name", PipelineCheckpointStatus.STEP_SUCCESS, null, null))
                .isInstanceOf(RuntimeException.class) // GH-90000
                .hasMessageContaining("not found [GH-90000]");

        verify(pipelineRepository).findById(instanceId); // GH-90000
        verify(pipelineRepository, never()).save(any()); // GH-90000
    }

    @Test
    void shouldFindCheckpointByInstanceId() { // GH-90000
        // Given
        String instanceId = "instance-123";
        PipelineCheckpointEntity entity = createTestEntity(instanceId, "pipeline", "key"); // GH-90000
        when(pipelineRepository.findById(instanceId)).thenReturn(Optional.of(entity)); // GH-90000

        // When
        Optional<PipelineCheckpoint> result = checkpointStore.findByInstanceId(instanceId); // GH-90000

        // Then
        assertThat(result).isPresent(); // GH-90000
        assertThat(result.get().getInstanceId()).isEqualTo(instanceId); // GH-90000

        verify(pipelineRepository).findById(instanceId); // GH-90000
    }

    @Test
    void shouldFindCheckpointByIdempotencyKey() { // GH-90000
        // Given
        String tenantId = TENANT_ID;
        String idempotencyKey = "key-123";
        PipelineCheckpointEntity entity = createTestEntity("instance", "pipeline", idempotencyKey); // GH-90000
        when(pipelineRepository.findByIdempotencyKey(tenantId, idempotencyKey)).thenReturn(Optional.of(entity)); // GH-90000

        // When
        Optional<PipelineCheckpoint> result = checkpointStore.findByIdempotencyKey(tenantId, idempotencyKey); // GH-90000

        // Then
        assertThat(result).isPresent(); // GH-90000
        assertThat(result.get().getIdempotencyKey()).isEqualTo(idempotencyKey); // GH-90000

        verify(pipelineRepository).findByIdempotencyKey(tenantId, idempotencyKey); // GH-90000
    }

    @Test
    void shouldFindCheckpointsByPipelineId() { // GH-90000
        // Given
        String tenantId = TENANT_ID;
        String pipelineId = "test-pipeline";
        int limit = 10;
        List<PipelineCheckpointEntity> entities = Arrays.asList( // GH-90000
                createTestEntity("instance-1", pipelineId, "key-1"), // GH-90000
                createTestEntity("instance-2", pipelineId, "key-2")); // GH-90000
        when(pipelineRepository.findByPipelineIdOrderByCreatedAtDesc(tenantId, pipelineId)) // GH-90000
                .thenReturn(entities); // GH-90000

        // When
        List<PipelineCheckpoint> result = checkpointStore.findByPipelineId(tenantId, pipelineId, limit); // GH-90000

        // Then
        assertThat(result).hasSize(2); // GH-90000
        assertThat(result.get(0).getPipelineId()).isEqualTo(pipelineId); // GH-90000
        assertThat(result.get(1).getPipelineId()).isEqualTo(pipelineId); // GH-90000

        verify(pipelineRepository).findByPipelineIdOrderByCreatedAtDesc(tenantId, pipelineId); // GH-90000
    }

    @Test
    void shouldFindActiveExecutions() { // GH-90000
        // Given
        int limit = 5;
        List<PipelineCheckpointEntity> entities = Arrays.asList( // GH-90000
                createTestEntity("instance-1", "pipeline-1", "key-1"), // GH-90000
                createTestEntity("instance-2", "pipeline-2", "key-2")); // GH-90000
        when(pipelineRepository.findActiveExecutions()).thenReturn(entities); // GH-90000

        // When
        List<PipelineCheckpoint> result = checkpointStore.findActive(limit); // GH-90000

        // Then
        assertThat(result).hasSize(2); // GH-90000

        verify(pipelineRepository).findActiveExecutions(); // GH-90000
    }

    @Test
    void shouldFindStaleExecutions() { // GH-90000
        // Given
        Instant staleBefore = Instant.now().minus(1, ChronoUnit.HOURS); // GH-90000
        List<PipelineCheckpointEntity> entities = Arrays.asList( // GH-90000
                createTestEntity("stale-1", "pipeline-1", "key-1"), createTestEntity("stale-2", "pipeline-2", "key-2")); // GH-90000
        when(pipelineRepository.findStaleExecutions(staleBefore)).thenReturn(entities); // GH-90000

        // When
        List<PipelineCheckpoint> result = checkpointStore.findStale(staleBefore); // GH-90000

        // Then
        assertThat(result).hasSize(2); // GH-90000

        verify(pipelineRepository).findStaleExecutions(staleBefore); // GH-90000
    }

    @Test
    void shouldCompleteExecution() { // GH-90000
        // Given
        String instanceId = "instance-123";
        PipelineCheckpointStatus finalStatus = PipelineCheckpointStatus.COMPLETED;
        Map<String, Object> finalResult = Map.of("result", "success"); // GH-90000

        PipelineCheckpointEntity entity = createTestEntity(instanceId, "pipeline", "key"); // GH-90000
        when(pipelineRepository.findById(instanceId)).thenReturn(Optional.of(entity)); // GH-90000
        when(pipelineRepository.save(any(PipelineCheckpointEntity.class))).thenReturn(entity); // GH-90000

        // When
        checkpointStore.completeExecution(instanceId, finalStatus, finalResult); // GH-90000

        // Then
        verify(pipelineRepository).findById(instanceId); // GH-90000
        verify(pipelineRepository).save(any(PipelineCheckpointEntity.class)); // GH-90000
    }

    @Test
    void shouldCleanupOldCheckpoints() { // GH-90000
        // Given
        Instant completedBefore = Instant.now().minus(7, ChronoUnit.DAYS); // GH-90000
        when(stepRepository.deleteForCompletedPipelines(completedBefore)).thenReturn(10); // GH-90000
        when(pipelineRepository.deleteCompletedBefore(completedBefore)).thenReturn(5); // GH-90000

        // When
        int deleted = checkpointStore.cleanupOldCheckpoints(completedBefore); // GH-90000

        // Then
        assertThat(deleted).isEqualTo(5); // GH-90000

        verify(stepRepository).deleteForCompletedPipelines(completedBefore); // GH-90000
        verify(pipelineRepository).deleteCompletedBefore(completedBefore); // GH-90000
    }

    @Test
    void shouldDetectDuplicates() { // GH-90000
        // Given
        String tenantId = TENANT_ID;
        String idempotencyKey = "duplicate-key";
        when(pipelineRepository.existsByIdempotencyKey(tenantId, idempotencyKey)) // GH-90000
                .thenReturn(true); // GH-90000

        // When
        boolean isDuplicate = checkpointStore.isDuplicate(tenantId, idempotencyKey); // GH-90000

        // Then
        assertThat(isDuplicate).isTrue(); // GH-90000

        verify(pipelineRepository).existsByIdempotencyKey(tenantId, idempotencyKey); // GH-90000
    }

    @Test
    void shouldGetLastSuccessfulStep() { // GH-90000
        // Given
        String instanceId = "instance-123";
        StepCheckpointEntity stepEntity = createTestStepEntity(instanceId, "step-1", "Test Step"); // GH-90000
        when(stepRepository.findLastSuccessfulStep(instanceId)).thenReturn(Optional.of(stepEntity)); // GH-90000

        // When
        Optional<StepCheckpoint> result = checkpointStore.getLastSuccessfulStep(instanceId); // GH-90000

        // Then
        assertThat(result).isPresent(); // GH-90000
        assertThat(result.get().getStepId()).isEqualTo("step-1 [GH-90000]");

        verify(stepRepository).findLastSuccessfulStep(instanceId); // GH-90000
    }

    @Test
    void shouldRecordStepCheckpoint() { // GH-90000
        // Given
        String instanceId = "instance-123";
        StepCheckpoint stepCheckpoint = new StepCheckpoint( // GH-90000
                "step-1",
                "Test Step",
                PipelineCheckpointStatus.STEP_SUCCESS,
                Map.of("input", "test"), // GH-90000
                Map.of("output", "result"), // GH-90000
                Instant.now(), // GH-90000
                Instant.now(), // GH-90000
                null,
                0);

        when(stepRepository.findByInstanceIdAndStepId(instanceId, "step-1")).thenReturn(Optional.empty()); // GH-90000
        when(stepRepository.save(any(StepCheckpointEntity.class))).thenReturn(new StepCheckpointEntity()); // GH-90000

        // When
        checkpointStore.recordStepCheckpoint(instanceId, stepCheckpoint); // GH-90000

        // Then
        verify(stepRepository).findByInstanceIdAndStepId(instanceId, "step-1"); // GH-90000
        verify(stepRepository).save(any(StepCheckpointEntity.class)); // GH-90000
    }

    @Test
    void shouldUpdateExistingStepCheckpoint() { // GH-90000
        // Given
        String instanceId = "instance-123";
        String stepId = "step-1";
        StepCheckpoint stepCheckpoint = new StepCheckpoint( // GH-90000
                stepId,
                "Test Step",
                PipelineCheckpointStatus.STEP_SUCCESS,
                Map.of("input", "test"), // GH-90000
                Map.of("output", "result"), // GH-90000
                Instant.now(), // GH-90000
                Instant.now(), // GH-90000
                null,
                1);

        StepCheckpointEntity existingEntity = createTestStepEntity(instanceId, stepId, "Test Step"); // GH-90000
        when(stepRepository.findByInstanceIdAndStepId(instanceId, stepId)).thenReturn(Optional.of(existingEntity)); // GH-90000
        when(stepRepository.save(any(StepCheckpointEntity.class))).thenReturn(existingEntity); // GH-90000

        // When
        checkpointStore.recordStepCheckpoint(instanceId, stepCheckpoint); // GH-90000

        // Then
        verify(stepRepository).findByInstanceIdAndStepId(instanceId, stepId); // GH-90000
        verify(stepRepository).save(existingEntity); // GH-90000
    }

    private PipelineCheckpointEntity createTestEntity(String instanceId, String pipelineId, String idempotencyKey) { // GH-90000
        PipelineCheckpointEntity entity = new PipelineCheckpointEntity(); // GH-90000
        entity.setInstanceId(instanceId); // GH-90000
        entity.setPipelineId(pipelineId); // GH-90000
        entity.setIdempotencyKey(idempotencyKey); // GH-90000
        entity.setStatus(PipelineCheckpointStatus.CREATED); // GH-90000
        entity.setState(Map.of("test", "state")); // GH-90000
        entity.setCreatedAt(Instant.now()); // GH-90000
        entity.setUpdatedAt(Instant.now()); // GH-90000
        entity.setCompletedSteps(0); // GH-90000
        entity.setTotalSteps(5); // GH-90000
        return entity;
    }

    private StepCheckpointEntity createTestStepEntity(String instanceId, String stepId, String stepName) { // GH-90000
        StepCheckpointEntity entity = new StepCheckpointEntity(); // GH-90000
        entity.setId(1L); // GH-90000
        entity.setInstanceId(instanceId); // GH-90000
        entity.setStepId(stepId); // GH-90000
        entity.setStepName(stepName); // GH-90000
        entity.setStatus(PipelineCheckpointStatus.STEP_SUCCESS); // GH-90000
        entity.setInput(Map.of("input", "test")); // GH-90000
        entity.setOutput(Map.of("output", "result")); // GH-90000
        entity.setStartedAt(Instant.now()); // GH-90000
        entity.setCompletedAt(Instant.now()); // GH-90000
        entity.setRetryCount(0); // GH-90000
        return entity;
    }
}
