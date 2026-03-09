package com.ghatana.virtualorg.workflows;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Workflow definition with ordered steps.
 *
 * <p><b>Purpose</b><br>
 * Defines a complete workflow pipeline with steps and execution order.
 *
 * @doc.type class
 * @doc.purpose Workflow pipeline definition
 * @doc.layer product
 * @doc.pattern Builder
 */
public class WorkflowDefinition {
    private final List<WorkflowStep> steps;

    private WorkflowDefinition(Builder builder) {
        this.steps = List.copyOf(builder.steps);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<WorkflowStep> getSteps() {
        return steps;
    }

    public static class Builder {
        private final List<WorkflowStep> steps = new ArrayList<>();

        public Builder addStep(WorkflowStep step) {
            this.steps.add(Objects.requireNonNull(step));
            return this;
        }

        /**
         * Adds a conditional step to the workflow.
         * TODO: Implement conditional execution logic
         */
        public Builder addConditionalStep(String stepId, java.util.function.Predicate<Object> condition, WorkflowStep step) {
            // For now, just add the step unconditionally
            // TODO: Store condition and evaluate during execution
            this.steps.add(Objects.requireNonNull(step));
            return this;
        }

        /**
         * Adds an aggregation step.
         * TODO: Implement aggregation logic
         */
        @SuppressWarnings("unchecked")
        public <R> Builder addAggregationStep(String stepId, java.util.function.Function<R, Object> aggregator) {
            // TODO: Create aggregation step and add to workflow
            return this;
        }

        public WorkflowDefinition build() {
            return new WorkflowDefinition(this);
        }
    }
}
