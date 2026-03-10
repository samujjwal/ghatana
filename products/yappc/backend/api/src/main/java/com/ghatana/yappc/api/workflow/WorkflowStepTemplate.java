/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.workflow;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Map;

/**
 * YAML-deserialized descriptor for a single step within a {@link WorkflowTemplate}.
 *
 * <p>Example YAML fragment:
 * <pre>
 * steps:
 *   - id: phase-intent
 *     type: lifecycle-phase-advance
 *     config:
 *       fromPhase: IDEATION
 *       toPhase: INTENT
 * </pre>
 *
 * @doc.type class
 * @doc.purpose YAML model for a single workflow step descriptor
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class WorkflowStepTemplate {

    @JsonProperty("id")
    private String id;

    @JsonProperty("type")
    private String type;

    @JsonProperty("config")
    private Map<String, String> config = Collections.emptyMap();

    // Jackson requires no-arg constructor
    public WorkflowStepTemplate() {}

    public WorkflowStepTemplate(String id, String type, Map<String, String> config) {
        this.id = id;
        this.type = type;
        this.config = config != null ? config : Collections.emptyMap();
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public Map<String, String> getConfig() { return config; }

    @Override
    public String toString() {
        return "WorkflowStepTemplate{id='" + id + "', type='" + type + "'}";
    }
}
