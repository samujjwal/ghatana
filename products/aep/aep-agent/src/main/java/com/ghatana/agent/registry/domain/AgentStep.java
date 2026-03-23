/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.agent.registry.domain;

import java.time.Instant;
import java.util.Map;
import java.util.List;

/**
 * Represents an agent step in the pipeline execution.
 */
public class AgentStep {
    private String stepId;
    private String agentId;
    private String stepType;
    private Map<String, Object> configuration;
    private List<String> inputTypes;
    private List<String> outputTypes;
    private Instant createdAt;
    private Instant updatedAt;

    public AgentStep() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public AgentStep(String stepId, String agentId, String stepType) {
        this();
        this.stepId = stepId;
        this.agentId = agentId;
        this.stepType = stepType;
    }

    // Getters and setters
    public String getStepId() {
        return stepId;
    }

    /**
     * Alias for getStepId() for compatibility.
     */
    public String getId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
        this.updatedAt = Instant.now();
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
        this.updatedAt = Instant.now();
    }

    public String getStepType() {
        return stepType;
    }

    public void setStepType(String stepType) {
        this.stepType = stepType;
        this.updatedAt = Instant.now();
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Map<String, Object> configuration) {
        this.configuration = configuration;
        this.updatedAt = Instant.now();
    }

    public List<String> getInputTypes() {
        return inputTypes;
    }

    public void setInputTypes(List<String> inputTypes) {
        this.inputTypes = inputTypes;
        this.updatedAt = Instant.now();
    }

    public List<String> getOutputTypes() {
        return outputTypes;
    }

    public void setOutputTypes(List<String> outputTypes) {
        this.outputTypes = outputTypes;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentStep agentStep = (AgentStep) o;
        return stepId != null ? stepId.equals(agentStep.stepId) : agentStep.stepId == null;
    }

    @Override
    public int hashCode() {
        return stepId != null ? stepId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "AgentStep{" +
                "stepId='" + stepId + '\'' +
                ", agentId='" + agentId + '\'' +
                ", stepType='" + stepType + '\'' +
                '}';
    }
}
