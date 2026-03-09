package com.ghatana.config.agents;

public class SequentialAgentConfig extends AgentConfig {

    public SequentialAgentConfig() {
    }

    @Override
    public <R> R accept(AgentConfigVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
