package com.ghatana.config.agents;

/**
 * @doc.type class
 * @doc.purpose Provides loop agent config functionality.
 * @doc.layer product
 * @doc.pattern Configuration
 */
public class LoopAgentConfig extends AgentConfig {

    private Integer maxIterations;
    private String condition;

    public LoopAgentConfig() {
    }

    public Integer getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(Integer maxIterations) {
        this.maxIterations = maxIterations;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public void normalizeCondition() {
        if (condition != null) {
            condition = condition.trim();
        }
    }

    @Override
    public <R> R accept(AgentConfigVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
