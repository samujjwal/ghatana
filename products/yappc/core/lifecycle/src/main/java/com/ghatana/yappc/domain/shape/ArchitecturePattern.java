package com.ghatana.yappc.domain.shape;

import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Architecture pattern specification
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record ArchitecturePattern(
    String name,
    String description,
    List<String> components,
    Map<String, Object> properties
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String name;
        private String description;
        private List<String> components = List.of();
        private Map<String, Object> properties = Map.of();
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder components(List<String> components) {
            this.components = components;
            return this;
        }
        
        public Builder properties(Map<String, Object> properties) {
            this.properties = properties;
            return this;
        }
        
        public ArchitecturePattern build() {
            return new ArchitecturePattern(name, description, components, properties);
        }
    }
}
