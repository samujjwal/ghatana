package com.ghatana.agent;

import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Original untyped agent contract.
 *
 * @deprecated Use {@link TypedAgent} instead.
 *
 * <p>This interface is retained for backward compatibility with legacy consumers
 * (virtual-org, yappc workflow engine, early versions of tutorputor). New code
 * must implement {@link TypedAgent} exclusively.
 *
 * <h2>Migration Path</h2>
 * <pre>
 *   Phase 1 (complete): Wrap legacy agents with {@link com.ghatana.agent.migration.LegacyAgentAdapter}
 *   Phase 2 (in progress): Migrate product code to implement {@link TypedAgent} directly
 *   Phase 3 (planned v3.0.0): Remove this interface and all adapters
 * </pre>
 *
 * @see TypedAgent
 * @see com.ghatana.agent.migration.LegacyAgentAdapter
 *
 * @doc.type interface
 * @doc.purpose Deprecated untyped agent contract — do not use in new code
 * @doc.layer core
 */
@Deprecated
public interface Agent {

    /**
     * Returns the unique identifier of this agent.
     */
    @NotNull
    String getId();

    /**
     * Returns the capabilities of this agent.
     *
     * @deprecated Use {@link TypedAgent#descriptor()} for richer metadata.
     */
    @Deprecated
    @NotNull
    AgentCapabilities getCapabilities();

    /**
     * Initializes the agent with the given context.
     *
     * @param context The agent context
     * @return A Promise resolving when initialization is complete
     */
    @NotNull
    Promise<Void> initialize(@NotNull AgentContext context);

    /**
     * Starts the agent.
     *
     * @return A Promise resolving when the agent is started
     */
    @NotNull
    Promise<Void> start();

    /**
     * Processes a task asynchronously.
     *
     * @param task The task to process
     * @param context The context in which the task is processed
     * @return A Promise resolving to the task result
     */
    @NotNull
    <T, R> Promise<R> process(@NotNull T task, @NotNull AgentContext context);

    /**
     * Shuts down the agent and releases resources.
     */
    @NotNull
    Promise<Void> shutdown();
}
