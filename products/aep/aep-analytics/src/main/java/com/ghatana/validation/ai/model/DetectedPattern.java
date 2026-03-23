package com.ghatana.validation.ai.model;

import java.util.Map;
import java.util.Objects;

/**
 * Represents a pattern detected in the event stream.
 
 *
 * @doc.type class
 * @doc.purpose Detected pattern
 * @doc.layer core
 * @doc.pattern Component
*/
public class DetectedPattern {
    private final String id;
    private final String name;
    private final String patternType;
    private final String description;
    private final double confidence;
    private final Map<String, Object> metadata;

    public DetectedPattern(String id, String name, String patternType, String description, 
                          double confidence, Map<String, Object> metadata) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.name = Objects.requireNonNull(name, "Name cannot be null");
        this.patternType = Objects.requireNonNull(patternType, "Pattern type cannot be null");
        this.description = Objects.requireNonNull(description, "Description cannot be null");
        this.confidence = validateConfidence(confidence);
        this.metadata = Map.copyOf(Objects.requireNonNull(metadata, "Metadata cannot be null"));
    }

    private double validateConfidence(double confidence) {
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
        }
        return confidence;
    }

    // Getters
    public String id() { return id; }
    public String name() { return name; }
    public String patternType() { return patternType; }
    public String getDescription() { return description; }
    public double confidence() { return confidence; }
    public Map<String, Object> getMetadata() { return Map.copyOf(metadata); }
    
    // Alias methods for compatibility
    public String getId() { return id(); }
    public String getName() { return name(); }
    public String getPatternType() { return patternType(); }
    public double getConfidence() { return confidence(); }

    // Builder pattern for easier construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id = java.util.UUID.randomUUID().toString();
        private String name = "";
        private String patternType = "CUSTOM";
        private String description = "";
        private double confidence = 0.0;
        private Map<String, Object> metadata = Map.of();

        public Builder id(String id) {
            this.id = id != null ? id : java.util.UUID.randomUUID().toString();
            return this;
        }

        public Builder name(String name) {
            this.name = name != null ? name : "";
            return this;
        }

        public Builder patternType(String patternType) {
            this.patternType = patternType != null ? patternType : "CUSTOM";
            return this;
        }

        public Builder description(String description) {
            this.description = description != null ? description : "";
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
            return this;
        }

        public DetectedPattern build() {
            return new DetectedPattern(
                id,
                name,
                patternType,
                description,
                confidence,
                metadata
            );
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DetectedPattern that = (DetectedPattern) o;
        return Double.compare(that.confidence, confidence) == 0 &&
               Objects.equals(id, that.id) &&
               Objects.equals(name, that.name) &&
               Objects.equals(patternType, that.patternType) &&
               Objects.equals(description, that.description) &&
               Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, patternType, description, confidence, metadata);
    }

    @Override
    public String toString() {
        return "DetectedPattern{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", patternType='" + patternType + '\'' +
               ", confidence=" + confidence +
               '}';
    }
}
