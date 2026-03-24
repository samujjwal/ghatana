package com.ghatana.yappc.domain.shape;

import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Workflow step specification
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record WorkflowStep(
    String id,
    String name,
    String type,
    Map<String, Object> config
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String name;
        private String type;
        private Map<String, Object> config = Map.of();
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder type(String type) {
            this.type = type;
            return this;
        }
        
        public Builder config(Map<String, Object> config) {
            this.config = config;
            return this;
        }
        
        public WorkflowStep build() {
            return new WorkflowStep(id, name, type, config);
        }
    }
}
