package com.ghatana.datacloud.application.observability;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.entity.observability.Span;
import com.ghatana.datacloud.entity.observability.TraceExporter;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Application service orchestrating trace export operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Coordinates span collection, batching, export, and error handling. Acts as
 * facade over TraceExporter with metrics and resilience.
 *
 * <p>
 * <b>Responsibilities</b><br>
 * - Collect spans from tracing service - Batch spans by trace ID or time window
 * - Trigger export via TraceExporter - Handle export failures (retry, dead
 * letter queue) - Track export metrics and health - Log export events for audit
 * trail
 *
 * <p>
 * <b>Export Flow</b><br>
 * 1. Collect spans from tracingService (in-memory buffer) 2. Group spans by
 * trace ID (spans from same trace) 3. For each trace batch: a. Call
 * traceExporter.exportSpans() b. If success: clear spans, update metrics c. If
 * failure: move to dead letter queue, log error 4. Track export latency,
 * success rate, error rate 5. Health check: verify exporter operational
 *
 * <p>
 * <b>Dead Letter Queue (DLQ)</b><br>
 * Failed spans moved to DLQ for later retry/analysis: - Exporter unavailable
 * (connection timeout) - Serialization errors - Malformed span data
 *
 * DLQ implementation (TODO): - Store in local file or database - Periodic retry
 * attempts - Operator dashboard for inspection
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * TraceExportService exportService = new TraceExportService(
 *     traceExporter,
 *     metrics
 * );
 *
 * Promise<Void> export = exportService.exportCollectedSpans(tenantId);
 * export.whenResult(v -> logger.info("Export complete"));
 * }</pre>
 *
 * <p>
 * <b>Metrics</b><br>
 * - "tracing.export.requested" (counter, by tenant/span count) -
 * "tracing.export.successful" (counter, by tenant) - "tracing.export.failed"
 * (counter, by tenant/error type) - "tracing.export.duration" (timer, by
 * tenant) - "tracing.export.dlq.size" (gauge, current DLQ size)
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe. Uses synchronized collections for span/DLQ storage.
 *
 * @see com.ghatana.datacloud.entity.observability.TraceExporter
 * @see com.ghatana.datacloud.application.observability.TracingService
 * @doc.type class
 * @doc.purpose Orchestrates trace export with metrics and error handling
 * @doc.layer product
 * @doc.pattern Service
 */
public final class TraceExportService {

    private static final Logger logger = LoggerFactory.getLogger(TraceExportService.class);

    private final TraceExporter traceExporter;
    private final MetricsCollector metrics;
    private final List<Span> deadLetterQueue;
    private final int maxDlqSize;

    /** Default maximum DLQ size if not specified. */
    public static final int DEFAULT_MAX_DLQ_SIZE = 10_000;

    /**
     * Create trace export service with default DLQ size (10,000).
     *
     * @param traceExporter exporter for batch spans
     * @param metrics metrics collector
     * @throws NullPointerException if exporter or metrics null
     */
    public TraceExportService(TraceExporter traceExporter, MetricsCollector metrics) {
        this(traceExporter, metrics, DEFAULT_MAX_DLQ_SIZE);
    }

    /**
     * Create trace export service with configurable DLQ size.
     *
     * @param traceExporter exporter for batch spans
     * @param metrics metrics collector
     * @param maxDlqSize maximum number of spans to retain in the dead-letter queue
     * @throws NullPointerException if exporter or metrics null
     * @throws IllegalArgumentException if maxDlqSize &lt;= 0
     */
    public TraceExportService(TraceExporter traceExporter, MetricsCollector metrics, int maxDlqSize) {
        this.traceExporter = Objects.requireNonNull(traceExporter, "traceExporter required");
        this.metrics = Objects.requireNonNull(metrics, "metrics required");
        if (maxDlqSize <= 0) {
            throw new IllegalArgumentException("maxDlqSize must be > 0, got: " + maxDlqSize);
        }
        this.maxDlqSize = maxDlqSize;
        this.deadLetterQueue = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * Export collected spans for tenant.
     *
     * <p>
     * Retrieves all spans collected for tenant and exports via traceExporter.
     * On failure, moves spans to dead letter queue.
     *
     * @param tenantId tenant identifier
     * @param spans spans to export
     * @return Promise resolving when export completes
     * @throws NullPointerException if tenantId or spans null
     *
     * <p>
     * <b>Async Guarantees</b><br>
     * Promise resolves when export completes (success or failure). Failures
     * logged but not rethrown (service continues operating).
     */
    public Promise<Void> exportSpans(String tenantId, List<Span> spans) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(spans, "spans required");

        if (spans.isEmpty()) {
            return Promise.complete();
        }

        long startTimeMs = System.currentTimeMillis();

        return traceExporter.exportSpans(spans)
                .whenResult(result -> {
                    long durationMs = System.currentTimeMillis() - startTimeMs;

                    if (result.isSuccessful()) {
                        metrics.incrementCounter(
                                "tracing.export.successful",
                                "tenant", tenantId,
                                "span_count", String.valueOf(result.getExportedCount())
                        );
                        logger.info(
                                "Exported {} spans for tenant {} in {}ms",
                                result.getExportedCount(),
                                tenantId,
                                durationMs
                        );
                    } else {
                        metrics.incrementCounter(
                                "tracing.export.failed",
                                "tenant", tenantId,
                                "error_count", String.valueOf(result.getErrorCount())
                        );

                        // Move failed spans to dead letter queue
                        addToDeadLetterQueue(spans);

                        logger.warn(
                                "Export failed for tenant {}: {} errors",
                                tenantId,
                                result.getErrorCount()
                        );
                        for (String error : result.getErrors()) {
                            logger.debug("Export error: {}", error);
                        }
                    }

                    // Record export duration
                    metrics.recordTimer(
                            "tracing.export.duration",
                            durationMs,
                            "tenant", tenantId,
                            "successful", String.valueOf(result.isSuccessful())
                    );
                })
                .whenException(ex -> {
                    long durationMs = System.currentTimeMillis() - startTimeMs;

                    metrics.incrementCounter(
                            "tracing.export.failed",
                            "tenant", tenantId,
                            "error_type", ex.getClass().getSimpleName()
                    );

                    // Move spans to DLQ on exception
                    addToDeadLetterQueue(spans);

                    logger.error(
                            "Export exception for tenant {} after {}ms",
                            tenantId,
                            durationMs,
                            ex
                    );
                })
                .map(result -> null);
    }

