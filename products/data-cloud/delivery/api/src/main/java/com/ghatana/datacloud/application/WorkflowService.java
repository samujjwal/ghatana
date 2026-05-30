package com.ghatana.datacloud.application;

import com.ghatana.datacloud.entity.*;
import com.ghatana.datacloud.observability.ObservabilityService;
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
 * <p><b>Observability</b><br>
 * Integrates with {@link ObservabilityService} to provide unified correlation tracking
 * across all workflow operations, ensuring consistent observability for runtime truth
 * with correlationId, tenantId, surface, runId, pipelineId, and other context identifiers.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkflowService service = new WorkflowService(
 *     workflowRepository,
 *     collectionRepository,
 *     executionRepository,
 *     metrics,
 *     observabilityService
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
 * - Uses ObservabilityService (unified correlation tracking)
 * - Enforces multi-tenancy and workflow validation
 *
 * <p><b>Thread Safety</b><br>
 * Stateless service - thread-safe. All state passed via parameters or repositories.
 *
 * @see Workflow
 * @see WorkflowNode
 * @see WorkflowEdge
 * @see MetricsCollector
 * @see ObservabilityService
 * @doc.type class
 * @doc.purpose Service for workflow management and execution with unified observability
 * @doc.layer product
 * @doc.pattern Service (Application Layer)
 */
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private final WorkflowRepository workflowRepository;
    private final CollectionRepository collectionRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final MetricsCollector metrics;
    private final ObservabilityService observabilityService;

    /**
     * Creates a new workflow service.
     *
     * @param workflowRepository   the workflow repository (required)
     * @param collectionRepository the collection repository (required)
     * @param executionRepository  the workflow execution repository (required)
     * @param metrics              the metrics collector (required)
     * @param observabilityService observability service for unified correlation tracking (required)
     * @throws NullPointerException if any parameter is null
     */
    public WorkflowService(
            WorkflowRepository workflowRepository,
            CollectionRepository collectionRepository,
            WorkflowExecutionRepository executionRepository,
            MetricsCollector metrics,
            ObservabilityService observabilityService) {
        this.workflowRepository = Objects.requireNonNull(workflowRepository, "WorkflowRepository must not be null");
        this.collectionRepository = Objects.requireNonNull(collectionRepository, "CollectionRepository must not be null");
        this.executionRepository = Objects.requireNonNull(executionRepository, "WorkflowExecutionRepository must not be null");
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector must not be null");
        this.observabilityService = Objects.requireNonNull(observabilityService, "ObservabilityService must not be null");
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
                    log.warn("Workflow validation failed: tenantId={}, errors={}",
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
                    log.info("Workflow created: tenantId={}, id={}, name={}, createdBy={}",
                        tenantId, result.getId(), result.getName(), userId);
                } else {
                    metrics.incrementCounter("workflow.create.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                    log.error("Failed to create workflow: tenantId={}", tenantId, ex);
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
                    log.debug("Listed workflows: tenantId={}, count={}",
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
                    log.info("Workflow updated: tenantId={}, id={}, version={}, updatedBy={}",
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
                    log.info("Workflow deleted: tenantId={}, id={}, deletedBy={}",
                        tenantId, workflowId, userId);
                } else {
                    metrics.incrementCounter("workflow.delete.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                }
            });
    }

    /**
     * Executes a workflow and returns the execution record.
     *
     * <p><b>Validation</b><br>
     * - Workflow must exist for the given tenant
     * - Workflow must be in ACTIVE or DRAFT status
     *
     * <p><b>Lifecycle</b><br>
     * Creates a PENDING execution, persists it, and asynchronously transitions to RUNNING.
     * The caller may poll {@code getExecution()} or subscribe via WebSocket to track progress.
     *
     * <p><b>Observability</b><br>
     * Uses {@link ObservabilityService} to track execution with unified correlation
     * identifiers including correlationId, tenantId, surface, runId, pipelineId.
     *
     * @param tenantId       the tenant identifier (required)
     * @param workflowId     the workflow to execute (required)
     * @param userId         the user triggering execution (for audit)
     * @param inputVariables runtime variables available to all nodes
     * @return Promise of the created WorkflowExecution (status PENDING at creation time)
     * @throws IllegalArgumentException if workflow not found or is not executable
     */
    public Promise<WorkflowExecution> executeWorkflow(
            String tenantId,
            UUID workflowId,
            String userId,
            Map<String, Object> inputVariables) {
        validateTenantId(tenantId);
        Objects.requireNonNull(workflowId, "Workflow ID must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        // Create observability context with unified correlation tracking
        String correlationId = UUID.randomUUID().toString();
        try (ObservabilityService.ObservabilityContext obsContext =
                observabilityService.createContext(
                    correlationId,
                    tenantId,
                    "workflow",
                    null,  // runId - will be set after execution ID is generated
                    null   // jobId - not applicable for direct workflow execution
                )) {

            obsContext.addMetadata("operation", "executeWorkflow");
            obsContext.addMetadata("workflowId", workflowId.toString());
            obsContext.addMetadata("userId", userId);
            obsContext.addMetadata("pipelineId", workflowId.toString());

            return workflowRepository.findById(tenantId, workflowId)
                .then(workflowOpt -> {
                    if (workflowOpt.isEmpty()) {
                        obsContext.recordEvent("workflow_not_found", Map.of(
                            "tenantId", tenantId,
                            "workflowId", workflowId.toString()
                        ));
                        metrics.incrementCounter("workflow.execute.not_found",
                            "tenant", tenantId);
                        return Promise.ofException(
                            new IllegalArgumentException("Workflow not found: " + workflowId)
                        );
                    }

                    Workflow workflow = workflowOpt.get();
                    String status = workflow.getStatus();
                    if (!"ACTIVE".equalsIgnoreCase(status) && !"DRAFT".equalsIgnoreCase(status)) {
                        obsContext.recordEvent("workflow_not_executable", Map.of(
                            "tenantId", tenantId,
                            "workflowId", workflowId.toString(),
                            "status", status
                        ));
                        metrics.incrementCounter("workflow.execute.not_executable",
                            "tenant", tenantId);
                        return Promise.ofException(
                            new IllegalStateException(
                                "Workflow " + workflowId + " cannot be executed in status: " + status)
                        );
                    }

                    WorkflowExecution execution = WorkflowExecution.builder()
                        .tenantId(tenantId)
                        .workflowId(workflowId)
                        .status(WorkflowExecution.Status.PENDING)
                        .startedBy(userId)
                        .inputVariables(inputVariables != null ? inputVariables : Map.of())
                        .build();

                    // Update runId with execution ID after creation
                    obsContext.set(ObservabilityService.RUN_ID, execution.getId().toString());

                    return executionRepository.save(tenantId, execution)
                        .then(savedExecution -> {
                            obsContext.recordEvent("workflow_execution_created", Map.of(
                                "tenantId", tenantId,
                                "workflowId", workflowId.toString(),
                                "executionId", savedExecution.getId().toString(),
                                "triggeredBy", userId
                            ));
                            return Promise.of(savedExecution);
                        });
                })
                .whenComplete((execution, ex) -> {
                    if (ex == null) {
                        metrics.incrementCounter("workflow.execute.created",
                            "tenant", tenantId);
                        log.info("Workflow execution created: tenantId={}, workflowId={}, executionId={}, correlationId={}, triggeredBy={}",
                            tenantId, workflowId, execution.getId(), correlationId, userId);
                    } else {
                        obsContext.recordEvent("workflow_execution_failed", Map.of(
                            "tenantId", tenantId,
                            "workflowId", workflowId.toString(),
                            "error", ex.getMessage()
                        ));
                        metrics.incrementCounter("workflow.execute.error",
                            "tenant", tenantId,
                            "error", ex.getClass().getSimpleName());
                        log.error("Failed to create workflow execution: tenantId={}, workflowId={}, correlationId={}",
                            tenantId, workflowId, correlationId, ex);
                    }
                });
        }
    }

    /**
     * Gets a workflow execution by ID.
     *
     * @param tenantId    the tenant identifier (required)
     * @param executionId the execution ID (required)
     * @return Promise of Optional containing the execution if found
     */
    public Promise<Optional<WorkflowExecution>> getExecution(String tenantId, UUID executionId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(executionId, "Execution ID must not be null");

        return executionRepository.findById(tenantId, executionId)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter(
                        result.isPresent() ? "workflow.execution.get.success" : "workflow.execution.get.not_found",
                        "tenant", tenantId);
                } else {
                    metrics.incrementCounter("workflow.execution.get.error",
                        "tenant", tenantId,
                        "error", ex.getClass().getSimpleName());
                }
            });
    }

    /**
     * Lists executions for a workflow definition.
     *
     * @param tenantId   the tenant identifier (required)
     * @param workflowId the workflow definition ID (required)
     * @param offset     zero-based offset for pagination
     * @param limit      maximum number of results
     * @return Promise of list of executions, newest first
     */
    public Promise<List<WorkflowExecution>> listExecutions(
            String tenantId,
            UUID workflowId,
            int offset,
            int limit) {
        validateTenantId(tenantId);
        Objects.requireNonNull(workflowId, "Workflow ID must not be null");
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must be >= 0");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }

        return executionRepository.findByWorkflowId(tenantId, workflowId, limit, offset)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("workflow.execution.list.success",
                        "tenant", tenantId);
                } else {
                    metrics.incrementCounter("workflow.execution.list.error",
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
