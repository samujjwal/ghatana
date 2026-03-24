package com.ghatana.yappc.domain.shape;

/**
 * @doc.type record
 * @doc.purpose Workflow transition specification
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record WorkflowTransition(
    String fromStep,
    String toStep,
    String condition
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String fromStep;
        private String toStep;
        private String condition;
        
        public Builder fromStep(String fromStep) {
            this.fromStep = fromStep;
            return this;
        }
        
        public Builder toStep(String toStep) {
            this.toStep = toStep;
            return this;
        }
        
        public Builder condition(String condition) {
            this.condition = condition;
            return this;
        }
        
        public WorkflowTransition build() {
            return new WorkflowTransition(fromStep, toStep, condition);
        }
    }
}
