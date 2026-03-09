package com.ghatana.agent.framework.coordination;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Orchestration strategy where a coordinator agent delegates to subordinate agents.
 * The coordinator decides task decomposition and delegates subtasks.
 * 
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Complex tasks requiring decomposition</li>
 *   <li>Manager-subordinate relationships</li>
 *   <li>Hierarchical decision making</li>
 *   <li>Dynamic task allocation</li>
 * </ul>
 * 
 * <p><b>Example:</b>
 * <pre>{@code
 * HierarchicalOrchestration orchestration = new HierarchicalOrchestration(
 *     coordinatorAgent,
 *     delegationManager
 * );
 * }</pre>
 * 
 * @doc.type class
 * @doc.purpose Hierarchical agent orchestration with coordinator
 * @doc.layer framework
 * @doc.pattern Strategy + Mediator
 */
public final class HierarchicalOrchestration implements OrchestrationStrategy {
    
    private final Agent<?, ?> coordinatorAgent;
    private final DelegationManager delegationManager;
    
    /**
     * Creates a new HierarchicalOrchestration.
     * 
     * @param coordinatorAgent The coordinator agent that delegates
     * @param delegationManager Delegation manager for routing
     */
    public HierarchicalOrchestration(
            @NotNull Agent<?, ?> coordinatorAgent,
            @NotNull DelegationManager delegationManager) {
        this.coordinatorAgent = Objects.requireNonNull(coordinatorAgent, 
            "coordinatorAgent cannot be null");
        this.delegationManager = Objects.requireNonNull(delegationManager, 
            "delegationManager cannot be null");
    }
    
    @Override
    @NotNull
    @SuppressWarnings("unchecked")
    public <TInput, TOutput> Promise<List<TOutput>> orchestrate(
            @NotNull List<Agent<TInput, TOutput>> agents,
            @NotNull TInput task,
            @NotNull com.ghatana.agent.framework.api.AgentContext context) {
        
        context.getLogger().debug("Hierarchical orchestration: coordinator={}, subordinates={}",
            coordinatorAgent.getAgentId(), agents.size());
        
        // 1. Coordinator decomposes task and decides delegation
        Agent<TInput, CoordinatorDecision> typedCoordinator = 
            (Agent<TInput, CoordinatorDecision>) coordinatorAgent;
        
        return typedCoordinator.execute(task, context)
            .then(decision -> {
                context.getLogger().debug("Coordinator decision: {} subtasks", 
                    decision.getSubtasks().size());
                
                // 2. Delegate subtasks to subordinate agents
                List<Promise<TOutput>> delegationPromises = new ArrayList<>();
                
                for (Subtask subtask : decision.getSubtasks()) {
                    // Find agent matching subtask requirements
                    Agent<TInput, TOutput> agent = findAgent(agents, subtask);
                    
                    if (agent != null) {
                        context.getLogger().debug("Delegating subtask {} to agent {}", 
                            subtask.getId(), agent.getAgentId());
                        
                        Promise<TOutput> result = agent.execute(
                            (TInput) subtask.getData(), 
                            context);
                        delegationPromises.add(result);
                    } else {
                        context.getLogger().warn("No agent found for subtask {}", subtask.getId());
                    }
                }
                
                // 3. Wait for all delegations to complete
                return io.activej.promise.Promises.toList(delegationPromises);
            });
    }
    
    @Override
    @NotNull
    public String getName() {
        return "Hierarchical";
    }
    
    /**
     * Finds an agent matching subtask requirements.
     * Simple implementation - can be enhanced with more sophisticated matching.
     */
    private <TInput, TOutput> Agent<TInput, TOutput> findAgent(
            List<Agent<TInput, TOutput>> agents,
            Subtask subtask) {
        
        String requiredAgentId = subtask.getRequiredAgentId();
        if (requiredAgentId != null) {
            return agents.stream()
                .filter(a -> a.getAgentId().equals(requiredAgentId))
                .findFirst()
                .orElse(null);
        }
        
        // If no specific agent required, return first available
        return agents.isEmpty() ? null : agents.get(0);
    }
    
    /**
     * Represents a coordinator's decision on how to decompose a task.
     */
    public static final class CoordinatorDecision {
        private final List<Subtask> subtasks;
        private final String strategy;
        
        public CoordinatorDecision(List<Subtask> subtasks, String strategy) {
            this.subtasks = List.copyOf(subtasks);
            this.strategy = strategy;
        }
        
        public List<Subtask> getSubtasks() {
            return subtasks;
        }
        
        public String getStrategy() {
            return strategy;
        }
    }
    
    /**
     * Represents a subtask to be delegated.
     */
    public static final class Subtask {
        private final String id;
        private final Object data;
        private final String requiredAgentId;
        private final String requiredRole;
        
        public Subtask(String id, Object data, String requiredAgentId, String requiredRole) {
            this.id = id;
            this.data = data;
            this.requiredAgentId = requiredAgentId;
            this.requiredRole = requiredRole;
        }
        
        public String getId() {
            return id;
        }
        
        public Object getData() {
            return data;
        }
        
        public String getRequiredAgentId() {
            return requiredAgentId;
        }
        
        public String getRequiredRole() {
            return requiredRole;
        }
    }
}
