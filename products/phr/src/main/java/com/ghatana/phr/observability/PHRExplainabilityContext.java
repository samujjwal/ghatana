package com.ghatana.phr.observability;

import com.ghatana.kernel.observability.ExplainabilityContext;

import java.util.*;

/**
 * Component for PHRExplainabilityContext
 *
 * @doc.type class
 * @doc.purpose Component for PHRExplainabilityContext
 * @doc.layer product
 * @doc.pattern Service
 */
public class PHRExplainabilityContext implements ExplainabilityContext {
    private final String decisionId;
    private final String agentId;
    private final String modelId;
    private final Map<String, Object> inputs;
    private final Map<String, Object> outputs;
    private final Map<String, Double> featureImportance;
    private String explanation;
    private double confidence;
    private long timestamp;

    public PHRExplainabilityContext(String decisionId, String agentId, String modelId) {
        this.decisionId = decisionId;
        this.agentId = agentId;
        this.modelId = modelId;
        this.inputs = new HashMap<>();
        this.outputs = new HashMap<>();
        this.featureImportance = new HashMap<>();
        this.explanation = "";
        this.confidence = 0.0;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public String getDecisionId() {
        return decisionId;
    }

    @Override
    public String getAgentId() {
        return agentId;
    }

    @Override
    public String getModelId() {
        return modelId;
    }

    @Override
    public Map<String, Object> getInputs() {
        return inputs;
    }

    @Override
    public Map<String, Object> getOutputs() {
        return outputs;
    }

    @Override
    public String getExplanation() {
        return explanation;
    }

    @Override
    public double getConfidence() {
        return confidence;
    }

    @Override
    public Map<String, Double> getFeatureImportance() {
        return featureImportance;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public void recordExplanation(String explanation) {
        this.explanation = explanation;
    }

    @Override
    public void recordFeatureImportance(String feature, double importance) {
        featureImportance.put(feature, importance);
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}
