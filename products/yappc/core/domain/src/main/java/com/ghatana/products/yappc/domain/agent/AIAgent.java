package com.ghatana.products.yappc.domain.agent;

import com.ghatana.agent.Agent;
import com.ghatana.agent.AgentCapabilities;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Base interface for YAPPC AI Agents.
 * <p>
 * Extends the core Agent interface with AI-specific capabilities such as
 * health checking, metrics collection, and structured input/output processing.
 *
 * @param <TInput>  The input type for agent requests
 * @param <TOutput> The output type for agent responses
 * @doc.type interface
 * @doc.purpose Base AI Agent interface for YAPPC
 * @doc.layer product
 * @doc.pattern Template Method
 */
public interface AIAgent<TInput, TOutput> extends Agent {

    /**
     * Executes the agent with structured input and output.
     *
     * @param input   The input request
     * @param context The execution context
     * @return Promise resolving to the agent result
     */
    @NotNull
    Promise<AgentResult<TOutput>> execute(@NotNull TInput input, @NotNull AIAgentContext context);

    /**
     * Returns the latency SLA in milliseconds.
     */
    long getLatencySLA();

    /**
     * Performs a health check on the agent.
     *
     * @return Promise resolving to the health status
     */
    @NotNull
    Promise<AgentHealth> healthCheck();

    /**
     * Returns the agent metadata including capabilities and supported models.
     */
    @NotNull
    AgentMetadata getMetadata();

    /**
     * Validates the input before processing.
     *
     * @param input The input to validate
     * @throws IllegalArgumentException if input is invalid
     */
    void validateInput(@NotNull TInput input);
}
