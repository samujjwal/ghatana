package com.ghatana.datacloud.entity;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for workflow execution persistence.
 *
 * <p><b>Purpose</b><br>
 * Defines the contract for persisting and querying workflow execution records.
 * Implementations are provided by the infrastructure layer. All operations are
 * tenant-scoped.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkflowExecutionRepository repository = new JpaWorkflowExecutionRepositoryImpl(em);
 *
 * // Save execution
 * Promise<WorkflowExecution> saved = repository.save(tenantId, execution);
 *
 * // Find by ID
 * Promise<Optional<WorkflowExecution>> found = repository.findById(tenantId, executionId);
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Repository port in domain layer (hexagonal architecture)
 * - Implemented by infrastructure layer
 * - Used by WorkflowService
 * - Enforces multi-tenancy at repository level
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe.
 *
 * @see WorkflowExecution
 * @doc.type interface
 * @doc.purpose Repository port for workflow execution persistence
 * @doc.layer domain
 * @doc.pattern Repository Port (Domain Layer)
 */
public interface WorkflowExecutionRepository {

    /**
     * Saves a workflow execution (insert or update).
     *
     * @param tenantId  the tenant identifier (required)
     * @param execution the execution to save (required)
     * @return Promise of saved execution
     */
    Promise<WorkflowExecution> save(String tenantId, WorkflowExecution execution);

    /**
     * Finds a workflow execution by its ID.
     *
     * @param tenantId    the tenant identifier (required)
     * @param executionId the execution ID (required)
     * @return Promise of Optional containing the execution if found
     */
    Promise<Optional<WorkflowExecution>> findById(String tenantId, UUID executionId);

    /**
     * Lists all executions for a given workflow definition, newest first.
     *
     * @param tenantId   the tenant identifier (required)
     * @param workflowId the workflow definition ID (required)
     * @param limit      maximum number of results
     * @param offset     zero-based offset for pagination
     * @return Promise of list of executions
     */
    Promise<List<WorkflowExecution>> findByWorkflowId(String tenantId, UUID workflowId, int limit, int offset);

    /**
     * Lists all executions for a tenant, newest first.
     *
     * @param tenantId the tenant identifier (required)
     * @param limit    maximum number of results
     * @param offset   zero-based offset for pagination
     * @return Promise of list of executions
     */
    Promise<List<WorkflowExecution>> findByTenant(String tenantId, int limit, int offset);
}
