package com.ghatana.yappc.domain;

import java.util.Map;
import java.util.Set;

/**
 * @doc.type record
 * @doc.purpose Controls runtime behavior and feature inclusions
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record ContextSpec(
    String tenantId,
    String environment,
    Map<String, Boolean> features,
    Set<String> excludedSteps,
    Map<String, String> overrides
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String tenantId;
        private String environment = "development";
        private Map<String, Boolean> features = Map.of();
        private Set<String> excludedSteps = Set.of();
        private Map<String, String> overrides = Map.of();
        
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }
        
        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }
        
        public Builder features(Map<String, Boolean> features) {
            this.features = features;
            return this;
        }
        
        public Builder excludedSteps(Set<String> excludedSteps) {
            this.excludedSteps = excludedSteps;
            return this;
        }
        
        public Builder overrides(Map<String, String> overrides) {
            this.overrides = overrides;
            return this;
        }
        
        public ContextSpec build() {
            return new ContextSpec(tenantId, environment, features, excludedSteps, overrides);
        }
    }
}
