package com.ghatana.virtualorg.workflows;

import com.ghatana.agent.Agent;
import java.util.List;
import java.util.Objects;

/**
 * Workflow step definition.
 *
 * <p><b>Purpose</b><br>
 * Represents a single step in a workflow pipeline with executor and dependencies.
 *
 * @doc.type class
 * @doc.purpose Workflow step definition for pipeline orchestration
 * @doc.layer product
 * @doc.pattern Builder
 */
public class WorkflowStep {
    private final String stepId;
    private final String stepName;
    private final Agent executor;
    private final String taskDescription;
    private final List<String> dependsOn;

    private WorkflowStep(Builder builder) {
        this.stepId = Objects.requireNonNull(builder.stepId);
        this.stepName = Objects.requireNonNull(builder.stepName);
        this.executor = Objects.requireNonNull(builder.executor);
        this.taskDescription = Objects.requireNonNull(builder.taskDescription);
        this.dependsOn = builder.dependsOn != null ? List.copyOf(builder.dependsOn) : List.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getStepId() { return stepId; }
    public String getStepName() { return stepName; }
    public Agent getExecutor() { return executor; }
    public String getTaskDescription() { return taskDescription; }
    public List<String> getDependsOn() { return dependsOn; }

    public static class Builder {
        private String stepId;
        private String stepName;
        private Agent executor;
        private String taskDescription;
        private List<String> dependsOn;

        public Builder stepId(String stepId) {
            this.stepId = stepId;
            return this;
        }

        public Builder stepName(String stepName) {
            this.stepName = stepName;
            return this;
        }

        public Builder executor(Agent executor) {
            this.executor = executor;
            return this;
        }

        public Builder taskDescription(String taskDescription) {
            this.taskDescription = taskDescription;
            return this;
        }

        public Builder dependsOn(List<String> dependsOn) {
            this.dependsOn = dependsOn;
            return this;
        }

        public WorkflowStep build() {
            return new WorkflowStep(this);
        }
    }
}
