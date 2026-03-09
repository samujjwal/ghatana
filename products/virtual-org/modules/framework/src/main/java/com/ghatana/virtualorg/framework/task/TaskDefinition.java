package com.ghatana.virtualorg.framework.task;

import com.ghatana.platform.types.identity.Identifier;
import com.ghatana.platform.domain.auth.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable definition of a task type in virtual organizations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines the blueprint for tasks that can be instantiated and assigned to
 * agents. Includes metadata for effort estimation, skill requirements, and SLA
 * tracking.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * TaskDefinition codingTask = TaskDefinition.builder()
 *     .tenantId(tenantId)
 *     .name("Implement Feature")
 *     .description("Write code for new feature")
 *     .estimatedDuration(Duration.ofHours(8))
 *     .requiredSkills(Map.of("java", "senior", "design", "mid"))
 *     .build();
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Immutable - all fields final. Thread-safe.
 *
 * @see TaskInstance
 * @doc.type record
 * @doc.purpose Immutable task type definition
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record TaskDefinition(
        Identifier id,
        TenantId tenantId,
        String name,
        String description,
        Duration estimatedDuration,
        Map<String, String> requiredSkills,
        TaskPriority priority,
        Instant createdAt
        ) {

    /**
     * Canonical constructor with validation.
     */
    public TaskDefinition        {
        Objects.requireNonNull(id, "Task definition ID must not be null");
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(name, "Task name must not be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("Task name must not be blank");
        }

        // Default values for optional fields
        description = description != null ? description : "";
        estimatedDuration = estimatedDuration != null ? estimatedDuration : Duration.ofHours(1);
        requiredSkills = requiredSkills != null ? Map.copyOf(requiredSkills) : Map.of();
        priority = priority != null ? priority : TaskPriority.MEDIUM;
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    /**
     * Creates builder for task definition.
     *
     * @param tenantId tenant owning task definitions
     * @return new builder instance
     */
    public static Builder builder(TenantId tenantId) {
        return new Builder(tenantId);
    }

    /**
     * Builder for TaskDefinition.
     */
    public static class Builder {

        private final TenantId tenantId;
        private Identifier id = Identifier.random();
        private String name;
        private String description;
        private Duration estimatedDuration;
        private Map<String, String> requiredSkills;
        private TaskPriority priority;
        private Instant createdAt;

        private Builder(TenantId tenantId) {
            this.tenantId = tenantId;
        }

        public Builder id(Identifier id) {
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

        public Builder estimatedDuration(Duration duration) {
            this.estimatedDuration = duration;
            return this;
        }

        public Builder requiredSkills(Map<String, String> skills) {
            this.requiredSkills = skills;
            return this;
        }

        public Builder priority(TaskPriority priority) {
            this.priority = priority;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public TaskDefinition build() {
            return new TaskDefinition(
                    id,
                    tenantId,
                    name,
                    description,
                    estimatedDuration,
                    requiredSkills,
                    priority,
                    createdAt
            );
        }
    }
}
