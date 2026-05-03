package com.ghatana.digitalmarketing.application.workflow;

import com.ghatana.digitalmarketing.domain.workflow.DmWorkflowExecution;
import com.ghatana.digitalmarketing.domain.workflow.DmWorkflowStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link DmWorkflowExecution}.
 *
 * @doc.type class
 * @doc.purpose Repository interface for durable workflow execution persistence (DMOS-F2-004)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmWorkflowRepository {

    /** Persist a new workflow execution. */
    Promise<DmWorkflowExecution> save(DmWorkflowExecution execution);

    /** Load a workflow execution by id. Returns empty if not found. */
    Promise<Optional<DmWorkflowExecution>> findById(String id);

    /** Load all workflow executions for a tenant with the given status. */
    Promise<List<DmWorkflowExecution>> findByStatus(String tenantId, DmWorkflowStatus status, int limit);

    /** Load active (RUNNING or PAUSED) workflows for a tenant. */
    Promise<List<DmWorkflowExecution>> findActive(String tenantId, int limit);

    /** Persist an updated workflow execution. */
    Promise<DmWorkflowExecution> update(DmWorkflowExecution execution);

    /** Count workflow executions by status for a tenant. */
    Promise<Long> countByStatus(String tenantId, DmWorkflowStatus status);
}
