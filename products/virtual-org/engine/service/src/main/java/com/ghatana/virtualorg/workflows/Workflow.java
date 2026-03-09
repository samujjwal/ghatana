package com.ghatana.virtualorg.workflows;

import io.activej.promise.Promise;

/**
 * Core abstraction for all workflows in the virtual organization system.
 *
 * <p><b>Purpose</b><br>
 * Provides a unified interface for workflow execution, enabling:
 * - Consistent orchestration patterns across all workflow types
 * - Pluggable workflow implementations
 * - Lifecycle management (validation, execution, cleanup)
 * - Error handling and recovery strategies
 * - Metrics and observability integration
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Workflow workflow = new CodeReviewWorkflow(agents, dispatcher);
 * WorkflowContext context = WorkflowContext.builder()
 *     .withInput("pullRequest", prData)
 *     .withTenantId("acme-corp")
 *     .build();
 * 
 * WorkflowResult result = workflow.execute(context).getResult();
 * if (result.isSuccess()) {
 *     // Process successful result
 * }
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Central abstraction for the workflow layer, consumed by:
 * - WorkflowEngine: Executes workflows with lifecycle management
 * - WorkflowRegistry: Discovers and manages available workflows
 * - WorkflowOrchestrator: Coordinates multi-workflow processes
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe as they may be invoked concurrently
 * by the WorkflowEngine. Use ActiveJ Promise for async execution.
 *
 * @see WorkflowContext
 * @see WorkflowResult
 * @see WorkflowMetadata
 * @doc.type interface
 * @doc.purpose Core workflow abstraction for orchestration
 * @doc.layer product
 * @doc.pattern Strategy
 */
public interface Workflow {

    /**
     * Returns metadata describing this workflow.
     *
     * <p>Metadata includes:
     * - Workflow name and description
     * - Required inputs and their types
     * - Expected outputs
     * - Estimated execution time
     * - Required agent roles
     *
     * @return workflow metadata
     */
    WorkflowMetadata getMetadata();

    /**
     * Validates the workflow context before execution.
     *
     * <p>Checks:
     * - Required inputs are present and valid
     * - Required agents/resources are available
     * - Permissions and access control
     * - Preconditions are met
     *
     * <p>This method is called by the WorkflowEngine before execute().
     * Implementations should fail fast with descriptive errors.
     *
     * @param context the workflow execution context
     * @return Promise completing successfully if valid, or with error if invalid
     */
    Promise<Void> validate(WorkflowContext context);

    /**
     * Executes the workflow with the given context.
     *
     * <p>This method contains the core workflow logic:
     * - Coordinate agent interactions
     * - Manage workflow state transitions
     * - Handle errors and retries
     * - Emit progress events
     *
     * <p>Implementations must:
     * - Use ActiveJ Promise for async execution
     * - Handle errors gracefully (return failed Promise, don't throw)
     * - Update context with intermediate results
     * - Emit metrics and trace spans
     *
     * @param context the workflow execution context
     * @return Promise completing with workflow result
     */
    Promise<WorkflowResult> execute(WorkflowContext context);

    /**
     * Cleanup hook called after workflow execution (success or failure).
     *
     * <p>Use this to:
     * - Release acquired resources
     * - Cleanup temporary state
     * - Emit final metrics
     * - Close connections
     *
     * <p>This method is called by WorkflowEngine in a finally block.
     * Implementations should never throw exceptions.
     *
     * @param context the workflow execution context
     * @return Promise completing when cleanup is done
     */
    default Promise<Void> cleanup(WorkflowContext context) {
        return Promise.complete();
    }
}
