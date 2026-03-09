package com.ghatana.core.pattern.learning;

import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

/**
 * Represents a frequency pattern discovered during learning.
 *
 * @doc.type class
 * @doc.purpose Frequency pattern representation
 * @doc.layer core
 * @doc.pattern Learning
 */
public class FrequencyPattern {

    private final String eventType;
    private final long count;
    private final double frequency;
    private final long timeWindow;
    private final Map<String, Object> features;

    private FrequencyPattern(Builder builder) {
        this.eventType = Objects.requireNonNull(builder.eventType);
        this.count = builder.count;
        this.frequency = builder.frequency;
        this.timeWindow = builder.timeWindow;
        this.features = Map.copyOf(builder.features);
    }

    public String getEventType() { return eventType; }
    public long getCount() { return count; }
    public double getFrequency() { return frequency; }
    public long getTimeWindow() { return timeWindow; }
    public Map<String, Object> getFeatures() { return features; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FrequencyPattern that = (FrequencyPattern) o;
        return Objects.equals(eventType, that.eventType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventType);
    }

    @Override
    public String toString() {
        return String.format("FrequencyPattern{type=%s, count=%d, frequency=%.3f}",
                eventType, count, frequency);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String eventType;
        private long count = 1;
        private double frequency;
        private long timeWindow = 300000; // 5 minutes default
        private Map<String, Object> features = new HashMap<>();

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder count(long count) {
            this.count = count;
            return this;
        }

        public Builder frequency(double frequency) {
            this.frequency = frequency;
            return this;
        }

        public Builder timeWindow(long timeWindow) {
            this.timeWindow = timeWindow;
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

        public FrequencyPattern build() {
            return new FrequencyPattern(this);
        }
    }
}
