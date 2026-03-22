/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Immutable, versioned workflow definition.
 *
 * <p>A workflow definition is the stable blueprint for a workflow. It describes
 * the ordered steps, trigger type, timeout, saga policy, and metadata. Definitions
 * are registered in a {@link WorkflowDefinitionRegistry} and instantiated into
 * runtime runs by the workflow engine.
 *
 * @doc.type record
 * @doc.purpose Versioned workflow blueprint with steps and trigger configuration
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record WorkflowDefinition(
    @NotNull String workflowId,
    @NotNull String name,
    int version,
    @NotNull WorkflowTriggerType triggerType,
    @Nullable String triggerFilter,
    @NotNull List<WorkflowStepDefinition> steps,
    @NotNull String entryStepId,
    @Nullable Duration timeout,
    @NotNull com.ghatana.platform.workflow.SagaPolicy sagaPolicy,
    @NotNull Map<String, String> metadata,
    @NotNull Instant createdAt,
    boolean enabled
) {

    public WorkflowDefinition {
        Objects.requireNonNull(workflowId, "workflowId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(triggerType, "triggerType");
        Objects.requireNonNull(steps, "steps");
        Objects.requireNonNull(entryStepId, "entryStepId");
        Objects.requireNonNull(sagaPolicy, "sagaPolicy");
        steps = List.copyOf(steps);
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Looks up a step definition by its ID.
     *
     * @param stepId step identifier
     * @return the step definition, or empty if not found
     */
    public Optional<WorkflowStepDefinition> findStep(String stepId) {
        return steps.stream()
            .filter(s -> s.stepId().equals(stepId))
            .findFirst();
    }

    /**
     * Convenience builder for creating definitions programmatically.
     */
    public static Builder builder(String workflowId, String name) {
        return new Builder(workflowId, name);
    }

    public static final class Builder {
        private final String workflowId;
        private final String name;
        private int version = 1;
        private WorkflowTriggerType triggerType = WorkflowTriggerType.API;
        private String triggerFilter;
        private final List<WorkflowStepDefinition> steps = new ArrayList<>();
        private String entryStepId;
        private Duration timeout;
        private com.ghatana.platform.workflow.SagaPolicy sagaPolicy =
            com.ghatana.platform.workflow.SagaPolicy.NONE;
        private final Map<String, String> metadata = new LinkedHashMap<>();
        private boolean enabled = true;

        private Builder(String workflowId, String name) {
            this.workflowId = workflowId;
            this.name = name;
        }

        public Builder version(int v) { this.version = v; return this; }
        public Builder triggerType(WorkflowTriggerType t) { this.triggerType = t; return this; }
        public Builder triggerFilter(String f) { this.triggerFilter = f; return this; }
        public Builder addStep(WorkflowStepDefinition s) { this.steps.add(s); return this; }
        public Builder entryStepId(String id) { this.entryStepId = id; return this; }
        public Builder timeout(Duration d) { this.timeout = d; return this; }
        public Builder sagaPolicy(com.ghatana.platform.workflow.SagaPolicy p) { this.sagaPolicy = p; return this; }
        public Builder metadata(String k, String v) { this.metadata.put(k, v); return this; }
        public Builder enabled(boolean e) { this.enabled = e; return this; }

        public WorkflowDefinition build() {
            if (entryStepId == null && !steps.isEmpty()) {
                entryStepId = steps.getFirst().stepId();
            }
            return new WorkflowDefinition(
                workflowId, name, version, triggerType, triggerFilter,
                steps, entryStepId, timeout, sagaPolicy, metadata,
                Instant.now(), enabled);
        }
    }
}
