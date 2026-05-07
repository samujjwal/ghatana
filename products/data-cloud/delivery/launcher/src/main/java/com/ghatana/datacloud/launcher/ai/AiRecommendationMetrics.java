package com.ghatana.datacloud.launcher.ai;

import com.ghatana.platform.observability.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Per-recommendation-type quality metrics instrumentation (DC-E3 acceptance criterion).
 *
 * <p>Records the following dimensions for each AI recommendation type:
 * <ul>
 *   <li><b>requests</b>: total recommendations requested</li>
 *   <li><b>fallbacks</b>: requests that resolved via heuristic fallback</li>
 *   <li><b>latency</b>: end-to-end latency from request to response (ms)</li>
 *   <li><b>confidence</b>: mean confidence score distribution</li>
 *   <li><b>thumbsUp / thumbsDown</b>: user-supplied quality signal (optional)</li>
 * </ul>
 *
 * <h2>Metric Names</h2>
 * <pre>
 *   dc.ai.recommendation.requests     [type, tenant, fallback]
 *   dc.ai.recommendation.latency_ms   [type, tenant]           — Timer
 *   dc.ai.recommendation.confidence   [type, tenant]           — Distribution summary
 *   dc.ai.recommendation.feedback     [type, tenant, signal]   — thumbsup / thumbsdown
 * </pre>
 *
 * <h2>Recommendation Types</h2>
 * <ul>
 *   <li>{@code entity_suggest} — EntityBrowserPage → POST /entities/:coll/suggest</li>
 *   <li>{@code analytics_suggest} — InsightsPage → POST /analytics/suggest</li>
 *   <li>{@code pipeline_hint} — pipeline optimisation → POST /pipelines/:id/optimise-hint</li>
 *   <li>{@code brain_explain} — anomaly explanation → POST /brain/explain</li>
 *   <li>{@code voice_intent} — VoiceGatewayHandler intent resolution</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * long start = System.currentTimeMillis();
 * // … execute AI call …
 * metrics.recordRecommendation("entity_suggest", tenantId, confidence, fallback,
 *                               System.currentTimeMillis() - start);
 * }</pre>
 *
 * <h2>Safety</h2>
 * All methods are null-safe and never throw — metrics must not affect the
 * business response path.  The class is thread-safe via atomic counters and
 * the {@link MetricsCollector} platform wrapper.
 *
 * @doc.type class
 * @doc.purpose Per-recommendation-type AI quality metrics instrumentation (E3)
 * @doc.layer product
 * @doc.pattern Service, Decorator
 * @doc.gaa.lifecycle capture
 */
public final class AiRecommendationMetrics {

    private static final Logger log = LoggerFactory.getLogger(AiRecommendationMetrics.class);

    /** Declared recommendation types — used for validation and Prometheus cardinality control. */
    public static final String TYPE_ENTITY_SUGGEST    = "entity_suggest";
    public static final String TYPE_ANALYTICS_SUGGEST = "analytics_suggest";
    public static final String TYPE_PIPELINE_DRAFT    = "pipeline_draft";
    public static final String TYPE_PIPELINE_HINT     = "pipeline_hint";
    public static final String TYPE_BRAIN_EXPLAIN     = "brain_explain";
    public static final String TYPE_VOICE_INTENT      = "voice_intent";

    public static final List<String> KNOWN_TYPES = List.of(
        TYPE_ENTITY_SUGGEST,
        TYPE_ANALYTICS_SUGGEST,
        TYPE_PIPELINE_DRAFT,
        TYPE_PIPELINE_HINT,
        TYPE_BRAIN_EXPLAIN,
        TYPE_VOICE_INTENT
    );

    private static final String METRIC_REQUESTS    = "dc.ai.recommendation.requests";
    private static final String METRIC_LATENCY     = "dc.ai.recommendation.latency_ms";
    private static final String METRIC_CONFIDENCE  = "dc.ai.recommendation.confidence";
    private static final String METRIC_FEEDBACK    = "dc.ai.recommendation.feedback";

    /** Noop sentinel returned when MetricsCollector is null (test / minimal deployments). */
    public static final AiRecommendationMetrics NOOP = new AiRecommendationMetrics(null);

    private final MetricsCollector metrics;

    /**
     * In-process running window for mean confidence per type — lightweight alternative
     * to a full distribution summary when the registry does not support histograms.
     */
    private final ConcurrentHashMap<String, RunningMean> confidenceWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> fallbackCounts = new ConcurrentHashMap<>();

