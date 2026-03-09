package com.ghatana.platform.workflow;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Canonical definition of a Workflow (or Pipeline) in the Ghatana platform.
 * <p>
 * A Workflow is a composed sequence of steps that achieves a business goal.
 *
 * @doc.type interface
 * @doc.purpose Canonical Workflow definition
 * @doc.layer core
 */
public interface Workflow {

    /**
     * Returns the unique identifier of this workflow.
     */
    @NotNull
    String getId();

    /**
     * Returns the name of this workflow.
     */
    @NotNull
    String getName();

    /**
     * Executes the workflow asynchronously.
     *
     * @param context The execution context
     * @return A Promise resolving to the workflow result
     */
    @NotNull
    <R> Promise<R> execute(@NotNull WorkflowContext context);
}
