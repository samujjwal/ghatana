package com.ghatana.datacloud.application.monitoring;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Broadcasts metrics to subscribed WebSocket clients.
 *
 * <p><b>Purpose</b><br>
 * Manages real-time metric streaming to dashboard clients, handles subscriptions,
 * and provides aggregated metric summaries.
 *
 * <p><b>Architecture Role</b><br>
 * Application service responsible for metric publishing and client management.
 * Part of the monitoring and observability subsystem.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MetricsBroadcaster broadcaster = new MetricsBroadcaster(metricsCollector);
 * broadcaster.subscribe("client-1", event -> dashboardUI.update(event));
 * broadcaster.publishMetric("query.time", new MetricData(42, "ms", Map.of()));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Real-time metric broadcasting to clients
 * @doc.layer application
 * @doc.pattern Service
 */
public class MetricsBroadcaster {
    private final ConcurrentHashMap<String, MetricsListener> subscribers = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<MetricEvent> eventBuffer;
    private final ConcurrentHashMap<String, LiveMetricAccumulator> liveMetrics = new ConcurrentHashMap<>();
    
    private static final int MAX_BUFFER_SIZE = 1000;
    
    /**
     * Creates a new metrics broadcaster.
     *
     * @param metricsCollector the metrics collector for instrumentation (currently unused, reserved for future metrics)
     */
    public MetricsBroadcaster(MetricsCollector metricsCollector) {
        this.eventBuffer = new ConcurrentLinkedDeque<>();
    }

    /**
     * Subscribes a client to metric events.
     *
     * @param clientId the client identifier
     * @param listener the listener to receive metric events
     * @throws IllegalArgumentException if clientId or listener is null
     */
    public void subscribe(String clientId, MetricsListener listener) {
        if (clientId == null || clientId.isEmpty()) {
            throw new IllegalArgumentException("clientId cannot be null or empty");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null");
        }
        subscribers.put(clientId, listener);
    }

    /**
     * Unsubscribes a client from metric events.
     *
     * @param clientId the client identifier
     */
    public void unsubscribe(String clientId) {
        subscribers.remove(clientId);
    }

    /**
     * Publishes a metric to all subscribed clients.
     *
     * @param metricName the name of the metric
     * @param data the metric data
     * @throws IllegalArgumentException if metricName is null
     */
    public void publishMetric(String metricName, MetricData data) {
        if (metricName == null || metricName.isEmpty()) {
            throw new IllegalArgumentException("metricName cannot be null or empty");
        }
        
        MetricEvent event = new MetricEvent(
            metricName,
            data,
            Instant.now()
        );
        
        // Add to buffer with size limit
        eventBuffer.addLast(event);
        while (eventBuffer.size() > MAX_BUFFER_SIZE) {
            eventBuffer.removeFirst();
        }
        
        // Update live metric statistics
        liveMetrics.compute(metricName, (key, accumulator) -> {
            if (accumulator == null) {
                accumulator = new LiveMetricAccumulator();
            }
            accumulator.add(data.value());
            return accumulator;
        });
        
        // Broadcast to all subscribers
        for (MetricsListener listener : subscribers.values()) {
            try {
                listener.onMetricEvent(event);
            } catch (Exception e) {
                // Log and continue broadcasting to other subscribers
            }
        }
    }

    /**
     * Gets live metric statistics for the given metric name.
     *
     * @param metricName the metric name
     * @return promise of live metric statistics
     */
    public Promise<LiveMetric> getLiveMetric(String metricName) {
        return Promise.of(liveMetrics
            .getOrDefault(metricName, new LiveMetricAccumulator())
            .toLiveMetric(metricName));
    }

    /**
     * Gets a dashboard summary of current metrics and subscriptions.
     *
     * @return promise of dashboard summary
     */
    public Promise<DashboardSummary> getDashboardSummary() {
        Map<String, AggregatedMetric> aggregatedMetrics = new HashMap<>();
        
        for (String metricName : liveMetrics.keySet()) {
            LiveMetricAccumulator accumulator = liveMetrics.get(metricName);
            if (accumulator != null) {
                aggregatedMetrics.put(
                    metricName,
                    new AggregatedMetric(
                        metricName,
                        accumulator.count(),
                        accumulator.sum() / Math.max(1, accumulator.count()),
                        accumulator.min(),
                        accumulator.max()
                    )
                );
            }
        }
        
        return Promise.of(new DashboardSummary(
            subscribers.size(),
            eventBuffer.size(),
            aggregatedMetrics
        ));
    }

    /**
     * Listener interface for metric events.
     */
    @FunctionalInterface
    public interface MetricsListener {
        void onMetricEvent(MetricEvent event);
    }

    /**
     * Metric event data.
     */
    public record MetricEvent(
        String metricName,
        MetricData data,
        Instant timestamp
    ) {}

    /**
     * Metric data point.
     */
    public record MetricData(
        double value,
        String unit,
        Map<String, String> tags
    ) {}

    /**
     * Live metric statistics.
     */
    public record LiveMetric(
        String name,
        double average,
        double min,
        double max,
        long count
    ) {}

    /**
     * Aggregated metric for dashboard.
     */
    public record AggregatedMetric(
        String name,
        long count,
        double average,
        double min,
        double max
    ) {}

    /**
     * Dashboard summary view.
     */
    public record DashboardSummary(
        int activeSubscriptions,
        int totalEvents,
        Map<String, AggregatedMetric> metrics
    ) {}

    /**
     * Accumulator for live metric statistics.
     */
    private static class LiveMetricAccumulator {
        private double sum = 0;
        private double min = Double.MAX_VALUE;
        private double max = Double.MIN_VALUE;
        private long count = 0;

        void add(double value) {
            sum += value;
            min = Math.min(min, value);
            max = Math.max(max, value);
            count++;
        }

        LiveMetric toLiveMetric(String name) {
            return new LiveMetric(
                name,
                count > 0 ? sum / count : 0,
                min == Double.MAX_VALUE ? 0 : min,
                max == Double.MIN_VALUE ? 0 : max,
                count
            );
        }

        double sum() { return sum; }
        double min() { return min == Double.MAX_VALUE ? 0 : min; }
        double max() { return max == Double.MIN_VALUE ? 0 : max; }
        long count() { return count; }
    }
}
