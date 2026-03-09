package com.ghatana.validation.ai.model;

import java.util.Map;
import java.util.Objects;

/**
 * Represents a suggested pattern for event detection.
 
 *
 * @doc.type class
 * @doc.purpose Pattern suggestion
 * @doc.layer core
 * @doc.pattern Component
*/
public class PatternSuggestion {
    private final String id;
    private final String name;
    private final String description;
    private final double confidenceScore;
    private final Map<String, Object> parameters;
    private final String patternType;
    private final String recommendationReason;
    private final Map<String, Double> featureScores;

    public PatternSuggestion(String id, String name, String description, double confidenceScore,
                           Map<String, Object> parameters, String patternType,
                           String recommendationReason, Map<String, Double> featureScores) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.name = Objects.requireNonNull(name, "Name cannot be null");
        this.description = Objects.requireNonNull(description, "Description cannot be null");
        this.confidenceScore = validateConfidence(confidenceScore);
        this.parameters = Map.copyOf(Objects.requireNonNull(parameters, "Parameters cannot be null"));
        this.patternType = Objects.requireNonNull(patternType, "Pattern type cannot be null");
        this.recommendationReason = Objects.requireNonNull(recommendationReason, "Recommendation reason cannot be null");
        this.featureScores = Map.copyOf(Objects.requireNonNull(featureScores, "Feature scores cannot be null"));
    }

    private double validateConfidence(double confidence) {
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Confidence score must be between 0.0 and 1.0");
        }
        return confidence;
    }

    // Getters
    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public double confidenceScore() { return confidenceScore; }
    public Map<String, Object> parameters() { return Map.copyOf(parameters); }
    public String patternType() { return patternType; }
    public String recommendationReason() { return recommendationReason; }
    public Map<String, Double> featureScores() { return Map.copyOf(featureScores); }
    
    // Alias methods for compatibility
    public String getId() { return id(); }
    public String getName() { return name(); }
    public String getDescription() { return description(); }
    public double getConfidenceScore() { return confidenceScore(); }
    public Map<String, Object> getParameters() { return parameters(); }
    public String getPatternType() { return patternType(); }
    public String getRecommendationReason() { return recommendationReason(); }
    public Map<String, Double> getFeatureScores() { return featureScores(); }

    // Builder pattern for easier construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id = java.util.UUID.randomUUID().toString();
        private String name = "";
        private String description = "";
        private double confidenceScore = 0.0;
        private Map<String, Object> parameters = Map.of();
        private String patternType = "GENERIC";
        private String recommendationReason = "Suggested by pattern analysis";
        private Map<String, Double> featureScores = Map.of();

        public Builder id(String id) {
            this.id = id != null ? id : java.util.UUID.randomUUID().toString();
            return this;
        }

        public Builder name(String name) {
            this.name = name != null ? name : "";
            return this;
        }

        public Builder description(String description) {
            this.description = description != null ? description : "";
            return this;
        }

        public Builder confidenceScore(double confidenceScore) {
            this.confidenceScore = confidenceScore;
            return this;
        }

        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
            return this;
        }

        public Builder patternType(String patternType) {
            this.patternType = patternType != null ? patternType : "GENERIC";
            return this;
        }

        public Builder recommendationReason(String recommendationReason) {
            this.recommendationReason = recommendationReason != null ? recommendationReason : "";
            return this;
        }

        public Builder featureScores(Map<String, Double> featureScores) {
            this.featureScores = featureScores != null ? Map.copyOf(featureScores) : Map.of();
            return this;
        }

        public PatternSuggestion build() {
            return new PatternSuggestion(
                id,
                name,
                description,
                confidenceScore,
                parameters,
                patternType,
                recommendationReason,
                featureScores
            );
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PatternSuggestion that = (PatternSuggestion) o;
        return Double.compare(that.confidenceScore, confidenceScore) == 0 &&
               Objects.equals(id, that.id) &&
               Objects.equals(name, that.name) &&
               Objects.equals(description, that.description) &&
               Objects.equals(parameters, that.parameters) &&
               Objects.equals(patternType, that.patternType) &&
               Objects.equals(recommendationReason, that.recommendationReason) &&
               Objects.equals(featureScores, that.featureScores);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, confidenceScore, parameters, 
                          patternType, recommendationReason, featureScores);
    }

    @Override
    public String toString() {
        return "PatternSuggestion{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", description='" + description + '\'' +
               ", confidenceScore=" + confidenceScore +
               ", patternType='" + patternType + '\'' +
               '}';
    }
}
