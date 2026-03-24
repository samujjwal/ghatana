package com.ghatana.products.yappc.domain.task;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Task definition loaded from configuration or registered at runtime.
 * Defines everything needed to discover, validate, and execute a task.
 *
 * @doc.type record
 * @doc.purpose Task metadata and execution contract
 * @doc.layer product
 * @doc.pattern ValueObject, Plugin SPI
 */
public record TaskDefinition(
        @NotNull String id,
        @NotNull String name,
        @NotNull String description,
        @NotNull String domain,
        @NotNull SDLCPhase phase,
        @NotNull List<String> requiredCapabilities,
        @NotNull Map<String, ParameterSpec> parameters,
        @NotNull TaskComplexity complexity,
        @NotNull List<TaskDependency> dependencies,
        @NotNull Map<String, Object> metadata
) {
    public TaskDefinition {
        Objects.requireNonNull(id, "Task ID cannot be null");
        Objects.requireNonNull(name, "Task name cannot be null");
        Objects.requireNonNull(requiredCapabilities, "Required capabilities cannot be null");

        if (requiredCapabilities.isEmpty()) {
            throw new IllegalArgumentException("Task must require at least one capability");
        }
    }

    /**
     * Checks if this task requires the specified capability.
     *
     * @param capability The capability to check
     * @return true if required
     */
    public boolean requiresCapability(@NotNull String capability) {
        return requiredCapabilities.contains(capability);
    }

    /**
     * Checks if this task has all dependencies satisfied.
     *
     * @param completedTasks Set of completed task IDs
     * @return true if all dependencies are met
     */
    public boolean areDependenciesSatisfied(@NotNull List<String> completedTasks) {
        return dependencies.stream()
                .filter(TaskDependency::required)
                .allMatch(dep -> completedTasks.contains(dep.taskId()));
    }

    /**
     * Creates a builder for TaskDefinition.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for TaskDefinition.
     */
    public static class Builder {
        private String id;
        private String name;
        private String description;
        private String domain;
        private SDLCPhase phase;
        private List<String> requiredCapabilities = List.of();
        private Map<String, ParameterSpec> parameters = Map.of();
        private TaskComplexity complexity = TaskComplexity.SIMPLE;
        private List<TaskDependency> dependencies = List.of();
        private Map<String, Object> metadata = Map.of();

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

        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        public Builder phase(SDLCPhase phase) {
            this.phase = phase;
            return this;
        }

        public Builder requiredCapabilities(List<String> requiredCapabilities) {
            this.requiredCapabilities = List.copyOf(requiredCapabilities);
            return this;
        }

        public Builder parameters(Map<String, ParameterSpec> parameters) {
            this.parameters = Map.copyOf(parameters);
            return this;
        }

        public Builder complexity(TaskComplexity complexity) {
            this.complexity = complexity;
            return this;
        }

        public Builder dependencies(List<TaskDependency> dependencies) {
            this.dependencies = List.copyOf(dependencies);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = Map.copyOf(metadata);
            return this;
        }

        public TaskDefinition build() {
            return new TaskDefinition(
                    id,
                    name,
                    description,
                    domain,
                    phase,
                    requiredCapabilities,
                    parameters,
                    complexity,
                    dependencies,
                    metadata
            );
        }
    }
}
