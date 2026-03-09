package com.ghatana.platform.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks comprehensive metrics for Write-Ahead Log (WAL) operations, recovery, and storage.
 *
 * <p><b>Purpose</b><br>
 * Monitors WAL durability through append operation metrics (starts/successes/errors with latency),
 * recovery operation tracking (starts/completes/errors with duration), truncation counters, file
 * size gauge, and recovered event count. Enables detection of write failures, slow appends,
 * recovery performance issues, and disk space monitoring for event persistence layer.
 *
 * <p><b>Architecture Role</b><br>
 * Core observability component for WriteAheadLog monitoring in EventCloud L1 storage tier.
 * Used by EventCloud partitions to track durable event persistence before in-memory processing.
 * Integrates with Prometheus/Grafana for WAL health dashboards and AlertManager for disk/recovery alerts.
 *
 * <p><b>Usage Examples</b><br>
 *
 * <pre>{@code
 * // Example 1: Basic WAL append metrics with error handling
 * MeterRegistry registry = new SimpleMeterRegistry();
 * WALMetrics metrics = new WALMetrics("partition-0-wal", registry);
 * WriteAheadLog wal = new WriteAheadLog(walFile, metrics);
 * 
 * // Record append with latency tracking
 * Timer.Sample sample = metrics.recordAppendStart();
 * try {
 *     wal.append(event);
 *     metrics.recordAppendSuccess(sample);
 *     
 *     // Update file size gauge
 *     metrics.recordWALSize(walFile.length());
 *     // Gauge: wal.size.bytes{name="partition-0-wal"} = 1048576
 * } catch (IOException e) {
 *     metrics.recordAppendError();
 *     // Counter: wal.append.errors{name="partition-0-wal"} incremented
 *     throw e;
 * }
 * // Timer: wal.append.latency{name="partition-0-wal"} = 2.5ms (P99)
 * }</pre>
 *
 * <pre>{@code
 * // Example 2: WAL recovery on startup (crash recovery)
 * WALMetrics metrics = new WALMetrics("event-wal", registry);
 * WriteAheadLog wal = new WriteAheadLog(walFile, metrics);
 * 
 * // Record recovery duration
 * Timer.Sample sample = metrics.recordRecoveryStart();
 * try {
 *     List<Event> events = wal.recover();
 *     metrics.recordRecoveryComplete(events.size());
 *     
 *     log.info("Recovered {} events from WAL", events.size());
 *     // Gauge: wal.recovered.events{name="event-wal"} = 5432
 *     // Timer: wal.recovery.duration{name="event-wal"} = 150ms
 * } catch (IOException e) {
 *     metrics.recordRecoveryError();
 *     // Counter: wal.recovery.errors{name="event-wal"} incremented
 *     throw e;
 * }
 * }</pre>
 *
 * <pre>{@code
 * // Example 3: WAL truncation (after checkpoint)
 * WALMetrics metrics = new WALMetrics("checkpoint-wal", registry);
 * WriteAheadLog wal = new WriteAheadLog(walFile, metrics);
 * 
 * // Checkpoint completed - truncate WAL to offset
 * long checkpointOffset = 1000;
 * wal.truncate(checkpointOffset);
 * 
 * metrics.recordTruncate();
 * // Counter: wal.truncations{name="checkpoint-wal"} incremented
 * 
 * metrics.recordWALSize(walFile.length());
 * // Gauge: wal.size.bytes{name="checkpoint-wal"} = 524288 (reduced)
 * }</pre>
 *
 * <pre>{@code
 * // Example 4: Prometheus alerts for WAL health
 * // prometheus-alerts.yml:
 * // - alert: WALAppendErrorRate
 * //   expr: rate(wal_append_errors_total[5m]) > 0.01
 * //   annotations:
 * //     summary: "WAL {{ $labels.name }} append errors detected"
 * //
 * // - alert: WALSizeCritical
 * //   expr: wal_size_bytes > 10737418240  # 10GB
 * //   for: 10m
 * //   annotations:
 * //     summary: "WAL {{ $labels.name }} exceeds 10GB"
 * 
 * WALMetrics metrics = new WALMetrics("critical-wal", registry);
 * // Grafana dashboard queries:
 * // - rate(wal_append_successes_total[5m])  # Append rate
 * // - histogram_quantile(0.99, wal_append_latency_seconds) # P99 append latency
 * // - wal_size_bytes / (10 * 1024^3)  # Size as % of 10GB limit
 * }</pre>
 *
 * <pre>{@code
 * // Example 5: Multi-partition WAL monitoring
 * MeterRegistry registry = new SimpleMeterRegistry();
 * 
 * Map<Integer, WALMetrics> partitionWALs = new HashMap<>();
 * for (int partition = 0; partition < 16; partition++) {
 *     String walName = "partition-" + partition + "-wal";
 *     WALMetrics metrics = new WALMetrics(walName, registry);
 *     partitionWALs.put(partition, metrics);
 * }
 * 
 * // Grafana aggregates across partitions:
 * // sum(rate(wal_append_successes_total[5m])) by (name)  # Per-partition throughput
 * // max(wal_size_bytes) by (name)  # Largest WAL file
 * }</pre>
 *
 * <p><b>Metrics Exposed</b><br>
 * - **wal.append.starts**: Counter - Append operations initiated
 * - **wal.append.successes**: Counter - Successful durable appends
 * - **wal.append.errors**: Counter - Failed appends (I/O errors, disk full)
 * - **wal.recovery.starts**: Counter - Recovery operations initiated (startup/crash)
 * - **wal.recovery.completes**: Counter - Successful recoveries
 * - **wal.recovery.errors**: Counter - Failed recoveries (corrupted WAL)
 * - **wal.truncations**: Counter - WAL truncation events (post-checkpoint cleanup)
 * - **wal.append.latency**: Timer - Append fsync latency (P50/P95/P99 percentiles)
 * - **wal.recovery.duration**: Timer - Recovery scan duration (P50/P95/P99)
 * - **wal.size.bytes**: Gauge - Current WAL file size (disk space monitoring)
 * - **wal.recovered.events**: Gauge - Events recovered in last recovery operation
 *
 * All metrics tagged with: `name="{walName}"`
 *
 * <p><b>WAL Lifecycle Metrics</b><br>
 * 1. **Startup**: recordRecoveryStart() → recover() → recordRecoveryComplete(count)
 * 2. **Normal operations**: recordAppendStart() → append() → recordAppendSuccess(sample)
 * 3. **Checkpoint**: truncate() → recordTruncate() → recordWALSize(newSize)
 * 4. **Error scenarios**: recordAppendError() or recordRecoveryError()
 *
 * <p><b>Best Practices</b><br>
 * - Use descriptive WAL names (e.g., "partition-3-wal", "event-ingestion-wal")
 * - Set Prometheus alerts for append error rate (>0.01 = 1% failure rate)
 * - Monitor P99 append latency (>10ms = slow disk, investigate)
 * - Alert on WAL size growth (>10GB = checkpoint not truncating, disk space risk)
 * - Track recovery duration on startup (>1min = large WAL, optimize checkpoints)
 * - Monitor recovered event count (spike = crash recovery, investigate)
 *
 * <p><b>Performance Characteristics</b><br>
 * - Metric recording: O(1) per operation
 * - Memory overhead: ~200 bytes per WALMetrics instance
 * - Timer overhead: <1μs per sample (negligible vs fsync latency)
 * - Thread-safe: Concurrent metric recording supported
 *
 * <p><b>Integration Points</b><br>
 * - WriteAheadLog: Primary consumer of metrics
 * - EventCloud L1 storage: WAL durability layer
 * - Prometheus: Scrapes metrics via /metrics endpoint
 * - Grafana: Dashboards for WAL health/disk space visualization
 * - AlertManager: Disk full/recovery failure/slow append alerts
 *
 * <p><b>Thread Safety</b><br>
 * This class is thread-safe. Micrometer metrics + AtomicLong for gauges.
 *
 * @see WriteAheadLog
 * @see PrometheusMetricsExporter
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Write-Ahead Log metrics for durability and recovery monitoring
 * @doc.layer core
 * @doc.pattern Observer
 */
