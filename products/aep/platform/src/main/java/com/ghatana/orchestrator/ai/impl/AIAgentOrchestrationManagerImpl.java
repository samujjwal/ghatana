package com.ghatana.orchestrator.ai.impl;

// removed unused import: Agent
import com.ghatana.aep.domain.agent.registry.AgentExecutionContext;
import com.ghatana.agent.registry.service.AgentRegistryService;
import com.ghatana.orchestrator.ai.AIAgentOrchestrationManager;
import com.ghatana.orchestrator.core.Orchestrator;
import com.ghatana.orchestrator.executor.AgentStepRunner;
import com.ghatana.orchestrator.queue.ExecutionQueue;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of AIAgentOrchestrationManager that integrates AI orchestration
 * capabilities with the existing EventCloud orchestrator infrastructure.
 * 
 * This class consolidates functionality from event-core DefaultAgentOrchestrationManager
 * while leveraging the existing orchestrator's pipeline management, execution queue,
 * and monitoring capabilities.
 */
@Slf4j
@RequiredArgsConstructor
public class AIAgentOrchestrationManagerImpl implements AIAgentOrchestrationManager {
    
    private final AgentRegistryService agentRegistryService;
    private final Orchestrator orchestrator;
    private final ExecutionQueue executionQueue;
    private final AgentStepRunner agentStepRunner;
    private final MetricsCollector metrics;
    private final ExecutorService blockingExecutor = Executors.newFixedThreadPool(4);
    private final ExecutorService executorService;
    
    // AI orchestration state management
    private final Map<String, AgentDefinition> agentDefinitions = new ConcurrentHashMap<>();
    private final Map<String, List<String>> agentChains = new ConcurrentHashMap<>();
    private final Map<String, AgentExecutionStatus> executionStatuses = new ConcurrentHashMap<>();
    private final Map<String, ScheduleDefinition> schedules = new ConcurrentHashMap<>();
    private final AtomicLong executionIdCounter = new AtomicLong(0);
    
    // ==================== AGENT REGISTRATION ====================

    @Override
    public Promise<String> registerAgent(AgentDefinition agentDef) {
        if (agentDef == null || agentDef.id() == null || agentDef.id().trim().isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("Agent definition and ID are required"));
        }
        
