package com.ghatana.core.pattern.learning;

import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

/**
 * Represents a correlation pattern discovered during learning.
 *
 * @doc.type class
 * @doc.purpose Correlation pattern representation
 * @doc.layer core
 * @doc.pattern Learning
 */
public class CorrelationPattern {

    private final String eventType1;
    private final String eventType2;
    private final double correlation;
    private final int cooccurrenceCount;
    private final Map<String, Object> features;

    private CorrelationPattern(Builder builder) {
        this.eventType1 = Objects.requireNonNull(builder.eventType1);
        this.eventType2 = Objects.requireNonNull(builder.eventType2);
        this.correlation = builder.correlation;
        this.cooccurrenceCount = builder.cooccurrenceCount;
        this.features = Map.copyOf(builder.features);
    }

    public String getEventType1() { return eventType1; }
    public String getEventType2() { return eventType2; }
    public double getCorrelation() { return correlation; }
    public int getCooccurrenceCount() { return cooccurrenceCount; }
    public Map<String, Object> getFeatures() { return features; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CorrelationPattern that = (CorrelationPattern) o;
        return Objects.equals(eventType1, that.eventType1) &&
               Objects.equals(eventType2, that.eventType2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventType1, eventType2);
    }

    @Override
    public String toString() {
        return String.format("CorrelationPattern{%s <-> %s, correlation=%.3f, count=%d}",
                eventType1, eventType2, correlation, cooccurrenceCount);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String eventType1;
        private String eventType2;
        private double correlation;
        private int cooccurrenceCount = 1;
        private Map<String, Object> features = new HashMap<>();

        public Builder eventType1(String eventType1) {
            this.eventType1 = eventType1;
            return this;
        }

        public Builder eventType2(String eventType2) {
            this.eventType2 = eventType2;
            return this;
        }

        public Builder correlation(double correlation) {
            this.correlation = correlation;
            return this;
        }

        public Builder cooccurrenceCount(int cooccurrenceCount) {
            this.cooccurrenceCount = cooccurrenceCount;
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

        public CorrelationPattern build() {
            return new CorrelationPattern(this);
        }
    }
}
