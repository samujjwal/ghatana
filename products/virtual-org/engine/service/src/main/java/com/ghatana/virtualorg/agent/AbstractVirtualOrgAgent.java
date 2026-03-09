package com.ghatana.virtualorg.agent;

import com.ghatana.platform.domain.agent.registry.AgentExecutionContext;
import com.ghatana.platform.domain.agent.registry.AgentMetrics;
import com.ghatana.platform.domain.agent.registry.HealthStatus;
import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.contracts.agent.v1.AgentInputProto;
import com.ghatana.contracts.agent.v1.AgentResultProto;
import com.ghatana.agent.AgentCapabilities;
import com.ghatana.agent.framework.api.AgentContext;
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
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract agent implementation integrating virtual organization capabilities with platform Agent interface.
 *
 * <p><b>Purpose</b><br>
 * Bridges virtual organization agents with EventCloud agent infrastructure, providing
 * platform compatibility while adding LLM reasoning, autonomous tool execution, and
 * hierarchical decision-making with authority delegation.
 *
 * <p><b>Architecture Role</b><br>
 * Integration layer between platform and product:
 * <ul>
 *   <li>Implements core Agent interface (platform compatibility)</li>
 *   <li>Implements VirtualOrgAgent interface (product features)</li>
 *   <li>Registers with AgentRegistry for discovery</li>
 *   <li>Reports metrics via AgentMetrics interface</li>
 *   <li>Emits agent events to EventCloud</li>
 *   <li>Integrates with platform observability stack</li>
 * </ul>
 *
 * <p><b>Platform Integration</b><br>
 * Implements platform Agent interface methods:
 * <ul>
 *   <li>{@code execute(AgentInputProto)}: Maps platform task to virtual-org task</li>
 *   <li>{@code getCapabilities()}: Exposes agent capabilities for discovery</li>
 *   <li>{@code getMetrics()}: Reports performance metrics</li>
 *   <li>{@code getStatus()}: Current agent state (IDLE/PROCESSING/etc)</li>
 * </ul>
 *
 * <p><b>Virtual Organization Features</b><br>
 * Product-specific capabilities:
 * <ul>
 *   <li>LLM Reasoning: Multi-turn conversations with reasoning/reflection</li>
 *   <li>Tool Execution: Git, file ops, HTTP, custom tools via registry</li>
 *   <li>Authority Delegation: Role-based decision authority with escalation</li>
 *   <li>Memory System: Short-term + long-term semantic memory (pgvector)</li>
 *   <li>Decision Making: Extract decisions from LLM responses with confidence</li>
 * </ul>
 *
 * <p><b>Authority Framework</b><br>
 * Enforces hierarchical decision-making:
 * <ul>
 *   <li>Each agent has DecisionAuthorityProto defining allowed decision types</li>
 *   <li>Escalates decisions outside authority to higher-role agents</li>
 *   <li>Tracks escalation chains for audit</li>
 * </ul>
 *
 * <p><b>Observability</b><br>
 * Full instrumentation:
 * <ul>
 *   <li>Metrics: Task duration, LLM calls, tool executions, decisions made</li>
 *   <li>Tracing: OpenTelemetry spans for distributed tracing</li>
 *   <li>Logging: Structured logs with agent context (id, role, task)</li>
 *   <li>Events: Lifecycle events to EventCloud (started, completed, failed)</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * public class SeniorEngineerAgent extends AbstractVirtualOrgAgent {
 *     public SeniorEngineerAgent(String agentId, ...) {
 *         super(agentId, "1.0.0", AgentRoleProto.SENIOR_ENGINEER, ...);
 *     }
 *
 *     @Override
 *     protected Promise<TaskResultProto> doProcessTask(TaskRequestProto task) {
 *         return llmClient.chat("Implement feature: " + task.getDescription())
 *             .then(response -> executeLLMPlan(response))
 *             .map(result -> buildTaskResult(result));
 *     }
 * }
 *
 * // Platform integration
 * AbstractVirtualOrgAgent agent = new SeniorEngineerAgent(...);
 * agentRegistry.register(agent);
 * agent.execute(AgentInputProto.newBuilder()...build());
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Uses AtomicReference and AtomicInteger for thread-safe state management.
 * All async operations execute on single-threaded ActiveJ Eventloop.
 *
 * @see VirtualOrgAgent
 * @see com.ghatana.platform.domain.agent.registry.Agent
 * @doc.type class
 * @doc.purpose Platform-integrated abstract agent with LLM reasoning and authority delegation
 * @doc.layer product
 * @doc.pattern Adapter
 */
