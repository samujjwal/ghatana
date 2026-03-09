package com.ghatana.datacloud.entity;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for workflow persistence.
 *
 * <p><b>Purpose</b><br>
 * Defines the contract for workflow persistence operations. Implementations
 * are provided by the infrastructure layer.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkflowRepository repository = new JpaWorkflowRepositoryImpl(entityManager);
 *
 * // Save workflow
 * Promise<Workflow> promise = repository.save(tenantId, workflow);
 *
 * // Find by ID
 * Promise<Optional<Workflow>> findPromise = repository.findById(tenantId, workflowId);
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
 * @see Workflow
 * @see WorkflowService
 * @doc.type interface
 * @doc.purpose Repository port for workflow persistence
 * @doc.layer domain
 * @doc.pattern Repository Port (Domain Layer)
 */
public interface WorkflowRepository {

    /**
     * Saves a workflow (insert or update).
     *
     * @param tenantId the tenant identifier (required)
     * @param workflow the workflow to save (required)
     * @return Promise of saved workflow with ID
     */
    Promise<Workflow> save(String tenantId, Workflow workflow);

    /**
     * Finds a workflow by ID.
     *
     * @param tenantId the tenant identifier (required)
     * @param workflowId the workflow ID (required)
     * @return Promise of Optional containing the workflow if found
     */
    Promise<Optional<Workflow>> findById(String tenantId, UUID workflowId);

    /**
     * Finds a workflow by name.
     *
     * @param tenantId the tenant identifier (required)
     * @param name the workflow name (required)
     * @return Promise of Optional containing the workflow if found
     */
    Promise<Optional<Workflow>> findByName(String tenantId, String name);

    /**
     * Finds all workflows for a tenant with pagination.
     *
     * @param tenantId the tenant identifier (required)
     * @param offset the offset (0-based)
     * @param limit the limit (max results)
     * @return Promise of list of workflows
     */
    Promise<List<Workflow>> findAll(String tenantId, int offset, int limit);

    /**
     * Finds all workflows for a collection.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionId the collection ID (required)
     * @return Promise of list of workflows
     */
    Promise<List<Workflow>> findByCollectionId(String tenantId, UUID collectionId);

    /**
     * Deletes a workflow (soft delete).
     *
     * @param tenantId the tenant identifier (required)
     * @param workflowId the workflow ID (required)
     * @return Promise of void
     */
    Promise<Void> delete(String tenantId, UUID workflowId);

    /**
     * Counts workflows for a tenant.
     *
     * @param tenantId the tenant identifier (required)
     * @return Promise of count
     */
    Promise<Long> count(String tenantId);
}
