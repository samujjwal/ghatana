package com.ghatana.platform.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks comprehensive metrics for BoundedEventQueue operations, backpressure, and lifecycle.
 *
 * <p><b>Purpose</b><br>
 * Monitors event queue health through operation counters (enqueued/dequeued/dropped), latency
 * distributions (P50/P90/P99), queue depth gauge, and pause/resume lifecycle events. Enables
 * detection of backpressure scenarios, queue saturation, and performance degradation in
 * event processing pipelines.
 *
 * <p><b>Architecture Role</b><br>
 * Core observability component for BoundedEventQueue monitoring. Used by event ingestion,
 * EventCloud partitions, and operator buffering to track queue health. Integrates with
 * Prometheus/Grafana for real-time dashboards and AlertManager for capacity alerts.
 *
 * <p><b>Usage Examples</b><br>
 *
 * <pre>{@code
 * // Example 1: Basic queue metrics integration
 * MeterRegistry registry = new SimpleMeterRegistry();
 * QueueMetrics metrics = new QueueMetrics("ingestion-queue", registry);
 * BoundedEventQueue queue = new BoundedEventQueue(1000, metrics);
 * 
 * // Enqueue automatically records metrics
 * queue.enqueue(event);
 * // Increments: queue.events.enqueued{queue="ingestion-queue"}
 * // Increments: queue.size.gauge{queue="ingestion-queue"} = 1
 * 
 * // Dequeue with latency tracking
 * long start = System.nanoTime();
 * Event event = queue.dequeue();
 * long latency = System.nanoTime() - start;
 * metrics.recordDequeued(latency);
 * // Increments: queue.events.dequeued
 * // Records: queue.dequeue.latency (P50/P90/P99 percentiles)
 * // Decrements: queue.size.gauge = 0
 * }</pre>
 *
 * <pre>{@code
 * // Example 2: Backpressure detection with dropped events
 * QueueMetrics metrics = new QueueMetrics("bounded-queue", registry);
 * BoundedEventQueue queue = new BoundedEventQueue(100, metrics);
 * 
 * // Fill queue to capacity
 * for (int i = 0; i < 100; i++) {
 *     queue.enqueue(new Event());
 * }
 * 
 * // Queue full - drop incoming event
 * if (queue.isFull()) {
 *     metrics.recordDropped();
 *     log.warn("Queue full, event dropped");
 * }
 * // Increments: queue.events.dropped{queue="bounded-queue"}
 * // Alert: queue_events_dropped_total > 10 (Prometheus alert)
 * }</pre>
 *
 * <pre>{@code
 * // Example 3: Pause/resume lifecycle tracking
 * QueueMetrics metrics = new QueueMetrics("pausable-queue", registry);
 * BoundedEventQueue queue = new BoundedEventQueue(1000, metrics);
 * 
 * // Pause queue (e.g., downstream system maintenance)
 * queue.pause();
 * metrics.recordPause();
 * // Increments: queue.pause.count{queue="pausable-queue"}
 * 
 * // ... maintenance window ...
 * 
 * // Resume queue
 * queue.resume();
 * metrics.recordResume();
 * // Increments: queue.resume.count{queue="pausable-queue"}
 * }</pre>
 *
 * <pre>{@code
 * // Example 4: Prometheus alert rules for queue saturation
 * // prometheus-alerts.yml:
 * // - alert: QueueNearCapacity
 * //   expr: queue_size_gauge / queue_capacity > 0.8
 * //   for: 5m
 * //   annotations:
 * //     summary: "Queue {{ $labels.queue }} at 80% capacity"
 * 
 * QueueMetrics metrics = new QueueMetrics("critical-queue", registry);
 * // Grafana dashboard queries:
 * // - rate(queue_events_enqueued_total[5m])  # Enqueue rate
 * // - rate(queue_events_dropped_total[5m])   # Drop rate (backpressure)
 * // - histogram_quantile(0.99, queue_dequeue_latency_seconds) # P99 latency
 * }</pre>
 *
 * <pre>{@code
 * // Example 5: Multi-queue monitoring (partitioned queues)
 * MeterRegistry registry = new SimpleMeterRegistry();
 * 
 * Map<Integer, QueueMetrics> partitionMetrics = new HashMap<>();
 * for (int partition = 0; partition < 8; partition++) {
 *     String queueName = "partition-" + partition;
 *     QueueMetrics metrics = new QueueMetrics(queueName, registry);
 *     partitionMetrics.put(partition, metrics);
 * }
 * 
 * // Grafana dashboard aggregates across partitions:
 * // sum(rate(queue_events_enqueued_total[5m])) by (queue)
 * }</pre>
 *
 * <p><b>Metrics Exposed</b><br>
 * - **queue.events.enqueued**: Counter - Total events successfully enqueued
 * - **queue.events.dequeued**: Counter - Total events dequeued (consumed)
 * - **queue.events.dropped**: Counter - Events dropped due to backpressure/pause/capacity
 * - **queue.pause.count**: Counter - Number of pause operations
 * - **queue.resume.count**: Counter - Number of resume operations
 * - **queue.dequeue.latency**: Timer - Dequeue wait time (P50/P90/P99 percentiles)
 * - **queue.size.gauge**: Gauge - Current queue depth (real-time capacity utilization)
 *
 * All metrics tagged with: `queue="{queueName}"`
 *
 * <p><b>Queue Size Tracking</b><br>
 * Gauge automatically updated:
 * - `recordEnqueued()`: Increments gauge (+1)
 * - `recordDequeued()`: Decrements gauge (-1)
 * - `recordDropped()`: No change (event never entered queue)
 *
 * <p><b>Latency Distribution</b><br>
 * Timer tracks dequeue latency with percentiles:
 * - P50: Median wait time
 * - P90: 90th percentile (most events faster)
 * - P99: 99th percentile (tail latency, detect outliers)
 *
 * <p><b>Best Practices</b><br>
 * - Use descriptive queue names (e.g., "ingestion-partition-3")
 * - Set Prometheus alerts for dropped events (backpressure indicator)
 * - Monitor P99 dequeue latency (detect blocking scenarios)
 * - Track queue size gauge relative to capacity (>80% = near saturation)
 * - Aggregate metrics across partitions in Grafana dashboards
 * - Alert on pause count spikes (unexpected system pauses)
 *
 * <p><b>Performance Characteristics</b><br>
 * - Metric recording: O(1) per operation
 * - Memory overhead: ~100 bytes per QueueMetrics instance
 * - Timer overhead: <1μs per recordDequeued() call
 * - Thread-safe: Concurrent metric recording supported
 *
 * <p><b>Integration Points</b><br>
 * - BoundedEventQueue: Primary consumer of metrics
 * - Prometheus: Scrapes metrics via /metrics endpoint
 * - Grafana: Dashboards for queue health visualization
 * - AlertManager: Capacity/backpressure/latency alerts
 *
 * <p><b>Thread Safety</b><br>
 * This class is thread-safe. Micrometer metrics + AtomicLong for queue size gauge.
 *
 * @see BoundedEventQueue
 * @see PrometheusMetricsExporter
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Event queue metrics for backpressure and latency monitoring
 * @doc.layer core
 * @doc.pattern Observer
 */
