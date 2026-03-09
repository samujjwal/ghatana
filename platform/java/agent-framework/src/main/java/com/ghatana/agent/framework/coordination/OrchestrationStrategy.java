package com.ghatana.agent.framework.coordination;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Strategy for orchestrating multiple agents.
 * 
 * <p><b>Built-in strategies:</b>
 * <ul>
 *   <li>{@link SequentialOrchestration} - Execute agents one after another</li>
 *   <li>{@link ParallelOrchestration} - Execute agents concurrently</li>
 *   <li>{@link HierarchicalOrchestration} - Coordinator delegates to subordinates</li>
 * </ul>
 * 
 * <p><b>Example:</b>
 * <pre>{@code
 * OrchestrationStrategy strategy = new SequentialOrchestration();
 * 
 * Promise<List<Result>> results = strategy.orchestrate(
 *     List.of(agent1, agent2, agent3),
 *     task,
 *     context
 * );
 * }</pre>
 * 
 * @doc.type interface
 * @doc.purpose Multi-agent orchestration strategy
 * @doc.layer framework
 * @doc.pattern Strategy
 */
public interface OrchestrationStrategy {
    
    /**
     * Orchestrates multiple agents to process a task.
     * 
     * @param agents List of agents to orchestrate
     * @param task Task to process
     * @param context Execution context
     * @param <TInput> Task type
     * @param <TOutput> Result type
     * @return Promise of results from all agents
     */
    @NotNull
    <TInput, TOutput> Promise<List<TOutput>> orchestrate(
        @NotNull List<Agent<TInput, TOutput>> agents,
        @NotNull TInput task,
        @NotNull com.ghatana.agent.framework.api.AgentContext context);
    
    /**
     * Gets the name of this orchestration strategy.
     * @return Strategy name
     */
    @NotNull
    String getName();
    
    /**
     * Represents an agent in orchestration.
     * @param <TInput> Input type
     * @param <TOutput> Output type
     */
    interface Agent<TInput, TOutput> {
        
        /**
         * Gets the agent ID.
         * @return Agent ID
         */
        @NotNull
        String getAgentId();
        
        /**
         * Executes the agent with given input.
         * 
         * @param input Input data
         * @param context Execution context
         * @return Promise of output
         */
        @NotNull
        Promise<TOutput> execute(
            @NotNull TInput input,
            @NotNull com.ghatana.agent.framework.api.AgentContext context);
    }
}
