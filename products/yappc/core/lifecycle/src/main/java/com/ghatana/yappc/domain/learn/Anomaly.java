package com.ghatana.yappc.domain.learn;

import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Detected anomaly in observations
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record Anomaly(
    String id,
    String type,
    String description,
    String severity,
    List<String> affectedComponents
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String type;
        private String description;
        private String severity = "low";
        private List<String> affectedComponents = List.of();
        
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
        
        public Builder severity(String severity) {
            this.severity = severity;
            return this;
        }
        
        public Builder affectedComponents(List<String> affectedComponents) {
            this.affectedComponents = affectedComponents;
            return this;
        }
        
        public Anomaly build() {
            return new Anomaly(id, type, description, severity, affectedComponents);
        }
    }
}