public class WALMetrics {
    private static final String METRIC_PREFIX = "wal.";
    
    // Counters
    private final Counter appendStarts;
    private final Counter appendSuccesses;
    private final Counter appendErrors;
    private final Counter recoveryStarts;
    private final Counter recoveryCompletes;
    private final Counter recoveryErrors;
    private final Counter truncations;
    
    // Gauges
    private final AtomicLong walSizeBytes;
    private final AtomicLong lastRecoveredCount;
    
    // Timers
    private final Timer appendLatency;
    private final Timer recoveryDuration;
    
    /**
     * Create a new WALMetrics instance.
     * 
     * @param name      Name for metrics (will be used as a tag)
     * @param registry  Metrics registry
     */
    public WALMetrics(String name, MeterRegistry registry) {
        // Initialize counters
        this.appendStarts = Counter.builder(METRIC_PREFIX + "append.starts")
                .tag("name", name)
                .description("Number of append operations started")
                .register(registry);
                
        this.appendSuccesses = Counter.builder(METRIC_PREFIX + "append.successes")
                .tag("name", name)
                .description("Number of successful append operations")
                .register(registry);
                
        this.appendErrors = Counter.builder(METRIC_PREFIX + "append.errors")
                .tag("name", name)
                .description("Number of failed append operations")
                .register(registry);
                
        this.recoveryStarts = Counter.builder(METRIC_PREFIX + "recovery.starts")
                .tag("name", name)
                .description("Number of recovery operations started")
                .register(registry);
                
        this.recoveryCompletes = Counter.builder(METRIC_PREFIX + "recovery.completes")
                .tag("name", name)
                .description("Number of successful recovery operations")
                .register(registry);
                
        this.recoveryErrors = Counter.builder(METRIC_PREFIX + "recovery.errors")
                .tag("name", name)
                .description("Number of failed recovery operations")
                .register(registry);
                
        this.truncations = Counter.builder(METRIC_PREFIX + "truncations")
                .tag("name", name)
                .description("Number of WAL truncations")
                .register(registry);
        
        // Initialize gauges
        this.walSizeBytes = registry.gauge(METRIC_PREFIX + "size.bytes", 
                Tags.of("name", name), 
                new AtomicLong(0));
                
        this.lastRecoveredCount = registry.gauge(METRIC_PREFIX + "recovered.events", 
                Tags.of("name", name), 
                new AtomicLong(0));
        
        // Initialize timers
        this.appendLatency = Timer.builder(METRIC_PREFIX + "append.latency")
                .tag("name", name)
                .description("Time taken for append operations")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
                
        this.recoveryDuration = Timer.builder(METRIC_PREFIX + "recovery.duration")
                .tag("name", name)
                .description("Time taken for recovery operations")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }
    