        return Promise.ofBlocking(blockingExecutor, () -> {
            try {
                // Validate dependencies
                if (agentDef.dependencies() != null) {
                    for (AgentDependency dep : agentDef.dependencies()) {
                        if (dep.required() && !agentDefinitions.containsKey(dep.agentId())) {
                            throw new IllegalArgumentException("Required dependency not found: " + dep.agentId());
                        }
                    }
                }
                
                // Store agent definition for orchestration
                agentDefinitions.put(agentDef.id(), agentDef);
                
                // Register with agent registry service for execution
                // Note: This would require converting AgentDefinition to AgentManifestProto
                // For now, we'll just track it in orchestration
                
                System.out.println("Registered AI agent for orchestration: " + agentDef.id());
                metrics.incrementCounter("ai.agent.registered");
                
                return agentDef.id();
                
            } catch (Exception e) {
                System.err.println("Failed to register AI agent: " + agentDef.id() + ", error: " + e.getMessage());
                metrics.incrementCounter("ai.agent.registration.failed");
                throw new RuntimeException("Failed to register AI agent", e);
            }
        });
    }

    @Override
    public Promise<String> chainAgents(String chainName, List<AgentDefinition> pipeline) {
        if (chainName == null || chainName.trim().isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("Chain name is required"));
        }
        if (pipeline == null || pipeline.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("Pipeline cannot be null or empty"));
        }
        
        return Promise.ofBlocking(blockingExecutor, () -> {
            try {
                String chainId = "chain_" + UUID.randomUUID().toString();
                
                // Validate all agents in the pipeline are registered
            List<String> agentIds = new ArrayList<>();
                for (AgentDefinition agentDef : pipeline) {
                    if (!agentDefinitions.containsKey(agentDef.id())) {
                        throw new IllegalArgumentException("Agent not registered: " + agentDef.id());
                    }
                    agentIds.add(agentDef.id());
                }
                
                // Store the chain
                agentChains.put(chainId, agentIds);
                
                System.out.println("Created AI agent chain '" + chainName + "' with " + agentIds.size() + " agents: " + agentIds);
                metrics.incrementCounter("ai.chain.created");
                
                return chainId;
                
            } catch (Exception e) {
                System.err.println("Failed to create agent chain '" + chainName + "': " + e.getMessage());
                metrics.incrementCounter("ai.chain.creation.failed");
                throw new RuntimeException("Failed to create agent chain", e);
            }
        });
    }

    @Override
    public Promise<List<Event>> executeChain(String chainId, Event inputEvent, AgentExecutionContext context) {
        if (chainId == null || chainId.trim().isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("Chain ID is required"));
        }
        if (inputEvent == null) {
            return Promise.ofException(new IllegalArgumentException("Input event is required"));
        }
        if (context == null) {
            return Promise.ofException(new IllegalArgumentException("Execution context is required"));
        }
        
        List<String> agentIds = agentChains.get(chainId);
        if (agentIds == null || agentIds.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("Chain not found: " + chainId));
        }
        
        String executionId = "exec_" + executionIdCounter.incrementAndGet();
        
        // Create execution status
        AgentExecutionStatus status = new AgentExecutionStatus(
            executionId,
            chainId,
            ExecutionState.PENDING,
            0.0,
            System.currentTimeMillis(),
            null,
            null,
            Map.of("inputEventType", inputEvent.getType())
        );
        executionStatuses.put(executionId, status);
        
        return executeChainInternal(executionId, chainId, agentIds, Arrays.asList(inputEvent), context, 0);
    }
    
    /**
     * Internal recursive method to execute agent chain
     */
    private Promise<List<Event>> executeChainInternal(String executionId, String chainId, List<String> agentIds, 
                                                     List<Event> currentEvents, AgentExecutionContext context, int stepIndex) {
        
        if (stepIndex >= agentIds.size()) {
            // Chain completed successfully
            updateExecutionStatus(executionId, ExecutionState.COMPLETED, 100.0, null);
            metrics.incrementCounter("ai.chain.completed");
            return Promise.of(currentEvents);
        }
        
        String currentAgentId = agentIds.get(stepIndex);
        double progress = (double) stepIndex / agentIds.size() * 100.0;
        
        // Update execution status
        updateExecutionStatus(executionId, ExecutionState.RUNNING, progress, null);
        
        // Execute current agent with all current events
        List<Event> accumulatedEvents = new ArrayList<>();
        
        // For simplicity, execute agents sequentially with accumulated events
        // In a real implementation, you might want to handle this differently
        Promise<List<Event>> currentStepPromise = Promise.of(accumulatedEvents);
        
        for (Event event : currentEvents) {
            // This is a simplified approach - in practice you'd handle this more elegantly
            try {
                List<Event> outputEvents = agentRegistryService.executeAgent(currentAgentId, event, context).getResult();
                if (outputEvents != null) {
                    accumulatedEvents.addAll(outputEvents);
                }
            } catch (Exception e) {
                return Promise.ofException(e);
            }
        }
        
        // Check if we have output events
        if (accumulatedEvents.isEmpty() && stepIndex < agentIds.size() - 1) {
            // No events to pass to next agent - this might be intentional or an error
            System.err.println("Agent " + currentAgentId + " produced no output events in chain " + chainId);
        }
                
        // Continue with next agent in chain
        try {
            return executeChainInternal(executionId, chainId, agentIds, accumulatedEvents, context, stepIndex + 1);
        } catch (Exception throwable) {
            // Chain execution failed
            updateExecutionStatus(executionId, ExecutionState.FAILED, progress, throwable.getMessage());
            metrics.incrementCounter("ai.chain.failed");
            System.err.println("Agent chain execution failed at step " + stepIndex + " (agent " + currentAgentId + "): " + throwable.getMessage());
            return Promise.ofException(new RuntimeException("Chain execution failed", throwable));
        }
    }

    @Override
    public Promise<String> scheduleExecution(String agentOrChainId, ScheduleDefinition schedule) {
        // This would integrate with the existing orchestrator's scheduling capabilities
        // For now, return a basic implementation
        return Promise.ofBlocking(blockingExecutor, () -> {
            String scheduleId = "schedule_" + UUID.randomUUID().toString();
            schedules.put(scheduleId, schedule);
            
            System.out.println("Scheduled execution for " + agentOrChainId + ": " + schedule.cronExpression());
            metrics.incrementCounter("ai.schedule.created");
            
            return scheduleId;
        });
    }

    @Override
    public Promise<AgentExecutionStatus> getExecutionStatus(String executionId) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            AgentExecutionStatus status = executionStatuses.get(executionId);
            if (status == null) {
                throw new RuntimeException("Execution not found: " + executionId);
            }
            return status;
        });
    }

    @Override
    public Promise<Boolean> cancelExecution(String executionId) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            AgentExecutionStatus status = executionStatuses.get(executionId);
            if (status == null) {
                return false;
            }
            
            if (status.state() == ExecutionState.RUNNING || status.state() == ExecutionState.PENDING) {
                updateExecutionStatus(executionId, ExecutionState.CANCELLED, status.progress(), "Cancelled by user");
                System.out.println("Cancelled execution: " + executionId);
                metrics.incrementCounter("ai.execution.cancelled");
                return true;
            }
            
            return false;
        });
    }

    @Override
    public Promise<List<ExecutionHistory>> getExecutionHistory(String agentOrChainId, int limit) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            // Filter execution statuses for the specified agent/chain
            List<ExecutionHistory> history = executionStatuses.values().stream()
                .filter(status -> agentOrChainId.equals(status.agentOrChainId()))
                .map(status -> new ExecutionHistory(
                    status.executionId(),
                    status.agentOrChainId(),
                    status.state(),
                    status.startTime(),
                    status.endTime() != null ? status.endTime() : System.currentTimeMillis(),
                    status.endTime() != null ? status.endTime() - status.startTime() : System.currentTimeMillis() - status.startTime(),
                    0, // events processed - would need to track this
                    status.errorMessage()
                ))
                .sorted((h1, h2) -> Long.compare(h2.startTime(), h1.startTime())) // Most recent first
                .limit(limit)
                .toList();
                
            return history;
        });
    }

    @Override
    public Promise<AgentPerformanceMetrics> getPerformanceMetrics(String agentOrChainId) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            // Calculate metrics from execution history
            List<AgentExecutionStatus> executions = executionStatuses.values().stream()
                .filter(status -> agentOrChainId.equals(status.agentOrChainId()))
                .toList();
                
            long totalExecutions = executions.size();
            long successfulExecutions = executions.stream()
                .mapToLong(status -> status.state() == ExecutionState.COMPLETED ? 1 : 0)
                .sum();
            long failedExecutions = executions.stream()
                .mapToLong(status -> status.state() == ExecutionState.FAILED ? 1 : 0)
                .sum();
                
            double averageDurationMs = executions.stream()
                .filter(status -> status.endTime() != null)
                .mapToLong(status -> status.endTime() - status.startTime())
                .average()
                .orElse(0.0);
                
            long lastExecutionTime = executions.stream()
                .mapToLong(AgentExecutionStatus::startTime)
                .max()
                .orElse(0L);
                
            return new AgentPerformanceMetrics(
                agentOrChainId,
                totalExecutions,
                successfulExecutions,
                failedExecutions,
                averageDurationMs,
                0.0, // throughput calculation would need more detailed tracking
                lastExecutionTime,
                Map.of() // custom metrics
            );
        });
    }
    
    // ==================== HELPER METHODS ====================
    
    private void updateExecutionStatus(String executionId, ExecutionState state, double progress, String errorMessage) {
        AgentExecutionStatus currentStatus = executionStatuses.get(executionId);
        if (currentStatus != null) {
            AgentExecutionStatus updatedStatus = new AgentExecutionStatus(
                currentStatus.executionId(),
                currentStatus.agentOrChainId(),
                state,
                progress,
                currentStatus.startTime(),
                isTerminalState(state) ? System.currentTimeMillis() : null,
                errorMessage,
                currentStatus.metadata()
            );
            executionStatuses.put(executionId, updatedStatus);
        }
    }
    
    /**
     * Helper method to determine if execution state is terminal
     */
    private boolean isTerminalState(ExecutionState state) {
        return state == ExecutionState.COMPLETED || 
               state == ExecutionState.FAILED || 
               state == ExecutionState.CANCELLED || 
               state == ExecutionState.TIMED_OUT;
    }
}