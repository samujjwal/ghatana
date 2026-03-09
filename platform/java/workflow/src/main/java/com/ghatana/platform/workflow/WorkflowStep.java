package com.ghatana.platform.workflow;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * A single step within a Workflow.
 *
 * @doc.type interface
 * @doc.purpose Workflow step definition
 * @doc.layer core
 */
public interface WorkflowStep {

    /**
     * Executes this step with the given workflow context (simplified API).
     * Default implementation calls the generic execute method with context as both input and output.
     *
     * @param context The execution context
     * @return A Promise resolving to the updated workflow context
     */
    @NotNull
    default Promise<WorkflowContext> execute(@NotNull WorkflowContext context) {
        return execute((Object) context, context).then(result -> {
            if (result instanceof WorkflowContext wc) {
                return Promise.of(wc);
            }
            // If result is not WorkflowContext, wrap it
            context.put("result", result);
            return Promise.of(context);
        });
    }

    /**
     * Executes this step (generic API for legacy compatibility).
     *
     * @param input The input to the step
     * @param context The execution context
     * @return A Promise resolving to the step output
     */
    @NotNull
    default <I, O> Promise<O> execute(I input, @NotNull WorkflowContext context) {
        // Default delegates to simplified version
        return execute(context).then(result -> (Promise<O>) Promise.of(result));
    }

    /**
     * Returns the ID of this step (optional).
     * Default implementation returns class simple name.
     */
    @NotNull
    default String getStepId() {
        return getClass().getSimpleName();
    }
}
