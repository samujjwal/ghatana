package com.ghatana.yappc.domain.learn;

import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Detected pattern in observations
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record Pattern(
    String id,
    String type,
    String description,
    double confidence,
    List<String> evidence
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String type;
        private String description;
        private double confidence = 0.0;
        private List<String> evidence = List.of();
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder type(String type) {
            this.type = type;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }
        
        public Builder evidence(List<String> evidence) {
            this.evidence = evidence;
            return this;
        }
        
        public Pattern build() {
            return new Pattern(id, type, description, confidence, evidence);
        }
    }
}
