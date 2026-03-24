package com.ghatana.yappc.domain.intent;

import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Project constraint specification
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record ConstraintSpec(
    String id,
    String type,
    String description,
    String severity,
    Map<String, Object> details
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String type;
        private String description;
        private String severity = "soft";
        private Map<String, Object> details = Map.of();
        
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
        
        public Builder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }
        
        public ConstraintSpec build() {
            return new ConstraintSpec(id, type, description, severity, details);
        }
    }
}
