package com.ghatana.agent.workflow;

import com.ghatana.agent.Agent;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of WorkflowAgentService.
 *
 * <p>Executes workflow agents using the ActiveJ Promise model, integrating
 * with LLMGateway for AI-powered agent capabilities. This service manages
 * execution state, metrics collection, and result handling.
 *
 * <p><b>Architecture:</b> Per copilot-instructions.md:
 * <ul>
 *   <li>Uses ActiveJ Promise for all async operations (no CompletableFuture)</li>
 *   <li>Delegates to LLMGateway for AI completions</li>
 *   <li>Collects metrics via MetricsCollector</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * WorkflowAgentService service = new DefaultWorkflowAgentService(
 *     registry,
 *     llmGateway,
 *     metricsCollector
 * );
 *
 * WorkflowAgentRequest request = WorkflowAgentRequest.builder(agentId, role)
 *     .input(Map.of("task", "Review this code"))
 *     .context(ExecutionContext.system("tenant-1"))
 *     .build();
 *
 * WorkflowAgentResult result = service.execute(request).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Default workflow agent service implementation
 * @doc.layer infrastructure
 * @doc.pattern Service
 */
public class DefaultWorkflowAgentService implements WorkflowAgentService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultWorkflowAgentService.class);

    private final WorkflowAgentRegistry registry;
    private final LLMGateway llmGateway;
    private final MetricsCollector metricsCollector;

    /**
     * Tracks execution status for pending requests.
     */
    private final ConcurrentHashMap<String, ExecutionTracker> executionTrackers = new ConcurrentHashMap<>();

    /**
     * Execution tracker for a single request.
     */
    private record ExecutionTracker(
            WorkflowAgentRequest request,
            ExecutionStatus status,
            Instant startedAt,
            boolean cancelled
    ) {}

    /**
     * Creates a new DefaultWorkflowAgentService.
     *
     * @param registry The agent registry
     * @param llmGateway The LLM gateway for AI-powered operations
     * @param metricsCollector The metrics collector
     */
    public DefaultWorkflowAgentService(
            @NotNull WorkflowAgentRegistry registry,
            @NotNull LLMGateway llmGateway,
            @NotNull MetricsCollector metricsCollector
    ) {
        this.registry = Objects.requireNonNull(registry, "registry is required");
        this.llmGateway = Objects.requireNonNull(llmGateway, "llmGateway is required");
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector is required");
    }

    @Override
    @NotNull
    public Promise<WorkflowAgentResult> execute(@NotNull WorkflowAgentRequest request) {
        Objects.requireNonNull(request, "request is required");

        Instant startTime = Instant.now();

        // Track execution
        executionTrackers.put(request.id(), new ExecutionTracker(
                request,
                ExecutionStatus.QUEUED,
                startTime,
                false
        ));

        LOG.debug("Executing workflow agent request: {} for agent: {} role: {}",
                request.id(), request.agentId(), request.role());

        // Get the agent from registry
        return registry.getAgent(request.agentId())
                .then(optionalAgent -> {
                    if (optionalAgent.isEmpty()) {
                        LOG.warn("Agent not found: {}", request.agentId());
                        return Promise.of(WorkflowAgentResult.failure(
                                generateResultId(),
                                request.id(),
                                request.agentId(),
                                "Agent not found: " + request.agentId(),
                                WorkflowAgentResult.ExecutionMetrics.empty(),
                                startTime
                        ));
                    }

                    // Check if cancelled
                    ExecutionTracker tracker = executionTrackers.get(request.id());
                    if (tracker != null && tracker.cancelled()) {
                        return Promise.of(WorkflowAgentResult.failure(
                                generateResultId(),
                                request.id(),
                                request.agentId(),
                                "Execution cancelled",
                                WorkflowAgentResult.ExecutionMetrics.empty(),
                                startTime
                        ));
                    }

                    // Update status to running
                    executionTrackers.put(request.id(), new ExecutionTracker(
                            request,
                            ExecutionStatus.RUNNING,
                            startTime,
                            false
                    ));

                    Agent agent = optionalAgent.get();
                    AgentContext agentContext = createAgentContext(request);

                    // Execute the agent
                    return agent.process(request.input(), agentContext)
                            .map(result -> {
                                long durationMs = System.currentTimeMillis() - startTime.toEpochMilli();

                                // Record metrics
                                metricsCollector.recordTimer(
                                        "workflow_agent.duration",
                                        durationMs,
                                        "role", request.role().getCode(),
                                        "status", "success"
                                );
                                metricsCollector.incrementCounter(
                                        "workflow_agent.executions",
                                        "role", request.role().getCode(),
                                        "status", "success"
                                );

                                @SuppressWarnings("unchecked")
                                Map<String, Object> output = result instanceof Map
                                        ? (Map<String, Object>) result
                                        : Map.of("result", result);

                                // Extract confidence from agent output, default to 0.5 (uncertain)
                                double confidence = 0.5;
                                Object rawConfidence = output.get("confidence");
                                if (rawConfidence instanceof Number num) {
                                    confidence = Math.max(0.0, Math.min(1.0, num.doubleValue()));
                                }

                                // Update status
                                executionTrackers.put(request.id(), new ExecutionTracker(
                                        request,
                                        ExecutionStatus.COMPLETED,
                                        startTime,
                                        false
                                ));

                                return WorkflowAgentResult.success(
                                        generateResultId(),
                                        request.id(),
                                        request.agentId(),
                                        output,
                                        confidence,
                                        new WorkflowAgentResult.ExecutionMetrics(durationMs, 0, 0.0),
                                        startTime
                                );
                            })
                            .mapException(error -> {
                                long durationMs = System.currentTimeMillis() - startTime.toEpochMilli();

                                LOG.error("Agent execution failed: {} - {}", request.agentId(), error.getMessage(), error);

                                // Record failure metrics
                                metricsCollector.recordTimer(
                                        "workflow_agent.duration",
                                        durationMs,
                                        "role", request.role().getCode(),
                                        "status", "failure"
                                );
                                metricsCollector.incrementCounter(
                                        "workflow_agent.executions",
                                        "role", request.role().getCode(),
                                        "status", "failure"
                                );

                                // Update status
                                executionTrackers.put(request.id(), new ExecutionTracker(
                                        request,
                                        ExecutionStatus.FAILED,
                                        startTime,
                                        false
                                ));

                                return new RuntimeException("Agent execution failed: " + error.getMessage(), error);
                            });
                });
    }

    @Override
    @NotNull
    public Promise<List<WorkflowAgentResult>> executeBatch(@NotNull List<WorkflowAgentRequest> requests) {
        Objects.requireNonNull(requests, "requests is required");

        if (requests.isEmpty()) {
            return Promise.of(List.of());
        }

        // Execute all requests concurrently
        List<Promise<WorkflowAgentResult>> promises = requests.stream()
                .map(this::execute)
                .toList();

        return Promises.toList(promises);
    }

    @Override
    @NotNull
    public Promise<Boolean> cancel(@NotNull String requestId) {
        Objects.requireNonNull(requestId, "requestId is required");

        ExecutionTracker tracker = executionTrackers.get(requestId);
        if (tracker == null) {
            return Promise.of(false);
        }

        if (tracker.status() == ExecutionStatus.COMPLETED ||
            tracker.status() == ExecutionStatus.FAILED ||
            tracker.status() == ExecutionStatus.CANCELLED) {
            return Promise.of(false);
        }

        // Mark as cancelled
        executionTrackers.put(requestId, new ExecutionTracker(
                tracker.request(),
                ExecutionStatus.CANCELLED,
                tracker.startedAt(),
                true
        ));

        LOG.info("Cancelled execution: {}", requestId);
        return Promise.of(true);
    }

    @Override
    @NotNull
    public Promise<ExecutionStatus> getStatus(@NotNull String requestId) {
        Objects.requireNonNull(requestId, "requestId is required");

        ExecutionTracker tracker = executionTrackers.get(requestId);
        if (tracker == null) {
            return Promise.of(ExecutionStatus.NOT_FOUND);
        }
        return Promise.of(tracker.status());
    }

    @Override
    @NotNull
    public Promise<List<String>> getAgentsForRole(@NotNull WorkflowAgentRole role) {
        return registry.getAgentsByRole(role);
    }

    @Override
    @NotNull
    public Promise<AgentHealthInfo> getAgentHealth(@NotNull String agentId) {
        Objects.requireNonNull(agentId, "agentId is required");

        return registry.getAgentMetadata(agentId)
                .map(optionalMetadata -> {
                    if (optionalMetadata.isEmpty()) {
                        return AgentHealthInfo.unhealthy(agentId, WorkflowAgentRole.GENERAL);
                    }

                    WorkflowAgentRegistry.AgentMetadata metadata = optionalMetadata.get();
                    return AgentHealthInfo.healthy(agentId, metadata.role());
                });
    }

    /**
     * Creates an AgentContext from the execution request.
     */
    private AgentContext createAgentContext(WorkflowAgentRequest request) {
        Map<String, Object> config = new HashMap<>(request.input());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("requestId", request.id());
        metadata.put("agentId", request.agentId());
        metadata.put("role", request.role().getCode());
        metadata.put("priority", request.priority().name());
        metadata.put("correlationId", Optional.ofNullable(request.context().correlationId()).orElse(""));
        metadata.put("userId", request.context().userId());

        return AgentContext.builder()
                .turnId(request.id())
                .agentId(request.agentId())
                .tenantId(request.context().tenantId())
                .userId(request.context().userId())
                .startTime(Instant.now())
                .memoryStore(MemoryStore.noOp())
                .config(config)
                .metadata(metadata)
                .build();
    }

    /**
     * Generates a unique result ID.
     */
    private String generateResultId() {
        return "result-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Clears all execution trackers.
     * Primarily for testing.
     */
    public void clearTrackers() {
        executionTrackers.clear();
    }
}
