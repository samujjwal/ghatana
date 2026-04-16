package com.ghatana.aep.analytics;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Shared analytics value types used by the AEP default analytics engines.
 *
 * @doc.type interface
 * @doc.purpose Shared analytics domain records for anomaly detection and forecasting
 * @doc.layer product
 * @doc.pattern Contract
 */
public interface AnalyticsEngine {

    /**
     * Time range used for report queries.
     */
    record TimeRange(Instant start, Instant end) {
        public TimeRange {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
        }
    }

    /**
     * Single observed data point.
     */
    record DataPoint(Instant timestamp, double value) {
        public DataPoint {
            Objects.requireNonNull(timestamp, "timestamp");
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public double getValue() {
            return value;
        }
    }

    /**
     * Ordered time-series data for a metric.
     */
    record TimeSeriesData(String metricName, List<DataPoint> points) {
        public TimeSeriesData {
            Objects.requireNonNull(metricName, "metricName");
            points = points == null ? List.of() : List.copyOf(points);
        }

        public String getMetricName() {
            return metricName;
        }

        public List<DataPoint> getPoints() {
            return points;
        }
    }

    /**
     * Forecasted value for a future timestamp.
     */
    record ForecastPoint(Instant timestamp, double value) {
        public ForecastPoint {
            Objects.requireNonNull(timestamp, "timestamp");
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public double getValue() {
            return value;
        }
    }

    /**
     * Anomaly detection result for a single event or metric observation.
     */
    record AnomalyResult(String eventType, double score, String reason) {
        public AnomalyResult {
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(reason, "reason");
        }

        public String getEventType() {
            return eventType;
        }

        public double getScore() {
            return score;
        }

        public String getReason() {
            return reason;
        }
    }
}