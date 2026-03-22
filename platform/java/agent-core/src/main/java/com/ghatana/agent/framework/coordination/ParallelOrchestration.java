package com.ghatana.agent.framework.coordination;

import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Orchestration strategy that executes agents in parallel.
 * All agents receive the same input and execute concurrently.
 * 
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Multiple agents analyzing same data</li>
 *   <li>Gathering diverse perspectives on a problem</li>
 *   <li>Parallel validation by different specialists</li>
 *   <li>Voting or consensus mechanisms</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose Parallel agent orchestration
 * @doc.layer framework
 * @doc.pattern Strategy
 */
public final class ParallelOrchestration implements OrchestrationStrategy {
    
    @Override
    @NotNull
    public <TInput, TOutput> Promise<List<TOutput>> orchestrate(
            @NotNull List<Agent<TInput, TOutput>> agents,
            @NotNull TInput task,
            @NotNull com.ghatana.agent.framework.api.AgentContext context) {
        
        context.getLogger().debug("Parallel orchestration: {} agents", agents.size());
        
        // Execute all agents in parallel
        List<Promise<TOutput>> promises = agents.stream()
            .map(agent -> {
                context.getLogger().debug("Starting agent: {}", agent.getAgentId());
                
                long startTime = System.currentTimeMillis();
                return agent.execute(task, context)
                    .whenComplete((result, error) -> {
                        long duration = System.currentTimeMillis() - startTime;
                        context.recordMetric(
                            String.format("orchestration.agent.%s.duration", agent.getAgentId()),
                            duration);
                        
                        if (error != null) {
                            context.getLogger().error("Agent {} failed", agent.getAgentId(), error);
                            context.recordMetric(
                                String.format("orchestration.agent.%s.failure", agent.getAgentId()),
                                1);
                        } else {
                            context.getLogger().debug("Agent {} completed", agent.getAgentId());
                            context.recordMetric(
                                String.format("orchestration.agent.%s.success", agent.getAgentId()),
                                1);
                        }
                    });
            })
            .toList();
        
        // Wait for all to complete
        return Promises.toList(promises);
    }
    
    @Override
    @NotNull
    public String getName() {
        return "Parallel";
    }
}
