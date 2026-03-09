package com.ghatana.virtualorg.agent;

import com.ghatana.agent.Agent;
import com.ghatana.agent.AgentCapabilities;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.virtualorg.v1.AgentMetricsProto;
import com.ghatana.virtualorg.v1.AgentPerformanceProto;
import com.ghatana.virtualorg.v1.AgentStateProto;
import com.ghatana.virtualorg.v1.TaskRequestProto;
import com.ghatana.virtualorg.v1.TaskResponseProto;
import com.ghatana.virtualorg.v1.ToolProto;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Lightweight VirtualOrg agent implementation used by adapter layer.
 *
 * <p>The implementation is intentionally simplified – it focuses on exposing
 * the contract required by {@code AgentStreamOperatorAdapter} while keeping
 * behaviour deterministic for unit and integration tests.</p>
 */
public class VirtualOrgAgent implements Agent {
    private static final Logger logger = LoggerFactory.getLogger(VirtualOrgAgent.class);

    /**
     * Role classifications supported by the lightweight agent.
     */
    public enum Role {
        CEO,
        CTO,
        ENGINEER,
        PRODUCT_MANAGER,
        QA_LEAD,
        DEVOPS_LEAD,
        UNKNOWN
    }

    /**
     * Decision authority level for the agent.
     */
    public enum DecisionAuthority {
        STRATEGIC,
        TACTICAL,
        OPERATIONAL,
        UNKNOWN
    }

    private final String agentId;
    private final String name;
    private final String description;
    private final Role role;
    private final DecisionAuthority authority;
    private final List<ToolProto> tools;

    private AgentStateProto state;
    private AgentPerformanceProto performance;

    public VirtualOrgAgent(
            String agentId,
            String name,
            String description,
            Role role,
            DecisionAuthority authority,
            List<ToolProto> tools
    ) {
        this.agentId = Objects.requireNonNull(agentId, "agentId");
        this.name = Objects.requireNonNull(name, "name");
        this.description = description != null ? description : "";
        this.role = role != null ? role : Role.UNKNOWN;
        this.authority = authority != null ? authority : DecisionAuthority.UNKNOWN;
        this.tools = tools != null ? List.copyOf(tools) : Collections.emptyList();
        this.state = AgentStateProto.newBuilder()
                .setState(AgentStateProto.State.AGENT_STATE_IDLE)
                .setStatusMessage("Initialized")
                .build();
        this.performance = AgentPerformanceProto.newBuilder()
                .setTasksProcessed(0)
                .setAverageProcessingTimeMs(0)
                .setErrorCount(0)
                .build();
    }

    @Override
    public @NotNull String getId() {
        return agentId;
    }

    public @NotNull String getName() {
        return name;
    }

    @Override
    public @NotNull AgentCapabilities getCapabilities() {
        return new AgentCapabilities(
            name,
            role.name(),
            description,
            java.util.Set.copyOf(List.of(authority.name())),
            java.util.Set.copyOf(tools.stream().map(ToolProto::getName).toList())
        );
    }

    @Override
    public @NotNull Promise<Void> initialize(@NotNull AgentContext context) {
        // Context can be stored if needed
        return Promise.complete();
    }

    public String getDescription() {
        return description;
    }

    public Role getRole() {
        return role;
    }

    public DecisionAuthority getAuthority() {
        return authority;
    }

    public List<ToolProto> getTools() {
        return tools;
    }

    public AgentStateProto getState() {
        return state;
    }

    public AgentPerformanceProto getPerformance() {
        return performance;
    }

    @Override
    public @NotNull Promise<Void> start() {
        logger.info("Starting agent: {}", name);
        state = AgentStateProto.newBuilder(state)
                .setState(AgentStateProto.State.AGENT_STATE_IDLE)
                .setStatusMessage("Running")
                .build();
        return Promise.complete();
    }

    @Override
    public @NotNull Promise<Void> shutdown() {
        logger.info("Stopping agent: {}", name);
        state = AgentStateProto.newBuilder(state)
                .setState(AgentStateProto.State.AGENT_STATE_TERMINATED)
                .setStatusMessage("Stopped at " + Instant.now())
                .build();
        return Promise.complete();
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull <T, R> Promise<R> process(@NotNull T task, @NotNull AgentContext context) {
        if (task instanceof TaskRequestProto request) {
            return (Promise<R>) processTask(request);
        }
        return Promise.ofException(new IllegalArgumentException("Expected TaskRequestProto, got " + task.getClass().getName()));
    }

    /**
     * Process task request in a deterministic but simplified manner.
     */
    public Promise<TaskResponseProto> processTask(TaskRequestProto request) {
        Objects.requireNonNull(request, "TaskRequestProto must not be null");
        logger.info("Agent {} processing task {}", agentId, request.getTaskId());

        long startNanos = System.nanoTime();
        state = AgentStateProto.newBuilder(state)
                .setState(AgentStateProto.State.AGENT_STATE_BUSY)
                .setStatusMessage("Processing task " + request.getTaskId())
                .build();

        TaskResponseProto.Builder responseBuilder = TaskResponseProto.newBuilder()
                .setTaskId(request.getTaskId())
                .setSuccess(true)
                .setResult("Completed task: " + request.getDescription())
                .setErrorMessage("");

        AgentMetricsProto metrics = AgentMetricsProto.newBuilder()
                .setCpuUsage(0.35)
                .setMemoryUsageBytes(32L * 1024 * 1024)
                .setQueueSize(0)
                .build();
        responseBuilder.setMetrics(metrics);

        long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        updatePerformance(durationMs, true);

        state = AgentStateProto.newBuilder(state)
                .setState(AgentStateProto.State.AGENT_STATE_IDLE)
                .setStatusMessage("Idle")
                .build();

        return Promise.of(responseBuilder.build());
    }

    private void updatePerformance(long processingTimeMs, boolean success) {
        long previousCount = performance.getTasksProcessed();
        long newCount = previousCount + 1;
        long errorCount = success ? performance.getErrorCount() : performance.getErrorCount() + 1;
        double previousAverage = performance.getAverageProcessingTimeMs();
        double newAverage = ((previousAverage * previousCount) + processingTimeMs) / Math.max(newCount, 1);

        performance = AgentPerformanceProto.newBuilder(performance)
                .setTasksProcessed(newCount)
                .setErrorCount(errorCount)
                .setAverageProcessingTimeMs(newAverage)
                .build();
    }
}
