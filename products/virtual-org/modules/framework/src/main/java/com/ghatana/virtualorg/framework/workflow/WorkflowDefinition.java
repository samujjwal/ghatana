package com.ghatana.virtualorg.framework.workflow;

import com.ghatana.virtualorg.framework.hierarchy.Role;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Declarative specification for an organizational workflow.
 *
 * <p><b>Purpose</b><br>
 * Defines the structure, steps, and transitions of a workflow that agents
 * execute. Workflows are declarative and can be persisted, versioned, and
 * replayed.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkflowDefinition sprint = WorkflowDefinition.builder()
 *     .id("sprint-planning")
 *     .name("Sprint Planning")
 *     .version("1.0.0")
 *     .addStep(WorkflowStep.of("load-backlog", "Load backlog items", "ProductManager"))
 *     .addStep(WorkflowStep.of("estimate", "Estimate complexity", "ArchitectLead"))
 *     .addStep(WorkflowStep.of("assign", "Assign capacity", "ProductManager"))
 *     .build();
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Part of virtual-org-framework product module. Provides declarative workflow
 * specifications that WorkflowEngine executes.
 *
 * <p><b>Thread Safety</b><br>
 * Immutable after construction.
 *
 * @see WorkflowEngine
 * @see WorkflowStep
 * @doc.type class
 * @doc.purpose Declarative workflow specification
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class WorkflowDefinition {
    
    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final String triggerEvent;
    private final List<WorkflowStep> steps;
    private final int timeoutSeconds;
    private final int maxRetries;
    
    private WorkflowDefinition(
            String id,
            String name,
            String version,
            String description,
            String triggerEvent,
            List<WorkflowStep> steps,
            int timeoutSeconds,
            int maxRetries) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.version = Objects.requireNonNull(version, "version cannot be null");
        this.description = description;
        // Allow triggerEvent to be optional for backward compatibility. If not provided,
        // default to a 'manual' trigger which represents ad-hoc/manual workflows.
        this.triggerEvent = triggerEvent != null ? triggerEvent : "manual";
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        this.timeoutSeconds = timeoutSeconds;
        this.maxRetries = maxRetries;
    }
    
    /**
     * Gets the workflow ID.
     */
    public String getId() {
        return id;
    }
    
    /**
     * Gets the workflow name.
     */
    public String getName() {
        return name;
    }
    
    /**
     * Gets the workflow version.
     */
    public String getVersion() {
        return version;
    }
    
    /**
     * Gets the workflow description.
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Gets the trigger event type that activates this workflow.
     *
     * @return trigger event type (never null)
     */
    public String getTriggerEvent() {
        return triggerEvent;
    }

    /**
     * Gets the workflow steps.
     */
    public List<WorkflowStep> getSteps() {
        return steps;
    }
    
    /**
     * Gets the timeout in seconds.
     */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
    
    /**
     * Gets the maximum number of retries.
     */
    public int getMaxRetries() {
        return maxRetries;
    }
    
    /**
     * Creates a builder for WorkflowDefinition.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for WorkflowDefinition.
     */
    public static class Builder {
        private String id;
        private String name;
        private String version;
        private String description;
        private String triggerEvent;
        private final List<WorkflowStep> steps = new ArrayList<>();
        private int timeoutSeconds = 300;
        private int maxRetries = 3;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder version(String version) {
            this.version = version;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder triggerEvent(String triggerEvent) {
            this.triggerEvent = triggerEvent;
            return this;
        }
        
        public Builder addStep(WorkflowStep step) {
            this.steps.add(step);
            return this;
        }
        
        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }
        
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
        
        public WorkflowDefinition build() {
            return new WorkflowDefinition(id, name, version, description, triggerEvent, steps, timeoutSeconds, maxRetries);
        }
    }
    
    /**
     * Represents a single step in a workflow.
     */
    public static final class WorkflowStep {
        private final String id;
        private final String description;
        private final String assignedRole;
        private final int timeoutSeconds;
        
        private WorkflowStep(String id, String description, String assignedRole, int timeoutSeconds) {
            this.id = Objects.requireNonNull(id, "id cannot be null");
            this.description = Objects.requireNonNull(description, "description cannot be null");
            this.assignedRole = Objects.requireNonNull(assignedRole, "assignedRole cannot be null");
            this.timeoutSeconds = timeoutSeconds;
        }
        
        public String getId() {
            return id;
        }
        
        public String getDescription() {
            return description;
        }
        
        public String getAssignedRole() {
            return assignedRole;
        }
        
        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }
        
        /**
         * Creates a workflow step.
         */
        public static WorkflowStep of(String id, String description, String assignedRole) {
            return new WorkflowStep(id, description, assignedRole, 60);
        }
        
        /**
         * Creates a workflow step with timeout.
         */
        public static WorkflowStep of(String id, String description, String assignedRole, int timeoutSeconds) {
            return new WorkflowStep(id, description, assignedRole, timeoutSeconds);
        }
    }
    
    @Override
    public String toString() {
        return "WorkflowDefinition{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", steps=" + steps.size() +
                '}';
    }
}
