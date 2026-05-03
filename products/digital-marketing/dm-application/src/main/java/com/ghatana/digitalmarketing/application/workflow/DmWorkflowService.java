package com.ghatana.digitalmarketing.application.workflow;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.workflow.DmWorkflowExecution;
import com.ghatana.digitalmarketing.domain.workflow.DmWorkflowStatus;
import com.ghatana.digitalmarketing.domain.workflow.DmWorkflowStep;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service interface for managing durable workflow executions.
 *
 * @doc.type class
 * @doc.purpose Defines the operations for creating, advancing, and querying workflow executions (DMOS-F2-004)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmWorkflowService {

    /**
     * Initiate a new workflow execution.
     *
     * @param ctx     operation context
     * @param request definition of the workflow to create
     * @return the newly created (PENDING) workflow execution
     */
    Promise<DmWorkflowExecution> initiate(DmOperationContext ctx, InitiateWorkflowRequest request);

    /**
     * Start a PENDING workflow, transitioning it to RUNNING.
     *
     * @param ctx operation context
     * @param id  workflow execution id
     * @return updated execution
     */
    Promise<DmWorkflowExecution> start(DmOperationContext ctx, String id);

    /**
     * Advance the workflow to the next step, recording the completion of the current step.
     *
     * @param ctx           operation context
     * @param id            workflow execution id
     * @param completedStep the step that just finished
     * @return updated execution
     */
    Promise<DmWorkflowExecution> advanceStep(DmOperationContext ctx, String id, DmWorkflowStep completedStep);

    /**
     * Mark a RUNNING workflow as COMPLETED.
     *
     * @param ctx operation context
     * @param id  workflow execution id
     * @return updated execution
     */
    Promise<DmWorkflowExecution> complete(DmOperationContext ctx, String id);

    /**
     * Fail a workflow with an explanation.
     *
     * @param ctx    operation context
     * @param id     workflow execution id
     * @param reason human-readable failure reason
     * @return updated execution
     */
    Promise<DmWorkflowExecution> fail(DmOperationContext ctx, String id, String reason);

    /**
     * Pause a RUNNING workflow.
     *
     * @param ctx operation context
     * @param id  workflow execution id
     * @return updated execution
     */
    Promise<DmWorkflowExecution> pause(DmOperationContext ctx, String id);

    /**
     * Resume a PAUSED workflow.
     *
     * @param ctx operation context
     * @param id  workflow execution id
     * @return updated execution
     */
    Promise<DmWorkflowExecution> resume(DmOperationContext ctx, String id);

    /**
     * Roll back a FAILED workflow.
     *
     * @param ctx operation context
     * @param id  workflow execution id
     * @return updated execution
     */
    Promise<DmWorkflowExecution> rollback(DmOperationContext ctx, String id);

    /**
     * Find a workflow execution by id.
     *
     * @param ctx operation context
     * @param id  workflow execution id
     * @return optional workflow execution
     */
    Promise<Optional<DmWorkflowExecution>> findById(DmOperationContext ctx, String id);

    /**
     * List active (RUNNING or PAUSED) workflow executions for the tenant.
     *
     * @param ctx   operation context
     * @param limit max results
     * @return list of active executions
     */
    Promise<List<DmWorkflowExecution>> listActive(DmOperationContext ctx, int limit);

    /**
     * Count workflow executions by status for the calling tenant.
     *
     * @param ctx    operation context
     * @param status status to count
     * @return count
     */
    Promise<Long> countByStatus(DmOperationContext ctx, DmWorkflowStatus status);

    // ── Request types ─────────────────────────────────────────────────────────

    /**
     * Request object for initiating a workflow.
     */
    record InitiateWorkflowRequest(
        String name,
        String correlationId,
        List<DmWorkflowStep> steps
    ) {
        public InitiateWorkflowRequest {
            Objects.requireNonNull(name, "name must not be null");
            if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
            Objects.requireNonNull(correlationId, "correlationId must not be null");
            if (correlationId.isBlank()) throw new IllegalArgumentException("correlationId must not be blank");
            Objects.requireNonNull(steps, "steps must not be null");
            if (steps.isEmpty()) throw new IllegalArgumentException("steps must not be empty");
        }
    }
}
