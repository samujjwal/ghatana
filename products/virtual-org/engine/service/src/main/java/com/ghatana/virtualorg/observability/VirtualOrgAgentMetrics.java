package com.ghatana.virtualorg.observability;

import com.ghatana.platform.domain.agent.registry.HealthStatus;
import com.ghatana.platform.observability.MetricsCollector;
import io.micrometer.core.instrument.*;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom metrics for Virtual Organization agents.
 *
 * <p><b>Purpose</b><br>
 * Provides comprehensive metrics collection for virtual organization agents including
 * task processing, LLM interactions, tool executions, and decision tracking.
 *
 * <p><b>Architecture Role</b><br>
 * Uses {@link MetricsCollector} abstraction from core/observability to ensure
 * platform-level control over metrics collection and avoid direct Micrometer dependencies.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MetricsCollector metricsCollector = MetricsCollectorFactory.create(registry);
 * VirtualOrgAgentMetrics metrics = new VirtualOrgAgentMetrics(
 *     metricsCollector, "agent-123", "CEO"
 * );
 *
 * metrics.recordTaskProcessed(true, 1500, 250);
 * metrics.recordDecision("APPROVE", 0.95);
 * }</pre>
 *
 * <p><b>Metrics Tracked</b><br>
 * <ul>
 *   <li>Task processing metrics (count, duration, success rate)</li>
 *   <li>LLM metrics (tokens, latency, cost)</li>
 *   <li>Tool execution metrics</li>
 *   <li>Decision metrics</li>
 *   <li>Queue metrics</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Virtual organization agent metrics collection using core/observability abstraction
 * @doc.layer product
 * @doc.pattern Facade
 */
public class VirtualOrgAgentMetrics implements com.ghatana.platform.domain.agent.registry.AgentMetrics {

    private final MetricsCollector metrics;
    private final MeterRegistry registry;  // For advanced features (timers, gauges)
    private final String agentId;
    private final String role;

    // Timers (advanced metrics - require registry)
    private final Timer taskProcessingTime;
    private final Timer llmRequestTime;
    private final Timer toolExecutionTime;
    private final Timer memoryRetrievalTime;

    // Gauges (advanced metrics - require registry)
    private final AtomicLong activeAgents;
    private final AtomicLong queuedTasks;
    private final AtomicLong totalTokensUsed;

    // Distributions (advanced metrics - require registry)
    private final DistributionSummary taskComplexity;
    private final DistributionSummary llmTokensPerRequest;

    // Counters for tracking success/failure
    private final Counter tasksProcessed;
    private final Counter tasksSucceeded;
    private final Counter tasksFailed;

