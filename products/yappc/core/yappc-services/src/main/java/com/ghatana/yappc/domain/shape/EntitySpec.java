package com.ghatana.yappc.domain.shape;

import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Domain entity specification
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record EntitySpec(
    String name,
    String description,
    List<FieldSpec> fields,
    List<String> behaviors
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String name;
        private String description;
        private List<FieldSpec> fields = List.of();
        private List<String> behaviors = List.of();
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder fields(List<FieldSpec> fields) {
            this.fields = fields;
            return this;
        }
        
        public Builder behaviors(List<String> behaviors) {
            this.behaviors = behaviors;
            return this;
        }
        
        public EntitySpec build() {
            return new EntitySpec(name, description, fields, behaviors);
        }
    }
}
