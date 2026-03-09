package com.ghatana.virtualorg.agent;

import com.ghatana.virtualorg.adapter.EventEmitter;
import com.ghatana.virtualorg.adapter.VirtualOrgEventFactory;
import com.ghatana.virtualorg.llm.LLMClient;
import com.ghatana.virtualorg.memory.AgentMemory;
import com.ghatana.virtualorg.tool.ToolExecutor;
import com.ghatana.virtualorg.tool.ToolRegistry;
import com.ghatana.virtualorg.v1.*;
import com.google.protobuf.Timestamp;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Tracer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base implementation of VirtualOrgAgent providing common agent lifecycle and execution framework.
 *
 * <p><b>Purpose</b><br>
 * Provides concrete implementation of agent lifecycle management, state transitions,
 * task processing pipeline, and decision-making framework. Serves as foundation for
 * all role-specific agent implementations.
 *
 * <p><b>Architecture Role</b><br>
 * Part of the agent hierarchy as the primary base class:
 * <ul>
 *   <li>Implements VirtualOrgAgent interface</li>
 *   <li>Manages agent state (IDLE → PROCESSING → COMPLETED/FAILED)</li>
 *   <li>Orchestrates LLM reasoning, tool execution, and memory persistence</li>
 *   <li>Integrates with platform observability (Micrometer metrics, OpenTelemetry tracing)</li>
 *   <li>Emits agent lifecycle events to EventCloud</li>
 * </ul>
 *
 * <p><b>Lifecycle Management</b><br>
 * Handles complete agent lifecycle with state transitions:
 * <ul>
 *   <li>IDLE: Agent ready to accept tasks</li>
 *   <li>PROCESSING: Task being executed</li>
 *   <li>COMPLETED: Task finished successfully</li>
 *   <li>FAILED: Task failed with error</li>
 * </ul>
 *
 * <p><b>Task Processing Pipeline</b><br>
 * Implements standardized task execution flow:
 * <ol>
 *   <li>Validate task and authority</li>
 *   <li>Transition to PROCESSING state</li>
 *   <li>Recall relevant memories (semantic search)</li>
 *   <li>Execute LLM reasoning with context</li>
 *   <li>Execute tools as needed</li>
 *   <li>Make decision with authority checks</li>
 *   <li>Store experience in memory</li>
 *   <li>Emit completion event</li>
 *   <li>Transition to COMPLETED/FAILED</li>
 * </ol>
 *
 * <p><b>Subclass Implementation</b><br>
 * Subclasses MUST override {@link #doProcessTask(TaskRequestProto)} to provide
 * role-specific task processing logic. The base class handles all infrastructure
 * concerns (state, metrics, events, memory).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * public class CustomAgent extends BaseVirtualOrgAgent {
 *     public CustomAgent(String agentId, DecisionAuthorityProto authority, ...) {
 *         super(agentId, AgentRoleProto.CUSTOM, authority, ...);
 *     }
 *
 *     @Override
 *     protected Promise<TaskResultProto> doProcessTask(TaskRequestProto task) {
 *         // Role-specific processing logic
 *         return llmClient.chat(prompt)
 *             .map(response -> buildTaskResult(response));
 *     }
 * }
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Uses AtomicReference for state management to ensure thread-safe state transitions.
 * All async operations use ActiveJ Eventloop for single-threaded execution.
 *
 * @see VirtualOrgAgent
 * @see AbstractVirtualOrgAgent
 * @doc.type class
 * @doc.purpose Base agent providing lifecycle, LLM integration, and observability for VirtualOrg agents
 * @doc.layer product
 * @doc.pattern Template Method
 */
public abstract class BaseVirtualOrgAgent implements VirtualOrgAgent {

    private static final Logger log = LoggerFactory.getLogger(BaseVirtualOrgAgent.class);

    protected final String agentId;
    protected final AgentRoleProto role;
    protected final AtomicReference<AgentStateProto> state;
    protected final DecisionAuthorityProto authority;
    protected final Eventloop eventloop;
    protected final LLMClient llmClient;
    protected final AgentMemory memory;
    protected final ToolRegistry toolRegistry;
    protected final ToolExecutor toolExecutor;
    protected final MeterRegistry meterRegistry;
    protected final Tracer tracer;
    protected final Optional<EventEmitter> eventEmitter;

    // Performance metrics
    protected final AgentPerformanceProto.Builder performanceBuilder;

    // Configuration
    protected LLMConfigProto llmConfig;
    protected MemoryConfigProto memoryConfig;

    protected BaseVirtualOrgAgent(
            @NotNull String agentId,
            @NotNull AgentRoleProto role,
            @NotNull DecisionAuthorityProto authority,
            @NotNull Eventloop eventloop,
            @NotNull LLMClient llmClient,
            @NotNull AgentMemory memory,
            @NotNull ToolRegistry toolRegistry,
            @NotNull ToolExecutor toolExecutor,
            @NotNull MeterRegistry meterRegistry,
            @NotNull Tracer tracer,
            @NotNull LLMConfigProto llmConfig,
            @NotNull MemoryConfigProto memoryConfig) {
        this(agentId, role, authority, eventloop, llmClient, memory, toolRegistry, toolExecutor,
                meterRegistry, tracer, llmConfig, memoryConfig, Optional.empty());
    }

    protected BaseVirtualOrgAgent(
            @NotNull String agentId,
            @NotNull AgentRoleProto role,
            @NotNull DecisionAuthorityProto authority,
            @NotNull Eventloop eventloop,
            @NotNull LLMClient llmClient,
            @NotNull AgentMemory memory,
            @NotNull ToolRegistry toolRegistry,
            @NotNull ToolExecutor toolExecutor,
            @NotNull MeterRegistry meterRegistry,
            @NotNull Tracer tracer,
            @NotNull LLMConfigProto llmConfig,
            @NotNull MemoryConfigProto memoryConfig,
            @NotNull Optional<EventEmitter> eventEmitter) {

        this.agentId = agentId;
        this.role = role;
        this.state = new AtomicReference<>(AgentStateProto.AGENT_STATE_IDLE);
        this.authority = authority;
        this.eventloop = eventloop;
        this.llmClient = llmClient;
        this.memory = memory;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
        this.eventEmitter = eventEmitter;
        this.llmConfig = llmConfig;
        this.memoryConfig = memoryConfig;

        this.performanceBuilder = AgentPerformanceProto.newBuilder()
                .setTasksCompleted(0)
                .setTasksFailed(0)
                .setAvgCompletionTimeSeconds(0.0)
                .setSuccessRate(0.0f)
                .setAvgConfidence(0.0f)
                .setTotalTokensUsed(0)
                .setTotalToolCalls(0)
                .setLastUpdated(currentTimestamp());

        log.info("Initialized agent: id={}, role={}", agentId, role);
    }

    @Override
    @NotNull
    public String getAgentId() {
        return agentId;
    }

    @Override
    @NotNull
    public AgentRoleProto getRole() {
        return role;
    }

    @Override
    @NotNull
    public AgentStateProto getState() {
        return state.get();
    }

    @Override
    @NotNull
    public DecisionAuthorityProto getAuthority() {
        return authority;
    }

    @Override
    @NotNull
    public Promise<Void> start() {
        log.info("Starting agent: {}", agentId);

        return Promise.ofBlocking(eventloop, () -> {
            if (!state.compareAndSet(AgentStateProto.AGENT_STATE_IDLE, AgentStateProto.AGENT_STATE_IDLE)) {
                throw new IllegalStateException("Agent is not in IDLE state");
            }

            // Initialize resources
            doStart();

            log.info("Agent started: {}", agentId);

            // EMIT: Agent started event
            emitEventAsync("com.ghatana.virtualorg.agent.started", Map.of(
                    "agentId", agentId,
                    "role", role.toString()
            ));

            return null;
        });
    }

    @Override
    @NotNull
    public Promise<Void> stop() {
        log.info("Stopping agent: {}", agentId);

        return Promise.ofBlocking(eventloop, () -> {
            AgentStateProto currentState = state.get();
            if (currentState == AgentStateProto.AGENT_STATE_TERMINATED) {
                log.warn("Agent already terminated: {}", agentId);
                return null;
            }

            state.set(AgentStateProto.AGENT_STATE_TERMINATED);

            // Cleanup resources
            doStop();

            log.info("Agent stopped: {}", agentId);

            // EMIT: Agent stopped event
            emitEventAsync("com.ghatana.virtualorg.agent.stopped", Map.of(
                    "agentId", agentId,
                    "role", role.toString()
            ));

            return null;
        });
    }

    @Override
    @NotNull
    public Promise<TaskResponseProto> processTask(@NotNull TaskRequestProto request) {
        var span = tracer.spanBuilder("agent.processTask")
                .setAttribute("agent.id", agentId)
                .setAttribute("agent.role", role.name())
                .setAttribute("task.id", request.getTask().getTaskId())
                .startSpan();

        Timer.Sample timerSample = Timer.start(meterRegistry);

        return Promise.ofBlocking(eventloop, () -> {
            try {
                // Update state
                if (!state.compareAndSet(AgentStateProto.AGENT_STATE_IDLE, AgentStateProto.AGENT_STATE_BUSY)) {
                    throw new IllegalStateException("Agent is not idle: " + state.get());
                }

                String taskId = request.getTask().getTaskId();
                log.info("Processing task: agentId={}, taskId={}", agentId, taskId);

                // EMIT: Task started event
                emitEventAsync("com.ghatana.virtualorg.task.started", Map.of(
                        "taskId", taskId,
                        "priority", request.getTask().getPriority().toString(),
                        "agentId", agentId
                ));

                // Process the task
                var response = doProcessTask(request);

                // Update metrics
                updateMetrics(response, timerSample);

                // Transition back to idle
                state.set(AgentStateProto.AGENT_STATE_IDLE);

                log.info("Task completed: agentId={}, taskId={}, success={}",
                        agentId, taskId, response.getSuccess());

                // EMIT: Task completed event
                emitEventAsync("com.ghatana.virtualorg.task.completed", Map.of(
                        "taskId", taskId,
                        "agentId", agentId,
                        "success", String.valueOf(response.getSuccess()),
                        "resultType", response.getResult().isEmpty() ? "reasoning" : "result"
                ));

                return response;

            } catch (Exception e) {
                log.error("Task failed: agentId={}, taskId={}", agentId, request.getTask().getTaskId(), e);

                state.set(AgentStateProto.AGENT_STATE_ERROR);

                // Update failure metrics
                performanceBuilder.setTasksFailed(performanceBuilder.getTasksFailed() + 1);

                // EMIT: Task failed event
                emitEventAsync("com.ghatana.virtualorg.task.failed", Map.of(
                        "taskId", request.getTask().getTaskId(),
                        "agentId", agentId,
                        "error", e.getMessage() != null ? e.getMessage() : "Unknown error"
                ));

                span.recordException(e);
                throw e;

            } finally {
                span.end();
            }
        });
    }

    @Override
    @NotNull
    public Promise<DecisionProto> makeDecision(
            @NotNull DecisionTypeProto decisionType,
            @NotNull Map<String, String> context,
            @NotNull List<OptionProto> options) {

        var span = tracer.spanBuilder("agent.makeDecision")
                .setAttribute("agent.id", agentId)
                .setAttribute("decision.type", decisionType.name())
                .startSpan();

        return Promise.ofBlocking(eventloop, () -> {
            try {
                log.info("Making decision: agentId={}, type={}", agentId, decisionType);

                // Check authority
                if (!canDecide(decisionType)) {
                    log.warn("Decision requires escalation: agentId={}, type={}", agentId, decisionType);
                    throw new IllegalArgumentException("Decision requires escalation: " + decisionType);
                }

                // Use LLM to reason about options
                var decision = doMakeDecision(decisionType, context, options);

                log.info("Decision made: agentId={}, type={}, confidence={}",
                        agentId, decisionType, decision.getConfidence());

                // EMIT: Decision made event
                emitEventAsync("com.ghatana.virtualorg.decision.made", Map.of(
                        "decisionType", decisionType.toString(),
                        "agentId", agentId,
                        "confidence", String.valueOf(decision.getConfidence()),
                        "reasoning", decision.getReasoning()
                ));

                return decision;

            } finally {
                span.end();
            }
        });
    }

    @Override
    public boolean canDecide(@NotNull DecisionTypeProto decisionType) {
        return authority.getCanDecideList().contains(decisionType) &&
                !authority.getMustEscalateList().contains(decisionType);
    }

    @Override
    @NotNull
    public Promise<EscalationRequestProto> escalate(
            @NotNull String taskId,
            @NotNull DecisionTypeProto decisionType,
            @NotNull String reason) {

        return Promise.ofBlocking(eventloop, () -> {
            log.info("Escalating: agentId={}, taskId={}, type={}", agentId, taskId, decisionType);

            var escalation = EscalationRequestProto.newBuilder()
                    .setEscalationId(generateId())
                    .setTaskId(taskId)
                    .setFromAgentId(agentId)
                    .setToAgentId(authority.getEscalationTarget())
                    .setReason(reason)
                    .setStatus(EscalationStatusProto.ESCALATION_STATUS_PENDING)
                    .setCreatedAt(currentTimestamp())
                    .build();

            // Send escalation request (implementation depends on orchestrator)
            doEscalate(escalation);

            return escalation;
        });
    }

    @Override
    @NotNull
    public AgentPerformanceProto getPerformance() {
        return performanceBuilder.build();
    }

    @Override
    @NotNull
    public Promise<Void> updateConfig(@NotNull VirtualOrgAgentProto config) {
        return Promise.ofBlocking(eventloop, () -> {
            log.info("Updating config: agentId={}", agentId);

            this.llmConfig = config.getLlmConfig();
            this.memoryConfig = config.getMemoryConfig();

            doUpdateConfig(config);

            return null;
        });
    }

    @Override
    @NotNull
    public Promise<Void> sendMessage(@NotNull String targetAgentId, @NotNull String message) {
        return Promise.ofBlocking(eventloop, () -> {
            log.debug("Sending message: from={}, to={}", agentId, targetAgentId);
            doSendMessage(targetAgentId, message);
            return null;
        });
    }

    @Override
    @NotNull
    public List<ToolProto> getTools() {
        return toolRegistry.getToolsForAgent(agentId);
    }

    @Override
    @NotNull
    public Promise<ToolCallProto> executeTool(
            @NotNull String toolName,
            @NotNull Map<String, String> arguments) {

        return toolExecutor.execute(toolName, arguments);
    }

    // =============================
    // Protected methods for subclasses
    // =============================

    /**
     * Called when the agent starts. Subclasses can override to perform
     * initialization.
     */
    protected void doStart() throws Exception {
        // Default implementation does nothing
    }

    /**
     * Called when the agent stops. Subclasses can override to perform cleanup.
     */
    protected void doStop() throws Exception {
        // Default implementation does nothing
    }

    /**
     * Processes a task. Subclasses must implement role-specific logic.
     *
     * @param request the task request
     * @return the task response
     */
    @NotNull
    protected abstract TaskResponseProto doProcessTask(@NotNull TaskRequestProto request) throws Exception;

    /**
     * Makes a decision. Subclasses can override for custom decision logic.
     *
     * @param decisionType the decision type
     * @param context      the context
     * @param options      the options
     * @return the decision
     */
    @NotNull
    protected abstract DecisionProto doMakeDecision(
            @NotNull DecisionTypeProto decisionType,
            @NotNull Map<String, String> context,
            @NotNull List<OptionProto> options) throws Exception;

    /**
     * Sends an escalation request. Subclasses can override to customize routing.
     *
     * @param escalation the escalation request
     */
    protected void doEscalate(@NotNull EscalationRequestProto escalation) throws Exception {
        // Default: log and wait for orchestrator
        log.info("Escalation created: {}", escalation.getEscalationId());
    }

    /**
     * Updates configuration. Subclasses can override for custom handling.
     *
     * @param config the new configuration
     */
    protected void doUpdateConfig(@NotNull VirtualOrgAgentProto config) throws Exception {
        // Default implementation does nothing
    }

    /**
     * Sends a message to another agent. Subclasses can override for custom routing.
     *
     * @param targetAgentId the target agent
     * @param message       the message
     */
    protected void doSendMessage(@NotNull String targetAgentId, @NotNull String message) throws Exception {
        // Default: log message
        log.info("Message sent: to={}, message={}", targetAgentId, message);
    }

    // =============================
    // Helper methods
    // =============================

    protected void updateMetrics(TaskResponseProto response, Timer.Sample timerSample) {
        long completed = performanceBuilder.getTasksCompleted();
        long failed = performanceBuilder.getTasksFailed();

        if (response.getSuccess()) {
            performanceBuilder.setTasksCompleted(completed + 1);
        } else {
            performanceBuilder.setTasksFailed(failed + 1);
        }

        long total = completed + failed + 1;
        performanceBuilder.setSuccessRate((float) (completed + (response.getSuccess() ? 1 : 0)) / total);

        performanceBuilder.setTotalTokensUsed(
                performanceBuilder.getTotalTokensUsed() + response.getMetrics().getTokensUsed());

        performanceBuilder.setTotalToolCalls(
                performanceBuilder.getTotalToolCalls() + response.getToolCallsCount());

        performanceBuilder.setLastUpdated(currentTimestamp());

        // Record metrics using project pattern
        meterRegistry.counter("agent.tasks.completed",
                "agent_id", agentId,
                "role", role.name(),
                "success", String.valueOf(response.getSuccess())
        ).increment();

        // Stop timer and record duration
        Timer taskDurationTimer = Timer.builder("agent.task.duration")
                .tag("agent_id", agentId)
                .tag("role", role.name())
                .register(meterRegistry);
        
        timerSample.stop(taskDurationTimer);
    }

    protected Timestamp currentTimestamp() {
        var now = Instant.now();
        return Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
    }

    protected String generateId() {
        return java.util.UUID.randomUUID().toString();
    }

    // =============================
    // Event Emission Methods
    // =============================

    /**
     * Emit an event asynchronously (fire-and-forget).
     * Events are emitted via EventEmitter if available.
     *
     * @param eventType the event type (e.g., "com.ghatana.virtualorg.task.completed")
     * @param data      the event data as key-value pairs
     */
    protected void emitEventAsync(@NotNull String eventType, @NotNull Map<String, String> data) {
        if (eventEmitter.isEmpty()) {
            return;
        }

        try {
            var event = VirtualOrgEventFactory.createEvent(eventType, agentId, data);
            eventEmitter.get().emit(event);
        } catch (Exception e) {
            log.warn("Failed to emit event: eventType={}", eventType, e);
        }
    }

    /**
     * Emit an event synchronously with guaranteed delivery.
     * Events are emitted via EventEmitter if available.
     *
     * @param eventType the event type (e.g., "com.ghatana.virtualorg.task.completed")
     * @param data      the event data as key-value pairs
     * @return a Promise that resolves when event is delivered
     */
    protected Promise<Void> emitEvent(@NotNull String eventType, @NotNull Map<String, String> data) {
        if (eventEmitter.isEmpty()) {
            return Promise.complete();
        }

        try {
            var event = VirtualOrgEventFactory.createEvent(eventType, agentId, data);
            return eventEmitter.get().emitAsync(event);
        } catch (Exception e) {
            log.warn("Failed to emit event: eventType={}", eventType, e);
            return Promise.complete();
        }
    }
}
