package com.ghatana.yappc.domain.validate;

import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Policy specification for validation
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record PolicySpec(
    String id,
    String name,
    List<String> rules,
    Map<String, Object> config
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String name;
        private List<String> rules = List.of();
        private Map<String, Object> config = Map.of();
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder rules(List<String> rules) {
            this.rules = rules;
            return this;
        }
        
        public Builder config(Map<String, Object> config) {
            this.config = config;
            return this;
        }
        
        public PolicySpec build() {
            return new PolicySpec(id, name, rules, config);
        }
    }
}