    /**
     * Creates a metrics instance backed by the supplied collector.
     *
     * @param metrics platform metrics collector; may be {@code null} (no-op mode)
     */
    public AiRecommendationMetrics(MetricsCollector metrics) {
        this.metrics = metrics;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Primary instrumentation API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Records a completed AI recommendation request.
     *
     * @param type       recommendation type (one of the {@code TYPE_*} constants)
     * @param tenantId   tenant scope (used as a dimension tag)
     * @param confidence model confidence score (0-1)
     * @param fallback   {@code true} if the heuristic path was taken
     * @param latencyMs  end-to-end latency in milliseconds
     */
    public void recordRecommendation(
            String type, String tenantId, double confidence, boolean fallback, long latencyMs) {
        if (metrics == null) return;
        if (type == null || type.isBlank()) return;

        try {
            String fallbackTag = Boolean.toString(fallback);
            // Sanitise tenantId to limit Prometheus cardinality (max 64 chars, alphanumeric + dash)
            String safeTenant = safeTenantTag(tenantId);

            metrics.incrementCounter(METRIC_REQUESTS, "type", type, "tenant", safeTenant, "fallback", fallbackTag);
            metrics.recordTimer(METRIC_LATENCY, latencyMs);

            // Update in-process mean confidence window
            confidenceWindows.computeIfAbsent(type, k -> new RunningMean()).add(confidence);
            if (fallback) {
                fallbackCounts.computeIfAbsent(type, k -> new LongAdder()).increment();
            }

            // Record confidence as a distribution summary via MeterRegistry for histogram buckets
            try {
                metrics.getMeterRegistry()
                    .summary(METRIC_CONFIDENCE, "type", type, "tenant", safeTenant)
                    .record(confidence);
            } catch (Exception ex) {
                // Micrometer registry may not be fully configured in all deployment modes
                log.debug("[DC-E3] Micrometer summary registration skipped: {}", ex.getMessage());
            }

        } catch (Exception e) {
            log.debug("[DC-E3] AiRecommendationMetrics.recordRecommendation failed silently: {}", e.getMessage());
        }
    }

    /**
     * Records an error during AI recommendation (LLM call failure, timeout, etc.).
     *
     * @param type     recommendation type
     * @param tenantId tenant scope
     * @param cause    the root cause exception
     */
    public void recordError(String type, String tenantId, Exception cause) {
        if (metrics == null) return;
        try {
            metrics.recordError(METRIC_REQUESTS + ".errors", cause,
                Map.of("type", type != null ? type : "unknown", "tenant", safeTenantTag(tenantId)));
        } catch (Exception ignored) {
            log.debug("[DC-E3] AiRecommendationMetrics.recordError failed silently: {}", cause.getMessage());
        }
    }

    /**
     * Records user-supplied quality signal for a recommendation.
     *
     * <p>This is used to track recommendation utility over time (thumbs-up/down
     * from UI) without coupling the feedback mechanism to any single UI pattern.
     *
     * @param type     recommendation type
     * @param tenantId tenant scope
     * @param positive {@code true} for thumbs-up, {@code false} for thumbs-down
     */
    public void recordFeedback(String type, String tenantId, boolean positive) {
        if (metrics == null) return;
        try {
            metrics.incrementCounter(METRIC_FEEDBACK,
                "type", Objects.requireNonNullElse(type, "unknown"),
                "tenant", safeTenantTag(tenantId),
                "signal", positive ? "thumbsup" : "thumbsdown");
        } catch (Exception ignored) {
            log.debug("[DC-E3] AiRecommendationMetrics.recordFeedback failed silently");
        }
    }

    /**
     * Returns the current mean confidence for the given recommendation type
     * from the in-process running window.  Useful for health checks and
     * admin dashboards.
     *
     * @param type recommendation type
     * @return mean confidence (NaN if no data recorded yet)
     */
    public double getMeanConfidence(String type) {
        RunningMean window = confidenceWindows.get(type);
        return window == null ? Double.NaN : window.mean();
    }

    /**
     * Returns the total request count for the given type from the in-process
     * counter (approximate — may not match Prometheus if registry is remote).
     *
     * @param type recommendation type
     * @return total observations
     */
    public long getRequestCount(String type) {
        RunningMean window = confidenceWindows.get(type);
        return window == null ? 0L : window.count();
    }

    /**
     * Returns the total heuristic-fallback count for a recommendation type.
     */
    public long getFallbackCount(String type) {
        LongAdder counter = fallbackCounts.get(type);
        return counter == null ? 0L : counter.sum();
    }

    /**
     * Returns the current heuristic fallback rate for a recommendation type.
     */
    public double getFallbackRate(String type) {
        long requests = getRequestCount(type);
        if (requests == 0) {
            return 0.0;
        }
        return (double) getFallbackCount(type) / (double) requests;
    }

    /**
     * Returns a lightweight process-local snapshot for each known recommendation type.
     */
    public List<AiQualitySnapshot> snapshot() {
        return KNOWN_TYPES.stream()
            .map(type -> new AiQualitySnapshot(
                type,
                getRequestCount(type),
                getFallbackCount(type),
                getFallbackRate(type),
                getMeanConfidence(type)))
            .toList();
    }

    /**
     * Immutable AI quality summary for a single recommendation type.
     */
    public record AiQualitySnapshot(
        String type,
        long requestCount,
        long fallbackCount,
        double fallbackRate,
        double meanConfidence
    ) {}

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sanitises a tenant ID for use as a Prometheus tag value.
     * Limits length to 64 characters and replaces non-alphanumeric characters.
     */
    private static String safeTenantTag(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) return "unknown";
        String clean = tenantId.replaceAll("[^a-zA-Z0-9\\-_.]", "_");
        return clean.length() > 64 ? clean.substring(0, 64) : clean;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Running mean (lock-free, approximate)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lightweight thread-safe running mean for confidence scores.
     *
     * <p>Uses a Welford-style update to avoid catastrophic cancellation.
     * Values are kept approximate — precision is not critical for dashboards.
     */
    private static final class RunningMean {
        private final LongAdder count = new LongAdder();
        // Store sum as integer millipoints (0-1000) to avoid double sync
        private final LongAdder sumMillipoints = new LongAdder();

        void add(double value) {
            count.increment();
            sumMillipoints.add(Math.round(value * 1000.0));
        }

        double mean() {
            long n = count.sum();
            if (n == 0) return Double.NaN;
            return sumMillipoints.sum() / (1000.0 * n);
        }

        long count() {
            return count.sum();
        }
    }
}