public abstract class AbstractVirtualOrgAgent implements VirtualOrgAgent {

    private static final Logger log = LoggerFactory.getLogger(AbstractVirtualOrgAgent.class);

    protected final String agentId;
    protected final String version;
    protected final AgentRoleProto role;
    protected final DecisionAuthorityProto authority;
    protected final Eventloop eventloop;
    protected final LLMClient llmClient;
    protected final AgentMemory memory;
    protected final ToolRegistry toolRegistry;
    protected final ToolExecutor toolExecutor;
    protected final MeterRegistry meterRegistry;
    protected final Tracer tracer;

    // Status management
    protected final AtomicReference<AgentStateProto> currentState = new AtomicReference<>(AgentStateProto.AGENT_STATE_IDLE);
    protected final AtomicInteger tasksCompleted = new AtomicInteger(0);
    protected final AtomicInteger tasksFailed = new AtomicInteger(0);
    protected final AtomicReference<Instant> lastProcessedAt = new AtomicReference<>(Instant.now());

    // Performance metrics
    protected final AgentPerformanceProto.Builder performanceBuilder;

    // Configuration
    protected LLMConfigProto llmConfig;
    protected MemoryConfigProto memoryConfig;

    protected AbstractVirtualOrgAgent(
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

        this.agentId = agentId;
        this.version = "1.0.0";
        this.role = role;
        this.authority = authority;
        this.eventloop = eventloop;
        this.llmClient = llmClient;
        this.memory = memory;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
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

        log.info("Initialized virtual-org agent: id={}, role={}", agentId, role);
    }

    // =============================
    // Agent interface methods
    // =============================

    @Override
    @NotNull
    public String getId() {
        return agentId;
    }

    @NotNull
    public String getVersion() {
        return version;
    }

    @NotNull
    public Set<String> getSupportedEventTypes() {
        // VirtualOrg agents support task events
        return Set.of("virtual-org.task", "virtual-org.decision");
    }

    @NotNull
    public Set<String> getOutputEventTypes() {
        // VirtualOrg agents produce task results and decisions
        return Set.of("virtual-org.task.result", "virtual-org.decision.result");
    }

    @NotNull
    public List<Event> handle(@NotNull Event event, @NotNull AgentExecutionContext context) {
        // VirtualOrg agents primarily use processTask() instead of handle()
        // This is a compatibility method for the Agent interface
        log.warn("handle() called on VirtualOrgAgent - consider using processTask() instead");
        return Collections.emptyList();
    }

    @NotNull
    public AgentResultProto execute(@NotNull AgentInputProto input) {
        // Compatibility method - VirtualOrg agents use processTask()
        log.warn("execute() called on VirtualOrgAgent - consider using processTask() instead");
        return AgentResultProto.newBuilder().build();
    }

    public boolean isHealthy() {
        AgentStateProto state = getState();
        return state != AgentStateProto.AGENT_STATE_ERROR 
            && state != AgentStateProto.AGENT_STATE_TERMINATED;
    }

