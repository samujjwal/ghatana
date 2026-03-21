package com.ghatana.validation.ai.anomaly;

import java.util.Map;
import java.util.Objects;

/**
 * Configuration for anomaly detection operations.
 
 *
 * @doc.type class
 * @doc.purpose Validation anomaly detection config
 * @doc.layer core
 * @doc.pattern Configuration
*/
public class ValidationAnomalyDetectionConfig {
    private final double threshold;
    private final String shardId;
    private final String algorithm;
    private final Map<String, Object> parameters;

    public ValidationAnomalyDetectionConfig(double threshold, String shardId, String algorithm, Map<String, Object> parameters) {
        this.threshold = validateThreshold(threshold);
        this.shardId = Objects.requireNonNull(shardId, "Shard ID cannot be null");
        this.algorithm = Objects.requireNonNull(algorithm, "Algorithm cannot be null");
        this.parameters = Map.copyOf(Objects.requireNonNull(parameters, "Parameters cannot be null"));
    }

    private double validateThreshold(double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("Threshold must be between 0.0 and 1.0");
        }
        return threshold;
    }

    // Getters - using JavaBean style for compatibility
    public double getThreshold() { return threshold; }
    public String getShardId() { return shardId; }
    public String getAlgorithm() { return algorithm; }
    public Map<String, Object> getParameters() { return Map.copyOf(parameters); }
    
    // Fluent style getters for method chaining
    public double threshold() { return threshold; }
    public double sensitivityThreshold() { return threshold; } // Alias for compatibility
    public String shardId() { return shardId; }
    public String algorithm() { return algorithm; }
    public Map<String, Object> parameters() { return Map.copyOf(parameters); }

    // Builder pattern for easier construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private double threshold = 0.8;
        private String shardId = "default";
        private String algorithm = "statistical";
        private Map<String, Object> parameters = Map.of();

        public Builder threshold(double threshold) {
            this.threshold = threshold;
            return this;
        }

        public Builder shardId(String shardId) {
            this.shardId = shardId != null ? shardId : "default";
            return this;
        }

        public Builder algorithm(String algorithm) {
            this.algorithm = algorithm != null ? algorithm : "statistical";
            return this;
        }

        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
            return this;
        }

        public ValidationAnomalyDetectionConfig build() {
            return new ValidationAnomalyDetectionConfig(
                threshold,
                shardId,
                algorithm,
                parameters
            );
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationAnomalyDetectionConfig that = (ValidationAnomalyDetectionConfig) o;
        return Double.compare(that.threshold, threshold) == 0 &&
               Objects.equals(shardId, that.shardId) &&
               Objects.equals(algorithm, that.algorithm) &&
               Objects.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(threshold, shardId, algorithm, parameters);
    }

    @Override
    public String toString() {
        return "ValidationAnomalyDetectionConfig{" +
               "threshold=" + threshold +
               ", shardId='" + shardId + '\'' +
               ", algorithm='" + algorithm + '\'' +
               ", parameters=" + parameters +
               '}';
    }
}
