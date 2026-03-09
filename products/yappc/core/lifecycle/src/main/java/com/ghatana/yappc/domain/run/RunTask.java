package com.ghatana.yappc.domain.run;

import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Individual run task specification
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record RunTask(
    String id,
    String type,
    String name,
    Map<String, Object> config,
    List<String> dependencies
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String type;
        private String name;
        private Map<String, Object> config = Map.of();
        private List<String> dependencies = List.of();
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder type(String type) {
            this.type = type;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder config(Map<String, Object> config) {
            this.config = config;
            return this;
        }
        
        public Builder dependencies(List<String> dependencies) {
            this.dependencies = dependencies;
            return this;
        }
        
        public RunTask build() {
            return new RunTask(id, type, name, config, dependencies);
        }
    }
}
