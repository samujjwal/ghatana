package com.ghatana.core.operator.adapter;

import com.ghatana.platform.domain.domain.event.*;
import com.ghatana.platform.workflow.operator.*;
import com.ghatana.platform.types.identity.OperatorId;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.ghatana.virtualorg.agent.VirtualOrgAgent;
import com.ghatana.virtualorg.v1.AgentMetricsProto;
import com.ghatana.virtualorg.v1.AgentPerformanceProto;
import com.ghatana.virtualorg.v1.AgentStateProto;
import com.ghatana.virtualorg.v1.TaskRequestProto;
import com.ghatana.virtualorg.v1.TaskResponseProto;
import com.ghatana.virtualorg.v1.ToolProto;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Adapter bridging VirtualOrgAgent to UnifiedOperator interface.
 *
 * <p><b>Purpose</b><br>
 * Enables virtual-org agents to participate in stream processing pipelines
 * as first-class operators, integrating LLM-powered reasoning with
 * event-driven architecture.
 *
 * <p><b>Architecture Role</b><br>
 * Bridge/Adapter pattern implementing Track 1 (Agent Stream Operator Unification).
 * Wraps VirtualOrgAgent, delegates to agent for task processing, maps between
 * Event domain and TaskProto domain.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * VirtualOrgAgent agent = new VirtualOrgAgent(...);
 * AgentStreamOperatorAdapter adapter = AgentStreamOperatorAdapterFactory.create(agent, meterRegistry);
 *
 * Event taskEvent = GEvent.builder()
 *     .id(EventId.create(UUID.randomUUID().toString(), "agent.task.code_review", "1.0", "tenant-1"))
 *     .time(EventTime.now())
 *     .payload(Map.of("pr_id", "123", "description", "Review PR"))
 *     .build();
 *
 * adapter.process(taskEvent).whenResult(result -> {
 *     result.getOutputEvents().forEach(event ->
 *         eventCloud.append(event));
 * });
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. Agent state transitions are synchronized internally.
 * Promise-based execution ensures non-blocking operation.
 *
 * <p><b>Performance Characteristics</b><br>
 * Target: &lt;10ms p99 latency for task mapping (excludes agent LLM processing).
 * Agent processing time depends on LLM backend (typically 500ms-2s).
 *
 * @see VirtualOrgAgent
 * @see UnifiedOperator
 * @see AbstractOperator
 */
public class AgentStreamOperatorAdapter extends AbstractOperator {

    private static final Logger logger = LoggerFactory.getLogger(AgentStreamOperatorAdapter.class);

    private final VirtualOrgAgent agent;

    /**
     * Creates adapter for virtual-org agent.
     *
     * @param id            Operator identifier (format: virtualorg:agent:{role}:1.0.0)
     * @param agent         Virtual-org agent instance
     * @param meterRegistry Metrics registry for observability
     */
    public AgentStreamOperatorAdapter(
            OperatorId id,
            VirtualOrgAgent agent,
            MeterRegistry meterRegistry
    ) {
        super(id, OperatorType.STREAM,
              buildOperatorName(Objects.requireNonNull(agent, "VirtualOrgAgent must not be null")),
              buildOperatorDescription(agent),
              buildCapabilities(agent),
              MetricsCollectorFactory.create(meterRegistry));
        this.agent = agent;
        logger.info("Created AgentStreamOperatorAdapter: id={}, role={}", id, agent.getRole());
    }

    // ========================================================================
    // EXECUTION (UnifiedOperator contract)
    // ========================================================================

    /**
     * Process event through agent.
     *
     * <p>Maps Event → TaskRequestProto, delegates to agent.processTask(),
     * then maps TaskResponseProto → OperatorResult.
     *
     * @param event Input event to process
     * @return Promise of OperatorResult with output events or error
     */
    @Override
    public Promise<OperatorResult> process(Event event) {
        logger.debug("Processing event: type={}, id={}", event.getType(), event.getId());

        // 1. Map Event → TaskRequestProto
        TaskRequestProto taskRequest = eventToTaskRequest(event);

        // 2. Delegate to agent
        return agent.processTask(taskRequest)
            .map(taskResponse -> {
                // 3. Map TaskResponseProto → OperatorResult
                OperatorResult result = taskResponseToOperatorResult(taskResponse, event);
                logger.debug("Agent task completed: taskId={}, success={}",
                            taskResponse.getTaskId(), taskResponse.getSuccess());
                return result;
            })
            .whenException(error -> {
                logger.error("Agent task failed: eventId={}", event.getId(), error);
            });
    }

