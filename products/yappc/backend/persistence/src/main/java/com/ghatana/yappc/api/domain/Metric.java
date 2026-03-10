/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a metric.
 *
 * @doc.type class
 * @doc.purpose Metrics domain entity for operations monitoring
 * @doc.layer domain
 * @doc.pattern Entity
 */
public class Metric {

    private UUID id;
    private String tenantId;
    private String projectId;
    private String name;
    private String description;
    private MetricType type;
    private String unit;
    private List<DataPoint> dataPoints;
    private MetricSummary summary;
    private Map<String, String> tags;
    private Map<String, Object> metadata;

    public Metric() {
        this.id = UUID.randomUUID();
        this.dataPoints = new ArrayList<>();
        this.tags = new HashMap<>();
        this.metadata = new HashMap<>();
    }

    // ========== Enums ==========

    public enum MetricType {
        COUNTER,
        GAUGE,
        HISTOGRAM,
        SUMMARY
    }

    public enum Aggregation {
        SUM,
        AVG,
        MIN,
        MAX,
        COUNT,
        P50,
        P95,
        P99
    }

    // ========== Nested Classes ==========

    public static class DataPoint {
        private Instant timestamp;
        private double value;
        private Map<String, String> tags;

        public DataPoint() {
            this.tags = new HashMap<>();
        }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }

        public Map<String, String> getTags() { return tags; }
        public void setTags(Map<String, String> tags) { this.tags = tags; }
    }

    public static class MetricSummary {
        private double avg;
        private double min;
        private double max;
        private double sum;
        private long count;
        private double p50;
        private double p95;
        private double p99;
        private Instant lastUpdated;

        public double getAvg() { return avg; }
        public void setAvg(double avg) { this.avg = avg; }

        public double getMin() { return min; }
        public void setMin(double min) { this.min = min; }

        public double getMax() { return max; }
        public void setMax(double max) { this.max = max; }

        public double getSum() { return sum; }
        public void setSum(double sum) { this.sum = sum; }

        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }

        public double getP50() { return p50; }
        public void setP50(double p50) { this.p50 = p50; }

        public double getP95() { return p95; }
        public void setP95(double p95) { this.p95 = p95; }

        public double getP99() { return p99; }
        public void setP99(double p99) { this.p99 = p99; }

        public Instant getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
    }

    // ========== Domain Methods ==========

    public void addDataPoint(DataPoint point) {
        dataPoints.add(point);
        updateSummary();
    }

    private void updateSummary() {
        if (dataPoints.isEmpty()) return;
        if (summary == null) summary = new MetricSummary();

        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;

        for (DataPoint dp : dataPoints) {
            sum += dp.getValue();
            min = Math.min(min, dp.getValue());
            max = Math.max(max, dp.getValue());
        }

        summary.setSum(sum);
        summary.setMin(min);
        summary.setMax(max);
        summary.setAvg(sum / dataPoints.size());
        summary.setCount(dataPoints.size());
        summary.setLastUpdated(Instant.now());
    }

    // ========== Getters and Setters ==========

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public MetricType getType() { return type; }
    public void setType(MetricType type) { this.type = type; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public List<DataPoint> getDataPoints() { return dataPoints; }
    public void setDataPoints(List<DataPoint> dataPoints) { this.dataPoints = dataPoints; }

    public MetricSummary getSummary() { return summary; }
    public void setSummary(MetricSummary summary) { this.summary = summary; }

    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Metric)) return false;
        Metric metric = (Metric) o;
        return Objects.equals(id, metric.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
