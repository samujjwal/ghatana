package com.ghatana.datacloud.entity.observability;

import io.activej.promise.Promise;
import java.util.List;
import java.util.Objects;

/**
 * Port interface for batch exporting traces to backend.
 *
 * <p><b>Purpose</b><br>
 * Defines contract for exporting collected traces to centralized backend
 * (e.g., Jaeger, Tempo, cloud tracing service).
 *
 * <p><b>Export Flow</b><br>
 * 1. Collect spans in local memory (TracingService)
 * 2. Batch spans by trace ID or time window
 * 3. Serialize spans to JSON/Protobuf (optional compression)
 * 4. Export batch via HTTP/gRPC to backend
 * 5. Handle retries and failures (dead letter queue)
 * 6. Track export metrics
 *
 * <p><b>Features</b><br>
 * - Async export via Promise<ExportResult>
 * - Batch compression (gzip)
 * - Retry strategy with exponential backoff
 * - Export result tracking (success/error counts)
 * - Multi-tenant isolation
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * TraceExporter exporter = batchTraceExporter;
 * List<Span> spans = tracingService.getRecordedSpans(tenantId);
 *
 * Promise<ExportResult> result = exporter.exportSpans(spans);
 * result.whenResult(exportResult -> {
 *     if (exportResult.isSuccessful()) {
 *         logger.info("Exported {} spans", exportResult.getExportedCount());
 *     } else {
 *         logger.warn("Export failed: {}", exportResult.getError());
 *     }
 * });
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe. Multiple threads may invoke export
 * concurrently for different tenants.
 *
 * <p><b>Performance</b><br>
 * Export should not block main tracing operations. Use async/Promise for
 * non-blocking export.
 *
 * @see com.ghatana.datacloud.entity.observability.Span
 * @doc.type interface
 * @doc.purpose Batch trace export contract
 * @doc.layer product
 * @doc.pattern Port
 */
public interface TraceExporter {

    /**
     * Export batch of spans to backend.
     *
     * <p>Serializes spans to JSON/Protobuf, applies optional compression,
     * and sends to backend. Handles retries and failures based on configuration.
     *
     * @param spans list of spans to export (should be same tenant)
     * @return Promise resolving to ExportResult with success/error counts
     * @throws NullPointerException if spans is null
     * @throws IllegalArgumentException if spans is empty or mixed tenants
     *
     * <p><b>Async Guarantees</b><br>
     * Promise resolves when export completes (success or exhausted retries).
     * Exceptions are caught and returned in ExportResult (not rethrown).
     */
    Promise<ExportResult> exportSpans(List<Span> spans);

    /**
     * Get exporter configuration.
     *
     * @return export configuration snapshot
     */
    ExportConfig getConfig();

    /**
     * Check health of exporter.
     *
     * @return Promise resolving to true if exporter is operational
     */
    Promise<Boolean> isHealthy();

    /**
     * Export result with success/error details.
     *
     * <p><b>Purpose</b><br>
     * Immutable result object containing:
     * - Export success flag
     * - Count of successfully exported spans
     * - Count of failed spans
     * - Error messages for failed spans
     * - Export duration
     *
     * <p>Always returns a result (never throws). Callers must check
     * isSuccessful() and handle failures via error list.
     */
    final class ExportResult {
        private final boolean successful;
        private final int exportedCount;
        private final int errorCount;
        private final List<String> errors;
        private final long durationMs;

        /**
         * Create export result.
         *
         * @param successful export success flag
         * @param exportedCount number of successfully exported spans
         * @param errorCount number of failed spans
         * @param errors list of error messages
         * @param durationMs export duration in milliseconds
         * @throws IllegalArgumentException if counts negative
         */
        public ExportResult(
            boolean successful,
            int exportedCount,
            int errorCount,
            List<String> errors,
            long durationMs
        ) {
            if (exportedCount < 0 || errorCount < 0 || durationMs < 0) {
                throw new IllegalArgumentException(
                    "Counts and duration must be non-negative"
                );
            }
            this.successful = successful;
            this.exportedCount = exportedCount;
            this.errorCount = errorCount;
            this.errors = Objects.requireNonNull(errors, "errors required");
            this.durationMs = durationMs;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public int getExportedCount() {
            return exportedCount;
        }

        public int getErrorCount() {
            return errorCount;
        }

        public List<String> getErrors() {
            return errors;
        }

        public long getDurationMs() {
            return durationMs;
        }

        @Override
        public String toString() {
            return "ExportResult{" +
                "successful=" + successful +
                ", exported=" + exportedCount +
                ", errors=" + errorCount +
                ", duration=" + durationMs + "ms" +
                '}';
        }
    }

    /**
     * Export configuration snapshot.
     */
    final class ExportConfig {
        private final String backendUrl;
        private final int maxBatchSize;
        private final long batchTimeoutMs;
        private final boolean compressionEnabled;
        private final int maxRetries;
        private final long retryBackoffMs;

        /**
         * Create export configuration.
         *
         * @param backendUrl backend URL
         * @param maxBatchSize max spans per batch
         * @param batchTimeoutMs timeout for batch accumulation
         * @param compressionEnabled enable gzip compression
         * @param maxRetries maximum retry attempts
         * @param retryBackoffMs exponential backoff base (ms)
         */
        public ExportConfig(
            String backendUrl,
            int maxBatchSize,
            long batchTimeoutMs,
            boolean compressionEnabled,
            int maxRetries,
            long retryBackoffMs
        ) {
            this.backendUrl = Objects.requireNonNull(backendUrl, "backendUrl required");
            this.maxBatchSize = Math.max(1, maxBatchSize);
            this.batchTimeoutMs = Math.max(100, batchTimeoutMs);
            this.compressionEnabled = compressionEnabled;
            this.maxRetries = Math.max(1, maxRetries);
            this.retryBackoffMs = Math.max(100, retryBackoffMs);
        }

        public String getBackendUrl() {
            return backendUrl;
        }

        public int getMaxBatchSize() {
            return maxBatchSize;
        }

        public long getBatchTimeoutMs() {
            return batchTimeoutMs;
        }

        public boolean isCompressionEnabled() {
            return compressionEnabled;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public long getRetryBackoffMs() {
            return retryBackoffMs;
        }

        @Override
        public String toString() {
            return "ExportConfig{" +
                "backendUrl='" + backendUrl + '\'' +
                ", maxBatchSize=" + maxBatchSize +
                ", batchTimeoutMs=" + batchTimeoutMs +
                ", compressionEnabled=" + compressionEnabled +
                ", maxRetries=" + maxRetries +
                ", retryBackoffMs=" + retryBackoffMs +
                '}';
        }
    }
}