    // ========================================================================
    // LIFECYCLE (AbstractOperator hooks)
    // ========================================================================

    /**
     * Initialize operator (agent already initialized in constructor).
     *
     * @param config Operator configuration
     * @return Promise that completes immediately
     */
    @Override
    protected Promise<Void> doInitialize(OperatorConfig config) {
        logger.debug("Initializing AgentStreamOperatorAdapter: {}", getId());
        return Promise.complete();
    }

    /**
     * Start agent operator.
     * Delegates to {@link VirtualOrgAgent#start()}.
     *
     * @return Promise that completes when agent started
     */
    @Override
    protected Promise<Void> doStart() {
        logger.info("Starting agent operator: {}", getId());
        return agent.start();
    }

    /**
     * Stop agent operator.
     * Delegates to {@link VirtualOrgAgent#shutdown()}.
     *
     * @return Promise that completes when agent stopped
     */
    @Override
    protected Promise<Void> doStop() {
        logger.info("Stopping agent operator: {}", getId());
        return agent.shutdown();
    }

    /**
     * Check operator health.
     * Operator is healthy if agent is not in FAILED or TERMINATED state.
     *
     * @return true if agent not failed
     */
    @Override
    public boolean isHealthy() {
        AgentStateProto.State state = agent.getState().getState();
        return state != AgentStateProto.State.AGENT_STATE_ERROR
            && state != AgentStateProto.State.AGENT_STATE_TERMINATED;
    }

    // ========================================================================
    // OBSERVABILITY (Extended metrics)
    // ========================================================================

    /**
     * Get operator metrics including agent performance data.
     * Merges {@link AbstractOperator} metrics with agent-specific metrics.
     *
     * @return Map of metric name → value
     */
    @Override
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>(super.getMetrics());

        // Merge agent performance metrics
        AgentPerformanceProto perf = agent.getPerformance();
        metrics.put("agent.tasks_processed", perf.getTasksProcessed());
        metrics.put("agent.error_count", perf.getErrorCount());
        metrics.put("agent.avg_processing_time_ms", perf.getAverageProcessingTimeMs());

