package com.ghatana.kernel.telemetry;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central telemetry collector for Kernel lifecycle operations.
 *
 * <p>This class provides metrics, spans, and log events for lifecycle phases,
 * adapter executions, and broker operations. It integrates with the platform
 * observability module to emit structured telemetry data.</p>
 *
 * @doc.type class
 * @doc.purpose Central telemetry collection for Kernel lifecycle operations
 * @doc.layer kernel
 * @doc.pattern Telemetry
 */
public final class KernelLifecycleTelemetry {

    private final Eventloop eventloop;
    private final String componentId;
    private final Map<String, LifecycleMetrics> metricsMap = new ConcurrentHashMap<>();
    private final Map<String, SpanContext> activeSpans = new ConcurrentHashMap<>();

    public KernelLifecycleTelemetry(Eventloop eventloop, String componentId) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.componentId = Objects.requireNonNull(componentId, "componentId must not be null");
    }

    /**
     * Records a lifecycle phase start.
     *
     * @param productId the product ID
     * @param phase the lifecycle phase
     * @param surfaceId the surface ID
     * @return a SpanContext for the operation
     */
    public SpanContext recordLifecycleStart(String productId, String phase, String surfaceId) {
        String spanId = generateSpanId();
        SpanContext context = new SpanContext(
            spanId,
            componentId,
            "lifecycle",
            Instant.now(),
            Map.of(
                "productId", productId,
                "phase", phase,
                "surfaceId", surfaceId != null ? surfaceId : "all"
            )
        );
        activeSpans.put(spanId, context);
        return context;
    }

    /**
     * Records a lifecycle phase completion.
     *
     * @param spanId the span ID
     * @param status the completion status
     * @param metadata additional metadata
     */
    public void recordLifecycleComplete(String spanId, String status, Map<String, String> metadata) {
        SpanContext context = activeSpans.remove(spanId);
        if (context != null) {
            Duration duration = Duration.between(context.startTime(), Instant.now());
            String metricKey = "lifecycle." + context.metadata().get("phase");
            LifecycleMetrics metrics = metricsMap.computeIfAbsent(metricKey, k -> new LifecycleMetrics(metricKey));
            metrics.recordCompletion(status, duration);
        }
    }

    /**
     * Records an adapter execution start.
     *
     * @param adapterId the adapter ID
     * @param operation the operation type
     * @return a SpanContext for the operation
     */
    public SpanContext recordAdapterStart(String adapterId, String operation) {
        String spanId = generateSpanId();
        SpanContext context = new SpanContext(
            spanId,
            componentId,
            "adapter",
            Instant.now(),
            Map.of(
                "adapterId", adapterId,
                "operation", operation
            )
        );
        activeSpans.put(spanId, context);
        return context;
    }

    /**
     * Records an adapter execution completion.
     *
     * @param spanId the span ID
     * @param status the completion status
     * @param metadata additional metadata
     */
    public void recordAdapterComplete(String spanId, String status, Map<String, String> metadata) {
        SpanContext context = activeSpans.remove(spanId);
        if (context != null) {
            Duration duration = Duration.between(context.startTime(), Instant.now());
            String metricKey = "adapter." + context.metadata().get("adapterId");
            LifecycleMetrics metrics = metricsMap.computeIfAbsent(metricKey, k -> new LifecycleMetrics(metricKey));
            metrics.recordCompletion(status, duration);
        }
    }

    /**
     * Records a broker interaction start.
     *
     * @param brokerType the broker type (product or plugin)
     * @param contractId the contract ID
     * @return a SpanContext for the operation
     */
    public SpanContext recordBrokerStart(String brokerType, String contractId) {
        String spanId = generateSpanId();
        SpanContext context = new SpanContext(
            spanId,
            componentId,
            "broker",
            Instant.now(),
            Map.of(
                "brokerType", brokerType,
                "contractId", contractId
            )
        );
        activeSpans.put(spanId, context);
        return context;
    }

    /**
     * Records a broker interaction completion.
     *
     * @param spanId the span ID
     * @param status the completion status
     * @param metadata additional metadata
     */
    public void recordBrokerComplete(String spanId, String status, Map<String, String> metadata) {
        SpanContext context = activeSpans.remove(spanId);
        if (context != null) {
            Duration duration = Duration.between(context.startTime(), Instant.now());
            String metricKey = "broker." + context.metadata().get("brokerType");
            LifecycleMetrics metrics = metricsMap.computeIfAbsent(metricKey, k -> new LifecycleMetrics(metricKey));
            metrics.recordCompletion(status, duration);
        }
    }

    /**
     * Gets metrics for a specific key.
     *
     * @param key the metric key
     * @return the metrics, or a new empty metrics object if not found
     */
    public LifecycleMetrics getMetrics(String key) {
        return metricsMap.getOrDefault(key, new LifecycleMetrics(key));
    }

    /**
     * Gets all metrics.
     *
     * @return map of all metrics
     */
    public Map<String, LifecycleMetrics> getAllMetrics() {
        return Map.copyOf(metricsMap);
    }

    /**
     * Resets all metrics (useful for testing).
     */
    public void resetMetrics() {
        metricsMap.clear();
        activeSpans.clear();
    }

    private String generateSpanId() {
        return componentId + "-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
    }

    /**
     * Context for a telemetry span.
     */
    public static final class SpanContext {
        private final String spanId;
        private final String componentId;
        private final String spanType;
        private final Instant startTime;
        private final Map<String, String> metadata;

        public SpanContext(String spanId, String componentId, String spanType, Instant startTime, Map<String, String> metadata) {
            this.spanId = spanId;
            this.componentId = componentId;
            this.spanType = spanType;
            this.startTime = startTime;
            this.metadata = Map.copyOf(metadata);
        }

        public String spanId() { return spanId; }
        public String componentId() { return componentId; }
        public String spanType() { return spanType; }
        public Instant startTime() { return startTime; }
        public Map<String, String> metadata() { return metadata; }
    }

    /**
     * Metrics for a specific operation type.
     */
    public static final class LifecycleMetrics {
        private final String key;
        private final AtomicLong totalCount = new AtomicLong(0);
        private final AtomicLong successCount = new AtomicLong(0);
        private final AtomicLong failureCount = new AtomicLong(0);
        private final AtomicLong totalDurationMs = new AtomicLong(0);
        private final AtomicLong minDurationMs = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxDurationMs = new AtomicLong(0);

        public LifecycleMetrics(String key) {
            this.key = key;
        }

        public String key() { return key; }
        public long totalCount() { return totalCount.get(); }
        public long successCount() { return successCount.get(); }
        public long failureCount() { return failureCount.get(); }
        public long totalDurationMs() { return totalDurationMs.get(); }
        public long minDurationMs() { return minDurationMs.get() == Long.MAX_VALUE ? 0 : minDurationMs.get(); }
        public long maxDurationMs() { return maxDurationMs.get(); }

        public double averageDurationMs() {
            long count = totalCount.get();
            return count > 0 ? (double) totalDurationMs.get() / count : 0;
        }

        public double successRate() {
            long count = totalCount.get();
            return count > 0 ? (double) successCount.get() / count : 0;
        }

        private void recordCompletion(String status, Duration duration) {
            totalCount.incrementAndGet();
            long durationMs = duration.toMillis();
            totalDurationMs.addAndGet(durationMs);
            
            // Update min/max
            long currentMin = minDurationMs.get();
            while (durationMs < currentMin && !minDurationMs.compareAndSet(currentMin, durationMs)) {
                currentMin = minDurationMs.get();
            }
            
            long currentMax = maxDurationMs.get();
            while (durationMs > currentMax && !maxDurationMs.compareAndSet(currentMax, durationMs)) {
                currentMax = maxDurationMs.get();
            }

            if ("success".equals(status) || "succeeded".equals(status)) {
                successCount.incrementAndGet();
            } else {
                failureCount.incrementAndGet();
            }
        }
    }
}