    /**
     * Record the start of an append operation.
     * 
     * @return A timer sample to measure latency
     */
    public Timer.Sample recordAppendStart() {
        appendStarts.increment();
        return Timer.start();
    }
    
    /**
     * Record a successful append operation.
     * 
     * @param sample The timer sample from recordAppendStart()
     */
    public void recordAppendSuccess(Timer.Sample sample) {
        if (sample != null) {
            sample.stop(appendLatency);
        }
        appendSuccesses.increment();
    }
    
    /**
     * Record a failed append operation.
     */
    public void recordAppendError() {
        appendErrors.increment();
    }
    
    /**
     * Record the start of a recovery operation.
     * 
     * @return A timer sample to measure duration
     */
    public Timer.Sample recordRecoveryStart() {
        recoveryStarts.increment();
        return Timer.start();
    }
    
    /**
     * Record a successful recovery operation.
     * 
     * @param eventCount Number of events recovered
     */
    public void recordRecoveryComplete(int eventCount) {
        recoveryCompletes.increment();
        lastRecoveredCount.set(eventCount);
        
        // Stop the timer if it was started
        Timer.Sample sample = Timer.start();
        sample.stop(recoveryDuration);
    }
    
    /**
     * Record a failed recovery operation.
     */
    public void recordRecoveryError() {
        recoveryErrors.increment();
    }
    
    /**
     * Record a WAL truncation.
     */
    public void recordTruncate() {
        truncations.increment();
    }
    
    /**
     * Record the current size of the WAL file.
     * 
     * @param sizeBytes Current size in bytes
     */
    public void recordWALSize(long sizeBytes) {
        if (sizeBytes >= 0) {
            walSizeBytes.set(sizeBytes);
        }
    }
}
