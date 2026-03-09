package com.ghatana.core.pattern.learning;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

/**
 * Represents a sequential pattern discovered during learning.
 *
 * @doc.type class
 * @doc.purpose Sequential pattern representation
 * @doc.layer core
 * @doc.pattern Learning
 */
public class SequentialPattern {

    private final String signature;
    private final List<EventSequence> sequences;
    private final int frequency;
    private final double confidence;
    private final Map<String, Object> features;

    private SequentialPattern(Builder builder) {
        this.signature = Objects.requireNonNull(builder.signature);
        this.sequences = List.copyOf(builder.sequences);
        this.frequency = builder.frequency;
        this.confidence = builder.confidence;
        this.features = Map.copyOf(builder.features);
    }

    public String getSignature() { return signature; }
    public List<EventSequence> getSequences() { return sequences; }
    public int getFrequency() { return frequency; }
    public double getConfidence() { return confidence; }
    public Map<String, Object> getFeatures() { return features; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SequentialPattern that = (SequentialPattern) o;
        return Objects.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(signature);
    }

    @Override
    public String toString() {
        return String.format("SequentialPattern{signature='%s', frequency=%d, confidence=%.3f}",
                signature, frequency, confidence);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String signature;
        private List<EventSequence> sequences = List.of();
        private int frequency = 1;
        private double confidence;
        private Map<String, Object> features = new HashMap<>();

        public Builder signature(String signature) {
            this.signature = signature;
            return this;
        }

        public Builder sequences(List<EventSequence> sequences) {
            this.sequences = sequences;
            return this;
        }

        public Builder frequency(int frequency) {
            this.frequency = frequency;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
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

        public SequentialPattern build() {
            return new SequentialPattern(this);
        }
    }
}