    /**
     * Export spans grouped by trace ID.
     *
     * <p>
     * Groups spans into logical traces (all spans with same traceId) and
     * exports each trace as a batch.
     *
     * @param tenantId tenant identifier
     * @param spans spans to export
     * @return Promise resolving when all exports complete
     */
    public Promise<Void> exportSpansByTrace(String tenantId, List<Span> spans) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(spans, "spans required");

        if (spans.isEmpty()) {
            return Promise.complete();
        }

        // Group spans by trace ID
        Map<String, List<Span>> traceGroups = spans.stream()
                .collect(Collectors.groupingBy(Span::getTraceId));

        // Export each trace
        List<Promise<Void>> exports = traceGroups.values().stream()
                .map(traceBatch -> exportSpans(tenantId, traceBatch))
                .toList();

        return Promises.all(exports).map(list -> null);
    }

    /**
     * Add spans to dead letter queue.
     *
     * @param spans spans to queue
     */
    private void addToDeadLetterQueue(List<Span> spans) {
        if (deadLetterQueue.size() >= maxDlqSize) {
            // Remove oldest span if DLQ full
            deadLetterQueue.remove(0);
        }
        deadLetterQueue.addAll(spans);

        // Record DLQ size as a counter metric (incrementing by current size)
        metrics.incrementCounter(
                "tracing.export.dlq.added",
                "count", String.valueOf(spans.size())
        );
    }

    /**
     * Get spans from dead letter queue.
     *
     * @return list of failed spans
     */
    public List<Span> getDeadLetterQueueSpans() {
        return new ArrayList<>(deadLetterQueue);
    }

    /**
     * Clear dead letter queue (e.g., after manual review).
     */
    public void clearDeadLetterQueue() {
        int size = deadLetterQueue.size();
        deadLetterQueue.clear();
        logger.info("Cleared {} spans from dead letter queue", size);
    }

    /**
     * Get export service health.
     *
     * @return Promise resolving to true if service is healthy
     */
    public Promise<Boolean> isHealthy() {
        return traceExporter.isHealthy();
    }

    /**
     * Get service statistics.
     *
     * @return statistics snapshot
     */
    public ExportServiceStatistics getStatistics() {
        return new ExportServiceStatistics(
                deadLetterQueue.size(),
                traceExporter.getConfig()
        );
    }

    /**
     * Immutable service statistics.
     */
    public static final class ExportServiceStatistics {

        private final int dlqSize;
        private final TraceExporter.ExportConfig exportConfig;

        public ExportServiceStatistics(
                int dlqSize,
                TraceExporter.ExportConfig exportConfig
        ) {
            this.dlqSize = dlqSize;
            this.exportConfig = Objects.requireNonNull(exportConfig, "exportConfig required");
        }

        public int getDlqSize() {
            return dlqSize;
        }

        public TraceExporter.ExportConfig getExportConfig() {
            return exportConfig;
        }

        @Override
        public String toString() {
            return "ExportServiceStatistics{"
                    + "dlqSize=" + dlqSize
                    + ", exportConfig=" + exportConfig
                    + '}';
        }
    }

    @Override
    public String toString() {
        return "TraceExportService{"
                + "exporter=" + traceExporter.getConfig()
                + ", statistics=" + getStatistics()
                + '}';
    }
}
