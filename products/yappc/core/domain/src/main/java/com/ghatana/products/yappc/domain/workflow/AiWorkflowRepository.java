package com.ghatana.products.yappc.domain.workflow;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for AI workflow instances.
 *
 * @doc.type interface
 * @doc.purpose Workflow persistence abstraction
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface AiWorkflowRepository {

    /**
     * Saves a workflow instance.
     *
     * @param workflow The workflow to save
     * @return Promise resolving to the saved workflow
     */
    @NotNull
    Promise<AiWorkflowInstance> save(@NotNull AiWorkflowInstance workflow);

    /**
     * Finds a workflow by ID.
     *
     * @param id The workflow ID
     * @param tenantId The tenant ID
     * @return Promise resolving to the workflow if found
     */
    @NotNull
    Promise<Optional<AiWorkflowInstance>> findById(@NotNull String id, @NotNull String tenantId);

    /**
     * Finds workflows by tenant.
     *
     * @param tenantId The tenant ID
     * @param status Optional status filter
     * @param limit Maximum results
     * @param offset Pagination offset
     * @return Promise resolving to list of workflows
     */
    @NotNull
    Promise<List<AiWorkflowInstance>> findByTenant(
        @NotNull String tenantId,
        @Nullable AiWorkflowInstance.WorkflowStatus status,
        int limit,
        int offset
    );

    /**
     * Finds workflows by type.
     *
     * @param tenantId The tenant ID
     * @param type The workflow type
     * @return Promise resolving to list of workflows
     */
    @NotNull
    Promise<List<AiWorkflowInstance>> findByType(
        @NotNull String tenantId,
        @NotNull AiWorkflowInstance.WorkflowType type
    );

    /**
     * Deletes a workflow.
     *
     * @param id The workflow ID
     * @param tenantId The tenant ID
     * @return Promise resolving to true if deleted
     */
    @NotNull
    Promise<Boolean> delete(@NotNull String id, @NotNull String tenantId);

    /**
     * Updates workflow status.
     *
     * @param id The workflow ID
     * @param tenantId The tenant ID
     * @param status The new status
     * @return Promise resolving to the updated workflow
     */
    @NotNull
    Promise<AiWorkflowInstance> updateStatus(
        @NotNull String id,
        @NotNull String tenantId,
        @NotNull AiWorkflowInstance.WorkflowStatus status
    );

    /**
     * Updates the current step.
     *
     * @param id The workflow ID
     * @param tenantId The tenant ID
     * @param stepId The step ID
     * @param stepIndex The step index
     * @return Promise resolving to the updated workflow
     */
    @NotNull
    Promise<AiWorkflowInstance> updateCurrentStep(
        @NotNull String id,
        @NotNull String tenantId,
        @NotNull String stepId,
        int stepIndex
    );

    /**
     * Saves a step result.
     *
     * @param workflowId The workflow ID
     * @param tenantId The tenant ID
     * @param stepResult The step result
     * @return Promise resolving to the updated workflow
     */
    @NotNull
    Promise<AiWorkflowInstance> saveStepResult(
        @NotNull String workflowId,
        @NotNull String tenantId,
        @NotNull AiWorkflowInstance.AiWorkflowStepResult stepResult
    );

    /**
     * Counts workflows by status for a tenant.
     *
     * @param tenantId The tenant ID
     * @return Promise resolving to status counts
     */
    @NotNull
    Promise<WorkflowStatusCounts> countByStatus(@NotNull String tenantId);

    /**
     * Status count summary
     */
    record WorkflowStatusCounts(
        int draft,
        int pending,
        int inProgress,
        int paused,
        int awaitingReview,
        int completed,
        int failed,
        int cancelled
    ) {
        public int total() {
            return draft + pending + inProgress + paused + awaitingReview + completed + failed + cancelled;
        }

        public int active() {
            return pending + inProgress + awaitingReview;
        }
    }
}
