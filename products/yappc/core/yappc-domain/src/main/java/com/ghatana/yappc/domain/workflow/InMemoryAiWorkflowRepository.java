package com.ghatana.products.yappc.domain.workflow;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of AiWorkflowRepository for testing and development.
 *
 * @doc.type class
 * @doc.purpose In-memory workflow storage
 * @doc.layer product
 * @doc.pattern Repository
 */
public class InMemoryAiWorkflowRepository implements AiWorkflowRepository {

    private final Map<String, Map<String, AiWorkflowInstance>> storage = new ConcurrentHashMap<>();

    private String key(String id, String tenantId) {
        return tenantId + ":" + id;
    }

    private Map<String, AiWorkflowInstance> getTenantStorage(String tenantId) {
        return storage.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
    }

    @Override
    @NotNull
    public Promise<AiWorkflowInstance> save(@NotNull AiWorkflowInstance workflow) {
        getTenantStorage(workflow.tenantId()).put(workflow.id(), workflow);
        return Promise.of(workflow);
    }

    @Override
    @NotNull
    public Promise<Optional<AiWorkflowInstance>> findById(
        @NotNull String id,
        @NotNull String tenantId
    ) {
        return Promise.of(Optional.ofNullable(getTenantStorage(tenantId).get(id)));
    }

    @Override
    @NotNull
    public Promise<List<AiWorkflowInstance>> findByTenant(
        @NotNull String tenantId,
        @Nullable AiWorkflowInstance.WorkflowStatus status,
        int limit,
        int offset
    ) {
        List<AiWorkflowInstance> result = getTenantStorage(tenantId).values().stream()
            .filter(w -> status == null || w.status() == status)
            .sorted(Comparator.comparing(AiWorkflowInstance::updatedAt).reversed())
            .skip(offset)
            .limit(limit)
            .collect(Collectors.toList());
        return Promise.of(result);
    }

    @Override
    @NotNull
    public Promise<List<AiWorkflowInstance>> findByType(
        @NotNull String tenantId,
        @NotNull AiWorkflowInstance.WorkflowType type
    ) {
        List<AiWorkflowInstance> result = getTenantStorage(tenantId).values().stream()
            .filter(w -> w.type() == type)
            .sorted(Comparator.comparing(AiWorkflowInstance::updatedAt).reversed())
            .collect(Collectors.toList());
        return Promise.of(result);
    }

    @Override
    @NotNull
    public Promise<Boolean> delete(@NotNull String id, @NotNull String tenantId) {
        AiWorkflowInstance removed = getTenantStorage(tenantId).remove(id);
        return Promise.of(removed != null);
    }

    @Override
    @NotNull
    public Promise<AiWorkflowInstance> updateStatus(
        @NotNull String id,
        @NotNull String tenantId,
        @NotNull AiWorkflowInstance.WorkflowStatus status
    ) {
        AiWorkflowInstance existing = getTenantStorage(tenantId).get(id);
        if (existing == null) {
            return Promise.ofException(new NoSuchElementException("Workflow not found: " + id));
        }

        Instant completedAt = (status == AiWorkflowInstance.WorkflowStatus.COMPLETED ||
                               status == AiWorkflowInstance.WorkflowStatus.FAILED ||
                               status == AiWorkflowInstance.WorkflowStatus.CANCELLED)
            ? Instant.now() : existing.completedAt();

        AiWorkflowInstance updated = new AiWorkflowInstance(
            existing.id(),
            existing.tenantId(),
            existing.name(),
            existing.description(),
            existing.type(),
            status,
            existing.currentStepId(),
            existing.currentStepIndex(),
            existing.totalSteps(),
            existing.context(),
            existing.stepResults(),
            existing.aiPlanId(),
            existing.createdBy(),
            existing.createdAt(),
            Instant.now(),
            completedAt,
            existing.errorMessage()
        );

        getTenantStorage(tenantId).put(id, updated);
        return Promise.of(updated);
    }

