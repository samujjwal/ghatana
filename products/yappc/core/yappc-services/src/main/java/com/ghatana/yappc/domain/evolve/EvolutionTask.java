package com.ghatana.yappc.domain.evolve;

import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Individual evolution task
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record EvolutionTask(
    String id,
    String type,
    String description,
    int priority,
    List<String> dependencies,
    Map<String, Object> details
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String type;
        private String description;
        private int priority = 0;
        private List<String> dependencies = List.of();
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
        
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }
        
        public Builder dependencies(List<String> dependencies) {
            this.dependencies = dependencies;
            return this;
        }
        
        public Builder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }
        
        public EvolutionTask build() {
            return new EvolutionTask(id, type, description, priority, dependencies, details);
        }
    }
}
