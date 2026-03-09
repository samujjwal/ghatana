package com.ghatana.platform.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks metrics for a ReplicatedEventHistory instance.
 * Monitors replication operations, quorum status, and replica health.
 *
 * <p>Provides comprehensive replication metrics for distributed event storage:
 * <ul>
 *   <li><strong>Operation Counters</strong>: Append starts, successes, failures across primary and replicas</li>
 *   <li><strong>Replica Health</strong>: Successful/failed replica operations</li>
 *   <li><strong>Query Metrics</strong>: Query count and errors</li>
 *   <li><strong>Latency Metrics</strong>: Append and query latency (P50/P90/P99/P999)</li>
 *   <li><strong>Replication Lag</strong>: Distribution summary of lag in milliseconds</li>
 *   <li><strong>Cluster State</strong>: Replica count, quorum size, last append time</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * MeterRegistry registry = new SimpleMeterRegistry();
 * ReplicationMetrics metrics = new ReplicationMetrics("replicated-history", registry, 3, 2);
 * 
 * // Record append operation
 * metrics.recordAppendStart();
 * Timer.Sample sample = Timer.start();
 * 
 * try {
 *     // Attempt quorum write (primary + 2 replicas)
 *     replicatedHistory.append(event);
 *     long durationNanos = sample.stop(metrics.getAppendLatencyTimer());
 *     metrics.recordAppendSuccess(durationNanos);
 *     
 *     // Record successful replica writes
 *     metrics.recordReplicaSuccess(); // Replica 1
 *     metrics.recordReplicaSuccess(); // Replica 2
 * } catch (QuorumException e) {
 *     metrics.recordAppendFailure();
 *     metrics.recordReplicaError(); // Failed replica
 * }
 * 
 * // Record replication lag
 * long lagMillis = replica.getLastAppendTime() - primary.getLastAppendTime();
 * metrics.recordReplicationLag(lagMillis);
 * 
 * // Record query
 * Timer.Sample querySample = Timer.start();
 * List<Event> events = replicatedHistory.query(criteria);
 * metrics.recordQuerySuccess(querySample.stop(metrics.getQueryLatencyTimer()));
 * }</pre>
 *
 * <h2>Metrics Exposed:</h2>
 * <ul>
 *   <li><strong>replication.append.starts</strong>: Counter - Append operations started</li>
 *   <li><strong>replication.append.successes</strong>: Counter - Successful appends</li>
 *   <li><strong>replication.append.failures</strong>: Counter - Failed appends</li>
 *   <li><strong>replication.replica.successes</strong>: Counter - Successful replica writes</li>
 *   <li><strong>replication.replica.failures</strong>: Counter - Failed replica writes</li>
 *   <li><strong>replication.queries</strong>: Counter - Query operations</li>
 *   <li><strong>replication.query.errors</strong>: Counter - Query errors</li>
 *   <li><strong>replication.append.latency</strong>: Timer - Append latency (P50/P90/P99/P999)</li>
 *   <li><strong>replication.query.latency</strong>: Timer - Query latency (P50/P90/P99)</li>
 *   <li><strong>replication.replica.count</strong>: Gauge - Number of replicas</li>
 *   <li><strong>replication.quorum.size</strong>: Gauge - Required quorum size</li>
 *   <li><strong>replication.last.append.time</strong>: Gauge - Last successful append timestamp</li>
 *   <li><strong>replication.replication.lag</strong>: DistributionSummary - Lag in milliseconds</li>
 * </ul>
 *
 * <h2>Quorum Semantics:</h2>
 * - **Quorum Size**: Minimum replicas required for successful write (typically ⌊N/2⌋ + 1)
 * - **Replica Count**: Total number of replicas (excluding primary)
 * - **Success Criteria**: Primary write + (quorum - 1) replica writes
 *
 * <h2>Thread Safety:</h2>
 * Thread-safe (Micrometer metrics + atomic gauges).
 *
 * <h2>Performance:</h2>
 * - Metric recording: O(1) per operation
 * - Memory overhead: ~500 bytes per instance
 *
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Tracks metrics for replicated event storage (quorum, replication lag, health)
 * @doc.layer observability
 * @doc.pattern Metrics Collector
 */
public class ReplicationMetrics {
    private static final String METRIC_PREFIX = "replication.";
    
    // Counters
    private final Counter appendStarts;
    private final Counter appendSuccesses;
    private final Counter appendFailures;
    private final Counter replicaSuccesses;
    private final Counter replicaFailures;
    private final Counter queries;
    private final Counter queryErrors;
    
    // Timers
    private final Timer appendLatency;
    private final Timer queryLatency;
    
    // Gauges
    private final AtomicInteger replicaCount;
    private final AtomicInteger quorumSize;
    private final AtomicLong lastAppendTime;
    
    // Distribution summaries
    private final DistributionSummary replicationLag;
    
