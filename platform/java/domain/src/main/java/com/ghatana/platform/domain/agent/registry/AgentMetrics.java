package com.ghatana.platform.domain.agent.registry;

import java.time.Instant;
import java.util.Map;

/**
 * {@code AgentMetrics} provides operational observability for agent instances,
 * including processing throughput, resource consumption, and health status.
 *
 * <h2>Purpose</h2>
 * Enables monitoring and observability by exposing:
 * <ul>
 *   <li>Processing throughput metrics (events processed, errors)</li>
 *   <li>Performance metrics (latency, throughput)</li>
 *   <li>Resource consumption (memory, CPU, threads)</li>
 *   <li>Health status and operational state</li>
 *   <li>Custom application-specific metrics</li>
 * </ul>
 *
 * <h2>Architecture Role</h2>
 * <ul>
 *   <li><b>Exposed by</b>: Agent runtime, agent instances</li>
 *   <li><b>Collected by</b>: Metrics collector, observability pipeline</li>
 *   <li><b>Stored in</b>: Prometheus, ClickHouse, or time-series DB</li>
 *   <li><b>Visualized by</b>: Grafana, dashboards, alerting systems</li>
 *   <li><b>Used by</b>: Auto-scaling, SLO enforcement, debugging</li>
 * </ul>
 *
 * <h2>Throughput Metrics</h2>
 *
 * <h3>Event Processing</h3>
 * {@code getEventsProcessed()} returns total successful events handled.
 * {@code processedCount()} is an alias for backward compatibility.
 *
 * <h3>Error Count</h3>
 * {@code getErrorCount()} tracks failed operations enabling:
 * <ul>
 *   <li>Error rate calculation: errorCount / (errorCount + successCount)</li>
 *   <li>SLO monitoring (e.g., target < 0.1% error rate)</li>
 *   <li>Alert triggering on degradation</li>
 * </ul>
 *
 * <h2>Performance Metrics</h2>
 *
 * <h3>Latency</h3>
 * {@code getAverageProcessingTimeMs()} reports mean operation latency in milliseconds,
 * enabling P50/P95/P99 tracking across multiple instances (collected externally).
 *
 * <h3>Throughput</h3>
 * {@code getCurrentThroughput()} reports operations/second right now.
 * {@code getPeakThroughput()} reports maximum throughput since startup/reset.
 * Enables:
 * <ul>
 *   <li>Capacity planning decisions</li>
 *   <li>Load balancing optimization</li>
 *   <li>Scalability testing</li>
 * </ul>
 *
 * <h3>Temporal Information</h3>
 * {@code getLastProcessedAt()} returns timestamp of most recent operation,
 * enabling detection of hung or stalled agents.
 *
 * <h2>Resource Metrics</h2>
 *
 * <h3>Memory</h3>
 * {@code getMemoryUsageMb()} reports current heap/buffer usage:
 * <ul>
 *   <li>Monitors against {@link ResourceRequirements#getMemoryMb()}</li>
 *   <li>Detects memory leaks over time</li>
 *   <li>Guides garbage collection tuning</li>
 * </ul>
 *
 * <h3>CPU</h3>
 * {@code getCpuUtilization()} reports percentage (0-100):
 * <ul>
 *   <li>CPU 0%: Idle, underutilized</li>
 *   <li>CPU 50-80%: Healthy utilization</li>
 *   <li>CPU 100%: Saturated, may cause latency spikes</li>
 * </ul>
 *
 * <h3>Concurrency</h3>
 * {@code getActiveThreads()} reports currently active work threads,
 * enabling:
 * <ul>
 *   <li>Back-pressure detection when approaching maxConcurrency</li>
 *   <li>Queue depth monitoring</li>
 *   <li>Deadlock detection patterns</li>
 * </ul>
 *
 * <h2>Health Status</h2>
 * {@code getHealthStatus()} returns operational state:
 * <ul>
 *   <li><b>HEALTHY</b>: Normal operation, all metrics nominal</li>
 *   <li><b>DEGRADED</b>: Operational but with elevated latency/errors</li>
 *   <li><b>UNHEALTHY</b>: Non-operational, requires attention</li>
 *   <li><b>UNKNOWN</b>: Health cannot be determined (e.g., just started)</li>
 * </ul>
 * Typically computed from:
 * <ul>
 *   <li>Error rate thresholds</li>
 *   <li>Latency percentiles</li>
 *   <li>Resource utilization</li>
 *   <li>Liveness checks</li>
 * </ul>
 *
 * <h2>Custom Metrics</h2>
 * {@code getCustomMetrics()} returns application-specific key-value pairs:
 * <ul>
 *   <li>"cache-hit-rate": 0.85 - Domain-specific performance indicator</li>
 *   <li>"pending-jobs": 150 - Business entity counts</li>
 *   <li>"dag-depth": 42 - Execution structure metrics</li>
 * </ul>
 * Enables domain-specific observability beyond standard metrics.
 *
 * <h2>Typical Usage</h2>
 * {@code
 * // Periodic metrics collection
 * Timer.periodic(Duration.ofSeconds(10), () -> {
 *     for (Agent agent : registry.listAgents()) {
 *         AgentMetrics metrics = agent.getMetrics();
 *         metricsCollector.record("agent.events_processed",
 *             metrics.getEventsProcessed(),
 *             Labels.of("agent", agent.getId()));
 *         metricsCollector.record("agent.memory_mb",
 *             metrics.getMemoryUsageMb(),
 *             Labels.of("agent", agent.getId()));
 *         if (metrics.getHealthStatus() == UNHEALTHY) {
 *             alerting.trigger("agent.unhealthy", agent.getId());
 *         }
 *     }
 * });
 * }
 *
 * <h2>Integration with Observability</h2>
 * Metrics typically flow to:
 * <ul>
 *   <li>Prometheus scrape endpoint via metrics exposition format</li>
 *   <li>OpenTelemetry metrics collector</li>
 *   <li>Custom metrics sink for ClickHouse, Grafana Loki</li>
 * </ul>
 *
 * @see ResourceRequirements
 * @see ProcessingCharacteristics
 * @see AgentCapabilities
 *
 * @doc.type interface
 * @doc.layer domain
 * @doc.purpose agent operational metrics contract
 * @doc.pattern contract, observability, metrics-exposition
 * @doc.test-hints metrics-collection, health-monitoring, performance-tracking, resource-utilization, custom-metrics
 */
public interface AgentMetrics {
    long processedCount();
    long getEventsProcessed();
    long getErrorCount();
    double getAverageProcessingTimeMs();
    double getCurrentThroughput();
    double getPeakThroughput();
    Instant getLastProcessedAt();
    long getMemoryUsageMb();
    double getCpuUtilization();
    int getActiveThreads();
    Map<String, Object> getCustomMetrics();
    
    /**
     * Gets the current health status of the agent.
     *
     * @return the health status
     */
    HealthStatus getHealthStatus();
}
