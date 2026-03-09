package com.ghatana.virtualorg.workflows;

import java.time.Duration;
import java.util.*;

/**
 * Metadata describing a workflow's characteristics and requirements.
 *
 * <p><b>Purpose</b><br>
 * Provides declarative information about workflows:
 * - Name, description, and version
 * - Required inputs and their validation rules
 * - Expected outputs
 * - Performance characteristics (estimated duration)
 * - Required resources (agent roles, permissions)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkflowMetadata metadata = WorkflowMetadata.builder()
 *     .withName("code-review")
 *     .withDescription("Multi-agent code review workflow")
 *     .withVersion("1.0.0")
 *     .withRequiredInput("pullRequest", "Pull request data", PullRequest.class)
 *     .withOptionalInput("reviewers", "Specific reviewers", List.class)
 *     .withExpectedOutput("approvalStatus", "Review decision", String.class)
 *     .withEstimatedDuration(Duration.ofMinutes(15))
 *     .withRequiredRole("SENIOR_ENGINEER")
 *     .withRequiredRole("TEAM_LEAD")
 *     .build();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable after build() - safe for concurrent access.
 *
 * @doc.type class
 * @doc.purpose Workflow metadata descriptor
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class WorkflowMetadata {

    private final String name;
    private final String description;
    private final String version;
    private final Map<String, InputSpec> requiredInputs;
    private final Map<String, InputSpec> optionalInputs;
    private final Map<String, OutputSpec> outputs;
    private final Duration estimatedDuration;
    private final Set<String> requiredRoles;
    private final Set<String> tags;

    private WorkflowMetadata(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "name");
        this.description = builder.description != null ? builder.description : "";
        this.version = builder.version != null ? builder.version : "1.0.0";
        this.requiredInputs = Map.copyOf(builder.requiredInputs);
        this.optionalInputs = Map.copyOf(builder.optionalInputs);
        this.outputs = Map.copyOf(builder.outputs);
        this.estimatedDuration = builder.estimatedDuration;
        this.requiredRoles = Set.copyOf(builder.requiredRoles);
        this.tags = Set.copyOf(builder.tags);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getVersion() {
        return version;
    }

    public Map<String, InputSpec> getRequiredInputs() {
        return requiredInputs;
    }

    public Map<String, InputSpec> getOptionalInputs() {
        return optionalInputs;
    }

    public Map<String, OutputSpec> getOutputs() {
        return outputs;
    }

    public Optional<Duration> getEstimatedDuration() {
        return Optional.ofNullable(estimatedDuration);
    }

    public Set<String> getRequiredRoles() {
        return requiredRoles;
    }

    public Set<String> getTags() {
        return tags;
    }

    /**
     * Creates a new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Specification for an input parameter.
     */
    public static final class InputSpec {
        private final String name;
        private final String description;
        private final Class<?> type;

        public InputSpec(String name, String description, Class<?> type) {
            this.name = name;
            this.description = description;
            this.type = type;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public Class<?> getType() { return type; }
    }

    /**
     * Specification for an output value.
     */
    public static final class OutputSpec {
        private final String name;
        private final String description;
        private final Class<?> type;

        public OutputSpec(String name, String description, Class<?> type) {
            this.name = name;
            this.description = description;
            this.type = type;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public Class<?> getType() { return type; }
    }

    /**
     * Builder for WorkflowMetadata.
     */
    public static final class Builder {
        private String name;
        private String description;
        private String version;
        private final Map<String, InputSpec> requiredInputs = new HashMap<>();
        private final Map<String, InputSpec> optionalInputs = new HashMap<>();
        private final Map<String, OutputSpec> outputs = new HashMap<>();
        private Duration estimatedDuration;
        private final Set<String> requiredRoles = new HashSet<>();
        private final Set<String> tags = new HashSet<>();

        private Builder() {}

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder withVersion(String version) {
            this.version = version;
            return this;
        }

        public Builder withRequiredInput(String name, String description, Class<?> type) {
            this.requiredInputs.put(name, new InputSpec(name, description, type));
            return this;
        }

        public Builder withOptionalInput(String name, String description, Class<?> type) {
            this.optionalInputs.put(name, new InputSpec(name, description, type));
            return this;
        }

        public Builder withExpectedOutput(String name, String description, Class<?> type) {
            this.outputs.put(name, new OutputSpec(name, description, type));
            return this;
        }

        public Builder withEstimatedDuration(Duration duration) {
            this.estimatedDuration = duration;
            return this;
        }

        public Builder withRequiredRole(String role) {
            this.requiredRoles.add(role);
            return this;
        }

        public Builder withTag(String tag) {
            this.tags.add(tag);
            return this;
        }

        public WorkflowMetadata build() {
            return new WorkflowMetadata(this);
        }
    }
}