    /**
     * Creates metrics collector for a virtual organization agent.
     *
     * <p>Uses {@link MetricsCollector} abstraction for counters and basic operations,
     * while accessing underlying {@link MeterRegistry} for advanced features like
     * timers, gauges, and distribution summaries.</p>
     *
     * @param metricsCollector the metrics collector from core/observability
     * @param agentId unique identifier for the agent
     * @param role role of the agent (CEO, CTO, Engineer, etc.)
     */
    public VirtualOrgAgentMetrics(
            @NotNull MetricsCollector metricsCollector,
            @NotNull String agentId,
            @NotNull String role) {
        this.metrics = metricsCollector;
        this.registry = metricsCollector.getMeterRegistry();
        this.agentId = agentId;
        this.role = role;

        Tags tags = Tags.of(
                "agent_id", agentId,
                "role", role
        );

        // Keep counters for compatibility with processedCount() and success rate calculations
        this.tasksProcessed = Counter.builder("virtualorg.tasks.processed")
                .description("Total tasks processed by agent")
                .tags(tags)
                .register(registry);
        
        this.tasksSucceeded = Counter.builder("virtualorg.tasks.succeeded")
                .description("Tasks that completed successfully")
                .tags(tags)
                .register(registry);
        
        this.tasksFailed = Counter.builder("virtualorg.tasks.failed")
                .description("Tasks that failed")
                .tags(tags)
                .register(registry);
        
        this.taskProcessingTime = Timer.builder("virtualorg.tasks.duration")
                .description("Task processing duration")
                .tags(tags)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.llmRequestTime = Timer.builder("virtualorg.llm.duration")
                .description("LLM request duration")
                .tags(tags)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.toolExecutionTime = Timer.builder("virtualorg.tools.duration")
                .description("Tool execution duration")
                .tags(tags)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.memoryRetrievalTime = Timer.builder("virtualorg.memory.retrieval.duration")
                .description("Memory retrieval duration")
                .tags(tags)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        // Initialize gauges
        this.activeAgents = registry.gauge("virtualorg.agents.active",
                tags,
                new AtomicLong(0));

        this.queuedTasks = registry.gauge("virtualorg.tasks.queued",
                tags,
                new AtomicLong(0));

        this.totalTokensUsed = registry.gauge("virtualorg.llm.tokens.total",
                tags,
                new AtomicLong(0));

        // Initialize distributions
        this.taskComplexity = DistributionSummary.builder("virtualorg.tasks.complexity")
                .description("Task complexity score")
                .tags(tags)
                .register(registry);

        this.llmTokensPerRequest = DistributionSummary.builder("virtualorg.llm.tokens.per_request")
                .description("LLM tokens used per request")
                .tags(tags)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    // =============================
    // Task metrics
    // =============================

    /**
     * Records a processed task with outcome and resource usage.
     *
     * <p>Uses {@link MetricsCollector} for counter increments (tasks processed, succeeded, failed)
     * to maintain abstraction from direct Micrometer usage.</p>
     *
     * @param success whether the task completed successfully
     * @param durationMs task processing duration in milliseconds
     * @param tokensUsed LLM tokens consumed during task execution
     */
    public void recordTaskProcessed(boolean success, long durationMs, int tokensUsed) {
        // Increment both MetricsCollector (for platform abstraction) and Counter (for local queries)
        metrics.incrementCounter("virtualorg.tasks.processed");
        tasksProcessed.increment();

        if (success) {
            metrics.incrementCounter("virtualorg.tasks.succeeded");
            tasksSucceeded.increment();
        } else {
            metrics.incrementCounter("virtualorg.tasks.failed");
            tasksFailed.increment();
        }

        // Advanced metrics still use registry (gauges, distributions)
        totalTokensUsed.addAndGet(tokensUsed);
        llmTokensPerRequest.record(tokensUsed);
    }

    @Override
    public long processedCount() {
        return (long) tasksProcessed.count();
    }

    public Timer.Sample startTaskTimer() {
        return Timer.start(registry);
    }

    public void recordTaskDuration(Timer.Sample sample) {
        sample.stop(taskProcessingTime);
    }

    // =============================
    // LLM metrics
    // =============================

    public Timer.Sample startLLMTimer() {
        return Timer.start(registry);
    }

    public void recordLLMRequest(Timer.Sample sample, int tokensUsed, double costUsd) {
        sample.stop(llmRequestTime);
        totalTokensUsed.addAndGet(tokensUsed);
        llmTokensPerRequest.record(tokensUsed);

        // Record cost (if tracking enabled)
        if (costUsd > 0) {
            Counter.builder("virtualorg.llm.cost.usd")
                    .register(registry)
                    .increment(costUsd);
        }
    }

    // =============================
    // Tool metrics
    // =============================

    /**
     * Records a tool execution with outcome and duration.
     *
     * <p>Uses {@link MetricsCollector} for basic counter increment.</p>
     *
     * @param toolName name of the executed tool
     * @param success whether execution succeeded
     * @param durationMs execution duration in milliseconds
     */
    public void recordToolExecution(String toolName, boolean success, long durationMs) {
        // Use MetricsCollector for basic counter
        metrics.incrementCounter("virtualorg.tools.executions");

        // Use MetricsCollector with tags for dimensional metrics
        metrics.incrementCounter(
                "virtualorg.tools.executions.by_tool",
                "tool", toolName,
                "success", String.valueOf(success)
        );

        // Advanced timer still uses registry
        toolExecutionTime.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    // =============================
    // Decision metrics
    // =============================

    /**
     * Records a decision made by the agent.
     *
     * <p>Uses {@link MetricsCollector} for counter increments.</p>
     *
     * @param decisionType type of decision (APPROVE, REJECT, DELEGATE, etc.)
     * @param escalated whether decision was escalated to higher authority
     */
    public void recordDecision(String decisionType, boolean escalated) {
        // Use MetricsCollector for basic counters
        metrics.incrementCounter("virtualorg.decisions.total");

        if (escalated) {
            metrics.incrementCounter("virtualorg.escalations.total");
        }

        // Dimensional metric with tags
        metrics.incrementCounter(
                "virtualorg.decisions.by_type",
                "type", decisionType,
                "escalated", String.valueOf(escalated)
        );
    }

    // =============================
    // Queue metrics
    // =============================

    public void updateQueueSize(long size) {
        queuedTasks.set(size);
    }

    public void updateActiveAgents(long count) {
        activeAgents.set(count);
    }

    // =============================
    // Memory metrics
    // =============================

    public Timer.Sample startMemoryTimer() {
        return Timer.start(registry);
    }

    public void recordMemoryRetrieval(Timer.Sample sample, int resultsFound) {
        sample.stop(memoryRetrievalTime);

        DistributionSummary.builder("virtualorg.memory.results")
                .register(registry)
                .record(resultsFound);
    }

    // =============================
    // Performance metrics
    // =============================

    public void recordTaskComplexity(double complexity) {
        taskComplexity.record(complexity);
    }

    public double getSuccessRate() {
        double total = tasksProcessed.count();
        if (total == 0) return 0.0;
        return tasksSucceeded.count() / total;
    }

    public double getAverageTaskDuration() {
        return taskProcessingTime.mean(java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public long getTotalTokensUsed() {
        return totalTokensUsed.get();
    }

    // =============================
    // AgentMetrics interface implementation
    // =============================

    @Override
    public long getEventsProcessed() {
        return processedCount();
    }

    @Override
    public long getErrorCount() {
        return (long) tasksFailed.count();
    }

    @Override
    public double getAverageProcessingTimeMs() {
        return getAverageTaskDuration();
    }

    @Override
    public double getCurrentThroughput() {
        // Calculate tasks per second
        return taskProcessingTime.count() / (taskProcessingTime.totalTime(java.util.concurrent.TimeUnit.SECONDS) + 0.001);
    }

    @Override
    public double getPeakThroughput() {
        // For now, return current throughput (would need time-window tracking for true peak)
        return getCurrentThroughput();
    }

    @Override
    public java.time.Instant getLastProcessedAt() {
        // This would need to be tracked separately - return current time as placeholder
        return java.time.Instant.now();
    }

    @Override
    public long getMemoryUsageMb() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    @Override
    public double getCpuUtilization() {
        // Would need JMX or system monitoring - return 0.0 as placeholder
        return 0.0;
    }

    @Override
    public int getActiveThreads() {
        return Thread.activeCount();
    }

    @Override
    public java.util.Map<String, Object> getCustomMetrics() {
        return java.util.Map.of(
                "success_rate", getSuccessRate(),
                "total_tokens_used", getTotalTokensUsed(),
                "queued_tasks", queuedTasks.get(),
                "active_agents", activeAgents.get()
        );
    }

    @Override
    public HealthStatus getHealthStatus() {
        double successRate = getSuccessRate();
        long errorCount = getErrorCount();

        if (successRate >= 0.95 && errorCount < 10) {
            return HealthStatus.HEALTHY;
        } else if (successRate >= 0.80 && errorCount < 50) {
            return HealthStatus.DEGRADED;
        } else if (successRate > 0.0) {
            return HealthStatus.UNHEALTHY;
        } else {
            return HealthStatus.UNKNOWN;
        }
    }
}
