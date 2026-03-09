package com.ghatana.core.pattern.learning;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

/**
 * Represents a sequence of events discovered during learning.
 *
 * @doc.type class
 * @doc.purpose Event sequence representation for pattern learning
 * @doc.layer core
 * @doc.pattern Learning
 */
public class EventSequence {

    private final String signature;
    private final List<String> eventTypes;
    private final double confidence;
    private final int frequency;
    private final Map<String, Object> features;

    public EventSequence(String signature, List<String> eventTypes, double confidence) {
        this.signature = Objects.requireNonNull(signature);
        this.eventTypes = List.copyOf(eventTypes);
        this.confidence = confidence;
        this.frequency = 1;
        this.features = Map.of();
    }

    public EventSequence(String signature, List<String> eventTypes, double confidence, int frequency, Map<String, Object> features) {
        this.signature = Objects.requireNonNull(signature);
        this.eventTypes = List.copyOf(eventTypes);
        this.confidence = confidence;
        this.frequency = frequency;
        this.features = Map.copyOf(features);
    }

    public String getSignature() { return signature; }
    public List<String> getEventTypes() { return eventTypes; }
    public double getConfidence() { return confidence; }
    public int getFrequency() { return frequency; }
    public Map<String, Object> getFeatures() { return features; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventSequence that = (EventSequence) o;
        return Objects.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(signature);
    }

    @Override
    public String toString() {
        return String.format("EventSequence{signature='%s', confidence=%.3f, frequency=%d}",
                signature, confidence, frequency);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String signature;
        private List<String> eventTypes;
        private double confidence;
        private int frequency = 1;
        private Map<String, Object> features = new HashMap<>();

        public Builder signature(String signature) {
            this.signature = signature;
            return this;
        }

        public Builder eventTypes(List<String> eventTypes) {
            this.eventTypes = eventTypes;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder frequency(int frequency) {
            this.frequency = frequency;
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

        public EventSequence build() {
            Objects.requireNonNull(signature, "Signature is required");
            Objects.requireNonNull(eventTypes, "Event types are required");
            return new EventSequence(signature, eventTypes, confidence, frequency, features);
        }
    }
}
