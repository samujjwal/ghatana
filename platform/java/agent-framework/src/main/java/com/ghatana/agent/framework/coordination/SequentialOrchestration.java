package com.ghatana.agent.framework.coordination;

import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Orchestration strategy that executes agents sequentially.
 * Output of one agent can be input to the next (if types match).
 * 
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Pipeline where each agent transforms previous output</li>
 *   <li>Workflow with dependencies between steps</li>
 *   <li>Budget-constrained execution (pay only for successful steps)</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose Sequential agent orchestration
 * @doc.layer framework
 * @doc.pattern Strategy
 */
public final class SequentialOrchestration implements OrchestrationStrategy {
    
    @Override
    @NotNull
    public <TInput, TOutput> Promise<List<TOutput>> orchestrate(
            @NotNull List<Agent<TInput, TOutput>> agents,
            @NotNull TInput task,
            @NotNull com.ghatana.agent.framework.api.AgentContext context) {
        
        context.getLogger().debug("Sequential orchestration: {} agents", agents.size());
        
        List<TOutput> results = new java.util.ArrayList<>();
        Promise<Void> chain = Promise.complete();
        
        for (int i = 0; i < agents.size(); i++) {
            final int index = i;
            final Agent<TInput, TOutput> agent = agents.get(i);
            
            chain = chain.then(() -> {
                context.getLogger().debug("Executing agent {}/{}: {}", 
                    index + 1, agents.size(), agent.getAgentId());
                
                long startTime = System.currentTimeMillis();
                return agent.execute(task, context)
                    .whenComplete((result, error) -> {
                        long duration = System.currentTimeMillis() - startTime;
                        context.recordMetric(
                            String.format("orchestration.agent.%d.duration", index + 1),
                            duration);
                        
                        if (error != null) {
                            context.getLogger().error("Agent {} failed", agent.getAgentId(), error);
                            context.recordMetric(
                                String.format("orchestration.agent.%d.failure", index + 1),
                                1);
                        } else {
                            results.add(result);
                        }
                    })
                    .toVoid();
            });
        }
        
        return chain.map(v -> results);
    }
    
    @Override
    @NotNull
    public String getName() {
        return "Sequential";
    }
}