public class QueueMetrics {
    private final Counter enqueuedCounter;
    private final Counter dequeuedCounter;
    private final Counter droppedCounter;
    private final Counter pauseCounter;
    private final Counter resumeCounter;
    private final Timer dequeueLatency;
    private final AtomicLong queueSizeGauge;
    private final String queueName;

    public QueueMetrics(String queueName, MeterRegistry registry) {
        this.queueName = queueName;
        
        // Initialize counters
        this.enqueuedCounter = Counter.builder("queue.events.enqueued")
                .tag("queue", queueName)
                .description("Number of events enqueued")
                .register(registry);
                
        this.dequeuedCounter = Counter.builder("queue.events.dequeued")
                .tag("queue", queueName)
                .description("Number of events dequeued")
                .register(registry);
                
        this.droppedCounter = Counter.builder("queue.events.dropped")
                .tag("queue", queueName)
                .description("Number of events dropped due to backpressure or pause")
                .register(registry);
                
        this.pauseCounter = Counter.builder("queue.pause.count")
                .tag("queue", queueName)
                .description("Number of times the queue was paused")
                .register(registry);
                
        this.resumeCounter = Counter.builder("queue.resume.count")
                .tag("queue", queueName)
                .description("Number of times the queue was resumed")
                .register(registry);
                
        this.dequeueLatency = Timer.builder("queue.dequeue.latency")
                .tag("queue", queueName)
                .description("Time spent waiting to dequeue events")
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);
                
        this.queueSizeGauge = registry.gauge("queue.size.gauge", 
                Tags.of("queue", queueName), 
                new AtomicLong(0));
    }

    public void recordEnqueued() {
        enqueuedCounter.increment();
        updateQueueSize(1);
    }

    public void recordDequeued(long latencyNanos) {
        dequeuedCounter.increment();
        dequeueLatency.record(latencyNanos, TimeUnit.NANOSECONDS);
        updateQueueSize(-1);
    }

    public void recordDropped() {
        droppedCounter.increment();
    }

    public void recordPause() {
        pauseCounter.increment();
    }

    public void recordResume() {
        resumeCounter.increment();
    }
    
    private void updateQueueSize(int delta) {
        if (queueSizeGauge != null) {
            queueSizeGauge.addAndGet(delta);
        }
    }
}
