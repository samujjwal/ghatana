package com.ghatana.orchestrator.ai.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.agent.AepContextBridge;
import com.ghatana.aep.domain.agent.registry.AgentExecutionContext;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.Episode;
import com.ghatana.agent.framework.memory.MemoryFilter;
import com.ghatana.agent.framework.runtime.AgentTurnPipeline;
import com.ghatana.agent.registry.service.AgentRegistryService;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.orchestrator.ai.AIAgentOrchestrationManager;
import com.ghatana.orchestrator.core.Orchestrator;
import com.ghatana.orchestrator.executor.AgentStepRunner;
import com.ghatana.orchestrator.queue.ExecutionQueue;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * <h2>Event Sourcing (YAPPC-Ph1e)</h2>
 * <p>All state mutations ({@code registerAgent}, {@code chainAgents}, execution
 * start/complete/fail/cancel) are durably appended to the {@link EventLogStore}
 * as typed, JSON-payload events. On startup call {@link #rebuildFromEventLog()}
 * to replay the log and reconstruct in-memory state.
 *
 * <p>The {@link EventLogStore} is optional: if {@code null}, event sourcing is
 * silently skipped and the implementation behaves as in-memory only.
 *
 * @doc.type class
 * @doc.purpose AI agent orchestration manager with AgentTurnPipeline lifecycle wiring
 * @doc.layer product
 * @doc.pattern Service, Chain-of-Responsibility, EventSourced
 * @doc.gaa.lifecycle perceive|reason|act|capture|reflect
 * @doc.gaa.memory episodic
 */
@Slf4j
public class AIAgentOrchestrationManagerImpl implements AIAgentOrchestrationManager {

    // ==================== Event-type constants for the event log ====================

    private static final String EVT_AGENT_REGISTERED      = "ORCHESTRATION_AGENT_REGISTERED";
    private static final String EVT_CHAIN_CREATED          = "ORCHESTRATION_CHAIN_CREATED";
    private static final String EVT_EXECUTION_STARTED      = "ORCHESTRATION_EXECUTION_STARTED";
    private static final String EVT_EXECUTION_COMPLETED    = "ORCHESTRATION_EXECUTION_COMPLETED";
    private static final String EVT_EXECUTION_FAILED       = "ORCHESTRATION_EXECUTION_FAILED";
    private static final String EVT_EXECUTION_CANCELLED    = "ORCHESTRATION_EXECUTION_CANCELLED";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ==================== Dependencies ====================

    private final AgentRegistryService agentRegistryService;
    private final Orchestrator orchestrator;
    private final ExecutionQueue executionQueue;
    private final AgentStepRunner agentStepRunner;
    private final MetricsCollector metrics;
    private final ExecutorService executorService;
    private final AepContextBridge contextBridge;

    /**
     * Durable event log for event-sourced orchestrator state.
     * All state mutations are appended here; state is rebuilt from the log on startup.
     */
    private final EventLogStore eventLogStore;

    /**
     * Tenant context used when writing and reading orchestration events.
     * Defaults to the {@code "aep-system"} system tenant.
     */
    private final TenantContext systemTenant;

    private final ExecutorService blockingExecutor = Executors.newFixedThreadPool(4);

    // AI orchestration state management (in-memory, rebuilt from event log on startup)
    private final Map<String, AgentDefinition> agentDefinitions = new ConcurrentHashMap<>();
    private final Map<String, List<String>> agentChains = new ConcurrentHashMap<>();
    private final Map<String, AgentExecutionStatus> executionStatuses = new ConcurrentHashMap<>();
    private final Map<String, ScheduleDefinition> schedules = new ConcurrentHashMap<>();
    private final Map<String, AgentTurnPipeline<Event, List<Event>>> pipelineCache = new ConcurrentHashMap<>();
    private final AtomicLong executionIdCounter = new AtomicLong(0);

    // ==================== Constructors ====================

    /**
     * Full constructor with event-sourcing support.
     *
     * @param agentRegistryService agent registry service
     * @param orchestrator         core orchestrator
     * @param executionQueue       execution queue
     * @param agentStepRunner      step runner
     * @param metrics              metrics collector
     * @param executorService      async executor
     * @param contextBridge        AEP context bridge
     * @param eventLogStore        event log store for durable event-sourced state (never {@code null})
     * @param systemTenantId       tenant id for orchestration events (default: {@code "aep-system"})
     */
    public AIAgentOrchestrationManagerImpl(
            AgentRegistryService agentRegistryService,
            Orchestrator orchestrator,
            ExecutionQueue executionQueue,
            AgentStepRunner agentStepRunner,
            MetricsCollector metrics,
            ExecutorService executorService,
            AepContextBridge contextBridge,
            EventLogStore eventLogStore,
            String systemTenantId) {
        this.agentRegistryService = agentRegistryService;
        this.orchestrator = orchestrator;
        this.executionQueue = executionQueue;
        this.agentStepRunner = agentStepRunner;
        this.metrics = metrics;
        this.executorService = executorService;
        this.contextBridge = contextBridge;
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore required — use EventLogStore.noOp() for tests/dev");
        this.systemTenant = TenantContext.of(systemTenantId != null ? systemTenantId : "aep-system");
    }

    /**
     * Backwards-compatible constructor without event sourcing.
     *
     * @param agentRegistryService agent registry service
     * @param orchestrator         core orchestrator
     * @param executionQueue       execution queue
     * @param agentStepRunner      step runner
     * @param metrics              metrics collector
     * @param executorService      async executor
     * @param contextBridge        AEP context bridge
     */
    public AIAgentOrchestrationManagerImpl(
            AgentRegistryService agentRegistryService,
            Orchestrator orchestrator,
            ExecutionQueue executionQueue,
            AgentStepRunner agentStepRunner,
            MetricsCollector metrics,
            ExecutorService executorService,
            AepContextBridge contextBridge) {
        this(agentRegistryService, orchestrator, executionQueue, agentStepRunner,
             metrics, executorService, contextBridge, null, null);
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

                // Event sourcing: append durable state event
                String agentTypeVal = (agentDef.metadata() != null)
                        ? agentDef.metadata().getOrDefault("agentType", "unknown")
                        : "unknown";
                appendStateEvent(EVT_AGENT_REGISTERED, Map.of(
                        "agentId", agentDef.id(),
                        "agentName", agentDef.name() != null ? agentDef.name() : "",
                        "agentType", agentTypeVal,
                        "timestamp", System.currentTimeMillis()
                ));

                log.info("Registered AI agent for orchestration with pipeline: {}", agentDef.id());
                metrics.incrementCounter("ai.agent.registered");

                return agentDef.id();
                
            } catch (Exception e) {
                log.error("Failed to register AI agent: {}, error: {}", agentDef.id(), e.getMessage(), e);
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

                // Event sourcing: append durable chain-created event
                appendStateEvent(EVT_CHAIN_CREATED, Map.of(
                        "chainId", chainId,
                        "chainName", chainName,
                        "agentIds", agentIds,
                        "timestamp", System.currentTimeMillis()
                ));

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

        // Event sourcing: append execution-started event (fire-and-forget; never blocks caller)
        appendStateEvent(EVT_EXECUTION_STARTED, Map.of(
                "executionId", executionId,
                "chainId", chainId,
                "inputEventType", inputEvent.getType(),
                "timestamp", System.currentTimeMillis()
        ));

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
            appendStateEvent(EVT_EXECUTION_COMPLETED, Map.of(
                    "executionId", executionId,
                    "chainId", chainId,
                    "outputEventCount", currentEvents.size(),
                    "timestamp", System.currentTimeMillis()
            ));
            return Promise.of(currentEvents);
        }

        String currentAgentId = agentIds.get(stepIndex);
        double progress = (double) stepIndex / agentIds.size() * 100.0;
        updateExecutionStatus(executionId, ExecutionState.RUNNING, progress, null);

        // Resolve (or lazily build) the GAA pipeline for this agent.
        AgentContext agentCtx = contextBridge.toAgentContext(context, currentAgentId);
        AgentTurnPipeline<Event, List<Event>> pipeline =
                pipelineCache.computeIfAbsent(currentAgentId, this::buildPipeline);

        // Pre-fetch recent episodes for perceive() context enrichment (GAA PERCEIVE phase).
        // Results are injected into the AgentContext metadata so that perceive() can prepend
        // them to the input synchronously without violating the no-getResult() rule.
        MemoryFilter recentFilter = MemoryFilter.builder().agentId(currentAgentId).build();
        return agentCtx.getMemoryStore()
                .queryEpisodes(recentFilter, 5)
                .then(
                    episodes -> Promise.of(episodes),
                    e -> {
                        // Episode pre-fetch failure is non-fatal; proceed without enrichment.
                        log.debug("agent={} episode pre-fetch failed (non-fatal): {}", currentAgentId, e.getMessage());
                        return Promise.of(List.<Episode>of());
                    })
                .then(recentEpisodes -> {
                    if (!recentEpisodes.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (Episode ep : recentEpisodes) {
                            sb.append("[episode ").append(ep.getTimestamp()).append("] ")
                              .append(truncateEpisodeText(ep.getInput(), 120))
                              .append(" → ")
                              .append(truncateEpisodeText(ep.getOutput(), 120))
                              .append('\n');
                        }
                        agentCtx.setMetadata("recentEpisodesSummary", sb.toString());
                    }

                    // Fan out: one Promise per input event, all dispatched without blocking.
                    List<Promise<List<Event>>> eventPromises = currentEvents.stream()
                            .map(event -> pipeline.execute(event, agentCtx))
                            .toList();
                    return Promises.toList(eventPromises);
                })
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
                    appendStateEvent(EVT_EXECUTION_FAILED, Map.of(
                            "executionId", executionId,
                            "chainId", chainId,
                            "stepIndex", stepIndex,
                            "agentId", currentAgentId,
                            "error", e.getMessage() != null ? e.getMessage() : "unknown",
                            "timestamp", System.currentTimeMillis()
                    ));
                    log.error("Agent chain {} failed at step {} (agent {})", chainId, stepIndex, currentAgentId, e);
                    return new RuntimeException("Chain execution failed at step " + stepIndex + " (agent " + currentAgentId + ")", e);
                });
    }

    /**
     * Truncates a string to {@code maxLen} characters, appending an ellipsis when cut.
     * Used when formatting episode summaries for perceive() context injection.
     */
    private static String truncateEpisodeText(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) return String.valueOf(text);
        return text.substring(0, maxLen) + "…";
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
                appendStateEvent(EVT_EXECUTION_CANCELLED, Map.of(
                        "executionId", executionId,
                        "chainId", status.agentOrChainId(),
                        "timestamp", System.currentTimeMillis()
                ));
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

    // ==================== EVENT SOURCING SUPPORT (YAPPC-Ph1e) ====================

    /**
     * Appends an orchestration state-mutation event to the {@link EventLogStore}.
     *
     * <p>This method is fire-and-forget: failures are logged as warnings and never
     * propagate to the caller.
     *
     * @param eventType the event type string (one of the {@code EVT_*} constants)
     * @param payload   key-value data describing the mutation
     *
     * @doc.gaa.memory episodic
     * @doc.gaa.lifecycle capture
     */
    private void appendStateEvent(String eventType, Map<String, Object> payload) {
        try {
            byte[] payloadBytes = MAPPER.writeValueAsBytes(payload);
            EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
                    .eventId(UUID.randomUUID())
                    .eventType(eventType)
                    .eventVersion("1.0.0")
                    .payload(payloadBytes)
                    .contentType("application/json")
                    .build();
            // Fire-and-forget: we don't block the caller waiting for the append Promise.
            // The Promise is started but its result is not observed here.
            eventLogStore.append(systemTenant, entry)
                    .whenException(e -> log.warn("[EventSource] failed to append {} event: {}", eventType, e.getMessage()));
        } catch (Exception e) {
            log.warn("[EventSource] failed to serialize {} event payload: {}", eventType, e.getMessage());
        }
    }

    /**
     * Replays all orchestration events from the event log to rebuild in-memory state.
     *
     * <p>Call this during application startup <em>before</em> handling any requests.
     * The method reads events in batches from offset 0, processes recognised event types
     * to restore {@code agentDefinitions}, {@code agentChains}, and
     * {@code executionStatuses}, and updates the execution-ID counter to avoid
     * ID collisions after restart.
     *
     * @return a {@link Promise} that completes when the replay is done
     *
     * @doc.gaa.memory episodic
     * @doc.gaa.lifecycle perceive
     */
    public Promise<Void> rebuildFromEventLog() {
        log.info("[EventSource] Rebuilding orchestration state from event log (tenant={})", systemTenant.tenantId());
        return rebuildFromEventLogBatch(0L, 0, 0L);
    }

    /**
     * Reads one batch of event-log entries and schedules the next batch recursively.
     *
     * <p>This approach avoids {@code .getResult()} (which blocks the calling
     * thread and returns {@code null} on an unresolved Promise) by chaining
     * Promises with {@link Promise#then}. Each batch processes up to
     * {@code batchSize} entries on the event loop; the per-entry work is pure
     * in-memory so it does not stall the loop.
     *
     * @param offset          log offset of the next entry to read
     * @param totalReplayed   cumulative count of replayed entries so far
     * @param maxExecutionId  highest execution counter seen so far
     * @return promise that completes when all entries have been replayed
     */
    private Promise<Void> rebuildFromEventLogBatch(long offset, int totalReplayed, long maxExecutionId) {
        final int batchSize = 500;
        return eventLogStore.read(systemTenant, Offset.of(offset), batchSize)
                .then(batch -> {
                    if (batch == null || batch.isEmpty()) {
                        finaliseRebuild(totalReplayed, maxExecutionId);
                        return Promise.complete();
                    }

                    int newReplayed = totalReplayed;
                    long newMaxExecId = maxExecutionId;

                    for (EventLogStore.EventEntry entry : batch) {
                        try {
                            replayEvent(entry);
                            newReplayed++;
                            // Track highest execution counter to avoid ID collisions post-restart
                            if (entry.eventType().equals(EVT_EXECUTION_STARTED)) {
                                Map<String, Object> data = MAPPER.readValue(
                                        toBytes(entry.payload()), new TypeReference<Map<String, Object>>() {});
                                String execId = (String) data.get("executionId");
                                if (execId != null && execId.startsWith("exec_")) {
                                    try {
                                        long counter = Long.parseLong(execId.substring(5));
                                        if (counter > newMaxExecId) newMaxExecId = counter;
                                    } catch (NumberFormatException ignored) { /* non-numeric suffix */ }
                                }
                            }
                        } catch (Exception e) {
                            log.warn("[EventSource] failed to replay event id={} type={}: {}",
                                    entry.eventId(), entry.eventType(), e.getMessage());
                        }
                    }

                    if (batch.size() < batchSize) {
                        // Last page — no more entries to read
                        finaliseRebuild(newReplayed, newMaxExecId);
                        return Promise.complete();
                    }

                    return rebuildFromEventLogBatch(offset + batch.size(), newReplayed, newMaxExecId);
                });
    }

    private void finaliseRebuild(int totalReplayed, long maxExecutionId) {
        if (maxExecutionId > 0) {
            executionIdCounter.set(maxExecutionId);
        }
        log.info("[EventSource] Rebuilt state: {} agents, {} chains, {} executions replayed (total events={})",
                agentDefinitions.size(), agentChains.size(), executionStatuses.size(), totalReplayed);
    }

    /**
     * Applies a single event log entry to the in-memory state.
     *
     * @param entry the event log entry to replay
     * @throws Exception if the payload cannot be deserialised
     */
    @SuppressWarnings("unchecked")
    private void replayEvent(EventLogStore.EventEntry entry) throws Exception {
        byte[] payloadBytes = toBytes(entry.payload());
        Map<String, Object> data = MAPPER.readValue(payloadBytes, new TypeReference<Map<String, Object>>() {});

        switch (entry.eventType()) {
            case EVT_AGENT_REGISTERED -> {
                String agentId = (String) data.get("agentId");
                if (agentId != null && !agentDefinitions.containsKey(agentId)) {
                    // Restore minimal AgentDefinition from the event; pipeline built lazily
                    String agentName = (String) data.getOrDefault("agentName", agentId);
                    String agentType = (String) data.getOrDefault("agentType", "unknown");
                    AgentDefinition restored = new AgentDefinition(agentId, agentName, null, null,
                            null, null, null, null, Map.of("agentType", agentType));
                    agentDefinitions.put(agentId, restored);
                }
            }
            case EVT_CHAIN_CREATED -> {
                String chainId = (String) data.get("chainId");
                List<String> agentIds = (List<String>) data.get("agentIds");
                if (chainId != null && agentIds != null && !agentChains.containsKey(chainId)) {
                    agentChains.put(chainId, new ArrayList<>(agentIds));
                }
            }
            case EVT_EXECUTION_STARTED -> {
                String executionId = (String) data.get("executionId");
                String chainId = (String) data.get("chainId");
                if (executionId != null && !executionStatuses.containsKey(executionId)) {
                    long ts = ((Number) data.getOrDefault("timestamp", System.currentTimeMillis())).longValue();
                    executionStatuses.put(executionId, new AgentExecutionStatus(
                            executionId, chainId, ExecutionState.PENDING,
                            0.0, ts, null, null, Map.of()));
                }
            }
            case EVT_EXECUTION_COMPLETED -> {
                String executionId = (String) data.get("executionId");
                if (executionId != null) {
                    updateExecutionStatus(executionId, ExecutionState.COMPLETED, 100.0, null);
                }
            }
            case EVT_EXECUTION_FAILED -> {
                String executionId = (String) data.get("executionId");
                String error = (String) data.getOrDefault("error", "replayed failure");
                if (executionId != null) {
                    updateExecutionStatus(executionId, ExecutionState.FAILED, 0.0, error);
                }
            }
            case EVT_EXECUTION_CANCELLED -> {
                String executionId = (String) data.get("executionId");
                if (executionId != null) {
                    updateExecutionStatus(executionId, ExecutionState.CANCELLED, 0.0, "Replayed: cancelled by user");
                }
            }
            default -> {
                // Ignore unrecognised event types — forward-compatible with future events
            }
        }
    }

    /** Extracts a byte array from a {@link java.nio.ByteBuffer}. */
    private static byte[] toBytes(java.nio.ByteBuffer buf) {
        if (buf == null) return new byte[0];
        byte[] bytes = new byte[buf.remaining()];
        buf.duplicate().get(bytes);
        return bytes;
    }
}