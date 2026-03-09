package com.ghatana.orchestrator.ai;

import com.ghatana.aep.domain.agent.registry.AgentExecutionContext;
import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;

/**
 * AI-enhanced agent orchestration manager that extends the existing orchestrator
 * with AI-specific capabilities from event-core. This interface consolidates
 * agent orchestration, chaining, and scheduling functionality.
 */
public interface AIAgentOrchestrationManager {
    
    /**
     * Registers a new agent definition with the orchestration framework.
     *
     * @param agentDef The agent definition to register
     * @return Promise that completes with the registered agent ID
     */
    Promise<String> registerAgent(AgentDefinition agentDef);
    
    /**
     * Creates a chain/pipeline of agents that will execute in sequence.
     *
     * @param chainName Name for the agent chain
     * @param pipeline List of agent definitions in execution order
     * @return Promise completing with the ID of the created pipeline
     */
    Promise<String> chainAgents(String chainName, List<AgentDefinition> pipeline);
    
    /**
     * Executes an agent chain with the given input event.
     *
     * @param chainId ID of the agent chain to execute
     * @param inputEvent The input event to process
     * @param context Execution context with security and tenant information
     * @return Promise completing with the final output events
     */
    Promise<List<Event>> executeChain(String chainId, Event inputEvent, AgentExecutionContext context);
    
    /**
     * Schedules an agent or chain to run according to the specified schedule.
     *
     * @param agentOrChainId ID of the agent or chain to schedule
     * @param schedule Schedule definition
     * @return Promise completing with the ID of the created schedule
     */
    Promise<String> scheduleExecution(String agentOrChainId, ScheduleDefinition schedule);
    
    /**
     * Gets the status of an agent execution.
     *
     * @param executionId ID of the agent execution
     * @return Promise completing with current status of the execution
     */
    Promise<AgentExecutionStatus> getExecutionStatus(String executionId);
    
    /**
     * Cancels a running agent execution.
     *
     * @param executionId ID of the execution to cancel
     * @return Promise completing with true if cancellation was successful
     */
    Promise<Boolean> cancelExecution(String executionId);
    
    /**
     * Gets execution history for an agent or chain.
     *
     * @param agentOrChainId ID of the agent or chain
     * @param limit Maximum number of executions to return
     * @return Promise completing with list of execution history
     */
    Promise<List<ExecutionHistory>> getExecutionHistory(String agentOrChainId, int limit);
    
    /**
     * Gets performance metrics for an agent or chain.
     *
     * @param agentOrChainId ID of the agent or chain
     * @return Promise completing with performance metrics
     */
    Promise<AgentPerformanceMetrics> getPerformanceMetrics(String agentOrChainId);
    
    /**
     * Agent definition for orchestration registration
     */
    record AgentDefinition(
        String id,
        String name,
        String description,
        String className,
        Map<String, Object> config,
        List<AgentCapability> capabilities,
        List<AgentDependency> dependencies,
        ResourceRequirements resourceRequirements,
        Map<String, String> metadata
    ) {
    }
    
    /**
     * Agent capability description
     */
    record AgentCapability(
        String name,
        String description,
        Map<String, Object> parameters
    ) {
    }
    
    /**
     * Agent dependency specification
     */
    record AgentDependency(
        String agentId,
        String versionConstraint,
        boolean required
    )
    {
    }
    
    /**
     * Resource requirements for agent execution
     */
    record ResourceRequirements(
        int minCpuCores,
        int maxCpuCores,
        long minMemoryMb,
        long maxMemoryMb,
        long timeoutMs,
        int maxConcurrentExecutions
    )
    {
    }
    
    /**
     * Schedule definition for periodic execution
     */
    record ScheduleDefinition(
        String cronExpression,
        Map<String, Object> parameters,
        boolean enabled,
        long startTime,
        Long endTime
    )
    {
    }
    
    /**
     * Agent execution status information
     */
    record AgentExecutionStatus(
        String executionId,
        String agentOrChainId,
        ExecutionState state,
        double progress,
        long startTime,
        Long endTime,
        String errorMessage,
        Map<String, Object> metadata
    )
    {
    }
    
    /**
     * Execution history entry
     */
    record ExecutionHistory(
        String executionId,
        String agentOrChainId,
        ExecutionState state,
        long startTime,
        long endTime,
        long durationMs,
        int eventsProcessed,
        String errorMessage
    )
    {
    }
    
    /**
     * Performance metrics for agents
     */
    record AgentPerformanceMetrics(
        String agentOrChainId,
        long totalExecutions,
        long successfulExecutions,
        long failedExecutions,
        double averageDurationMs,
        double averageThroughput,
        long lastExecutionTime,
        Map<String, Double> customMetrics
    )
    {
    }
    
    /**
     * Execution state enumeration
     */
    enum ExecutionState {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        TIMED_OUT,
        CANCELLED
    }
}