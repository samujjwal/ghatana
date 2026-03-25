package com.ghatana.yappc.core.kpi;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KPI Collector for tracking and measuring performance metrics Week 10 Day 50: KPI measurement and
 * reporting
 *
 * @doc.type class
 * @doc.purpose KPI Collector for tracking and measuring performance metrics Week 10 Day 50: KPI measurement and
 * @doc.layer platform
 * @doc.pattern Component
 */
public class KPICollector {
    private static final Logger logger = LoggerFactory.getLogger(KPICollector.class);

    private final Map<String, List<MeasurementPoint>> measurements = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public KPICollector() {
        this.objectMapper = JsonUtils.getDefaultMapper();
        // Configure ObjectMapper for basic JSON serialization
        this.objectMapper.findAndRegisterModules();
    }

    /**
 * Record a measurement point for a specific metric */
    public void recordMeasurement(String metricName, double value, Map<String, String> tags) {
        MeasurementPoint point =
                new MeasurementPoint(
                        Instant.now(),
                        value,
                        tags != null ? new ConcurrentHashMap<>(tags) : new ConcurrentHashMap<>());

        measurements
                .computeIfAbsent(metricName, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(point);

        logger.debug("Recorded measurement: {} = {} with tags: {}", metricName, value, tags);
    }

    /**
 * Record a simple measurement without tags */
    public void recordMeasurement(String metricName, double value) {
        recordMeasurement(metricName, value, null);
    }

    /**
 * Start timing a process */
    public TimingContext startTiming(String processName) {
        return new TimingContext(processName, this);
    }

    /**
 * Record build time to green metrics */
    public void recordBuildTimeToGreen(String projectName, Duration buildTime, boolean success) {
        Map<String, String> tags =
                Map.of("project", projectName, "success", String.valueOf(success));

        recordMeasurement("build.time_to_green", buildTime.toMillis(), tags);
        recordMeasurement("build.success_rate", success ? 1.0 : 0.0, tags);
    }

    /**
 * Record test execution metrics */
    public void recordTestMetrics(
            String suiteName, int totalTests, int passedTests, Duration executionTime) {
        Map<String, String> tags = Map.of("suite", suiteName);

        recordMeasurement("test.total_count", totalTests, tags);
        recordMeasurement("test.passed_count", passedTests, tags);
        recordMeasurement("test.failure_count", totalTests - passedTests, tags);
        recordMeasurement("test.success_rate", (double) passedTests / totalTests, tags);
        recordMeasurement("test.execution_time", executionTime.toMillis(), tags);
    }

    /**
 * Record cache performance metrics */
    public void recordCacheMetrics(String cacheName, long hits, long misses, double hitRatio) {
        Map<String, String> tags = Map.of("cache", cacheName);

        recordMeasurement("cache.hits", hits, tags);
        recordMeasurement("cache.misses", misses, tags);
        recordMeasurement("cache.hit_ratio", hitRatio, tags);
    }

    /**
 * Record deployment metrics */
    public void recordDeploymentMetrics(
            String environment, Duration deploymentTime, boolean success) {
        Map<String, String> tags =
                Map.of("environment", environment, "success", String.valueOf(success));

        recordMeasurement("deployment.time", deploymentTime.toMillis(), tags);
        recordMeasurement("deployment.success_rate", success ? 1.0 : 0.0, tags);
    }

    /**
 * Get summary statistics for a metric */
    public MetricSummary getSummary(String metricName) {
        List<MeasurementPoint> points =
                measurements.getOrDefault(metricName, Collections.emptyList());

        if (points.isEmpty()) {
            return new MetricSummary(metricName, 0, 0, 0, 0, 0);
        }

        List<Double> values =
                points.stream()
                        .map(MeasurementPoint::getValue)
                        .sorted()
                        .collect(Collectors.toList());

        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        double mean = sum / values.size();
        double min = values.get(0);
        double max = values.get(values.size() - 1);
        double median =
                values.size() % 2 == 0
                        ? (values.get(values.size() / 2 - 1) + values.get(values.size() / 2)) / 2.0
                        : values.get(values.size() / 2);

        return new MetricSummary(metricName, values.size(), mean, min, max, median);
    }

    /**
 * Get all available metrics */
    public List<String> getAvailableMetrics() {
        return new ArrayList<>(measurements.keySet());
    }

    /**
 * Get raw measurements for a metric */
    public List<MeasurementPoint> getMeasurements(String metricName) {
        return new ArrayList<>(measurements.getOrDefault(metricName, Collections.emptyList()));
    }

    /**
 * Generate a comprehensive KPI report */
    public KPIReport generateReport() {
        Map<String, MetricSummary> summaries =
                measurements.keySet().stream()
                        .collect(Collectors.toMap(metric -> metric, this::getSummary));

        return new KPIReport(Instant.now(), summaries, calculateTrends());
    }

    /**
 * Export measurements as JSON */
    public String exportAsJson() {
        try {
            return objectMapper.writeValueAsString(measurements);
        } catch (Exception e) {
            logger.error("Failed to export measurements as JSON", e);
            return "{}";
        }
    }

    /**
 * Clear all measurements */
    public void clear() {
        measurements.clear();
        logger.info("Cleared all KPI measurements");
    }

    /**
 * Calculate trends for metrics (simplified implementation) */
    private Map<String, TrendInfo> calculateTrends() {
        return measurements.entrySet().stream()
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> calculateTrendForMetric(entry.getValue())));
    }

    private TrendInfo calculateTrendForMetric(List<MeasurementPoint> points) {
        if (points.size() < 2) {
            return new TrendInfo(0.0, "stable");
        }

        // Simple linear trend calculation
        List<MeasurementPoint> sortedPoints =
                points.stream()
                        .sorted((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()))
                        .collect(Collectors.toList());

        double firstValue = sortedPoints.get(0).getValue();
        double lastValue = sortedPoints.get(sortedPoints.size() - 1).getValue();

        double changePercent = ((lastValue - firstValue) / firstValue) * 100.0;
        String direction;

        if (Math.abs(changePercent) < 5.0) {
            direction = "stable";
        } else if (changePercent > 0) {
            direction = "increasing";
        } else {
            direction = "decreasing";
        }

        return new TrendInfo(changePercent, direction);
    }

    /**
 * Timing context for measuring durations */
    public static class TimingContext implements AutoCloseable {
        private final String processName;
        private final KPICollector collector;
        private final Instant startTime;
        private final Map<String, String> tags;

        public TimingContext(String processName, KPICollector collector) {
            this.processName = processName;
            this.collector = collector;
            this.startTime = Instant.now();
            this.tags = new ConcurrentHashMap<>();
        }

        public TimingContext withTag(String key, String value) {
            tags.put(key, value);
            return this;
        }

        @Override
        public void close() {
            Duration duration = Duration.between(startTime, Instant.now());
            collector.recordMeasurement(processName + ".duration", duration.toMillis(), tags);
        }
    }

    /**
 * Measurement point data class */
    public static class MeasurementPoint {
        @JsonProperty private final Instant timestamp;
        @JsonProperty private final double value;
        @JsonProperty private final Map<String, String> tags;

        public MeasurementPoint(Instant timestamp, double value, Map<String, String> tags) {
            this.timestamp = timestamp;
            this.value = value;
            this.tags = tags;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public double getValue() {
            return value;
        }

        public Map<String, String> getTags() {
            return tags;
        }
    }

    /**
 * Metric summary statistics */
    public static class MetricSummary {
        @JsonProperty private final String metricName;
        @JsonProperty private final int count;
        @JsonProperty private final double mean;
        @JsonProperty private final double min;
        @JsonProperty private final double max;
        @JsonProperty private final double median;

        public MetricSummary(
                String metricName, int count, double mean, double min, double max, double median) {
            this.metricName = metricName;
            this.count = count;
            this.mean = mean;
            this.min = min;
            this.max = max;
            this.median = median;
        }

        public String getMetricName() {
            return metricName;
        }

        public int getCount() {
            return count;
        }

        public double getMean() {
            return mean;
        }

        public double getMin() {
            return min;
        }

        public double getMax() {
            return max;
        }

        public double getMedian() {
            return median;
        }
    }

    /**
 * Trend information */
    public static class TrendInfo {
        @JsonProperty private final double changePercent;
        @JsonProperty private final String direction;

        public TrendInfo(double changePercent, String direction) {
            this.changePercent = changePercent;
            this.direction = direction;
        }

        public double getChangePercent() {
            return changePercent;
        }

        public String getDirection() {
            return direction;
        }
    }

    /**
 * Comprehensive KPI report */
    public static class KPIReport {
        @JsonProperty private final Instant generatedAt;
        @JsonProperty private final Map<String, MetricSummary> metricSummaries;
        @JsonProperty private final Map<String, TrendInfo> trends;

        public KPIReport(
                Instant generatedAt,
                Map<String, MetricSummary> metricSummaries,
                Map<String, TrendInfo> trends) {
            this.generatedAt = generatedAt;
            this.metricSummaries = metricSummaries;
            this.trends = trends;
        }

        public Instant getGeneratedAt() {
            return generatedAt;
        }

        public Map<String, MetricSummary> getMetricSummaries() {
            return metricSummaries;
        }

        public Map<String, TrendInfo> getTrends() {
            return trends;
        }
    }
}
