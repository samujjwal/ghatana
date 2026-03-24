package com.ghatana.yappc.domain.learn;

import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Recommendation for improvement
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record Recommendation(
    String id,
    String type,
    String description,
    int priority,
    double estimatedImpact,
    List<String> actionItems
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String type;
        private String description;
        private int priority = 0;
        private double estimatedImpact = 0.0;
        private List<String> actionItems = List.of();
        
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
        
        public Builder estimatedImpact(double estimatedImpact) {
            this.estimatedImpact = estimatedImpact;
            return this;
        }
        
        public Builder actionItems(List<String> actionItems) {
            this.actionItems = actionItems;
            return this;
        }
        
        public Recommendation build() {
            return new Recommendation(id, type, description, priority, estimatedImpact, actionItems);
        }
    }
}