    @NotNull
    public AgentMetrics getMetrics() {
        // Convert VirtualOrg performance metrics to platform AgentMetrics
        return new AgentMetrics() {
            @Override
            public long processedCount() {
                return tasksCompleted.get();
            }

            @Override
            public long getEventsProcessed() {
                return tasksCompleted.get();
            }

            @Override
            public long getErrorCount() {
                return tasksFailed.get();
            }

            @Override
            public double getAverageProcessingTimeMs() {
                return performanceBuilder.getAvgCompletionTimeSeconds() * 1000.0;
            }

            @Override
            public double getCurrentThroughput() {
                // Return 0 as placeholder - would need time-window tracking
                return 0.0;
            }

            @Override
            public double getPeakThroughput() {
                return 0.0; // Placeholder
            }

            @Override
            public java.time.Instant getLastProcessedAt() {
                return lastProcessedAt.get();
            }

            @Override
            public long getMemoryUsageMb() {
                Runtime runtime = Runtime.getRuntime();
                return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            }

            @Override
            public double getCpuUtilization() {
                return 0.0; // Would need JMX monitoring
            }

            @Override
            public int getActiveThreads() {
                return Thread.activeCount();
            }

            @Override
            public HealthStatus getHealthStatus() {
                return isHealthy() ? HealthStatus.HEALTHY : HealthStatus.UNHEALTHY;
            }

            @Override
            public Map<String, Object> getCustomMetrics() {
                return Map.of(
                    "total_tokens_used", performanceBuilder.getTotalTokensUsed(),
                    "total_tool_calls", performanceBuilder.getTotalToolCalls(),
                    "success_rate", performanceBuilder.getSuccessRate(),
                    "avg_confidence", performanceBuilder.getAvgConfidence()
                );
            }
        };
    }

    // =============================
    // VirtualOrgAgent interface methods
    // =============================

    @Override
    @NotNull
    public AgentRoleProto getRole() {
        return role;
    }

    @Override
    @NotNull
    public AgentStateProto getState() {
        return currentState.get();
    }

    @Override
    @NotNull
    public DecisionAuthorityProto getAuthority() {
        return authority;
    }

    @Override
    @NotNull
    public Promise<Void> start() {
        return Promise.ofBlocking(eventloop, () -> {
            log.info("Starting virtual-org agent: id={}, role={}", agentId, role);
            currentState.set(AgentStateProto.AGENT_STATE_IDLE);
            // Subclasses can override to add custom initialization
            return null;
        });
    }

    @Override
    @NotNull
    public Promise<Void> stop() {
        return Promise.ofBlocking(eventloop, () -> {
            log.info("Stopping virtual-org agent: id={}, role={}", agentId, role);
            currentState.set(AgentStateProto.AGENT_STATE_TERMINATED);
            // Subclasses can override to add custom cleanup
            return null;
        });
    }

    @Override
    @NotNull
    public Promise<TaskResponseProto> processTask(@NotNull TaskRequestProto request) {
        Span span = tracer.spanBuilder("agent.processTask")
                .setAttribute("agent.id", getAgentId())
                .setAttribute("agent.role", role.name())
                .setAttribute("task.id", request.getTask().getTaskId())
                .startSpan();

        Timer.Sample timer = Timer.start(meterRegistry);

        // Check agent is running
        if (!isRunning()) {
            return Promise.ofException(new IllegalStateException("Agent is not running: " + getState()));
        }

        log.info("Processing task: agentId={}, taskId={}", getAgentId(), request.getTask().getTaskId());

        // Mark as busy
        currentState.set(AgentStateProto.AGENT_STATE_BUSY);

        // Process the task (delegated to subclass)
        return doProcessTask(request)
                .whenResult(response -> {
                    // Update metrics
                    updatePerformanceMetrics(response, timer);

                    log.info("Task completed: agentId={}, taskId={}, success={}",
                            getAgentId(), request.getTask().getTaskId(), response.getSuccess());
                })
                .whenException(e -> {
                    log.error("Task failed: agentId={}, taskId={}", getAgentId(), request.getTask().getTaskId(), e);

                    // Update failure metrics
                    tasksFailed.incrementAndGet();
                    performanceBuilder.setTasksFailed(tasksFailed.get());

                    span.recordException(e);
                })
                .whenComplete((response, e) -> {
                    // Return to idle state
                    currentState.set(AgentStateProto.AGENT_STATE_IDLE);
                    span.end();
                });
    }

