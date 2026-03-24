package com.ghatana.yappc.domain.intent;

import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Product goal specification
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record GoalSpec(
    String id,
    String description,
    String category,
    int priority,
    List<String> successMetrics
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String description;
        private String category = "business";
        private int priority = 0;
        private List<String> successMetrics = List.of();
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder category(String category) {
            this.category = category;
            return this;
        }
        
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }
        
        public Builder successMetrics(List<String> successMetrics) {
            this.successMetrics = successMetrics;
            return this;
        }
        
        public GoalSpec build() {
            return new GoalSpec(id, description, category, priority, successMetrics);
        }
    }
}
