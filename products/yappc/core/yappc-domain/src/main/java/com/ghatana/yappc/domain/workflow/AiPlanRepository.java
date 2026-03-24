package com.ghatana.products.yappc.domain.workflow;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for AI plans.
 *
 * @doc.type interface
 * @doc.purpose AI plan persistence abstraction
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface AiPlanRepository {

    /**
     * Saves an AI plan.
     *
     * @param plan The plan to save
     * @return Promise resolving to the saved plan
     */
    @NotNull
    Promise<AiPlan> save(@NotNull AiPlan plan);

    /**
     * Finds a plan by ID.
     *
     * @param id The plan ID
     * @param tenantId The tenant ID
     * @return Promise resolving to the plan if found
     */
    @NotNull
    Promise<Optional<AiPlan>> findById(@NotNull String id, @NotNull String tenantId);

    /**
     * Finds plans by workflow.
     *
     * @param workflowId The workflow ID
     * @param tenantId The tenant ID
     * @return Promise resolving to list of plans
     */
    @NotNull
    Promise<List<AiPlan>> findByWorkflow(@NotNull String workflowId, @NotNull String tenantId);

    /**
     * Finds the active plan for a workflow.
     *
     * @param workflowId The workflow ID
     * @param tenantId The tenant ID
     * @return Promise resolving to the active plan if exists
     */
    @NotNull
    Promise<Optional<AiPlan>> findActivePlan(@NotNull String workflowId, @NotNull String tenantId);

    /**
     * Updates plan status.
     *
     * @param id The plan ID
     * @param tenantId The tenant ID
     * @param status The new status
     * @return Promise resolving to the updated plan
     */
    @NotNull
    Promise<AiPlan> updateStatus(
        @NotNull String id,
        @NotNull String tenantId,
        @NotNull AiPlan.PlanStatus status
    );

    /**
     * Updates plan steps.
     *
     * @param id The plan ID
     * @param tenantId The tenant ID
     * @param steps The new steps
     * @return Promise resolving to the updated plan
     */
    @NotNull
    Promise<AiPlan> updateSteps(
        @NotNull String id,
        @NotNull String tenantId,
        @NotNull List<AiPlan.PlanStep> steps
    );

    /**
     * Deletes a plan.
     *
     * @param id The plan ID
     * @param tenantId The tenant ID
     * @return Promise resolving to true if deleted
     */
    @NotNull
    Promise<Boolean> delete(@NotNull String id, @NotNull String tenantId);
}
