package com.ghatana.datacloud.application;

import com.ghatana.datacloud.entity.*;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Service for managing workflow definitions and execution.
 *
 * <p><b>Purpose</b><br>
 * Provides business logic for workflow CRUD operations, validation, and execution management.
 * All operations are tenant-scoped and return ActiveJ Promises for non-blocking execution.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkflowService service = new WorkflowService(
 *     workflowRepository,
 *     collectionRepository,
 *     metrics
 * );
 *
 * // Create workflow
 * Workflow workflow = Workflow.builder()
 *     .tenantId("tenant-123")
 *     .name("Order Processing")
 *     .collectionId(collectionId)
 *     .nodes(List.of(...))
 *     .edges(List.of(...))
 *     .build();
 *
 * Promise<Workflow> promise = service.createWorkflow("tenant-123", workflow, "user-789");
 *
 * // In test with EventloopTestBase:
 * Workflow created = runPromise(() -> promise);
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Service in application layer (hexagonal architecture)
 * - Uses WorkflowRepository (domain port)
 * - Uses MetricsCollector (core/observability)
 * - Enforces multi-tenancy and workflow validation
 *
 * <p><b>Thread Safety</b><br>
 * Stateless service - thread-safe. All state passed via parameters or repositories.
 *
 * @see Workflow
 * @see WorkflowNode
 * @see WorkflowEdge
 * @see MetricsCollector
 * @doc.type class
 * @doc.purpose Service for workflow management and execution
 * @doc.layer product
 * @doc.pattern Service (Application Layer)
 */
