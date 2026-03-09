/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.orchestrator.store;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link CheckpointStore} for integration testing.
 * Thread-safe via ConcurrentHashMap.
 */
public class InMemoryCheckpointStore implements CheckpointStore {

    private final ConcurrentHashMap<String, PipelineCheckpoint> checkpoints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<StepCheckpoint>> stepCheckpoints = new ConcurrentHashMap<>();
    private final Set<String> idempotencyKeys = ConcurrentHashMap.newKeySet();

    @Override
    public PipelineCheckpoint createExecution(String tenantId, String pipelineId, String instanceId,
                                             String idempotencyKey, Map<String, Object> initialState) {
        String compositeKey = tenantId + "::" + idempotencyKey;
        if (!idempotencyKeys.add(compositeKey)) {
            throw new RuntimeException("Duplicate execution: " + idempotencyKey);
        }

        int totalSteps = 0;
        if (initialState != null && initialState.containsKey("totalSteps")) {
            Object ts = initialState.get("totalSteps");
            totalSteps = ts instanceof Number ? ((Number) ts).intValue() : Integer.parseInt(ts.toString());
        }

        PipelineCheckpoint cp = new PipelineCheckpoint(
                instanceId, tenantId, pipelineId, idempotencyKey,
                PipelineCheckpointStatus.CREATED,
                initialState != null ? new HashMap<>(initialState) : new HashMap<>(),
                new HashMap<>(),
                Instant.now(), Instant.now(),
                null, null, 0, totalSteps);
        checkpoints.put(instanceId, cp);
        stepCheckpoints.put(instanceId, new ArrayList<>());
        return cp;
    }

    @Override
    public PipelineCheckpoint updateCheckpoint(String instanceId, String stepId, String stepName,
                                              PipelineCheckpointStatus status, Map<String, Object> result,
                                              Map<String, Object> state) {
        PipelineCheckpoint existing = checkpoints.get(instanceId);
        if (existing == null) {
            throw new RuntimeException("Checkpoint not found: " + instanceId);
        }

        PipelineCheckpointStatus newStatus =
                status == PipelineCheckpointStatus.STEP_SUCCESS ? PipelineCheckpointStatus.RUNNING : status;
        int completedSteps = existing.getCompletedSteps() +
                (status == PipelineCheckpointStatus.STEP_SUCCESS ? 1 : 0);

        int totalSteps = existing.getTotalSteps();
        if (state != null && state.containsKey("totalSteps")) {
            Object ts = state.get("totalSteps");
            totalSteps = ts instanceof Number ? ((Number) ts).intValue() : Integer.parseInt(ts.toString());
        }

        PipelineCheckpoint updated = new PipelineCheckpoint(
                instanceId, existing.getTenantId(), existing.getPipelineId(), existing.getIdempotencyKey(),
                newStatus,
                state != null ? new HashMap<>(state) : existing.getState(),
                result != null ? new HashMap<>(result) : existing.getResult(),
                existing.getCreatedAt(), Instant.now(),
                stepId, stepName, completedSteps, totalSteps);
        checkpoints.put(instanceId, updated);
        return updated;
    }

    @Override
    public Optional<PipelineCheckpoint> findByInstanceId(String instanceId) {
        return Optional.ofNullable(checkpoints.get(instanceId));
    }

    @Override
    public Optional<PipelineCheckpoint> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        return checkpoints.values().stream()
                .filter(c -> c.getTenantId().equals(tenantId) && c.getIdempotencyKey().equals(idempotencyKey))
                .findFirst();
    }

    @Override
    public List<PipelineCheckpoint> findByPipelineId(String tenantId, String pipelineId, int limit) {
        return checkpoints.values().stream()
                .filter(c -> c.getTenantId().equals(tenantId) && c.getPipelineId().equals(pipelineId))
                .sorted(Comparator.comparing(PipelineCheckpoint::getCreatedAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<PipelineCheckpoint> findActive(int limit) {
        return checkpoints.values().stream()
                .filter(PipelineCheckpoint::isActive)
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<PipelineCheckpoint> findStale(Instant staleBefore) {
        return checkpoints.values().stream()
                .filter(PipelineCheckpoint::isActive)
                .filter(c -> c.getUpdatedAt().isBefore(staleBefore))
                .collect(Collectors.toList());
    }

    @Override
    public void completeExecution(String instanceId, PipelineCheckpointStatus status, Map<String, Object> finalResult) {
        PipelineCheckpoint existing = checkpoints.get(instanceId);
        if (existing == null) {
            throw new RuntimeException("Checkpoint not found: " + instanceId);
        }
        PipelineCheckpoint completed = new PipelineCheckpoint(
                instanceId, existing.getTenantId(), existing.getPipelineId(), existing.getIdempotencyKey(),
                status,
                existing.getState(),
                finalResult != null ? new HashMap<>(finalResult) : existing.getResult(),
                existing.getCreatedAt(), Instant.now(),
                existing.getCurrentStepId(), existing.getCurrentStepName(),
                existing.getCompletedSteps(), existing.getTotalSteps());
        checkpoints.put(instanceId, completed);
    }

    @Override
    public int cleanupOldCheckpoints(Instant completedBefore) {
        List<String> toRemove = checkpoints.entrySet().stream()
                .filter(e -> !e.getValue().isActive() && e.getValue().getUpdatedAt().isBefore(completedBefore))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        toRemove.forEach(id -> {
            checkpoints.remove(id);
            stepCheckpoints.remove(id);
        });
        return toRemove.size();
    }

    @Override
    public boolean isDuplicate(String tenantId, String idempotencyKey) {
        return idempotencyKeys.contains(tenantId + "::" + idempotencyKey);
    }

    @Override
    public Optional<StepCheckpoint> getLastSuccessfulStep(String instanceId) {
        List<StepCheckpoint> steps = stepCheckpoints.getOrDefault(instanceId, List.of());
        return steps.stream()
                .filter(s -> s.getStatus() == PipelineCheckpointStatus.STEP_SUCCESS)
                .reduce((first, second) -> second); // last in list order
    }

    @Override
    public void recordStepCheckpoint(String instanceId, StepCheckpoint stepCheckpoint) {
        List<StepCheckpoint> steps = stepCheckpoints.computeIfAbsent(instanceId, k -> new ArrayList<>());
        // Upsert: replace existing step checkpoint for same stepId
        steps.removeIf(s -> s.getStepId().equals(stepCheckpoint.getStepId()));
        steps.add(stepCheckpoint);
    }

    @Override
    public boolean isExecutionAllowed(String instanceId) {
        PipelineCheckpoint cp = checkpoints.get(instanceId);
        return cp != null && cp.isActive();
    }

    /** Clear all data — useful in test @BeforeEach. */
    public void clear() {
        checkpoints.clear();
        stepCheckpoints.clear();
        idempotencyKeys.clear();
    }

    /** Return total checkpoint count. */
    public int size() {
        return checkpoints.size();
    }

    /** Return all step checkpoints for an instance. */
    public List<StepCheckpoint> getStepHistory(String instanceId) {
        return List.copyOf(stepCheckpoints.getOrDefault(instanceId, List.of()));
    }
}
