/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Immutable value object representing a single YAPPC lifecycle stage loaded from
 * {@code config/lifecycle/stages.yaml}.
 *
 * <p>Each {@code StageSpec} captures:
 * <ul>
 *   <li>Stage identity ({@code id}, {@code name}, {@code order})</li>
 *   <li>Entry and exit criteria (human-readable checklist items)</li>
 *   <li>Required artifacts that MUST be produced before the stage can exit</li>
 *   <li>Quality gate definitions (optional, may be empty)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Value object for a YAPPC lifecycle stage definition
 * @doc.layer product
 * @doc.pattern ValueObject
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class StageSpec {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("order")
    private int order;

    @JsonProperty("icon")
    private String icon;

    @JsonProperty("color")
    private String color;

    @JsonProperty("required")
    private boolean required;

    @JsonProperty("entry_criteria")
    private List<String> entryCriteria;

    @JsonProperty("exit_criteria")
    private List<String> exitCriteria;

    @JsonProperty("typical_activities")
    private List<String> typicalActivities;

    @JsonProperty("artifacts")
    private List<String> artifacts;

    @JsonProperty("quality_gates")
    private List<String> qualityGates;

    @JsonProperty("agent_assignments")
    private List<String> agentAssignments;

    /** Required by Jackson. */
    public StageSpec() {}

    // ─── Accessors ────────────────────────────────────────────────────────────

    /** Stable stage identifier (e.g., {@code "intent"}, {@code "plan"}). */
    public String getId()          { return id; }

    /** Human-readable stage name. */
    public String getName()        { return name; }

    /** One-line description of the stage's purpose. */
    public String getDescription() { return description; }

    /** 1-based ordering index within the lifecycle. */
    public int getOrder()          { return order; }

    /** Material icon name for UI rendering. */
    public String getIcon()        { return icon; }

    /** Hex colour for UI rendering (e.g., {@code "#FFD54F"}). */
    public String getColor()       { return color; }

    /** Whether this stage is mandatory in every project lifecycle. */
    public boolean isRequired()    { return required; }

    /** Checklist items that MUST be true to enter this stage. */
    public List<String> getEntryCriteria() {
        return entryCriteria == null ? List.of() : List.copyOf(entryCriteria);
    }

    /** Checklist items that MUST be true to exit this stage. */
    public List<String> getExitCriteria() {
        return exitCriteria == null ? List.of() : List.copyOf(exitCriteria);
    }

    /** Typical activities carried out during this stage. */
    public List<String> getTypicalActivities() {
        return typicalActivities == null ? List.of() : List.copyOf(typicalActivities);
    }

    /** Artifact IDs that must be produced before the stage can be completed. */
    public List<String> getArtifacts() {
        return artifacts == null ? List.of() : List.copyOf(artifacts);
    }

    /** Quality gate identifiers for this stage (may be empty). */
    public List<String> getQualityGates() {
        return qualityGates == null ? List.of() : List.copyOf(qualityGates);
    }

    /** Agent IDs assigned to execute tasks in this stage. */
    public List<String> getAgentAssignments() {
        return agentAssignments == null ? List.of() : List.copyOf(agentAssignments);
    }

    @Override
    public String toString() {
        return "StageSpec{id='" + id + "', name='" + name + "', order=" + order + "}";
    }
}
