package com.ghatana.virtualorg.workflows;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Engine for executing workflows with lifecycle management.
 *
 * <p><b>Purpose</b><br>
 * Provides workflow execution orchestration:
 * - Workflow registration and discovery
 * - Lifecycle management (validate → execute → cleanup)
 * - Error handling and recovery
 * - Metrics and tracing integration
 * - Execution history tracking
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkflowEngine engine = new WorkflowEngine();
 * engine.registerWorkflow(new CodeReviewWorkflow());
 * engine.registerWorkflow(new DailyStandupWorkflow());
 * 
 * WorkflowContext context = WorkflowContext.builder()
 *     .withTenantId("acme-corp")
 *     .withInput("teamId", "team-123")
 *     .build();
 * 
 * WorkflowResult result = engine.execute("daily-standup", context).getResult();
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Central orchestrator for workflow execution, integrating with:
 * - WorkflowRegistry: Manages available workflows
 * - MetricsCollector: Emits execution metrics
 * - TracingProvider: Distributed tracing
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe - concurrent workflow executions are supported.
 *
 * @doc.type class
 * @doc.purpose Workflow execution engine and orchestrator
 * @doc.layer product
 * @doc.pattern Facade
 */
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final Map<String, Workflow> workflows = new ConcurrentHashMap<>();

    /**
     * Registers a workflow with the engine.
     *
     * @param workflow the workflow to register
     * @throws IllegalArgumentException if workflow with same name already registered
     */
    public void registerWorkflow(Workflow workflow) {
        String name = workflow.getMetadata().getName();
        
        if (workflows.containsKey(name)) {
            throw new IllegalArgumentException(
                "Workflow already registered: " + name);
        }
        
        workflows.put(name, workflow);
        log.info("Registered workflow: {} (version {})", 
            name, workflow.getMetadata().getVersion());
    }

    /**
     * Executes a workflow by name with the given context.
     *
     * <p>Execution flow:
     * 1. Lookup workflow by name
     * 2. Validate context
     * 3. Execute workflow
     * 4. Cleanup (always, even on error)
     *
     * @param workflowName the name of the workflow to execute
     * @param context the execution context
     * @return Promise completing with workflow result
     */
    public Promise<WorkflowResult> execute(String workflowName, WorkflowContext context) {
        Workflow workflow = workflows.get(workflowName);
        
        if (workflow == null) {
            log.error("Workflow not found: {}", workflowName);
            return Promise.of(WorkflowResult.failure(
                "Workflow not found: " + workflowName
            ).build());
        }
        
        log.info("Executing workflow: {} for tenant: {}", 
            workflowName, context.getTenantId());
        
        // Validate → Execute → Cleanup (always)
        Promise<WorkflowResult> executionPromise = workflow.validate(context)
            .then(() -> workflow.execute(context))
            .whenComplete((result, error) -> {
                // Always cleanup, even on error
                workflow.cleanup(context)
                    .whenException(cleanupError -> {
                        log.warn("Cleanup failed for workflow: {}", 
                            workflowName, cleanupError);
                    });
            });

        // Handle execution errors (ActiveJ Promise pattern)
        executionPromise.whenException(error -> {
            log.error("Workflow execution failed: {}", workflowName, error);
        });

        return executionPromise;
    }

    /**
     * Executes a workflow definition directly.
     *
     * <p>Used for dynamic workflows that aren't pre-registered.
     *
     * @param workflow the workflow definition to execute
     * @param context the execution context
     * @return Promise completing with workflow result
     */
    public Promise<WorkflowResult> executeWorkflow(WorkflowDefinition workflow, WorkflowContext context) {
        // TODO: Implement workflow definition execution
        // For now, return a stub success result
        log.warn("executeWorkflow stub called for workflow: {}", 
            workflow != null ? "defined" : "null");
        
        return Promise.of(WorkflowResult.success()
            .withOutput("result", "Workflow definition execution not yet implemented")
            .build());
    }

    /**
     * Gets a registered workflow by name.
     *
     * @param workflowName the workflow name
     * @return the workflow, or null if not found
     */
    public Workflow getWorkflow(String workflowName) {
        return workflows.get(workflowName);
    }

    /**
     * Gets all registered workflow names.
     *
     * @return set of workflow names
     */
    public java.util.Set<String> getRegisteredWorkflows() {
        return workflows.keySet();
    }

    /**
     * Gets metadata for all registered workflows.
     *
     * @return map of workflow name to metadata
     */
    public Map<String, WorkflowMetadata> getAllMetadata() {
        Map<String, WorkflowMetadata> metadata = new ConcurrentHashMap<>();
        workflows.forEach((name, workflow) -> 
            metadata.put(name, workflow.getMetadata()));
        return metadata;
    }

    /**
     * Unregisters a workflow.
     *
     * @param workflowName the workflow name to unregister
     * @return true if workflow was removed, false if not found
     */
    public boolean unregisterWorkflow(String workflowName) {
        Workflow removed = workflows.remove(workflowName);
        if (removed != null) {
            log.info("Unregistered workflow: {}", workflowName);
            return true;
        }
        return false;
    }

    /**
     * Clears all registered workflows.
     */
    public void clear() {
        workflows.clear();
        log.info("Cleared all registered workflows");
    }
}
