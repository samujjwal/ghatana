package com.ghatana.config.agents;

public class LlmAgentConfig extends AgentConfig {

    private String model;
    private String includeContents;
    private Double temperature;
    private Integer maxTokens;
    private Double topP;
    private Double presencePenalty;
    private Double frequencyPenalty;
    private Integer maxRetries;
    private Boolean streaming;
    private String modelAlias;

    public LlmAgentConfig() {
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getIncludeContents() {
        return includeContents;
    }

    public void setIncludeContents(String includeContents) {
        this.includeContents = includeContents;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Double getPresencePenalty() {
        return presencePenalty;
    }

    public void setPresencePenalty(Double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public void setFrequencyPenalty(Double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Boolean getStreaming() {
        return streaming;
    }

    public void setStreaming(Boolean streaming) {
        this.streaming = streaming;
    }

    public String getModelAlias() {
        return modelAlias;
    }

    public void setModelAlias(String modelAlias) {
        this.modelAlias = modelAlias;
    }

    public void normalizeModelAlias() {
        if (model == null && modelAlias != null && !modelAlias.isEmpty()) {
            this.model = modelAlias;
        }
    }

    @Override
    public <R> R accept(AgentConfigVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