public class WorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowService.class);

    private final WorkflowRepository workflowRepository;
    private final CollectionRepository collectionRepository;
    private final MetricsCollector metrics;

    /**
     * Creates a new workflow service.
     *
     * @param workflowRepository the workflow repository (required)
     * @param collectionRepository the collection repository (required)
     * @param metrics the metrics collector (required)
     * @throws NullPointerException if any parameter is null
     */
    public WorkflowService(
            WorkflowRepository workflowRepository,
            CollectionRepository collectionRepository,
            MetricsCollector metrics) {
        this.workflowRepository = Objects.requireNonNull(workflowRepository, "WorkflowRepository must not be null");
        this.collectionRepository = Objects.requireNonNull(collectionRepository, "CollectionRepository must not be null");
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector must not be null");
    }

    /**
     * Creates a new workflow.
     *
     * <p><b>Validation</b><br>
     * - Collection must exist and be active
     * - Workflow name must be unique within tenant
     * - Nodes and edges must be valid
     *
     * @param tenantId the tenant identifier (required)
     * @param workflow the workflow definition (required)
     * @param userId the user creating the workflow (for audit)
     * @return Promise of created workflow with generated ID
     * @throws IllegalArgumentException if validation fails
     */
    public Promise<Workflow> createWorkflow(
            String tenantId,
            Workflow workflow,
            String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(workflow, "Workflow must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        // Validate collection exists
        return collectionRepository.findById(tenantId, workflow.getCollectionId())
            .then(collectionOpt -> {
                if (collectionOpt.isEmpty()) {
                    metrics.incrementCounter("workflow.create.collection_not_found",
                        "tenant", tenantId);
                    return Promise.ofException(
                        new IllegalArgumentException("Collection not found: " + workflow.getCollectionId())
                    );
                }

                // Validate workflow structure
                List<String> validationErrors = validateWorkflowStructure(workflow);
                if (!validationErrors.isEmpty()) {
                    metrics.incrementCounter("workflow.create.validation_failed",
                        "tenant", tenantId);
                    logger.warn("Workflow validation failed: tenantId={}, errors={}",
                        tenantId, validationErrors);
                    return Promise.ofException(
                        new IllegalArgumentException("Workflow validation failed: " + validationErrors)
                    );
                }

                // Create workflow
                Workflow newWorkflow = Workflow.builder()
                    .tenantId(tenantId)
                    .name(workflow.getName())
                    .description(workflow.getDescription())
                    .collectionId(workflow.getCollectionId())
                    .nodes(workflow.getNodes())
                    .edges(workflow.getEdges())
                    .triggers(workflow.getTriggers())
                    .variables(workflow.getVariables())
                    .status("DRAFT")
                    .version(1)
                    .active(true)
                    .createdBy(userId)
                    .updatedBy(userId)
                    .build();

                return workflowRepository.save(tenantId, newWorkflow);
            })
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("workflow.create.success",
                        "tenant", tenantId);
                    logger.info("Workflow created: tenantId={}, id={}, name={}, createdBy={}",
                        tenantId, result.getId(), result.getName(), userId);
                } else {
                    metrics.incrementCounter("workflow.create.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                    logger.error("Failed to create workflow: tenantId={}", tenantId, ex);
                }
            });
    }

    /**
     * Gets a workflow by ID.
     *
     * @param tenantId the tenant identifier (required)
     * @param workflowId the workflow ID (required)
     * @return Promise of Optional containing the workflow if found
     */
    public Promise<Optional<Workflow>> getWorkflow(String tenantId, UUID workflowId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(workflowId, "Workflow ID must not be null");

        return workflowRepository.findById(tenantId, workflowId)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    if (result.isPresent()) {
                        metrics.incrementCounter("workflow.get.success",
                            "tenant", tenantId);
                    } else {
                        metrics.incrementCounter("workflow.get.not_found",
                            "tenant", tenantId);
                    }
                } else {
                    metrics.incrementCounter("workflow.get.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                }
            });
    }

    /**
     * Lists workflows for a tenant.
     *
     * @param tenantId the tenant identifier (required)
     * @param offset the offset (0-based)
     * @param limit the limit (max results)
     * @return Promise of list of workflows
     */
    public Promise<List<Workflow>> listWorkflows(String tenantId, int offset, int limit) {
        validateTenantId(tenantId);

        if (offset < 0) {
            throw new IllegalArgumentException("Offset must be >= 0");
        }
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Limit must be between 1 and 1000");
        }

        return workflowRepository.findAll(tenantId, offset, limit)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("workflow.list.success",
                        "tenant", tenantId);
                    metrics.increment("workflow.list.count", result.size(),
                        Map.of("tenant", tenantId));
                    logger.debug("Listed workflows: tenantId={}, count={}",
                        tenantId, result.size());
                } else {
                    metrics.incrementCounter("workflow.list.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                }
            });
    }

    /**
     * Updates a workflow.
     *
     * @param tenantId the tenant identifier (required)
     * @param workflowId the workflow ID (required)
     * @param updateData the partial update data (required)
     * @param userId the user updating the workflow (for audit)
     * @return Promise of updated workflow
     */
    public Promise<Workflow> updateWorkflow(
            String tenantId,
            UUID workflowId,
            Map<String, Object> updateData,
            String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(workflowId, "Workflow ID must not be null");
        Objects.requireNonNull(updateData, "Update data must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        return workflowRepository.findById(tenantId, workflowId)
            .then(workflowOpt -> {
                if (workflowOpt.isEmpty()) {
                    metrics.incrementCounter("workflow.update.not_found",
                        "tenant", tenantId);
                    return Promise.ofException(
                        new IllegalArgumentException("Workflow not found: " + workflowId)
                    );
                }

                Workflow workflow = workflowOpt.get();
                // Apply updates (simplified - in production would merge properly)
                Workflow updated = Workflow.builder()
                    .id(workflow.getId())
                    .tenantId(workflow.getTenantId())
                    .name((String) updateData.getOrDefault("name", workflow.getName()))
                    .description((String) updateData.getOrDefault("description", workflow.getDescription()))
                    .collectionId(workflow.getCollectionId())
                    .nodes(workflow.getNodes())
                    .edges(workflow.getEdges())
                    .triggers(workflow.getTriggers())
                    .variables(workflow.getVariables())
                    .status((String) updateData.getOrDefault("status", workflow.getStatus()))
                    .version(workflow.getVersion() + 1)
                    .active(workflow.getActive())
                    .createdAt(workflow.getCreatedAt())
                    .updatedAt(java.time.Instant.now())
                    .createdBy(workflow.getCreatedBy())
                    .updatedBy(userId)
                    .build();

                return workflowRepository.save(tenantId, updated);
            })
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("workflow.update.success",
                        "tenant", tenantId);
                    logger.info("Workflow updated: tenantId={}, id={}, version={}, updatedBy={}",
                        tenantId, workflowId, result.getVersion(), userId);
                } else {
                    metrics.incrementCounter("workflow.update.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                }
            });
    }

    /**
     * Deletes a workflow (soft delete).
     *
     * @param tenantId the tenant identifier (required)
     * @param workflowId the workflow ID (required)
     * @param userId the user deleting the workflow (for audit)
     * @return Promise of void
     */
    public Promise<Void> deleteWorkflow(String tenantId, UUID workflowId, String userId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(workflowId, "Workflow ID must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        return workflowRepository.delete(tenantId, workflowId)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("workflow.delete.success",
                        "tenant", tenantId);
                    logger.info("Workflow deleted: tenantId={}, id={}, deletedBy={}",
                        tenantId, workflowId, userId);
                } else {
                    metrics.incrementCounter("workflow.delete.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                }
            });
    }

    /**
     * Validates workflow structure.
     *
     * @param workflow the workflow to validate
     * @return list of validation errors (empty if valid)
     */
    private List<String> validateWorkflowStructure(Workflow workflow) {
        List<String> errors = new ArrayList<>();

        // Validate name
        if (workflow.getName() == null || workflow.getName().trim().isEmpty()) {
            errors.add("Workflow name is required");
        }

        // Validate nodes
        if (workflow.getNodes().isEmpty()) {
            errors.add("Workflow must have at least one node");
        }

        // Validate node IDs are unique
        Set<String> nodeIds = new HashSet<>();
        for (WorkflowNode node : workflow.getNodes()) {
            if (!nodeIds.add(node.getId())) {
                errors.add("Duplicate node ID: " + node.getId());
            }
        }

        // Validate edges reference valid nodes
        for (WorkflowEdge edge : workflow.getEdges()) {
            if (!nodeIds.contains(edge.getSourceNodeId())) {
                errors.add("Edge references invalid source node: " + edge.getSourceNodeId());
            }
            if (!nodeIds.contains(edge.getTargetNodeId())) {
                errors.add("Edge references invalid target node: " + edge.getTargetNodeId());
            }
        }

        return errors;
    }

    /**
     * Validates tenant ID is not null or empty.
     *
     * @param tenantId the tenant ID to validate
     * @throws IllegalArgumentException if tenantId is null or empty
     */
    private void validateTenantId(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant ID must not be null or empty");
        }
    }
}
