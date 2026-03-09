package com.ghatana.virtualorg.workflows;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Base implementation for workflows providing common functionality.
 *
 * <p><b>Purpose</b><br>
 * Provides default implementations and utilities for workflows:
 * - Validation framework
 * - Error handling patterns
 * - Logging and metrics
 * - Common helper methods
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * public class CodeReviewWorkflow extends BaseWorkflow {
 *     
 *     @Override
 *     public WorkflowMetadata getMetadata() {
 *         return WorkflowMetadata.builder()
 *             .withName("code-review")
 *             .withDescription("Multi-agent code review")
 *             .build();
 *     }
 *     
 *     @Override
 *     protected Promise<WorkflowResult> executeInternal(WorkflowContext context) {
 *         // Workflow logic here
 *         return Promise.of(WorkflowResult.success()
 *             .withOutput("status", "approved")
 *             .build());
 *     }
 * }
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Template method pattern for workflows, providing hooks for:
 * - Custom validation logic (validateInternal)
 * - Core workflow execution (executeInternal)
 * - Cleanup operations (cleanupInternal)
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe - uses immutable metadata and stateless execution.
 *
 * @doc.type class
 * @doc.purpose Base workflow implementation with common patterns
 * @doc.layer product
 * @doc.pattern Template Method
 */
public abstract class BaseWorkflow implements Workflow {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Template method for validation with logging and error handling.
     */
    @Override
    public Promise<Void> validate(WorkflowContext context) {
        log.debug("Validating workflow {} for tenant {}", 
            getMetadata().getName(), context.getTenantId());
        
        try {
            // Validate required inputs
            WorkflowMetadata metadata = getMetadata();
            for (Map.Entry<String, WorkflowMetadata.InputSpec> entry : 
                    metadata.getRequiredInputs().entrySet()) {
                String inputName = entry.getKey();
                if (!context.getInput(inputName).isPresent()) {
                    return Promise.ofException(
                        new IllegalArgumentException(
                            "Missing required input: " + inputName));
                }
            }
            
            // Custom validation
            return validateInternal(context);
            
        } catch (Exception e) {
            log.error("Validation failed for workflow {}", 
                getMetadata().getName(), e);
            return Promise.ofException(e);
        }
    }

    /**
     * Template method for execution with logging and error handling.
     */
    @Override
    public Promise<WorkflowResult> execute(WorkflowContext context) {
        String workflowName = getMetadata().getName();
        log.info("Executing workflow {} for tenant {}", 
            workflowName, context.getTenantId());
        
        long startTime = System.currentTimeMillis();
        
        Promise<WorkflowResult> executionPromise = executeInternal(context)
            .whenComplete((result, error) -> {
                long duration = System.currentTimeMillis() - startTime;
                
                if (error != null) {
                    log.error("Workflow {} failed after {}ms", 
                        workflowName, duration, error);
                } else if (result.isSuccess()) {
                    log.info("Workflow {} completed successfully in {}ms", 
                        workflowName, duration);
                } else {
                    log.warn("Workflow {} completed with errors in {}ms: {}", 
                        workflowName, duration, result.getErrorMessage().orElse("Unknown"));
                }
            });

        // Handle execution errors (ActiveJ Promise pattern)
        executionPromise.whenException(error -> {
            log.error("Workflow {} execution failed", workflowName, error);
        });

        return executionPromise;
    }

    /**
     * Template method for cleanup with logging.
     */
    @Override
    public Promise<Void> cleanup(WorkflowContext context) {
        log.debug("Cleaning up workflow {} for tenant {}", 
            getMetadata().getName(), context.getTenantId());
        
        return cleanupInternal(context)
            .whenException(error -> {
                log.warn("Cleanup failed for workflow {}", 
                    getMetadata().getName(), error);
                // Suppress cleanup errors
            });
    }

    /**
     * Custom validation logic - override to add workflow-specific validation.
     *
     * <p>Default implementation validates nothing (always succeeds).
     * Override to check:
     * - Input data formats
     * - Resource availability
     * - Permissions
     * - Preconditions
     *
     * @param context the workflow context
     * @return Promise completing successfully if valid
     */
    protected Promise<Void> validateInternal(WorkflowContext context) {
        return Promise.complete();
    }

    /**
     * Core workflow execution logic - must be implemented by subclasses.
     *
     * <p>This method contains the actual workflow steps.
     * Implementations should:
     * - Use ActiveJ Promise for async operations
     * - Return failed WorkflowResult on business errors (don't throw)
     * - Update context state as needed
     * - Emit metrics and traces
     *
     * @param context the workflow context
     * @return Promise completing with workflow result
     */
    protected abstract Promise<WorkflowResult> executeInternal(WorkflowContext context);

    /**
     * Custom cleanup logic - override to add workflow-specific cleanup.
     *
     * <p>Default implementation does nothing.
     * Override to:
     * - Release resources
     * - Close connections
     * - Cleanup temp files
     * - Final metrics
     *
     * @param context the workflow context
     * @return Promise completing when cleanup is done
     */
    protected Promise<Void> cleanupInternal(WorkflowContext context) {
        return Promise.complete();
    }

    /**
     * Helper to validate a required input exists and has the expected type.
     *
     * @param context the workflow context
     * @param inputName the input parameter name
     * @param expectedType the expected type
     * @return the input value
     * @throws IllegalArgumentException if missing or wrong type
     */
    protected <T> T requireInput(WorkflowContext context, String inputName, Class<T> expectedType) {
        return context.getInput(inputName, expectedType)
            .orElseThrow(() -> new IllegalArgumentException(
                "Missing or invalid required input: " + inputName + 
                " (expected " + expectedType.getSimpleName() + ")"));
    }

    /**
     * Helper to get an optional input with a default value.
     */
    protected <T> T getInputOrDefault(WorkflowContext context, String inputName, 
                                      Class<T> expectedType, T defaultValue) {
        return context.getInput(inputName, expectedType).orElse(defaultValue);
    }
}
