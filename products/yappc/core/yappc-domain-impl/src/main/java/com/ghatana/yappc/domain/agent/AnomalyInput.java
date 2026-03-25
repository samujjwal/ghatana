package com.ghatana.products.yappc.domain.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Input for the Anomaly Detector Agent.
 *
 * @doc.type record
 * @doc.purpose Anomaly detector agent input
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record AnomalyInput(
        @NotNull AnomalyMetricType metricType,
        @NotNull List<MetricDataPoint> currentMetrics,
        @Nullable List<MetricDataPoint> timeSeriesData,
        double sensitivity,
        @Nullable Map<String, Object> context
) {

    /**
     * Types of metrics to monitor for anomalies.
     */
    public enum AnomalyMetricType {
        VELOCITY,
        THROUGHPUT,
        ERROR_RATE,
        RESPONSE_TIME,
        RESOURCE_USAGE,
        SECURITY_EVENTS,
        QUALITY_METRICS,
        CUSTOM
    }

    /**
     * A single metric data point.
     */
    public record MetricDataPoint(
            @NotNull String timestamp,
            @NotNull String metricName,
            double value,
            @Nullable Map<String, String> labels
    ) {}

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private AnomalyMetricType metricType;
        private List<MetricDataPoint> currentMetrics = List.of();
        private List<MetricDataPoint> timeSeriesData;
        private double sensitivity = 0.8;
        private Map<String, Object> context;

        public Builder metricType(AnomalyMetricType metricType) {
            this.metricType = metricType;
            return this;
        }

        public Builder currentMetrics(List<MetricDataPoint> currentMetrics) {
            this.currentMetrics = currentMetrics;
            return this;
        }

        public Builder timeSeriesData(List<MetricDataPoint> timeSeriesData) {
            this.timeSeriesData = timeSeriesData;
            return this;
        }

        public Builder sensitivity(double sensitivity) {
            this.sensitivity = sensitivity;
            return this;
        }

        public Builder context(Map<String, Object> context) {
            this.context = context;
            return this;
        }

        public AnomalyInput build() {
            return new AnomalyInput(metricType, currentMetrics, timeSeriesData, sensitivity, context);
        }
    }
}
