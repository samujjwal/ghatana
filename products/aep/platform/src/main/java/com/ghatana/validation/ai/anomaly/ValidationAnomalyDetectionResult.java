package com.ghatana.validation.ai.anomaly;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the result of an anomaly detection operation.
 
 *
 * @doc.type class
 * @doc.purpose Validation anomaly detection result
 * @doc.layer core
 * @doc.pattern Component
*/
public class ValidationAnomalyDetectionResult {
    private final String detectorId;
    private final List<DetectedAnomaly> anomalies;
    private final double confidenceScore;
    private final String status;
    private final Map<String, Object> metadata;

    public ValidationAnomalyDetectionResult(String detectorId, 
                                List<DetectedAnomaly> anomalies, 
                                double confidenceScore, 
                                String status,
                                Map<String, Object> metadata) {
        this.detectorId = Objects.requireNonNull(detectorId, "Detector ID cannot be null");
        this.anomalies = List.copyOf(Objects.requireNonNull(anomalies, "Anomalies list cannot be null"));
        this.confidenceScore = validateConfidence(confidenceScore);
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.metadata = Map.copyOf(Objects.requireNonNull(metadata, "Metadata cannot be null"));
    }

    private double validateConfidence(double confidence) {
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Confidence score must be between 0.0 and 1.0");
        }
        return confidence;
    }

    // Getters
    public String getDetectorId() { return detectorId; }
    public List<DetectedAnomaly> getAnomalies() { return List.copyOf(anomalies); }
    public double getConfidenceScore() { return confidenceScore; }
    public String getStatus() { return status; }
    public Map<String, Object> getMetadata() { return Map.copyOf(metadata); }

    // Builder pattern for easier construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String detectorId = "default-detector";
        private List<DetectedAnomaly> anomalies = List.of();
        private double confidenceScore = 0.0;
        private String status = "PENDING";
        private Map<String, Object> metadata = Map.of();
        
        public Builder detectorId(String detectorId) {
            this.detectorId = detectorId != null ? detectorId : "default-detector";
            return this;
        }
        
        public Builder anomalies(List<DetectedAnomaly> anomalies) {
            this.anomalies = anomalies != null ? List.copyOf(anomalies) : List.of();
            return this;
        }
        
        public Builder confidenceScore(double confidenceScore) {
            this.confidenceScore = confidenceScore;
            return this;
        }
        
        public Builder status(String status) {
            this.status = status != null ? status : "PENDING";
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
            return this;
        }
        
        public Builder shardId(String shardId) {
            Map<String, Object> updatedMetadata = new HashMap<>(this.metadata);
            updatedMetadata.put("shardId", shardId != null ? shardId : "default");
            this.metadata = Map.copyOf(updatedMetadata);
            return this;
        }
        
        public Builder isAnomaly(boolean isAnomaly) {
            Map<String, Object> updatedMetadata = new HashMap<>(this.metadata);
            updatedMetadata.put("isAnomaly", isAnomaly);
            this.metadata = Map.copyOf(updatedMetadata);
            return this;
        }
        
        public Builder score(double score) {
            Map<String, Object> updatedMetadata = new HashMap<>(this.metadata);
            updatedMetadata.put("score", score);
            this.metadata = Map.copyOf(updatedMetadata);
            return this;
        }
        
        public Builder message(String message) {
            Map<String, Object> updatedMetadata = new HashMap<>(this.metadata);
            updatedMetadata.put("message", message != null ? message : "");
            this.metadata = Map.copyOf(updatedMetadata);
            return this;
        }
        
        public Builder details(Map<String, Object> details) {
            Map<String, Object> updatedMetadata = new HashMap<>(this.metadata);
            updatedMetadata.put("details", details != null ? Map.copyOf(details) : Map.of());
            this.metadata = Map.copyOf(updatedMetadata);
            return this;
        }
        
        public Builder enoughData(boolean enoughData) {
            Map<String, Object> updatedMetadata = new HashMap<>(this.metadata);
            updatedMetadata.put("enoughData", enoughData);
            this.metadata = Map.copyOf(updatedMetadata);
            return this;
        }
        
        public ValidationAnomalyDetectionResult build() {
            return new ValidationAnomalyDetectionResult(
                detectorId,
                anomalies,
                confidenceScore,
                status,
                metadata
            );
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationAnomalyDetectionResult that = (ValidationAnomalyDetectionResult) o;
        return Double.compare(that.confidenceScore, confidenceScore) == 0 &&
               Objects.equals(detectorId, that.detectorId) &&
               Objects.equals(anomalies, that.anomalies) &&
               Objects.equals(status, that.status) &&
               Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(detectorId, anomalies, confidenceScore, status, metadata);
    }

    @Override
    public String toString() {
        return "ValidationAnomalyDetectionResult{" +
               "detectorId='" + detectorId + '\'' +
               ", anomalyCount=" + anomalies.size() +
               ", confidenceScore=" + confidenceScore +
               ", status='" + status + '\'' +
               '}';
    }
}
