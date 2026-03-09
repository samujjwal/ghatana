/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

/**
 * Day 39: Unit tests for PostgreSQL checkpoint store implementation.
 * Tests duplicate prevention, checkpoint management, and exactly-once semantics.
 */
@ExtendWith(MockitoExtension.class)
class PostgresqlCheckpointStoreTest {

    private static final String TENANT_ID = "tenant-1";

    @Mock
    private PipelineCheckpointRepository pipelineRepository;

    @Mock
    private StepCheckpointRepository stepRepository;

    private PostgresqlCheckpointStore checkpointStore;

    @BeforeEach
    void setUp() {
        checkpointStore = new PostgresqlCheckpointStore(pipelineRepository, stepRepository);
    }

    @Test
    void shouldCreateExecutionCheckpoint() {
        // Given
        String tenantId = TENANT_ID;
        String pipelineId = "test-pipeline";
        String instanceId = "instance-123";
        String idempotencyKey = "key-456";
        Map<String, Object> initialState = Map.of("input", "test");

        PipelineCheckpointEntity savedEntity = createTestEntity(instanceId, pipelineId, idempotencyKey);
        when(pipelineRepository.existsByIdempotencyKey(tenantId, idempotencyKey)).thenReturn(false);
        when(pipelineRepository.save(any(PipelineCheckpointEntity.class))).thenReturn(savedEntity);

        // When
        PipelineCheckpoint checkpoint = checkpointStore.createExecution(tenantId, pipelineId, instanceId, idempotencyKey, initialState);

        // Then
        assertThat(checkpoint).isNotNull();
        assertThat(checkpoint.getInstanceId()).isEqualTo(instanceId);
        assertThat(checkpoint.getPipelineId()).isEqualTo(pipelineId);
        assertThat(checkpoint.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(checkpoint.getStatus()).isEqualTo(PipelineCheckpointStatus.CREATED);

        verify(pipelineRepository).existsByIdempotencyKey(tenantId, idempotencyKey);
        verify(pipelineRepository).save(any(PipelineCheckpointEntity.class));
    }

    @Test
    void shouldPreventDuplicateExecution() {
        // Given
        String tenantId = TENANT_ID;
        String idempotencyKey = "duplicate-key";
        when(pipelineRepository.existsByIdempotencyKey(tenantId, idempotencyKey)).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> 
            checkpointStore.createExecution(tenantId, "pipeline", "instance", idempotencyKey, Map.of())
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("already exists");

        verify(pipelineRepository).existsByIdempotencyKey(tenantId, idempotencyKey);
        verify(pipelineRepository, never()).save(any());
    }

    @Test
    void shouldUpdateCheckpointWithStepProgress() {
        // Given
        String instanceId = "instance-123";
        String stepId = "step-1";
        String stepName = "Test Step";
        PipelineCheckpointStatus status = PipelineCheckpointStatus.STEP_SUCCESS;
        Map<String, Object> result = Map.of("output", "success");
        Map<String, Object> state = Map.of("progress", 50);

        PipelineCheckpointEntity existingEntity = createTestEntity(instanceId, "pipeline", "key");
        existingEntity.setCompletedSteps(0);
        PipelineCheckpointEntity updatedEntity = createTestEntity(instanceId, "pipeline", "key");
        updatedEntity.setCompletedSteps(1);
        updatedEntity.setCurrentStepId(stepId);
        updatedEntity.setCurrentStepName(stepName);

        when(pipelineRepository.findById(instanceId)).thenReturn(Optional.of(existingEntity));
        when(pipelineRepository.save(any(PipelineCheckpointEntity.class))).thenReturn(updatedEntity);

        // When
        PipelineCheckpoint checkpoint = checkpointStore.updateCheckpoint(instanceId, stepId, stepName, status, result, state);

        // Then
        assertThat(checkpoint.getCurrentStepId()).isEqualTo(stepId);
        assertThat(checkpoint.getCurrentStepName()).isEqualTo(stepName);
        assertThat(checkpoint.getCompletedSteps()).isEqualTo(1);

        verify(pipelineRepository).findById(instanceId);
        verify(pipelineRepository).save(any(PipelineCheckpointEntity.class));
    }

    @Test
    void shouldThrowExceptionWhenUpdatingNonExistentCheckpoint() {
        // Given
        String instanceId = "non-existent";
        when(pipelineRepository.findById(instanceId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> 
            checkpointStore.updateCheckpoint(instanceId, "step", "name", PipelineCheckpointStatus.STEP_SUCCESS, null, null)
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("not found");

        verify(pipelineRepository).findById(instanceId);
        verify(pipelineRepository, never()).save(any());
    }

    @Test
    void shouldFindCheckpointByInstanceId() {
        // Given
        String instanceId = "instance-123";
        PipelineCheckpointEntity entity = createTestEntity(instanceId, "pipeline", "key");
        when(pipelineRepository.findById(instanceId)).thenReturn(Optional.of(entity));

        // When
        Optional<PipelineCheckpoint> result = checkpointStore.findByInstanceId(instanceId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getInstanceId()).isEqualTo(instanceId);

        verify(pipelineRepository).findById(instanceId);
    }

    @Test
    void shouldFindCheckpointByIdempotencyKey() {
        // Given
        String tenantId = TENANT_ID;
        String idempotencyKey = "key-123";
        PipelineCheckpointEntity entity = createTestEntity("instance", "pipeline", idempotencyKey);
        when(pipelineRepository.findByIdempotencyKey(tenantId, idempotencyKey)).thenReturn(Optional.of(entity));

        // When
        Optional<PipelineCheckpoint> result = checkpointStore.findByIdempotencyKey(tenantId, idempotencyKey);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getIdempotencyKey()).isEqualTo(idempotencyKey);

        verify(pipelineRepository).findByIdempotencyKey(tenantId, idempotencyKey);
    }

    @Test
    void shouldFindCheckpointsByPipelineId() {
        // Given
        String tenantId = TENANT_ID;
        String pipelineId = "test-pipeline";
        int limit = 10;
        List<PipelineCheckpointEntity> entities = Arrays.asList(
            createTestEntity("instance-1", pipelineId, "key-1"),
            createTestEntity("instance-2", pipelineId, "key-2")
        );
        when(pipelineRepository.findByPipelineIdOrderByCreatedAtDesc(tenantId, pipelineId)).thenReturn(entities);

        // When
        List<PipelineCheckpoint> result = checkpointStore.findByPipelineId(tenantId, pipelineId, limit);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPipelineId()).isEqualTo(pipelineId);
        assertThat(result.get(1).getPipelineId()).isEqualTo(pipelineId);

        verify(pipelineRepository).findByPipelineIdOrderByCreatedAtDesc(tenantId, pipelineId);
    }

    @Test
    void shouldFindActiveExecutions() {
        // Given
        int limit = 5;
        List<PipelineCheckpointEntity> entities = Arrays.asList(
            createTestEntity("instance-1", "pipeline-1", "key-1"),
            createTestEntity("instance-2", "pipeline-2", "key-2")
        );
        when(pipelineRepository.findActiveExecutions()).thenReturn(entities);

        // When
        List<PipelineCheckpoint> result = checkpointStore.findActive(limit);

        // Then
        assertThat(result).hasSize(2);

        verify(pipelineRepository).findActiveExecutions();
    }

    @Test
    void shouldFindStaleExecutions() {
        // Given
        Instant staleBefore = Instant.now().minus(1, ChronoUnit.HOURS);
        List<PipelineCheckpointEntity> entities = Arrays.asList(
            createTestEntity("stale-1", "pipeline-1", "key-1"),
            createTestEntity("stale-2", "pipeline-2", "key-2")
        );
        when(pipelineRepository.findStaleExecutions(staleBefore)).thenReturn(entities);

        // When
        List<PipelineCheckpoint> result = checkpointStore.findStale(staleBefore);

        // Then
        assertThat(result).hasSize(2);

        verify(pipelineRepository).findStaleExecutions(staleBefore);
    }

    @Test
    void shouldCompleteExecution() {
        // Given
        String instanceId = "instance-123";
        PipelineCheckpointStatus finalStatus = PipelineCheckpointStatus.COMPLETED;
        Map<String, Object> finalResult = Map.of("result", "success");

        PipelineCheckpointEntity entity = createTestEntity(instanceId, "pipeline", "key");
        when(pipelineRepository.findById(instanceId)).thenReturn(Optional.of(entity));
        when(pipelineRepository.save(any(PipelineCheckpointEntity.class))).thenReturn(entity);

        // When
        checkpointStore.completeExecution(instanceId, finalStatus, finalResult);

        // Then
        verify(pipelineRepository).findById(instanceId);
        verify(pipelineRepository).save(any(PipelineCheckpointEntity.class));
    }

    @Test
    void shouldCleanupOldCheckpoints() {
        // Given
        Instant completedBefore = Instant.now().minus(7, ChronoUnit.DAYS);
        when(stepRepository.deleteForCompletedPipelines(completedBefore)).thenReturn(10);
        when(pipelineRepository.deleteCompletedBefore(completedBefore)).thenReturn(5);

        // When
        int deleted = checkpointStore.cleanupOldCheckpoints(completedBefore);

        // Then
        assertThat(deleted).isEqualTo(5);

        verify(stepRepository).deleteForCompletedPipelines(completedBefore);
        verify(pipelineRepository).deleteCompletedBefore(completedBefore);
    }

    @Test
    void shouldDetectDuplicates() {
        // Given
        String tenantId = TENANT_ID;
        String idempotencyKey = "duplicate-key";
        when(pipelineRepository.existsByIdempotencyKey(tenantId, idempotencyKey)).thenReturn(true);

        // When
        boolean isDuplicate = checkpointStore.isDuplicate(tenantId, idempotencyKey);

        // Then
        assertThat(isDuplicate).isTrue();

        verify(pipelineRepository).existsByIdempotencyKey(tenantId, idempotencyKey);
    }

    @Test
    void shouldGetLastSuccessfulStep() {
        // Given
        String instanceId = "instance-123";
        StepCheckpointEntity stepEntity = createTestStepEntity(instanceId, "step-1", "Test Step");
        when(stepRepository.findLastSuccessfulStep(instanceId)).thenReturn(Optional.of(stepEntity));

        // When
        Optional<StepCheckpoint> result = checkpointStore.getLastSuccessfulStep(instanceId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getStepId()).isEqualTo("step-1");

        verify(stepRepository).findLastSuccessfulStep(instanceId);
    }

    @Test
    void shouldRecordStepCheckpoint() {
        // Given
        String instanceId = "instance-123";
        StepCheckpoint stepCheckpoint = new StepCheckpoint(
            "step-1", "Test Step", PipelineCheckpointStatus.STEP_SUCCESS,
            Map.of("input", "test"), Map.of("output", "result"),
            Instant.now(), Instant.now(), null, 0
        );

        when(stepRepository.findByInstanceIdAndStepId(instanceId, "step-1")).thenReturn(Optional.empty());
        when(stepRepository.save(any(StepCheckpointEntity.class))).thenReturn(new StepCheckpointEntity());

        // When
        checkpointStore.recordStepCheckpoint(instanceId, stepCheckpoint);

        // Then
        verify(stepRepository).findByInstanceIdAndStepId(instanceId, "step-1");
        verify(stepRepository).save(any(StepCheckpointEntity.class));
    }

    @Test
    void shouldUpdateExistingStepCheckpoint() {
        // Given
        String instanceId = "instance-123";
        String stepId = "step-1";
        StepCheckpoint stepCheckpoint = new StepCheckpoint(
            stepId, "Test Step", PipelineCheckpointStatus.STEP_SUCCESS,
            Map.of("input", "test"), Map.of("output", "result"),
            Instant.now(), Instant.now(), null, 1
        );

        StepCheckpointEntity existingEntity = createTestStepEntity(instanceId, stepId, "Test Step");
        when(stepRepository.findByInstanceIdAndStepId(instanceId, stepId)).thenReturn(Optional.of(existingEntity));
        when(stepRepository.save(any(StepCheckpointEntity.class))).thenReturn(existingEntity);

        // When
        checkpointStore.recordStepCheckpoint(instanceId, stepCheckpoint);

        // Then
        verify(stepRepository).findByInstanceIdAndStepId(instanceId, stepId);
        verify(stepRepository).save(existingEntity);
    }

    private PipelineCheckpointEntity createTestEntity(String instanceId, String pipelineId, String idempotencyKey) {
        PipelineCheckpointEntity entity = new PipelineCheckpointEntity();
        entity.setInstanceId(instanceId);
        entity.setPipelineId(pipelineId);
        entity.setIdempotencyKey(idempotencyKey);
        entity.setStatus(PipelineCheckpointStatus.CREATED);
        entity.setState(Map.of("test", "state"));
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        entity.setCompletedSteps(0);
        entity.setTotalSteps(5);
        return entity;
    }

    private StepCheckpointEntity createTestStepEntity(String instanceId, String stepId, String stepName) {
        StepCheckpointEntity entity = new StepCheckpointEntity();
        entity.setId(1L);
        entity.setInstanceId(instanceId);
        entity.setStepId(stepId);
        entity.setStepName(stepName);
        entity.setStatus(PipelineCheckpointStatus.STEP_SUCCESS);
        entity.setInput(Map.of("input", "test"));
        entity.setOutput(Map.of("output", "result"));
        entity.setStartedAt(Instant.now());
        entity.setCompletedAt(Instant.now());
        entity.setRetryCount(0);
        return entity;
    }
}