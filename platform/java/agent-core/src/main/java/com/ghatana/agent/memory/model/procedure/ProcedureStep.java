/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.model.procedure;

/**
 * A single step within an {@link EnhancedProcedure}.
 *
 * @doc.type class
 * @doc.purpose Represents one actionable step in a learned agent procedure
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public class ProcedureStep {

    private String description;
    private int order;
    private String tool;
    private String expectedOutcome;

    /** No-arg constructor for serialization frameworks. */
    public ProcedureStep() {}

    public ProcedureStep(String description) {
        this.description = description;
    }

    public ProcedureStep(String description, int order) {
        this.description = description;
        this.order = order;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public String getExpectedOutcome() {
        return expectedOutcome;
    }

    public void setExpectedOutcome(String expectedOutcome) {
        this.expectedOutcome = expectedOutcome;
    }

    @Override
    public String toString() {
        return "ProcedureStep{order=" + order + ", description='" + description + "'}";
    }
}