    @Override
    @NotNull
    public Promise<AiWorkflowInstance> updateCurrentStep(
        @NotNull String id,
        @NotNull String tenantId,
        @NotNull String stepId,
        int stepIndex
    ) {
        AiWorkflowInstance existing = getTenantStorage(tenantId).get(id);
        if (existing == null) {
            return Promise.ofException(new NoSuchElementException("Workflow not found: " + id));
        }

        AiWorkflowInstance updated = new AiWorkflowInstance(
            existing.id(),
            existing.tenantId(),
            existing.name(),
            existing.description(),
            existing.type(),
            existing.status(),
            stepId,
            stepIndex,
            existing.totalSteps(),
            existing.context(),
            existing.stepResults(),
            existing.aiPlanId(),
            existing.createdBy(),
            existing.createdAt(),
            Instant.now(),
            existing.completedAt(),
            existing.errorMessage()
        );

        getTenantStorage(tenantId).put(id, updated);
        return Promise.of(updated);
    }

    @Override
    @NotNull
    public Promise<AiWorkflowInstance> saveStepResult(
        @NotNull String workflowId,
        @NotNull String tenantId,
        @NotNull AiWorkflowInstance.AiWorkflowStepResult stepResult
    ) {
        AiWorkflowInstance existing = getTenantStorage(tenantId).get(workflowId);
        if (existing == null) {
            return Promise.ofException(new NoSuchElementException("Workflow not found: " + workflowId));
        }

        Map<String, AiWorkflowInstance.AiWorkflowStepResult> newResults = new HashMap<>(existing.stepResults());
        newResults.put(stepResult.stepId(), stepResult);

        AiWorkflowInstance updated = new AiWorkflowInstance(
            existing.id(),
            existing.tenantId(),
            existing.name(),
            existing.description(),
            existing.type(),
            existing.status(),
            existing.currentStepId(),
            existing.currentStepIndex(),
            existing.totalSteps(),
            existing.context(),
            newResults,
            existing.aiPlanId(),
            existing.createdBy(),
            existing.createdAt(),
            Instant.now(),
            existing.completedAt(),
            existing.errorMessage()
        );

        getTenantStorage(tenantId).put(workflowId, updated);
        return Promise.of(updated);
    }

    @Override
    @NotNull
    public Promise<WorkflowStatusCounts> countByStatus(@NotNull String tenantId) {
        Map<AiWorkflowInstance.WorkflowStatus, Long> counts = getTenantStorage(tenantId).values().stream()
            .collect(Collectors.groupingBy(AiWorkflowInstance::status, Collectors.counting()));

        return Promise.of(new WorkflowStatusCounts(
            counts.getOrDefault(AiWorkflowInstance.WorkflowStatus.DRAFT, 0L).intValue(),
            counts.getOrDefault(AiWorkflowInstance.WorkflowStatus.PENDING, 0L).intValue(),
            counts.getOrDefault(AiWorkflowInstance.WorkflowStatus.IN_PROGRESS, 0L).intValue(),
            counts.getOrDefault(AiWorkflowInstance.WorkflowStatus.PAUSED, 0L).intValue(),
            counts.getOrDefault(AiWorkflowInstance.WorkflowStatus.AWAITING_REVIEW, 0L).intValue(),
            counts.getOrDefault(AiWorkflowInstance.WorkflowStatus.COMPLETED, 0L).intValue(),
            counts.getOrDefault(AiWorkflowInstance.WorkflowStatus.FAILED, 0L).intValue(),
            counts.getOrDefault(AiWorkflowInstance.WorkflowStatus.CANCELLED, 0L).intValue()
        ));
    }

    /**
     * Clears all data (for testing)
     */
    public void clear() {
        storage.clear();
    }

    /**
     * Clears data for a specific tenant (for testing)
     */
    public void clearTenant(String tenantId) {
        storage.remove(tenantId);
    }
}
