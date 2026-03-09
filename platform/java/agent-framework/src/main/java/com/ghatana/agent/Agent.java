package com.ghatana.agent;

import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Canonical definition of an Agent in the Ghatana platform.
 * <p>
 * An Agent is an autonomous entity capable of performing tasks, making decisions,
 * and interacting with its environment through tools and events.
 *
 * @doc.type interface
 * @doc.purpose Canonical Agent definition
 * @doc.layer core
 */
public interface Agent {

    /**
     * Returns the unique identifier of this agent.
     */
    @NotNull
    String getId();

    /**
     * Returns the capabilities of this agent.
     */
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
