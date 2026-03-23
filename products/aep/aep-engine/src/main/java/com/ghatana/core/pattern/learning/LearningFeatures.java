package com.ghatana.core.pattern.learning;

import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

/**
 * Features extracted from events for pattern learning.
 *
 * @doc.type class
 * @doc.purpose Event feature extraction for learning
 * @doc.layer core
 * @doc.pattern Learning
 */
public class LearningFeatures {

    private final String eventType;
    private final long timestamp;
    private final String correlationId;
    private final Map<String, Object> features;

    private LearningFeatures(Builder builder) {
        this.eventType = builder.eventType;
        this.timestamp = builder.timestamp;
        this.correlationId = builder.correlationId;
        this.features = Map.copyOf(builder.features);
    }

    public String getEventType() { return eventType; }
    public long getTimestamp() { return timestamp; }
    public String getCorrelationId() { return correlationId; }
    public Map<String, Object> getFeatures() { return features; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LearningFeatures that = (LearningFeatures) o;
        return Objects.equals(correlationId, that.correlationId) &&
               Objects.equals(eventType, that.eventType) &&
               timestamp == that.timestamp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(correlationId, eventType, timestamp);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String eventType;
        private long timestamp;
        private String correlationId;
        private Map<String, Object> features = new HashMap<>();

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
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

        public LearningFeatures build() {
            Objects.requireNonNull(eventType, "Event type is required");
            Objects.requireNonNull(correlationId, "Correlation ID is required");
            return new LearningFeatures(this);
        }
    }
}
