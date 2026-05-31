package com.ghatana.datacloud.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * Enhanced metrics collection service for Data Cloud with comprehensive runtime tracking.
 *
 * <p><b>Purpose</b><br>
 * Provides centralized metrics collection, aggregation, and reporting for all
 * Data Cloud components. Supports counters, gauges, histograms, and timers
 * with proper correlation context tracking.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MetricsService metrics = MetricsService.builder()
 *     .serviceName("data-cloud-api")
 *     .build();
 * 
 * // Record metrics
 * metrics.incrementCounter("api_requests_total", Map.of("method", "GET", "endpoint", "/api/v1/entities"));
 * metrics.recordTimer("request_duration_ms", 150, Map.of("tenant_id", "tenant-123"));
 * metrics.setGauge("active_connections", 42);
 * }</pre>
 *
 * @see ObservabilityService
 * @doc.type class
 * @doc.purpose Enhanced metrics collection and aggregation
 * @doc.layer product
 * @doc.pattern Service, Observer
 */
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    private final String serviceName;
    private final Map<String, Metric> metrics = new ConcurrentHashMap<>();
    private final Map<String, MetricSnapshot> snapshots = new ConcurrentHashMap<>();
    private final boolean enableAggregation;
    private final int retentionHours;

    private MetricsService(Builder builder) {
        this.serviceName = builder.serviceName;
        this.enableAggregation = builder.enableAggregation;
        this.retentionHours = builder.retentionHours;
    }

    /**
     * Increments a counter metric.
     *
     * @param metricName metric name
     * @param tags metric tags
     */
    public void incrementCounter(String metricName, Map<String, String> tags) {
        CounterMetric counter = getOrCreateMetric(metricName, MetricType.COUNTER, CounterMetric::new);
        counter.increment(tags);
    }

    /**
     * Increments a counter metric by a specific value.
     *
     * @param metricName metric name
     * @param value increment value
     * @param tags metric tags
     */
    public void incrementCounter(String metricName, double value, Map<String, String> tags) {
        CounterMetric counter = getOrCreateMetric(metricName, MetricType.COUNTER, CounterMetric::new);
        counter.increment(value, tags);
    }

    /**
     * Sets a gauge metric value.
     *
     * @param metricName metric name
     * @param value gauge value
     * @param tags metric tags
     */
    public void setGauge(String metricName, double value, Map<String, String> tags) {
        GaugeMetric gauge = getOrCreateMetric(metricName, MetricType.GAUGE, GaugeMetric::new);
        gauge.set(value, tags);
    }

    /**
     * Records a timer metric.
     *
     * @param metricName metric name
     * @param durationMs duration in milliseconds
     * @param tags metric tags
     */
    public void recordTimer(String metricName, long durationMs, Map<String, String> tags) {
        TimerMetric timer = getOrCreateMetric(metricName, MetricType.TIMER, TimerMetric::new);
        timer.record(durationMs, tags);
    }

    /**
     * Records a histogram metric.
     *
     * @param metricName metric name
     * @param value histogram value
     * @param tags metric tags
     */
    public void recordHistogram(String metricName, double value, Map<String, String> tags) {
        HistogramMetric histogram = getOrCreateMetric(metricName, MetricType.HISTOGRAM, HistogramMetric::new);
        histogram.record(value, tags);
    }

    /**
     * Gets a metric snapshot.
     *
     * @param metricName metric name
     * @return metric snapshot
     */
    public Optional<MetricSnapshot> getMetricSnapshot(String metricName) {
        Metric metric = metrics.get(metricName);
        if (metric == null) {
            return Optional.empty();
        }
        return Optional.of(metric.getSnapshot());
    }

    /**
     * Gets all metric snapshots.
     *
     * @return all metric snapshots
     */
    public Map<String, MetricSnapshot> getAllMetricSnapshots() {
        Map<String, MetricSnapshot> result = new HashMap<>();
        metrics.forEach((name, metric) -> result.put(name, metric.getSnapshot()));
        return result;
    }

    /**
     * Gets metrics filtered by tags.
     *
     * @param tagFilters tag filters
     * @return filtered metrics
     */
    public Map<String, MetricSnapshot> getMetricsByTags(Map<String, String> tagFilters) {
        Map<String, MetricSnapshot> result = new HashMap<>();
        metrics.forEach((name, metric) -> {
            MetricSnapshot snapshot = metric.getSnapshot();
            if (matchesTags(snapshot.getTags(), tagFilters)) {
                result.put(name, snapshot);
            }
        });
        return result;
    }

    /**
     * Creates a time series snapshot for historical tracking.
     */
    public void createSnapshot() {
        if (!enableAggregation) {
            return;
        }

        Instant timestamp = Instant.now();
        metrics.forEach((name, metric) -> {
            MetricSnapshot snapshot = metric.getSnapshot();
            snapshots.put(name + ":" + timestamp.toEpochMilli(), snapshot);
        });

        // Cleanup old snapshots
        cleanupOldSnapshots();
    }

    /**
     * Gets service metrics summary.
     */
    public ServiceMetricsSummary getServiceMetricsSummary() {
        Map<MetricType, Long> counts = new EnumMap<>(MetricType.class);
        Arrays.stream(MetricType.values()).forEach(type -> counts.put(type, 0L));

        metrics.values().forEach(metric -> {
            MetricType type = metric.getType();
            counts.put(type, counts.get(type) + 1);
        });

        return new ServiceMetricsSummary(
            serviceName,
            metrics.size(),
            counts,
            snapshots.size(),
            Instant.now()
        );
    }

    /**
     * Resets all metrics.
     */
    public void resetAllMetrics() {
        metrics.clear();
    }

    /**
     * Resets a specific metric.
     *
     * @param metricName metric name
     */
    public void resetMetric(String metricName) {
        metrics.remove(metricName);
    }

    /**
     * Removes old snapshots based on retention policy.
     */
    private void cleanupOldSnapshots() {
        Instant cutoff = Instant.now().minus(retentionHours, ChronoUnit.HOURS);
        snapshots.entrySet().removeIf(entry -> {
            String[] parts = entry.getKey().split(":");
            if (parts.length < 2) return true;
            
            try {
                long timestamp = Long.parseLong(parts[parts.length - 1]);
                return Instant.ofEpochMilli(timestamp).isBefore(cutoff);
            } catch (NumberFormatException e) {
                return true; // Remove malformed keys
            }
        });
    }

    /**
     * Gets or creates a metric of the specified type.
     */
    @SuppressWarnings("unchecked")
    private <T extends Metric> T getOrCreateMetric(String metricName, MetricType type, MetricFactory<T> factory) {
        return (T) metrics.computeIfAbsent(metricName, name -> {
            if (type != MetricType.COUNTER && type != MetricType.GAUGE && 
                type != MetricType.TIMER && type != MetricType.HISTOGRAM) {
                throw new IllegalArgumentException("Unsupported metric type: " + type);
            }
            return factory.create(name);
        });
    }

    /**
     * Checks if tags match the filter criteria.
     */
    private boolean matchesTags(Map<String, String> tags, Map<String, String> tagFilters) {
        if (tagFilters.isEmpty()) {
            return true;
        }

        for (Map.Entry<String, String> filter : tagFilters.entrySet()) {
            String tagValue = tags.get(filter.getKey());
            if (tagValue == null || !tagValue.equals(filter.getValue())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Metric factory interface.
     */
    @FunctionalInterface
    private interface MetricFactory<T extends Metric> {
        T create(String name);
    }

    /**
     * Base metric interface.
     */
    private interface Metric {
        MetricType getType();
        MetricSnapshot getSnapshot();
        void reset();
    }

    /**
     * Counter metric implementation.
     */
    private static class CounterMetric implements Metric {
        private final String name;
        private final Map<String, DoubleAdder> counters = new ConcurrentHashMap<>();

        CounterMetric(String name) {
            this.name = name;
        }

        void increment(Map<String, String> tags) {
            increment(1.0, tags);
        }

        void increment(double value, Map<String, String> tags) {
            String key = tagsKey(tags);
            counters.computeIfAbsent(key, k -> new DoubleAdder()).add(value);
        }

        @Override
        public MetricType getType() {
            return MetricType.COUNTER;
        }

        @Override
        public MetricSnapshot getSnapshot() {
            Map<String, Double> values = new HashMap<>();
            Map<String, String> allTags = new HashMap<>();
            counters.forEach((key, adder) -> {
                Map<String, String> tags = parseTagsKey(key);
                values.put(tagsKey(tags), adder.sum());
                allTags.putAll(tags);
            });

            return new MetricSnapshot(name, MetricType.COUNTER, values, allTags, Instant.now());
        }

        @Override
        public void reset() {
            counters.clear();
        }
    }

    /**
     * Gauge metric implementation.
     */
    private static class GaugeMetric implements Metric {
        private final String name;
        private final Map<String, Double> gauges = new ConcurrentHashMap<>();

        GaugeMetric(String name) {
            this.name = name;
        }

        void set(double value, Map<String, String> tags) {
            String key = tagsKey(tags);
            gauges.put(key, value);
        }

        @Override
        public MetricType getType() {
            return MetricType.GAUGE;
        }

        @Override
        public MetricSnapshot getSnapshot() {
            Map<String, String> tags = new HashMap<>();
            gauges.keySet().forEach(key -> tags.putAll(parseTagsKey(key)));
            return new MetricSnapshot(name, MetricType.GAUGE, new HashMap<>(gauges), tags, Instant.now());
        }

        @Override
        public void reset() {
            gauges.clear();
        }
    }

    /**
     * Timer metric implementation.
     */
    private static class TimerMetric implements Metric {
        private final String name;
        private final Map<String, TimerData> timers = new ConcurrentHashMap<>();

        TimerMetric(String name) {
            this.name = name;
        }

        void record(long durationMs, Map<String, String> tags) {
            String key = tagsKey(tags);
            timers.computeIfAbsent(key, k -> new TimerData()).record(durationMs);
        }

        @Override
        public MetricType getType() {
            return MetricType.TIMER;
        }

        @Override
        public MetricSnapshot getSnapshot() {
            Map<String, Double> values = new HashMap<>();
            Map<String, String> allTags = new HashMap<>();
            timers.forEach((key, timer) -> {
                Map<String, String> tags = parseTagsKey(key);
                values.put(tagsKey(tags) + "_count", (double) timer.getCount());
                values.put(tagsKey(tags) + "_sum", (double) timer.getSum());
                values.put(tagsKey(tags) + "_avg", timer.getAverage());
                allTags.putAll(tags);
            });

            return new MetricSnapshot(name, MetricType.TIMER, values, allTags, Instant.now());
        }

        @Override
        public void reset() {
            timers.clear();
        }

        private static class TimerData {
            private final LongAdder count = new LongAdder();
            private final LongAdder sum = new LongAdder();

            void record(long durationMs) {
                count.increment();
                sum.add(durationMs);
            }

            long getCount() {
                return count.sum();
            }

            long getSum() {
                return sum.sum();
            }

            double getAverage() {
                long c = getCount();
                return c > 0 ? (double) getSum() / c : 0.0;
            }
        }
    }

    /**
     * Histogram metric implementation.
     */
    private static class HistogramMetric implements Metric {
        private final String name;
        private final Map<String, HistogramData> histograms = new ConcurrentHashMap<>();

        HistogramMetric(String name) {
            this.name = name;
        }

        void record(double value, Map<String, String> tags) {
            String key = tagsKey(tags);
            histograms.computeIfAbsent(key, k -> new HistogramData()).record(value);
        }

        @Override
        public MetricType getType() {
            return MetricType.HISTOGRAM;
        }

        @Override
        public MetricSnapshot getSnapshot() {
            Map<String, Double> values = new HashMap<>();
            Map<String, String> allTags = new HashMap<>();
            histograms.forEach((key, histogram) -> {
                Map<String, String> tags = parseTagsKey(key);
                String prefix = tagsKey(tags);
                values.put(prefix + "_count", (double) histogram.getCount());
                values.put(prefix + "_sum", histogram.getSum());
                values.put(prefix + "_avg", histogram.getAverage());
                values.put(prefix + "_min", histogram.getMin());
                values.put(prefix + "_max", histogram.getMax());
                allTags.putAll(tags);
            });

            return new MetricSnapshot(name, MetricType.HISTOGRAM, values, allTags, Instant.now());
        }

        @Override
        public void reset() {
            histograms.clear();
        }

        private static class HistogramData {
            private final LongAdder count = new LongAdder();
            private final DoubleAdder sum = new DoubleAdder();
            private volatile double min = Double.MAX_VALUE;
            private volatile double max = Double.MIN_VALUE;

            void record(double value) {
                count.increment();
                sum.add(value);
                
                // Update min/max (not thread-safe but acceptable for metrics)
                if (value < min) {
                    min = value;
                }
                if (value > max) {
                    max = value;
                }
            }

            long getCount() {
                return count.sum();
            }

            double getSum() {
                return sum.sum();
            }

            double getAverage() {
                long c = getCount();
                return c > 0 ? getSum() / c : 0.0;
            }

            double getMin() {
                return count.sum() > 0 ? min : 0.0;
            }

            double getMax() {
                return count.sum() > 0 ? max : 0.0;
            }
        }
    }

    /**
     * Creates a tags key from a map.
     */
    private static String tagsKey(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }

        return tags.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .reduce((a, b) -> a + "," + b)
            .orElse("");
    }

    /**
     * Parses a tags key back into a map.
     */
    private static Map<String, String> parseTagsKey(String tagsKey) {
        Map<String, String> tags = new HashMap<>();
        if (tagsKey == null || tagsKey.isEmpty()) {
            return tags;
        }

        String[] pairs = tagsKey.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                tags.put(keyValue[0], keyValue[1]);
            }
        }

        return tags;
    }

    /**
     * Metric types.
     */
    public enum MetricType {
        COUNTER, GAUGE, TIMER, HISTOGRAM
    }

    /**
     * Metric snapshot.
     */
    public static class MetricSnapshot {
        private final String name;
        private final MetricType type;
        private final Map<String, Double> values;
        private final Map<String, String> tags;
        private final Instant timestamp;

        MetricSnapshot(String name, MetricType type, Map<String, Double> values, Map<String, String> tags, Instant timestamp) {
            this.name = name;
            this.type = type;
            this.values = new HashMap<>(values);
            this.tags = tags != null ? new HashMap<>(tags) : new HashMap<>();
            this.timestamp = timestamp;
        }

        public String getName() {
            return name;
        }

        public MetricType getType() {
            return type;
        }

        public Map<String, Double> getValues() {
            return new HashMap<>(values);
        }

        public Map<String, String> getTags() {
            return new HashMap<>(tags);
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return String.format("MetricSnapshot{name=%s, type=%s, values=%d, timestamp=%s}",
                    name, type, values.size(), timestamp);
        }
    }

    /**
     * Service metrics summary.
     */
    public static class ServiceMetricsSummary {
        private final String serviceName;
        private final int totalMetrics;
        private final Map<MetricType, Long> metricCounts;
        private final int snapshotCount;
        private final Instant timestamp;

        ServiceMetricsSummary(String serviceName, int totalMetrics, Map<MetricType, Long> metricCounts,
                             int snapshotCount, Instant timestamp) {
            this.serviceName = serviceName;
            this.totalMetrics = totalMetrics;
            this.metricCounts = new EnumMap<>(metricCounts);
            this.snapshotCount = snapshotCount;
            this.timestamp = timestamp;
        }

        public ServiceMetricsSummary(String serviceName, int totalMetrics, Map<?, ?> metricCounts, int snapshotCount) {
            this.serviceName = serviceName;
            this.totalMetrics = totalMetrics;
            this.metricCounts = new EnumMap<>(MetricType.class);
            metricCounts.forEach((key, value) -> {
                if (key instanceof MetricType metricType && value instanceof Number number) {
                    this.metricCounts.put(metricType, number.longValue());
                }
            });
            this.snapshotCount = snapshotCount;
            this.timestamp = Instant.now();
        }

        public String getServiceName() {
            return serviceName;
        }

        public int getTotalMetrics() {
            return totalMetrics;
        }

        public Map<MetricType, Long> getMetricCounts() {
            return new HashMap<>(metricCounts);
        }

        public int getSnapshotCount() {
            return snapshotCount;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return String.format("ServiceMetricsSummary{service=%s, total=%d, snapshots=%d, timestamp=%s}",
                    serviceName, totalMetrics, snapshotCount, timestamp);
        }
    }

    // ============ Builder Pattern ============

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String serviceName = "data-cloud";
        private boolean enableAggregation = true;
        private int retentionHours = 24;

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder enableAggregation(boolean enableAggregation) {
            this.enableAggregation = enableAggregation;
            return this;
        }

        public Builder retentionHours(int retentionHours) {
            this.retentionHours = retentionHours;
            return this;
        }

        public MetricsService build() {
            return new MetricsService(this);
        }
    }
}
