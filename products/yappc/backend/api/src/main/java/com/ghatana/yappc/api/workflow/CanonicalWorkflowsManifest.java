/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.workflow;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Top-level YAML model for {@code lifecycle-workflow-templates.yaml}.
 *
 * <p>Example structure:
 * <pre>
 * workflows:
 *   - id: new-feature
 *     name: "New Feature Development"
 *     steps:
 *       - id: phase-intent
 *         type: lifecycle-phase-advance
 *         config:
 *           fromPhase: IDEATION
 *           toPhase: INTENT
 * </pre>
 *
 * @doc.type class
 * @doc.purpose YAML root model wrapping all canonical workflow templates
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class CanonicalWorkflowsManifest {

    @JsonProperty("workflows")
    private List<WorkflowTemplate> workflows = Collections.emptyList();

    // Jackson requires no-arg constructor
    public CanonicalWorkflowsManifest() {}

    public CanonicalWorkflowsManifest(List<WorkflowTemplate> workflows) {
        this.workflows = workflows != null ? workflows : Collections.emptyList();
    }

    public List<WorkflowTemplate> getWorkflows() { return workflows; }

    @Override
    public String toString() {
        return "CanonicalWorkflowsManifest{workflows=" + workflows.size() + "}";
    }
}