    /**
     * Create a new ReplicationMetrics instance.
     * 
     * @param name          Name for metrics and tracing
     * @param registry      Metrics registry
     * @param replicaCount  Number of replicas
     * @param quorumSize    Required quorum size
     */
    public ReplicationMetrics(String name, MeterRegistry registry, int replicaCount, int quorumSize) {
        // Initialize counters
        this.appendStarts = Counter.builder(METRIC_PREFIX + "append.starts")
                .tag("name", name)
                .description("Number of append operations started")
                .register(registry);
                
        this.appendSuccesses = Counter.builder(METRIC_PREFIX + "append.successes")
                .tag("name", name)
                .description("Number of successful append operations")
                .register(registry);
                
        this.appendFailures = Counter.builder(METRIC_PREFIX + "append.failures")
                .tag("name", name)
                .description("Number of failed append operations")
                .register(registry);
                
        this.replicaSuccesses = Counter.builder(METRIC_PREFIX + "replica.successes")
                .tag("name", name)
                .description("Number of successful replica operations")
                .register(registry);
                
        this.replicaFailures = Counter.builder(METRIC_PREFIX + "replica.failures")
                .tag("name", name)
                .description("Number of failed replica operations")
                .register(registry);
                
        this.queries = Counter.builder(METRIC_PREFIX + "queries")
                .tag("name", name)
                .description("Number of query operations")
                .register(registry);
                
        this.queryErrors = Counter.builder(METRIC_PREFIX + "query.errors")
                .tag("name", name)
                .description("Number of query errors")
                .register(registry);
        
        // Initialize timers
        this.appendLatency = Timer.builder(METRIC_PREFIX + "append.latency")
                .tag("name", name)
                .description("Time taken for append operations")
                .publishPercentiles(0.5, 0.9, 0.99, 0.999)
                .register(registry);
                
        this.queryLatency = Timer.builder(METRIC_PREFIX + "query.latency")
                .tag("name", name)
                .description("Time taken for query operations")
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);
        
        // Initialize gauges
        this.replicaCount = registry.gauge(METRIC_PREFIX + "replica.count", 
                Tags.of("name", name), 
                new AtomicInteger(replicaCount));
                
        this.quorumSize = registry.gauge(METRIC_PREFIX + "quorum.size", 
                Tags.of("name", name), 
                new AtomicInteger(quorumSize));
                
        this.lastAppendTime = registry.gauge(METRIC_PREFIX + "last.append.time", 
                Tags.of("name", name), 
                new AtomicLong(System.currentTimeMillis()));
        
        // Initialize distribution summaries
        this.replicationLag = DistributionSummary.builder(METRIC_PREFIX + "replication.lag")
                .tag("name", name)
                .description("Replication lag in milliseconds")
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);
    }
    
    /**
     * Record the start of an append operation.
     */
    public void recordAppendStart() {
        appendStarts.increment();
    }
    
    /**
     * Record a successful append operation.
     */
    public void recordAppendSuccess() {
        appendSuccesses.increment();
        lastAppendTime.set(System.currentTimeMillis());
    }
    
    /**
     * Record a failed append operation.
     */
    public void recordAppendFailure() {
        appendFailures.increment();
    }
    
    /**
     * Record a successful replica operation.
     */
    public void recordReplicaSuccess() {
        replicaSuccesses.increment();
    }
    
    /**
     * Record a failed replica operation.
     */
    public void recordReplicaError() {
        replicaFailures.increment();
    }
    
    /**
     * Record a query operation.
     */
    public void recordQuery() {
        queries.increment();
    }
    
    /**
     * Record the start of a query operation.
     */
    public void recordQueryStart() {
        queries.increment();
    }
    
    /**
     * Record a query error.
     */
    public void recordQueryError() {
        queryErrors.increment();
    }
    
    /**
     * Record a successful query operation with duration.
     * 
     * @param durationNanos The duration in nanoseconds
     */
    public void recordQuerySuccess(long durationNanos) {
        queries.increment();
        queryLatency.record(java.time.Duration.ofNanos(durationNanos));
    }
    
    /**
     * Record a failed query operation.
     */
    public void recordQueryFailure() {
        queryErrors.increment();
    }
    
    /**
     * Record a successful append operation with duration.
     * 
     * @param durationNanos The duration in nanoseconds
     */
    public void recordAppendSuccess(long durationNanos) {
        appendSuccesses.increment();
        lastAppendTime.set(System.currentTimeMillis());
        appendLatency.record(java.time.Duration.ofNanos(durationNanos));
    }
    
    /**
     * Record replication lag.
     * 
     * @param lagMillis The replication lag in milliseconds
     */
    public void recordReplicationLag(long lagMillis) {
        replicationLag.record(lagMillis);
    }
    
    /**
     * Get the timer for append operations.
     * 
     * @return The append latency timer
     */
    public Timer getAppendLatencyTimer() {
        return appendLatency;
    }
    
    /**
     * Get the timer for query operations.
     * 
     * @return The query latency timer
     */
    public Timer getQueryLatencyTimer() {
        return queryLatency;
    }
}
