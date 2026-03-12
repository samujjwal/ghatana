package com.ghatana.platform.workflow;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * A single executable step within a Workflow.
 *
 * <p>This is a functional interface — implementations may be provided as a lambda:
 * <pre>{@code
 * WorkflowStep step = ctx -> {
 *     ctx.setVariable("done", true);
 *     return Promise.of(ctx);
 * };
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Workflow step definition (functional interface)
 * @doc.layer core
 */
@FunctionalInterface
public interface WorkflowStep {

    /**
     * Executes this step with the given workflow context.
     *
     * @param context The execution context (mutable)
     * @return A Promise resolving to the updated workflow context
     */
    @NotNull
    Promise<WorkflowContext> execute(@NotNull WorkflowContext context);

    /**
     * Executes this step with explicit input (legacy compatibility).
     *
     * <p>Default implementation ignores {@code input} and delegates to
     * {@link #execute(WorkflowContext)}.
     *
     * @param input   the step input (ignored by default; context carries all state)
     * @param context The execution context
     * @return A Promise resolving to the step output
     */
    @NotNull
    @SuppressWarnings("unchecked")
    default <I, O> Promise<O> execute(I input, @NotNull WorkflowContext context) {
        return execute(context).then(result -> (Promise<O>) Promise.of(result));
    }

    /**
     * Returns the ID of this step (optional).
     * Default implementation returns the class simple name.
     */
    @NotNull
    default String getStepId() {
        return getClass().getSimpleName();
    }
}
