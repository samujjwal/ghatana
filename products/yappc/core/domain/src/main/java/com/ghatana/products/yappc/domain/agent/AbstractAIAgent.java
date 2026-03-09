package com.ghatana.products.yappc.domain.agent;

import com.ghatana.agent.AgentCapabilities;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/**
 * Abstract base class for YAPPC AI Agents.
 * <p>
 * Provides common functionality including:
 * <ul>
 *   <li>Telemetry and metrics collection</li>
 *   <li>Error handling and wrapping</li>
 *   <li>Timeout management</li>
 *   <li>Health checking</li>
 *   <li>SLA monitoring</li>
 * </ul>
 *
 * @param <TInput>  The input type for agent requests
 * @param <TOutput> The output type for agent responses
 * @doc.type class
 * @doc.purpose Abstract base AI agent implementation
 * @doc.layer product
 * @doc.pattern Template Method
 */
public abstract class AbstractAIAgent<TInput, TOutput> implements AIAgent<TInput, TOutput> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractAIAgent.class);

    protected final AgentName agentName;
    protected final String version;
    protected final long latencySLA;
    protected final MetricsCollector metricsCollector;
    protected final AgentMetadata metadata;

    private volatile AgentHealth lastHealthCheck;

    /**
     * Creates a new AI agent.
     *
     * @param agentName        The agent name
     * @param version          The agent version
     * @param description      The agent description
     * @param capabilities     The agent capabilities
     * @param supportedModels  The supported AI models
     * @param metricsCollector The metrics collector
     */
    protected AbstractAIAgent(
            @NotNull AgentName agentName,
            @NotNull String version,
            @NotNull String description,
            @NotNull java.util.List<String> capabilities,
            @NotNull java.util.List<String> supportedModels,
            @NotNull MetricsCollector metricsCollector
    ) {
        this.agentName = agentName;
        this.version = version;
        this.latencySLA = agentName.getLatencySLA();
        this.metricsCollector = metricsCollector;
        this.metadata = AgentMetadata.builder()
                .name(agentName)
                .version(version)
                .description(description)
                .capabilities(capabilities)
                .supportedModels(supportedModels)
                .latencySLA(latencySLA)
                .build();
    }

    @Override
    public @NotNull String getId() {
        return agentName.name();
    }

    @Override
    public @NotNull AgentCapabilities getCapabilities() {
        return new AgentCapabilities(
                agentName.getDisplayName(),
                "AI Agent",
                metadata.description(),
                Set.copyOf(metadata.capabilities()),
                Set.of()
        );
    }

    @Override
    public @NotNull Promise<Void> initialize(@NotNull AgentContext context) {
        LOG.info("Initializing agent: {}", agentName.getDisplayName());
        return doInitialize(context);
    }

    @Override
    public @NotNull Promise<Void> start() {
        LOG.info("Starting agent: {}", agentName.getDisplayName());
        return doStart();
    }

    @Override
    public @NotNull Promise<Void> shutdown() {
        LOG.info("Shutting down agent: {}", agentName.getDisplayName());
        return doShutdown();
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull <T, R> Promise<R> process(@NotNull T task, @NotNull AgentContext context) {
        // Build an AIAgentContext from the canonical AgentContext
        AIAgentContext aiContext = AIAgentContext.builder()
                .tenantId(context.getTenantId())
                .organizationId(String.valueOf(context.getConfig("organizationId")))
                .userId(context.getUserId() != null ? context.getUserId() : "system")
                .workspaceId(String.valueOf(context.getConfig("workspaceId")))
                .requestId(context.getTurnId())
                .metadata(context.getMetadata())
                .build();
        return execute((TInput) task, aiContext)
                .map(result -> (R) result);
    }

    @Override
    public long getLatencySLA() {
        return latencySLA;
    }

    @Override
    public @NotNull AgentMetadata getMetadata() {
        return metadata;
    }

    @Override
    public @NotNull Promise<AgentResult<TOutput>> execute(
            @NotNull TInput input,
            @NotNull AIAgentContext context
    ) {
        final long startTime = System.currentTimeMillis();
        final String requestId = context.requestId() != null ? context.requestId() : UUID.randomUUID().toString();

        // Record request start
        metricsCollector.incrementCounter("agent.requests.total", "agent", agentName.name());

        return Promise.complete()
                .then(() -> {
                    // Validate input
                    validateInput(input);

                    // Check if agent is excluded for this user
                    if (context.preferences() != null &&
                            context.preferences().excludedAgents().contains(agentName)) {
                        return Promise.ofException(
                                new AgentExcludedException(agentName, context.userId())
                        );
                    }

                    // Process with timeout
                    long timeout = context.timeout() > 0 ? context.timeout() : AIAgentContext.DEFAULT_TIMEOUT;
                    return Promises.timeout(Duration.ofMillis(timeout), processRequest(input, context));
                })
                .map(processResult -> {
                    long latencyMs = System.currentTimeMillis() - startTime;

                    // Check SLA
                    if (latencyMs > latencySLA) {
                        LOG.warn("[{}] SLA violation: {}ms > {}ms",
                                agentName.getDisplayName(), latencyMs, latencySLA);
                        metricsCollector.incrementCounter("agent.sla.violations", "agent", agentName.name());
                    }

                        // Record metrics
                        metricsCollector.getMeterRegistry()
                            .timer("agent.latency.ms", "agent", agentName.name())
                            .record(latencyMs, TimeUnit.MILLISECONDS);
                    metricsCollector.incrementCounter("agent.requests.success", "agent", agentName.name());

                    AgentResult.AgentMetrics metrics = AgentResult.AgentMetrics.builder()
                            .latencyMs(latencyMs)
                            .tokensUsed(processResult.tokensUsed())
                            .modelVersion(processResult.modelVersion() != null ? processResult.modelVersion() : version)
                            .confidence(processResult.confidence())
                            .build();

                    AgentResult.AgentTrace trace = AgentResult.AgentTrace.of(
                            agentName.getDisplayName(),
                            requestId,
                            Map.of("success", true)
                    );

                    return AgentResult.success(processResult.data(), metrics, trace);
                })
                .mapException(error -> {
                    long latencyMs = System.currentTimeMillis() - startTime;

                    LOG.error("[{}] Request failed: {}", agentName.getDisplayName(), error.getMessage(), error);
                    metricsCollector.incrementCounter("agent.requests.error", "agent", agentName.name());

                    AgentResult.AgentMetrics metrics = AgentResult.AgentMetrics.builder()
                            .latencyMs(latencyMs)
                            .modelVersion(version)
                            .build();

                    AgentResult.AgentTrace trace = AgentResult.AgentTrace.of(
                            agentName.getDisplayName(),
                            requestId,
                            Map.of("success", false, "error", error.getMessage())
                    );

                    AgentResult.AgentError agentError = wrapError(error);

                    // Return a wrapped result - this allows graceful degradation
                    return new AgentExecutionException(AgentResult.failure(agentError, metrics, trace));
                });
    }

    @Override
    public @NotNull Promise<AgentHealth> healthCheck() {
        long startTime = System.currentTimeMillis();

        return doHealthCheck()
                .map(dependencies -> {
                    long latencyMs = System.currentTimeMillis() - startTime;
                    lastHealthCheck = AgentHealth.healthy(latencyMs, dependencies);
                    return lastHealthCheck;
                })
                .mapException(error -> {
                    long latencyMs = System.currentTimeMillis() - startTime;
                    lastHealthCheck = AgentHealth.unhealthy(latencyMs, error.getMessage());
                    return new HealthCheckException(lastHealthCheck);
                });
    }

    /**
     * Process the request. Must be implemented by concrete agents.
     *
     * @param input   The input request
     * @param context The execution context
     * @return Promise resolving to the process result
     */
    protected abstract Promise<ProcessResult<TOutput>> processRequest(
            @NotNull TInput input,
            @NotNull AIAgentContext context
    );

    /**
     * Perform agent-specific initialization.
     */
    protected Promise<Void> doInitialize(@NotNull AgentContext context) {
        return Promise.complete();
    }

    /**
     * Perform agent-specific startup.
     */
    protected Promise<Void> doStart() {
        return Promise.complete();
    }

    /**
     * Perform agent-specific shutdown.
     */
    protected Promise<Void> doShutdown() {
        return Promise.complete();
    }

    /**
     * Perform agent-specific health check.
     *
     * @return Promise resolving to dependency statuses
     */
    protected abstract Promise<Map<String, AgentHealth.DependencyStatus>> doHealthCheck();

    /**
     * Wraps an exception in an AgentError.
     */
    protected AgentResult.AgentError wrapError(Throwable error) {
        if (error instanceof AgentExcludedException ex) {
            return AgentResult.AgentError.of("AGENT_EXCLUDED", ex.getMessage(), agentName.name());
        }
        if (error instanceof java.util.concurrent.TimeoutException) {
            return AgentResult.AgentError.retryable("TIMEOUT", "Request timed out", agentName.name());
        }
        if (error instanceof IllegalArgumentException ex) {
            return AgentResult.AgentError.of("INVALID_INPUT", ex.getMessage(), agentName.name());
        }
        return AgentResult.AgentError.withDetails(
                "INTERNAL_ERROR",
                "Internal agent error",
                agentName.name(),
                error.getMessage()
        );
    }

    /**
     * Result from processing a request.
     *
     * @param <T> The output data type
     */
    protected record ProcessResult<T>(
            T data,
            Integer tokensUsed,
            String modelVersion,
            Double confidence
    ) {
        public static <T> ProcessResult<T> of(T data) {
            return new ProcessResult<>(data, null, null, null);
        }

        public static <T> ProcessResult<T> of(T data, Double confidence) {
            return new ProcessResult<>(data, null, null, confidence);
        }

        public static <T> ProcessResult<T> of(T data, Integer tokensUsed, String modelVersion, Double confidence) {
            return new ProcessResult<>(data, tokensUsed, modelVersion, confidence);
        }
    }

    /**
     * Exception thrown when an agent is excluded for a user.
     */
    public static class AgentExcludedException extends RuntimeException {
        private final AgentName agentName;
        private final String userId;

        public AgentExcludedException(AgentName agentName, String userId) {
            super("Agent " + agentName.getDisplayName() + " is excluded for user " + userId);
            this.agentName = agentName;
            this.userId = userId;
        }

        public AgentName getAgentName() {
            return agentName;
        }

        public String getUserId() {
            return userId;
        }
    }

    /**
     * Exception wrapping an agent execution failure.
     */
    public static class AgentExecutionException extends RuntimeException {
        private final AgentResult<?> result;

        public AgentExecutionException(AgentResult<?> result) {
            super(result.error() != null ? result.error().message() : "Agent execution failed");
            this.result = result;
        }

        public AgentResult<?> getResult() {
            return result;
        }
    }

    /**
     * Exception wrapping a health check failure.
     */
    public static class HealthCheckException extends RuntimeException {
        private final AgentHealth health;

        public HealthCheckException(AgentHealth health) {
            super(health.errorMessage());
            this.health = health;
        }

        public AgentHealth getHealth() {
            return health;
        }
    }
}
