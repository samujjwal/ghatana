package com.ghatana.core.pattern.learning;

import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

/**
 * Represents an anomalous pattern detected in event streams.
 *
 * @doc.type class
 * @doc.purpose Anomaly pattern representation for pattern learning
 * @doc.layer core
 * @doc.pattern Learning
 */
public class AnomalyPattern {

    private final String eventType;
    private final AnomalyType anomalyType;
    private final double anomalyScore;
    private final String description;
    private final Map<String, Object> features;

    private AnomalyPattern(Builder builder) {
        this.eventType = Objects.requireNonNull(builder.eventType);
        this.anomalyType = Objects.requireNonNull(builder.anomalyType);
        this.anomalyScore = builder.anomalyScore;
        this.description = Objects.requireNonNull(builder.description);
        this.features = Map.copyOf(builder.features);
    }

    public String getEventType() { return eventType; }
    public AnomalyType getAnomalyType() { return anomalyType; }
    public double getAnomalyScore() { return anomalyScore; }
    public String getDescription() { return description; }
    public Map<String, Object> getFeatures() { return features; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnomalyPattern that = (AnomalyPattern) o;
        return Objects.equals(eventType, that.eventType) &&
               Objects.equals(anomalyType, that.anomalyType) &&
               Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventType, anomalyType, description);
    }

    @Override
    public String toString() {
        return String.format("AnomalyPattern{type=%s, anomaly=%s, score=%.3f, desc='%s'}",
                eventType, anomalyType, anomalyScore, description);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String eventType;
        private AnomalyType anomalyType;
        private double anomalyScore;
        private String description;
        private Map<String, Object> features = new HashMap<>();

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder anomalyType(AnomalyType anomalyType) {
            this.anomalyType = anomalyType;
            return this;
        }

        public Builder anomalyScore(double anomalyScore) {
            this.anomalyScore = anomalyScore;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder addFeature(String key, Object value) {
            this.features.put(key, value);
            return this;
        }

        public Builder features(Map<String, Object> features) {
            this.features = new HashMap<>(features);
            return this;
        }

        public AnomalyPattern build() {
            return new AnomalyPattern(this);
        }
    }
}
