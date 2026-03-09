package com.ghatana.platform.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks metrics for a PartitionedEventHistory instance.
 * Provides per-partition and aggregate metrics for append/query operations.
 *
 * <p>Collects comprehensive metrics for partitioned event storage:
 * <ul>
 *   <li><strong>Aggregate Metrics</strong>: Total appends, queries, errors across all partitions</li>
 *   <li><strong>Partition Metrics</strong>: Per-partition append counters</li>
 *   <li><strong>Latency Metrics</strong>: P50, P90, P99, P999 percentiles for operations</li>
 *   <li><strong>Error Tracking</strong>: Errors by operation type (append, query)</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * MeterRegistry registry = new SimpleMeterRegistry();
 * PartitionMetrics metrics = new PartitionMetrics("event-history", registry, 16);
 * 
 * // Record append operation
 * Timer.Sample sample = metrics.startAppendTimer();
 * try {
 *     eventHistory.append(partition, event);
 *     metrics.recordAppend(partition);
 * } catch (Exception e) {
 *     metrics.recordError("append");
 * } finally {
 *     metrics.recordAppendDuration(sample);
 * }
 * 
 * // Record query operation
 * Timer.Sample querySample = metrics.startQueryTimer();
 * try {
 *     List<Event> events = eventHistory.query(criteria);
 *     metrics.recordQuery();
 * } catch (Exception e) {
 *     metrics.recordError("query");
 * } finally {
 *     metrics.recordQueryDuration(querySample);
 * }
 * }</pre>
 *
 * <h2>Metrics Exposed:</h2>
 * <ul>
 *   <li><strong>partitioned.history.appends</strong>: Counter - Total append operations</li>
 *   <li><strong>partitioned.history.queries</strong>: Counter - Total query operations</li>
 *   <li><strong>partitioned.history.errors</strong>: Counter - Total errors</li>
 *   <li><strong>partitioned.history.errors.by.operation</strong>: Counter - Errors tagged by operation</li>
 *   <li><strong>partitioned.history.append.latency</strong>: Timer - Append latency (P50/P90/P99/P999)</li>
 *   <li><strong>partitioned.history.query.latency</strong>: Timer - Query latency (P50/P90/P99/P999)</li>
 *   <li><strong>partitioned.history.partition.appends</strong>: Counter - Appends per partition</li>
 *   <li><strong>partitioned.history.partition.count</strong>: Gauge - Number of partitions</li>
 * </ul>
 *
 * <h2>Thread Safety:</h2>
 * Thread-safe (Micrometer metrics + ConcurrentHashMap for partition counters).
 *
 * <h2>Performance:</h2>
 * - Metric recording: O(1) per operation
 * - Partition counter lookup: O(1) via ConcurrentHashMap
 * - Timer samples: Negligible overhead (<1μs)
 *
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Tracks metrics for partitioned event storage (appends, queries, latency)
 * @doc.layer observability
 * @doc.pattern Metrics Collector
 */
public class PartitionMetrics {
    private static final String METRIC_PREFIX = "partitioned.history.";
    
    private final Counter appendCounter;
    private final Counter queryCounter;
    private final Counter errorCounter;
    private final Timer appendLatency;
    private final Timer queryLatency;
    private final ConcurrentHashMap<Integer, Counter> partitionAppendCounters;
    private final String name;
    private final int partitionCount;

    public PartitionMetrics(String name, MeterRegistry registry, int partitionCount) {
        this.name = name;
        this.partitionCount = partitionCount;
        
        // Initialize base metrics
        this.appendCounter = Counter.builder(METRIC_PREFIX + "appends")
                .tag("name", name)
                .description("Number of append operations")
                .register(registry);
                
        this.queryCounter = Counter.builder(METRIC_PREFIX + "queries")
                .tag("name", name)
                .description("Number of query operations")
                .register(registry);
                
        this.errorCounter = Counter.builder(METRIC_PREFIX + "errors")
                .tag("name", name)
                .description("Number of errors by operation type")
                .register(registry);
                
        this.appendLatency = Timer.builder(METRIC_PREFIX + "append.latency")
                .tag("name", name)
                .description("Time taken for append operations")
                .publishPercentiles(0.5, 0.9, 0.99, 0.999)
                .register(registry);
                
        this.queryLatency = Timer.builder(METRIC_PREFIX + "query.latency")
                .tag("name", name)
                .description("Time taken for query operations")
                .publishPercentiles(0.5, 0.9, 0.99, 0.999)
                .register(registry);
                
        // Initialize partition-specific metrics
        this.partitionAppendCounters = new ConcurrentHashMap<>();
        for (int i = 0; i < partitionCount; i++) {
            Counter counter = Counter.builder(METRIC_PREFIX + "partition.appends")
                    .tag("name", name)
                    .tag("partition", String.valueOf(i))
                    .description("Number of appends per partition")
                    .register(registry);
            partitionAppendCounters.put(i, counter);
        }
        
        // Register partition count gauge
        Gauge.builder(METRIC_PREFIX + "partition.count", () -> (double) partitionCount)
                .tag("name", name)
                .description("Number of partitions")
                .register(registry);
    }
    
    /**
     * Record an append operation to a specific partition.
     * 
     * @param partition The partition index
     */
    public void recordAppend(int partition) {
        appendCounter.increment();
        Counter partitionCounter = partitionAppendCounters.get(partition);
        if (partitionCounter != null) {
            partitionCounter.increment();
        }
    }
    
    /**
     * Record a query operation.
     */
    public void recordQuery() {
        queryCounter.increment();
    }
    
    /**
     * Record an error for a specific operation.
     * 
     * @param operation The operation that failed (e.g., "append", "query")
     */
    public void recordError(String operation) {
        errorCounter.increment();
        // Also record error by operation type
        Metrics.counter(METRIC_PREFIX + "errors.by.operation", 
                      "name", name,
                      "operation", operation)
               .increment();
    }
    
    /**
     * Time an append operation.
     * 
     * @return A timer sample that should be stopped when the operation completes
     */
    public Timer.Sample startAppendTimer() {
        return Timer.start();
    }
    
    /**
     * Record the duration of an append operation.
     * 
     * @param sample The timer sample to stop
     */
    public void recordAppendDuration(Timer.Sample sample) {
        if (sample != null) {
            sample.stop(appendLatency);
        }
    }
    
    /**
     * Time a query operation.
     * 
     * @return A timer sample that should be stopped when the operation completes
     */
    public Timer.Sample startQueryTimer() {
        return Timer.start();
    }
    
    /**
     * Record the duration of a query operation.
     * 
     * @param sample The timer sample to stop
     */
    public void recordQueryDuration(Timer.Sample sample) {
        if (sample != null) {
            sample.stop(queryLatency);
        }
    }
    
    /**
     * Get the name of the partitioned history these metrics are for.
     * 
     * @return The name of the partitioned history
     */
    public String getName() {
        return name;
    }
    
    /**
     * Get the number of partitions.
     * 
     * @return The number of partitions
     */
    public int getPartitionCount() {
        return partitionCount;
    }
}
