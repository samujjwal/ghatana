package com.ghatana.yappc.domain.shape;

import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Bounded context specification (DDD)
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record BoundedContextSpec(
    String name,
    String description,
    List<String> entities
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String name;
        private String description;
        private List<String> entities = List.of();
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder entities(List<String> entities) {
            this.entities = entities;
            return this;
        }
        
        public BoundedContextSpec build() {
            return new BoundedContextSpec(name, description, entities);
        }
    }
}
