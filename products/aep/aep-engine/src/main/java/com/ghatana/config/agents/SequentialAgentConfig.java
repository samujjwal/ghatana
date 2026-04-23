package com.ghatana.config.agents;

/**
 * @doc.type class
 * @doc.purpose Provides sequential agent config functionality.
 * @doc.layer product
 * @doc.pattern Configuration
 */
public class SequentialAgentConfig extends AgentConfig {

    public SequentialAgentConfig() {
    }

    @Override
    public <R> R accept(AgentConfigVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
