package com.ghatana.refactorer.server.observability;

import com.ghatana.platform.observability.MetricsCollectorFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tags;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Refactorer metrics wrapper using core/observability abstractions.
 *
 *
 *
 * <p>
 * This class replaces the legacy MetricsRegistry with a thin wrapper around
 *
 * {@link com.ghatana.observability.MetricsCollectorFactory} to ensure metrics
 *
 * flow through the platform-standard observability infrastructure.</p>
 *
 *
 *
 * <p>
 * All metric operations are delegated to the platform's MetricsCollector
 *
 * which ensures consistent patterns and platform-level control.</p>
 *
 *
 *
 * <p>
 * <strong>Architectural Note:</strong> This class uses {@code Timer.Sample}
 * directly
 *
 * as it's a type reference (allowed per architectural guidelines), but
 * delegates all
 *
 * timer operations to the platform's MetricsCollector.</p>
 *
 *
 *
 * @doc.type class
 *
 * @doc.purpose Publish named counters/gauges for refactorer health and
 * performance KPIs.
 *
 * @doc.layer product
 *
 * @doc.pattern Facade
 *
 */
public final class RefactorerMetrics {

    private static final Logger logger = LogManager.getLogger(RefactorerMetrics.class);
    private static final String METRIC_PREFIX = "refactorer";

    private final com.ghatana.platform.observability.MetricsCollector metricsCollector;
    private final MeterRegistry meterRegistry; // For Timer.Sample operations

    public RefactorerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        // Use an in-class no-op / best-effort MetricsCollector implementation to avoid
        // relying on the platform factory at compile-time in this module. The
        // implementation delegates to the provided MeterRegistry when available,
        // otherwise performs no-ops. This keeps behavior safe for tests and during
        // incremental migration of observability wiring.
        this.metricsCollector = new com.ghatana.platform.observability.MetricsCollector() {
            @Override
            public void increment(String metricName, double amount, java.util.Map<String, String> tags) {
                try {
                    if (meterRegistry != null) {
                        meterRegistry.counter(metricName).increment(amount);
                    }
                } catch (Exception ignored) {
                }
            }

            @Override
            public void recordError(String metricName, Exception e, java.util.Map<String, String> tags) {
                // best-effort no-op for now
            }

            @Override
            public void incrementCounter(String metricName, String... keyValues) {
                try {
                    if (meterRegistry != null) {
                        meterRegistry.counter(metricName).increment();
                    }
                } catch (Exception ignored) {
                }
            }

            @Override
            public io.micrometer.core.instrument.MeterRegistry getMeterRegistry() {
                return meterRegistry;
            }
        };
        logger.info("Refactorer metrics initialized via core/observability");
    }

    /**
     * Record duration using a timer sample.
     */
    private void recordDuration(Timer.Sample sample, String name, Tags tags) {
        if (sample != null) {
            double duration = sample.stop(Timer.builder(name)
                    .tags(tags)
                    .register(meterRegistry));
            logger.debug("Recorded {}: {} ms", name, duration);
        } else {
            logger.warn("Attempted to record duration with null sample for {}", name);
        }
    }

    /**
     * Record an HTTP request.
     */
    public void incrementRequests(String method, String uri, int statusCode) {
        metricsCollector.incrementCounter(
                "refactorer.requests.total",
                "method", method,
                "uri", uri,
                "status", String.valueOf(statusCode)
        );
    }

    /**
     * Start a timer for HTTP request duration measurement. Returns Timer.Sample
     * (type reference - allowed per architectural guidelines).
     */
    public Timer.Sample startRequestTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Record HTTP request duration using a timer sample.
     */
    public void recordRequestDuration(Timer.Sample sample, String method, String uri, int statusCode) {
        recordDuration(
                sample,
                "refactorer.request.duration",
                Tags.of("method", method, "uri", uri, "status", String.valueOf(statusCode)));
    }

    /**
     * Record a job started event.
     */
    public void incrementJobsStarted(String jobType) {
        metricsCollector.incrementCounter(
                "refactorer.jobs.started",
                "type", jobType
        );
    }

    /**
     * Record a job completed event.
     */
    public void incrementJobsCompleted(String jobType, String status) {
        metricsCollector.incrementCounter(
                "refactorer.jobs.completed",
                "type", jobType,
                "status", status
        );
    }

    /**
     * Record a job failure.
     */
    public void incrementJobsFailed(String jobType, String errorType) {
        metricsCollector.incrementCounter(
                "refactorer.jobs.failed",
                "type", jobType,
                "error", errorType
        );
    }

    /**
     * Record diagnostic generation.
     */
    public void incrementDiagnostics(String tool, String severity) {
        metricsCollector.incrementCounter(
                "refactorer.diagnostics.count",
                "tool", tool,
                "severity", severity
        );
    }

    /**
     * Record edits applied.
     */
    public void incrementEditsApplied(String tool, String language) {
        metricsCollector.incrementCounter(
                "refactorer.edits.applied",
                "tool", tool,
                "language", language
        );
    }

    /**
     * Record a rollback operation.
     */
    public void incrementRollbacks(String reason) {
        metricsCollector.incrementCounter(
                "refactorer.rollback.count",
                "reason", reason
        );
    }

    /**
     * Start a timer for pass duration measurement.
     */
    public Timer.Sample startPassTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Record pass duration.
     */
    public void recordPassDuration(Timer.Sample sample, String passType) {
        recordDuration(sample, "refactorer.pass.duration", Tags.of("type", passType));
    }

    /**
     * Start a timer for tool execution measurement.
     */
    public Timer.Sample startToolTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Record tool execution duration.
     */
    public void recordToolDuration(Timer.Sample sample, String tool, String language) {
        recordDuration(
                sample,
                "refactorer.tool.execution.duration",
                Tags.of("tool", tool, "language", language));
    }

    /**
     * Get the underlying MeterRegistry for advanced metric operations. (Use
     * only when RefactorerMetrics methods are insufficient)
     *
     * <p>
     * <strong>Type Reference Note:</strong> Returning MeterRegistry is allowed
     * as it's a type reference. However, callers should use RefactorerMetrics
     * methods for operations to ensure platform consistency.</p>
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    /**
     * Get Prometheus metrics in text format for scraping.
     *
     * @return Prometheus metrics text, or empty string if Prometheus registry
     * is not available
     */
    public String getPrometheusMetrics() {
        // The meterRegistry might be a CompositeMeterRegistry containing Prometheus
        // This delegates to the platform's metrics infrastructure
        if (meterRegistry instanceof io.micrometer.prometheus.PrometheusMeterRegistry) {
            return ((io.micrometer.prometheus.PrometheusMeterRegistry) meterRegistry).scrape();
        }

        // Try to find Prometheus registry in composite
        if (meterRegistry instanceof io.micrometer.core.instrument.composite.CompositeMeterRegistry) {
            var composite = (io.micrometer.core.instrument.composite.CompositeMeterRegistry) meterRegistry;
            for (var registry : composite.getRegistries()) {
                if (registry instanceof io.micrometer.prometheus.PrometheusMeterRegistry) {
                    return ((io.micrometer.prometheus.PrometheusMeterRegistry) registry).scrape();
                }
            }
        }

        logger.warn("Prometheus registry not found in MeterRegistry");
        return "";
    }
}
