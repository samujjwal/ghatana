package com.ghatana.agent.workflow;

import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.runtime.GaaAgentExecutor;
import com.ghatana.agent.runtime.TypedAgentExecutor;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of WorkflowAgentService.
 *
 * <p>Executes workflow agents using the ActiveJ Promise model, integrating
 * with LLMGateway for AI-powered agent capabilities. This service manages
 * execution state, metrics collection, structured logging, and distributed tracing.
 *
 * <p><b>Architecture:</b> Per copilot-instructions.md:
 * <ul>
 *   <li>Uses ActiveJ Promise for all async operations (no CompletableFuture)</li>
 *   <li>Delegates to LLMGateway for AI completions</li>
 *   <li>Collects metrics via WorkflowAgentMetrics facade</li>
 *   <li>Emits distributed traces via OpenTelemetry Tracer</li>
 *   <li>Uses MDC for structured logging context</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * WorkflowAgentService service = new DefaultWorkflowAgentService(
 *     registry,
 *     llmGateway,
 *     metricsCollector,
 *     tracer
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
 * @doc.purpose Default workflow agent service implementation with observability
 * @doc.layer infrastructure
 * @doc.pattern Service
 */
public class DefaultWorkflowAgentService implements WorkflowAgentService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultWorkflowAgentService.class);

    // Constants for duplicate literals
    private static final String ROLE = "role";
    private static final String STATUS = "status";
    private static final String SUCCESS = "success";
    private static final String RESULT = "result";

    private final WorkflowAgentRegistry registry;
    private final LLMGateway llmGateway;
    private final WorkflowAgentMetrics metrics;
    private final Tracer tracer;
    private final TypedAgentExecutor typedAgentExecutor = new GaaAgentExecutor();

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
     * @param tracer The OpenTelemetry tracer for distributed tracing
     */
    public DefaultWorkflowAgentService(
            @NotNull WorkflowAgentRegistry registry,
            @NotNull LLMGateway llmGateway,
            @NotNull MetricsCollector metricsCollector,
            @NotNull Tracer tracer
    ) {
        this.registry = Objects.requireNonNull(registry, "registry is required");
        this.llmGateway = Objects.requireNonNull(llmGateway, "llmGateway is required");
        this.metrics = new WorkflowAgentMetrics(Objects.requireNonNull(metricsCollector, "metricsCollector is required"));
        this.tracer = Objects.requireNonNull(tracer, "tracer is required");
    }

    /**
     * Creates a new DefaultWorkflowAgentService with no-op tracer.
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
        this(registry, llmGateway, metricsCollector,
             io.opentelemetry.api.OpenTelemetry.noop().getTracer("workflow-agent-service"));
    }

    @Override
    @NotNull
    public Promise<WorkflowAgentResult> execute(@NotNull WorkflowAgentRequest request) {
        Objects.requireNonNull(request, "request is required");

        Instant startTime = Instant.now();
        String tenantId = request.context().tenantId();

        // Set MDC context for structured logging
        MDC.put("requestId", request.id());
        MDC.put("agentId", request.agentId());
        MDC.put("tenantId", tenantId);
        MDC.put("role", request.role().getCode());

        // Create execution span
        Span executionSpan = tracer.spanBuilder("workflow.agent.execute")
                .setAttribute("request_id", request.id())
                .setAttribute("agent_id", request.agentId())
                .setAttribute("tenant_id", tenantId)
                .setAttribute("role", request.role().getCode())
                .setAttribute("priority", request.priority().name())
                .startSpan();

        try (Scope scope = executionSpan.makeCurrent()) {
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
                            executionSpan.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
                            metrics.recordFailed(request.role().getCode(), request.agentId(), tenantId,
                                    new IllegalArgumentException("Agent not found: " + request.agentId()));
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
                            LOG.info("Execution cancelled: {}", request.id());
                            executionSpan.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
                            metrics.recordCancelled(request.role().getCode(), request.agentId(), tenantId);
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

                        @SuppressWarnings("unchecked")
                        TypedAgent<Map<String, Object>, Object> agent =
                                (TypedAgent<Map<String, Object>, Object>) optionalAgent.get();
                        AgentContext agentContext = createAgentContext(request);

                        // Execute the agent through the governed lifecycle executor.
                        Span agentProcessSpan = tracer.spanBuilder("workflow.agent.process")
                                .setParent(Context.current())
                                .setAttribute("agent_id", request.agentId())
                                .startSpan();

                        return typedAgentExecutor.execute(agent, agentContext, request.input())
                                .map(agentResult -> {
                                    long durationMs = System.currentTimeMillis() - startTime.toEpochMilli();

                                    // Record metrics using facade
                                    metrics.recordCompleted(request.role().getCode(), request.agentId(),
                                            tenantId, durationMs);

                                    agentProcessSpan.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                                    agentProcessSpan.setAttribute("duration_ms", durationMs);
                                    agentProcessSpan.setAttribute("confidence", agentResult.getConfidence());
                                    agentProcessSpan.end();

                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> output = agentResult.getOutput() instanceof Map<?,?> m
                                            ? (Map<String, Object>) m
                                            : Map.of(RESULT, agentResult.getOutput());

                                    double confidence = agentResult.getConfidence();

                                    // Update status
                                    executionTrackers.put(request.id(), new ExecutionTracker(
                                            request,
                                            ExecutionStatus.COMPLETED,
                                            startTime,
                                            false
                                    ));

                                    LOG.debug("Agent execution completed: {} duration: {}ms",
                                            request.agentId(), durationMs);

                                    executionSpan.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                                    executionSpan.end();

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

                                    // Record failure metrics using facade
                                    metrics.recordFailed(request.role().getCode(), request.agentId(),
                                            tenantId, error instanceof RuntimeException ? (RuntimeException) error : new RuntimeException(error));

                                    agentProcessSpan.recordException(error);
                                    agentProcessSpan.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
                                    agentProcessSpan.setAttribute("duration_ms", durationMs);
                                    agentProcessSpan.end();

                                    // Update status
                                    executionTrackers.put(request.id(), new ExecutionTracker(
                                            request,
                                            ExecutionStatus.FAILED,
                                            startTime,
                                            false
                                    ));

                                    executionSpan.recordException(error);
                                    executionSpan.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
                                    executionSpan.end();

                                    return new RuntimeException("Agent execution failed: " + error.getMessage(), error);
                                });
                    });
        } finally {
            MDC.remove("requestId");
            MDC.remove("agentId");
            MDC.remove("tenantId");
            MDC.remove("role");
        }
    }

    @Override
    @NotNull
    public Promise<List<WorkflowAgentResult>> executeBatch(@NotNull List<WorkflowAgentRequest> requests) {
        Objects.requireNonNull(requests, "requests are required");

        if (requests.isEmpty()) {
            return Promise.of(List.of());
        }

        // Record batch metrics
        if (!requests.isEmpty()) {
            String tenantId = requests.get(0).context().tenantId();
            String role = requests.get(0).role().getCode();
            metrics.recordBatchExecution(role, tenantId, requests.size());
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

        MDC.put("requestId", requestId);
        Span cancelSpan = tracer.spanBuilder("workflow.agent.cancel")
                .setAttribute("request_id", requestId)
                .startSpan();

        try (Scope scope = cancelSpan.makeCurrent()) {
            ExecutionTracker tracker = executionTrackers.get(requestId);
            if (tracker == null) {
                LOG.debug("Cancel requested for unknown request: {}", requestId);
                cancelSpan.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
                cancelSpan.end();
                return Promise.of(false);
            }

            if (tracker.status() == ExecutionStatus.COMPLETED ||
                tracker.status() == ExecutionStatus.FAILED ||
                tracker.status() == ExecutionStatus.CANCELLED) {
                LOG.debug("Cancel requested for terminal status: {}", tracker.status());
                cancelSpan.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
                cancelSpan.setAttribute("current_status", tracker.status().name());
                cancelSpan.end();
                return Promise.of(false);
            }

            // Record cancellation metrics
            metrics.recordCancelled(tracker.request().role().getCode(),
                    tracker.request().agentId(),
                    tracker.request().context().tenantId());

            // Mark as cancelled
            executionTrackers.put(requestId, new ExecutionTracker(
                    tracker.request(),
                    ExecutionStatus.CANCELLED,
                    tracker.startedAt(),
                    true
            ));

            LOG.info("Cancelled execution: {}", requestId);
            cancelSpan.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
            cancelSpan.end();
            return Promise.of(Boolean.TRUE);
        } finally {
            MDC.remove("requestId");
        }
    }

    @Override
    @NotNull
    public Promise<ExecutionStatus> getStatus(@NotNull String requestId) {
        Objects.requireNonNull(requestId, "requestId is required");

        MDC.put("requestId", requestId);
        Span statusSpan = tracer.spanBuilder("workflow.agent.get_status")
                .setAttribute("request_id", requestId)
                .startSpan();

        try (Scope scope = statusSpan.makeCurrent()) {
            ExecutionTracker tracker = executionTrackers.get(requestId);
            if (tracker == null) {
                statusSpan.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
                statusSpan.setAttribute("status", ExecutionStatus.NOT_FOUND.name());
                statusSpan.end();
                return Promise.of(ExecutionStatus.NOT_FOUND);
            }
            statusSpan.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
            statusSpan.setAttribute("status", tracker.status().name());
            statusSpan.end();
            return Promise.of(tracker.status());
        } finally {
            MDC.remove("requestId");
        }
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

        MDC.put("agentId", agentId);
        Span healthSpan = tracer.spanBuilder("workflow.agent.get_health")
                .setAttribute("agent_id", agentId)
                .startSpan();

        try (Scope scope = healthSpan.makeCurrent()) {
            return registry.getAgentMetadata(agentId)
                    .map(optionalMetadata -> {
                        if (optionalMetadata.isEmpty()) {
                            healthSpan.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
                            healthSpan.setAttribute("healthy", false);
                            healthSpan.end();
                            metrics.recordHealthCheck(agentId, WorkflowAgentRole.GENERAL.getCode(), false);
                            return AgentHealthInfo.unhealthy(agentId, WorkflowAgentRole.GENERAL);
                        }

                        WorkflowAgentRegistry.AgentMetadata metadata = optionalMetadata.get();
                        healthSpan.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                        healthSpan.setAttribute("healthy", true);
                        healthSpan.setAttribute("role", metadata.role().getCode());
                        healthSpan.end();
                        metrics.recordHealthCheck(agentId, metadata.role().getCode(), true);
                        return AgentHealthInfo.healthy(agentId, metadata.role());
                    });
        } finally {
            MDC.remove("agentId");
        }
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
