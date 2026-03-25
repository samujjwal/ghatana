package com.ghatana.products.yappc.domain.agent;

import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Base interface for YAPPC AI Agents.
 * <p>
 * Defines the contract for YAPPC's typed, domain-specific AI agents,
 * including structured execution, health checking, and metadata.
 *
 * <p><b>Migration from {@code com.ghatana.agent.Agent}</b><br>
 * As of v3.7.0 this interface no longer extends the deprecated platform
 * {@code Agent} interface.  The YAPPC agent ecosystem uses its own typed
 * {@link AgentResult}, {@link AIAgentContext}, and {@link AgentMetadata}
 * models.  For platform integration (e.g. registering with the platform
 * {@code AgentFrameworkRegistry}), use
 * {@link com.ghatana.agent.migration.LegacyAgentAdapter} or create a
 * {@code TypedAgent} wrapper.
 *
 * @param <TInput>  The input type for agent requests
 * @param <TOutput> The output type for agent responses
 * @doc.type interface
 * @doc.purpose Base AI Agent interface for YAPPC
 * @doc.layer product
 * @doc.pattern Template Method
 */
public interface AIAgent<TInput, TOutput> {

    /**
     * Returns the unique identifier of this agent.
     */
    @NotNull
    String getId();

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
