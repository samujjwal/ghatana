package com.ghatana.orchestrator.ai.impl;

import com.ghatana.aep.agent.AepContextBridge;
import com.ghatana.aep.domain.agent.registry.AgentExecutionContext;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.runtime.AgentTurnPipeline;
import com.ghatana.agent.registry.service.AgentRegistryService;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.orchestrator.ai.AIAgentOrchestrationManager;
import com.ghatana.orchestrator.core.Orchestrator;
import com.ghatana.orchestrator.executor.AgentStepRunner;
import com.ghatana.orchestrator.queue.ExecutionQueue;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
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
 * <p>Each registered agent is wrapped in an {@link AgentTurnPipeline} that enforces
 * the GAA PERCEIVE → REASON → ACT → CAPTURE → REFLECT lifecycle. Agent chains
 * are executed sequentially, fanning out each step across all current input events
 * in parallel via {@link Promises#toList}.
 *
 * @doc.type class
 * @doc.purpose AI agent orchestration manager with AgentTurnPipeline lifecycle wiring
 * @doc.layer product
 * @doc.pattern Service, Chain-of-Responsibility
 * @doc.gaa.lifecycle perceive|reason|act|capture|reflect
 */
@Slf4j
public class AIAgentOrchestrationManagerImpl implements AIAgentOrchestrationManager {

    private final AgentRegistryService agentRegistryService;
    private final Orchestrator orchestrator;
    private final ExecutionQueue executionQueue;
    private final AgentStepRunner agentStepRunner;
    private final MetricsCollector metrics;
    private final ExecutorService executorService;
    private final AepContextBridge contextBridge;

    private final ExecutorService blockingExecutor = Executors.newFixedThreadPool(4);

    // AI orchestration state management
    private final Map<String, AgentDefinition> agentDefinitions = new ConcurrentHashMap<>();
    private final Map<String, List<String>> agentChains = new ConcurrentHashMap<>();
    private final Map<String, AgentExecutionStatus> executionStatuses = new ConcurrentHashMap<>();
    private final Map<String, ScheduleDefinition> schedules = new ConcurrentHashMap<>();
    private final Map<String, AgentTurnPipeline<Event, List<Event>>> pipelineCache = new ConcurrentHashMap<>();
    private final AtomicLong executionIdCounter = new AtomicLong(0);

    public AIAgentOrchestrationManagerImpl(
            AgentRegistryService agentRegistryService,
            Orchestrator orchestrator,
            ExecutionQueue executionQueue,
            AgentStepRunner agentStepRunner,
            MetricsCollector metrics,
            ExecutorService executorService,
            AepContextBridge contextBridge) {
        this.agentRegistryService = agentRegistryService;
        this.orchestrator = orchestrator;
        this.executionQueue = executionQueue;
        this.agentStepRunner = agentStepRunner;
        this.metrics = metrics;
        this.executorService = executorService;
        this.contextBridge = contextBridge;
    }
    
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
                
                // Store agent definition and build its AgentTurnPipeline
                agentDefinitions.put(agentDef.id(), agentDef);
                pipelineCache.put(agentDef.id(), buildPipeline(agentDef.id()));

                log.info("Registered AI agent for orchestration with pipeline: {}", agentDef.id());
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
                
                log.info("Created AI agent chain '{}' with {} agents: {}", chainName, agentIds.size(), agentIds);
                metrics.incrementCounter("ai.chain.created");

                return chainId;

            } catch (Exception e) {
                log.error("Failed to create agent chain '{}': {}", chainName, e.getMessage(), e);
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
     * Internal recursive method to execute agent chain steps.
     *
     * <p>Each step fans out execution across all {@code currentEvents} in parallel using
     * {@link Promises#toList}. The accumulated output events are then passed to the
     * next step via tail recursion through {@link Promise#then}, so the event loop is
     * never blocked.</p>
     */
    private Promise<List<Event>> executeChainInternal(String executionId, String chainId, List<String> agentIds,
                                                     List<Event> currentEvents, AgentExecutionContext context, int stepIndex) {

        if (stepIndex >= agentIds.size()) {
            updateExecutionStatus(executionId, ExecutionState.COMPLETED, 100.0, null);
            metrics.incrementCounter("ai.chain.completed");
            return Promise.of(currentEvents);
        }

        String currentAgentId = agentIds.get(stepIndex);
        double progress = (double) stepIndex / agentIds.size() * 100.0;
        updateExecutionStatus(executionId, ExecutionState.RUNNING, progress, null);

        // Resolve (or lazily build) the GAA pipeline for this agent.
        AgentContext agentCtx = contextBridge.toAgentContext(context, currentAgentId);
        AgentTurnPipeline<Event, List<Event>> pipeline =
                pipelineCache.computeIfAbsent(currentAgentId, this::buildPipeline);

        // Fan out: one Promise per input event, all dispatched without blocking.
        List<Promise<List<Event>>> eventPromises = currentEvents.stream()
                .map(event -> pipeline.execute(event, agentCtx))
                .toList();

        return Promises.toList(eventPromises)
                .map(results -> {
                    List<Event> accumulated = new ArrayList<>();
                    for (List<Event> outEvents : results) {
                        if (outEvents != null) {
                            accumulated.addAll(outEvents);
                        }
                    }
                    return accumulated;
                })
                .then(accumulated -> {
                    if (accumulated.isEmpty() && stepIndex < agentIds.size() - 1) {
                        log.warn("Agent {} produced no output events in chain {}", currentAgentId, chainId);
                    }
                    return executeChainInternal(executionId, chainId, agentIds, accumulated, context, stepIndex + 1);
                })
                .mapException(e -> {
                    updateExecutionStatus(executionId, ExecutionState.FAILED, progress, e.getMessage());
                    metrics.incrementCounter("ai.chain.failed");
                    log.error("Agent chain {} failed at step {} (agent {})", chainId, stepIndex, currentAgentId, e);
                    return new RuntimeException("Chain execution failed at step " + stepIndex + " (agent " + currentAgentId + ")", e);
                });
    }

    @Override
    public Promise<String> scheduleExecution(String agentOrChainId, ScheduleDefinition schedule) {
        // This would integrate with the existing orchestrator's scheduling capabilities
        // For now, return a basic implementation
        return Promise.ofBlocking(blockingExecutor, () -> {
            String scheduleId = "schedule_" + UUID.randomUUID().toString();
            schedules.put(scheduleId, schedule);
            log.info("Scheduled execution for {}: {}", agentOrChainId, schedule.cronExpression());
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
                log.info("Cancelled execution: {}", executionId);
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
     * Builds an {@link AgentTurnPipeline} that wraps registry-service execution
     * with the GAA PERCEIVE → REASON → ACT → CAPTURE → REFLECT lifecycle.
     *
     * <ul>
     *   <li><b>PERCEIVE</b>: identity — memory enrichment wired in AEP-P3</li>
     *   <li><b>REASON</b>: delegates to {@code AgentRegistryService.executeAgent()}</li>
     *   <li><b>ACT</b>: identity pass-through</li>
     *   <li><b>CAPTURE</b>: no-op — episode storage wired in AEP-P3</li>
     *   <li><b>REFLECT</b>: no-op — learning loop wired in AEP-P4</li>
     * </ul>
     *
     * @param agentId the agent identifier used for tracing
     * @return a new pipeline instance
     */
    private AgentTurnPipeline<Event, List<Event>> buildPipeline(String agentId) {
        return AgentTurnPipeline.<Event, List<Event>>builder(agentId)
                // PERCEIVE: identity pass-through — memory enrichment added in AEP-P3
                .perceive((input, ctx) -> Promise.of(input))
                // REASON: delegate to the agent registry service for actual execution
                .reason((input, ctx) -> {
                    AgentExecutionContext aepCtx = ctx::getTenantId;
                    return agentRegistryService.executeAgent(
                            TenantId.of(ctx.getTenantId()), agentId, input, aepCtx);
                })
                // ACT: identity pass-through
                .act((output, ctx) -> Promise.of(output))
                // CAPTURE: no-op — episode storage added in AEP-P3
                .capture((input, output, ctx) -> Promise.complete())
                // REFLECT: no-op — learning loop added in AEP-P4 (fire-and-forget)
                .reflect((input, output, ctx) -> Promise.complete())
                .build();
    }

    /**
     * Helper method to determine if execution state is terminal.
     */
    private boolean isTerminalState(ExecutionState state) {
        return state == ExecutionState.COMPLETED ||
               state == ExecutionState.FAILED ||
               state == ExecutionState.CANCELLED ||
               state == ExecutionState.TIMED_OUT;
    }
}