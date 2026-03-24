package com.ghatana.yappc.domain.shape;

import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Entity field specification
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record FieldSpec(
    String name,
    String type,
    boolean required,
    String description,
    Map<String, Object> validation
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String name;
        private String type;
        private boolean required = false;
        private String description;
        private Map<String, Object> validation = Map.of();
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder type(String type) {
            this.type = type;
            return this;
        }
        
        public Builder required(boolean required) {
            this.required = required;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder validation(Map<String, Object> validation) {
            this.validation = validation;
            return this;
        }
        
        public FieldSpec build() {
            return new FieldSpec(name, type, required, description, validation);
        }
    }
}
