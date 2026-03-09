package com.ghatana.products.yappc.domain.workflow;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of AiPlanRepository for testing and development.
 *
 * @doc.type class
 * @doc.purpose In-memory plan storage
 * @doc.layer product
 * @doc.pattern Repository
 */
public class InMemoryAiPlanRepository implements AiPlanRepository {

    private final Map<String, Map<String, AiPlan>> storage = new ConcurrentHashMap<>();

    private Map<String, AiPlan> getTenantStorage(String tenantId) {
        return storage.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
    }

    @Override
    @NotNull
    public Promise<AiPlan> save(@NotNull AiPlan plan) {
        getTenantStorage(plan.tenantId()).put(plan.id(), plan);
        return Promise.of(plan);
    }

    @Override
    @NotNull
    public Promise<Optional<AiPlan>> findById(@NotNull String id, @NotNull String tenantId) {
        return Promise.of(Optional.ofNullable(getTenantStorage(tenantId).get(id)));
    }

    @Override
    @NotNull
    public Promise<List<AiPlan>> findByWorkflow(
        @NotNull String workflowId,
        @NotNull String tenantId
    ) {
        List<AiPlan> result = getTenantStorage(tenantId).values().stream()
            .filter(p -> p.workflowId().equals(workflowId))
            .collect(Collectors.toList());
        return Promise.of(result);
    }

    @Override
    @NotNull
    public Promise<Optional<AiPlan>> findActivePlan(
        @NotNull String workflowId,
        @NotNull String tenantId
    ) {
        Optional<AiPlan> activePlan = getTenantStorage(tenantId).values().stream()
            .filter(p -> p.workflowId().equals(workflowId))
            .filter(p -> p.status() == AiPlan.PlanStatus.APPROVED ||
                         p.status() == AiPlan.PlanStatus.EXECUTED)
            .findFirst();
        return Promise.of(activePlan);
    }

    @Override
    @NotNull
    public Promise<AiPlan> updateStatus(
        @NotNull String id,
        @NotNull String tenantId,
        @NotNull AiPlan.PlanStatus status
    ) {
        AiPlan existing = getTenantStorage(tenantId).get(id);
        if (existing == null) {
            return Promise.ofException(new NoSuchElementException("Plan not found: " + id));
        }

        AiPlan updated = new AiPlan(
            existing.id(),
            existing.workflowId(),
            existing.tenantId(),
            existing.objective(),
            existing.steps(),
            status,
            existing.generatedBy(),
            existing.modelUsed(),
            existing.confidence(),
            existing.reasoning(),
            existing.metadata()
        );

        getTenantStorage(tenantId).put(id, updated);
        return Promise.of(updated);
    }

    @Override
    @NotNull
    public Promise<AiPlan> updateSteps(
        @NotNull String id,
        @NotNull String tenantId,
        @NotNull List<AiPlan.PlanStep> steps
    ) {
        AiPlan existing = getTenantStorage(tenantId).get(id);
        if (existing == null) {
            return Promise.ofException(new NoSuchElementException("Plan not found: " + id));
        }

        AiPlan updated = new AiPlan(
            existing.id(),
            existing.workflowId(),
            existing.tenantId(),
            existing.objective(),
            steps,
            existing.status(),
            existing.generatedBy(),
            existing.modelUsed(),
            existing.confidence(),
            existing.reasoning(),
            existing.metadata()
        );

        getTenantStorage(tenantId).put(id, updated);
        return Promise.of(updated);
    }

    @Override
    @NotNull
    public Promise<Boolean> delete(@NotNull String id, @NotNull String tenantId) {
        AiPlan removed = getTenantStorage(tenantId).remove(id);
        return Promise.of(removed != null);
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