        return metrics;
    }

    /**
     * Check if operator maintains state.
     * Agent operators are stateful (maintain memory).
     *
     * @return true (agents maintain memory state)
     */
    @Override
    public boolean isStateful() {
        return true;
    }

    // ========================================================================
    // SERIALIZATION (UnifiedOperator contract)
    // ========================================================================

    /**
     * Serialize operator to Event for EventCloud storage.
     *
     * @return Event representation of this operator
     */
    @Override
    public Event toEvent() {
        EventId eventId = EventId.create(
            UUID.randomUUID().toString(),
            "operator.agent.definition",
            "1.0.0",
            getId().getNamespace()
        );

        Map<String, Object> payload = new HashMap<>();
        payload.put("operatorId", getId().toString());
        payload.put("operatorType", "agent-stream-adapter");
        payload.put("agentRole", agent.getRole().name());
        payload.put("agentId", agent.getId());
        payload.put("capabilities", String.join(",", getCapabilities()));
        payload.put("version", getVersion());
        payload.put("stateful", isStateful());

        Map<String, String> headers = new HashMap<>();
        headers.put("operator.name", getName());
        headers.put("operator.description", getDescription());

        return GEvent.builder()
            .id(eventId)
            .time(EventTime.now())
            .payload(payload)
            .headers(headers)
            .stats(EventStats.builder().build())
            .relations(EventRelations.builder().build())
            .build();
    }

    // ========================================================================
    // MAPPING LOGIC (Event ↔ Task)
    // ========================================================================

    /**
     * Map Event to TaskRequestProto.
     *
     * @param event Input event
     * @return TaskRequestProto for agent processing
     */
    private TaskRequestProto eventToTaskRequest(Event event) {
        Map<String, String> parameters = new HashMap<>();
        extractPayloadToContext(event, parameters);

        return TaskRequestProto.newBuilder()
            .setTaskId(event.getId().getId())
            .setDescription(extractDescription(event))
            .putAllParameters(parameters)
            .build();
    }

    /**
     * Map TaskResponseProto to OperatorResult.
     *
     * @param response      Agent task response
     * @param originalEvent Original input event (for correlation)
     * @return OperatorResult with output events or error
     */
    private OperatorResult taskResponseToOperatorResult(TaskResponseProto response, Event originalEvent) {
        if (response.getSuccess()) {
            Map<String, Object> resultPayload = new HashMap<>();
            resultPayload.put("task_id", response.getTaskId());
            resultPayload.put("result", response.getResult());
            resultPayload.put("agent_id", agent.getId());
            resultPayload.put("agent_role", agent.getRole().name());
            resultPayload.put("original_event_id", originalEvent.getId().getId());
            resultPayload.put("original_event_type", originalEvent.getType());

            if (response.hasMetrics()) {
                AgentMetricsProto metrics = response.getMetrics();
                resultPayload.put("cpu_usage", metrics.getCpuUsage());
                resultPayload.put("memory_usage_bytes", metrics.getMemoryUsageBytes());
                resultPayload.put("queue_size", metrics.getQueueSize());
            }

            Map<String, String> resultHeaders = new HashMap<>();
            if (originalEvent.getCorrelationId() != null) {
                resultHeaders.put("correlationId", originalEvent.getCorrelationId());
            }
            resultHeaders.put("causationId", originalEvent.getId().getId());
            resultHeaders.put("tenantId", originalEvent.getTenantId());

            EventId resultEventId = EventId.create(
                UUID.randomUUID().toString(),
                "agent.task.completed",
                "1.0.0",
                originalEvent.getTenantId()
            );

            GEvent resultEvent = GEvent.builder()
                .id(resultEventId)
                .time(EventTime.now())
                .payload(resultPayload)
                .headers(resultHeaders)
                .stats(EventStats.builder().build())
                .relations(EventRelations.builder().build())
                .build();

            return OperatorResult.of(resultEvent);

        } else {
            String errorMessage = response.getErrorMessage().isEmpty()
                ? "Task failed without error message"
                : response.getErrorMessage();
            return OperatorResult.failed(errorMessage);
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private static String buildOperatorName(VirtualOrgAgent agent) {
        return String.format("Virtual-Org %s Agent", agent.getRole().name());
    }

    private static String buildOperatorDescription(VirtualOrgAgent agent) {
        return String.format(
            "LLM-powered %s agent for %s decisions and tasks",
            agent.getRole().name(),
            agent.getAuthority().name().toLowerCase()
        );
    }

    private static List<String> buildCapabilities(VirtualOrgAgent agent) {
        List<String> capabilities = new ArrayList<>();
        capabilities.add("agent.task_processing");
        capabilities.add("agent.decision_making");
        capabilities.add("agent.role." + agent.getRole().name().toLowerCase());

        for (ToolProto tool : agent.getTools()) {
            capabilities.add("tool." + tool.getName());
        }

        return capabilities;
    }

    private String extractDescription(Event event) {
        Object desc = event.getPayload("description");
        if (desc != null) {
            return String.valueOf(desc);
        }
        return "Process event: " + event.getType();
    }

    private void extractPayloadToContext(Event event, Map<String, String> context) {
        String[] knownFields = {
            "description", "pr_id", "repository", "branch", "commit",
            "severity", "priority", "status", "assignee", "labels"
        };

        for (String field : knownFields) {
            Object value = event.getPayload(field);
            if (value != null) {
                context.put(field, String.valueOf(value));
            }
        }

        context.put("event_type", event.getType());
        context.put("event_id", event.getId().getId());
        if (event.getTenantId() != null) {
            context.put("tenant_id", event.getTenantId());
        }

        if (event.getCorrelationId() != null) {
            context.put("correlation_id", event.getCorrelationId());
        }
        if (event.getCausationId() != null) {
            context.put("causation_id", event.getCausationId());
        }
    }
}
