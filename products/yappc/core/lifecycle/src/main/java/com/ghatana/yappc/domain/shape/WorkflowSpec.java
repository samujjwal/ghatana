package com.ghatana.yappc.domain.shape;

import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Workflow specification
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record WorkflowSpec(
    String id,
    String name,
    String description,
    List<WorkflowStep> steps,
    List<WorkflowTransition> transitions
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String name;
        private String description;
        private List<WorkflowStep> steps = List.of();
        private List<WorkflowTransition> transitions = List.of();
        
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
        
        public Builder steps(List<WorkflowStep> steps) {
            this.steps = steps;
            return this;
        }
        
        public Builder transitions(List<WorkflowTransition> transitions) {
            this.transitions = transitions;
            return this;
        }
        
        public WorkflowSpec build() {
            return new WorkflowSpec(id, name, description, steps, transitions);
        }
    }
}
