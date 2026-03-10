/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.workflow;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * YAML-deserialized workflow template — the blueprint for a durable workflow.
 *
 * <p>Templates are loaded from {@code lifecycle-workflow-templates.yaml} at runtime by
 * {@link WorkflowMaterializer}. Each template is converted into a list of
 * {@link com.ghatana.platform.workflow.engine.DurableWorkflowEngine.StepDefinition}s
 * and registered in the workflow registry.
 *
 * @doc.type class
 * @doc.purpose YAML model for a YAPPC canonical workflow template
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class WorkflowTemplate {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("version")
    private String version = "1.0.0";

    @JsonProperty("description")
    private String description;

    @JsonProperty("category")
    private String category;

    @JsonProperty("steps")
    private List<WorkflowStepTemplate> steps = Collections.emptyList();

    // Jackson requires no-arg constructor
    public WorkflowTemplate() {}

    public WorkflowTemplate(String id, String name, String version, String description,
                             String category, List<WorkflowStepTemplate> steps) {
        this.id = id;
        this.name = name;
        this.version = version != null ? version : "1.0.0";
        this.description = description;
        this.category = category;
        this.steps = steps != null ? steps : Collections.emptyList();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public List<WorkflowStepTemplate> getSteps() { return steps; }

    @Override
    public String toString() {
        return "WorkflowTemplate{id='" + id + "', name='" + name
                + "', steps=" + steps.size() + "}";
    }
}
