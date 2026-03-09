package com.ghatana.virtualorg.framework.workflow;

import com.ghatana.virtualorg.framework.hierarchy.Role;

import java.util.Objects;

/**
 * Single step in an organizational workflow.
 *
 * <p><b>Purpose</b><br>
 * Represents one execution unit in a workflow, assigned to a specific role.
 * Steps are executed sequentially (or in parallel for future enhancements).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkflowStep step = WorkflowStep.builder()
 *     .id("review-code")
 *     .name("Review Pull Request")
 *     .description("Review code changes and approve/reject")
 *     .agentRole(Role.of("SeniorEngineer", Layer.INDIVIDUAL_CONTRIBUTOR))
 *     .build();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable after construction.
 *
 * @see WorkflowDefinition
 * @see Role
 * @doc.type class
 * @doc.purpose Single workflow step specification
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class WorkflowStep {
    
    private final String id;
    private final String name;
    private final String description;
    private final Role agentRole;
    private final int timeoutSeconds;
    private final int retries;
    
    private WorkflowStep(
            String id,
            String name,
            String description,
            Role agentRole,
            int timeoutSeconds,
            int retries) {
        this.id = Objects.requireNonNull(id, "id required");
        this.name = Objects.requireNonNull(name, "name required");
        this.description = description != null ? description : "";
        this.agentRole = Objects.requireNonNull(agentRole, "agentRole required");
        this.timeoutSeconds = timeoutSeconds;
        this.retries = retries;
    }
    
    /**
     * Gets step identifier.
     *
     * @return step ID (never null)
     */
    public String getId() {
        return id;
    }
    
    /**
     * Gets step name.
     *
     * @return step name (never null)
     */
    public String getName() {
        return name;
    }
    
    /**
     * Gets step description.
     *
     * @return description (never null, may be empty)
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Gets agent role responsible for executing this step.
     *
     * @return agent role (never null)
     */
    public Role getAgentRole() {
        return agentRole;
    }
    
    /**
     * Gets step timeout in seconds.
     *
     * @return timeout in seconds
     */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
    
    /**
     * Gets maximum number of retries on failure.
     *
     * @return max retries
     */
    public int getRetries() {
        return retries;
    }
    
    /**
     * Creates builder for WorkflowStep.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for WorkflowStep.
     */
    public static class Builder {
        private String id;
        private String name;
        private String description = "";
        private Role agentRole;
        private int timeoutSeconds = 60;
        private int retries = 2;
        
        /**
         * Sets step identifier.
         *
         * @param id step ID
         * @return this builder
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        /**
         * Sets step name.
         *
         * @param name step name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        /**
         * Sets step description.
         *
         * @param description step description
         * @return this builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        /**
         * Sets agent role for this step.
         *
         * @param agentRole agent role
         * @return this builder
         */
        public Builder agentRole(Role agentRole) {
            this.agentRole = agentRole;
            return this;
        }
        
        /**
         * Sets step timeout.
         *
         * @param timeoutSeconds timeout in seconds
         * @return this builder
         */
        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }
        
        /**
         * Sets max retries on failure.
         *
         * @param retries max retries
         * @return this builder
         */
        public Builder retries(int retries) {
            this.retries = retries;
            return this;
        }
        
        /**
         * Builds WorkflowStep.
         *
         * @return workflow step
         * @throws IllegalArgumentException if required fields are missing
         */
        public WorkflowStep build() {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id required");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name required");
            }
            if (agentRole == null) {
                throw new IllegalArgumentException("agentRole required");
            }
            return new WorkflowStep(id, name, description, agentRole, timeoutSeconds, retries);
        }
    }
    
    @Override
    public String toString() {
        return "WorkflowStep{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", agentRole=" + agentRole +
                '}';
    }
}

