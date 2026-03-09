package com.ghatana.yappc.domain.validate;

import java.util.Set;

/**
 * @doc.type record
 * @doc.purpose Configuration for validation execution
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record ValidationConfig(
    Set<String> includedTags,
    Set<String> excludedIds,
    boolean failFast
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static ValidationConfig defaultConfig() {
        return new ValidationConfig(Set.of(), Set.of(), false);
    }
    
    public static class Builder {
        private Set<String> includedTags = Set.of();
        private Set<String> excludedIds = Set.of();
        private boolean failFast = false;
        
        public Builder includedTags(Set<String> includedTags) {
            this.includedTags = includedTags;
            return this;
        }
        
        public Builder excludedIds(Set<String> excludedIds) {
            this.excludedIds = excludedIds;
            return this;
        }
        
        public Builder failFast(boolean failFast) {
            this.failFast = failFast;
            return this;
        }
        
        public ValidationConfig build() {
            return new ValidationConfig(includedTags, excludedIds, failFast);
        }
    }
}