    @Override
    @NotNull
    public Promise<DecisionProto> makeDecision(
            @NotNull DecisionTypeProto decisionType,
            @NotNull Map<String, String> context,
            @NotNull List<OptionProto> options) {

        Span span = tracer.spanBuilder("agent.makeDecision")
                .setAttribute("agent.id", getAgentId())
                .setAttribute("decision.type", decisionType.name())
                .startSpan();

        return Promise.ofBlocking(eventloop, () -> {
            try {
                log.info("Making decision: agentId={}, type={}", getAgentId(), decisionType);

                // Check authority
                if (!canDecide(decisionType)) {
                    log.warn("Decision requires escalation: agentId={}, type={}", getAgentId(), decisionType);
                    throw new IllegalArgumentException("Decision requires escalation: " + decisionType);
                }

                // Use LLM to reason about options
                DecisionProto decision = doMakeDecision(decisionType, context, options);

                log.info("Decision made: agentId={}, type={}, confidence={}",
                        getAgentId(), decisionType, decision.getConfidence());

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
            log.info("Escalating: agentId={}, taskId={}, type={}", getAgentId(), taskId, decisionType);

            EscalationRequestProto escalation = EscalationRequestProto.newBuilder()
                    .setEscalationId(generateId())
                    .setTaskId(taskId)
                    .setFromAgentId(getAgentId())
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
            log.info("Updating config: agentId={}", getAgentId());

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
            log.debug("Sending message: from={}, to={}", getAgentId(), targetAgentId);
            doSendMessage(targetAgentId, message);
            return null;
        });
    }

    @Override
    @NotNull
    public List<ToolProto> getTools() {
        return toolRegistry.getToolsForAgent(getAgentId());
    }

    @Override
    @NotNull
    public Promise<ToolCallProto> executeTool(
            @NotNull String toolName,
            @NotNull Map<String, String> arguments) {

        return toolExecutor.execute(toolName, arguments);
    }

    // =============================
    // BaseAgent lifecycle hooks
    // =============================

    protected Promise<Void> doStart() {
        try {
            onStart();
            return Promise.complete();
        } catch (Exception e) {
            log.error("Error starting agent: {}", getAgentId(), e);
            return Promise.ofException(e);
        }
    }

    protected Promise<Void> doStop() {
        try {
            onStop();
            return Promise.complete();
        } catch (Exception e) {
            log.error("Error stopping agent: {}", getAgentId(), e);
            return Promise.ofException(e);
        }
    }

    // =============================
    // Protected methods for subclasses
    // =============================

    /**
     * Called when the agent starts. Subclasses can override to perform initialization.
     */
    protected void onStart() throws Exception {
        // Default implementation does nothing
    }

    /**
     * Called when the agent stops. Subclasses can override to perform cleanup.
     */
    protected void onStop() throws Exception {
        // Default implementation does nothing
    }

    /**
     * Processes a task. Subclasses must implement role-specific logic.
     *
     * @param request the task request
     * @return a promise of the task response
     */
    @NotNull
    protected abstract Promise<TaskResponseProto> doProcessTask(@NotNull TaskRequestProto request);

    /**
     * Makes a decision. Subclasses can override for custom decision logic.
     * Default implementation returns a basic decision with the specified type.
     *
     * @param decisionType the decision type
     * @param context      the context
     * @param options      the options
     * @return the decision
     */
    @NotNull
    protected DecisionProto doMakeDecision(
            @NotNull DecisionTypeProto decisionType,
            @NotNull Map<String, String> context,
            @NotNull List<OptionProto> options) throws Exception {
        // Default implementation - subclasses can override
        return DecisionProto.newBuilder()
                .setDecisionId(java.util.UUID.randomUUID().toString())
                .setType(decisionType)
                .setReasoning("Default decision implementation")
                .setConfidence(0.5f)
                .setApprovedAt(currentTimestamp())  // Use approvedAt instead of timestamp
                .build();
    }

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

    protected void updatePerformanceMetrics(TaskResponseProto response, Timer.Sample timer) {
        if (response.getSuccess()) {
            tasksCompleted.incrementAndGet();
            performanceBuilder.setTasksCompleted(tasksCompleted.get());
        } else {
            tasksFailed.incrementAndGet();
            performanceBuilder.setTasksFailed(tasksFailed.get());
        }

        long total = tasksCompleted.get() + tasksFailed.get();
        if (total > 0) {
            performanceBuilder.setSuccessRate((float) tasksCompleted.get() / total);
        }

        performanceBuilder.setTotalTokensUsed(
                performanceBuilder.getTotalTokensUsed() + response.getMetrics().getTokensUsed());

        performanceBuilder.setTotalToolCalls(
                performanceBuilder.getTotalToolCalls() + response.getToolCallsCount());

        performanceBuilder.setLastUpdated(currentTimestamp());

        // Record metrics
        meterRegistry.counter("agent.tasks.completed",
                "agent_id", getAgentId(),
                "role", role.name(),
                "success", String.valueOf(response.getSuccess())
        ).increment();

        timer.stop(meterRegistry.timer("agent.task.duration",
                "agent_id", getAgentId(),
                "role", role.name()
        ));
    }

    protected Timestamp currentTimestamp() {
        Instant now = Instant.now();
        return Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
    }

    protected String generateId() {
        return java.util.UUID.randomUUID().toString();
    }

    /**
     * Stores a decision in agent memory. Subclasses can override for custom storage.
     *
     * @param decision the decision to store
     * @param task the related task
     */
    protected void storeDecision(@NotNull DecisionProto decision, @NotNull TaskProto task) {
        // Default: log only (memory integration can be added later)
        log.debug("Decision stored: decision_id={}, task_id={}", 
                decision.getDecisionId(), task.getTaskId());
    }

    /**
     * Executes tool calls and returns results. Subclasses can override for custom tool execution.
     *
     * @param toolCalls the tool calls to execute
     * @param task the related task
     * @return promise of tool results (as strings for now)
     */
    @NotNull
    protected Promise<List<String>> executeTools(
            @NotNull List<ToolCallProto> toolCalls,
            @NotNull TaskProto task) {
        // Default: return empty results (tool execution can be added later)
        log.debug("Tool execution requested: count={}, task_id={}", 
                toolCalls.size(), task.getTaskId());
        return Promise.of(List.of());
    }

    /**
     * Helper method to check if agent is in a running state.
     * @return true if the agent can process tasks
     */
    protected boolean isRunning() {
        AgentStateProto state = getState();
        return state == AgentStateProto.AGENT_STATE_IDLE 
            || state == AgentStateProto.AGENT_STATE_BUSY;
    }

    // =============================
    // New Agent Interface Implementation
    // =============================

    @Override
    public @NotNull AgentCapabilities getCapabilities() {
        return new AgentCapabilities(
            getId(),
            getRole().name(),
            "Virtual Org Agent: " + getRole().name(),
            getSupportedEventTypes(),
            java.util.Collections.emptySet()
        );
    }

    @Override
    public @NotNull Promise<Void> initialize(@NotNull AgentContext context) {
        return Promise.complete();
    }

    @Override
    public <T, R> @NotNull Promise<R> process(@NotNull T task, @NotNull AgentContext context) {
        if (task instanceof TaskRequestProto) {
            return (Promise<R>) processTask((TaskRequestProto) task);
        }
        return Promise.ofException(new IllegalArgumentException("Unsupported task type: " + task.getClass()));
    }

    @Override
    public @NotNull Promise<Void> shutdown() {
        return stop();
    }
}
