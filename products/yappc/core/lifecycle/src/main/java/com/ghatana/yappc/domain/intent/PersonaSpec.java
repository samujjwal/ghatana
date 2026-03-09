package com.ghatana.yappc.domain.intent;

import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose User persona specification
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record PersonaSpec(
    String id,
    String name,
    String description,
    List<String> needs,
    List<String> painPoints,
    Map<String, String> attributes
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String name;
        private String description;
        private List<String> needs = List.of();
        private List<String> painPoints = List.of();
        private Map<String, String> attributes = Map.of();
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder needs(List<String> needs) {
            this.needs = needs;
            return this;
        }
        
        public Builder painPoints(List<String> painPoints) {
            this.painPoints = painPoints;
            return this;
        }
        
        public Builder attributes(Map<String, String> attributes) {
            this.attributes = attributes;
            return this;
        }
        
        public PersonaSpec build() {
            return new PersonaSpec(id, name, description, needs, painPoints, attributes);
        }
    }
}
