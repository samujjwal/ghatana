package com.ghatana.config.agents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AgentConfig {

    private String name;
    private String description;
    private String instruction;
    private String outputKey;
    private List<String> subAgents;
    private List<AgentConfig> components;
    private Map<String, Object> parameters;
    private List<String> afterAgentCallbacks;
    private List<String> tools;

    public AgentConfig() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public String getOutputKey() {
        return outputKey;
    }

    public void setOutputKey(String outputKey) {
        this.outputKey = outputKey;
    }

    public List<String> getSubAgents() {
        return subAgents;
    }

    public void setSubAgents(List<String> subAgents) {
        this.subAgents = subAgents;
    }

    public List<AgentConfig> getComponents() {
        if (components == null) {
            components = new ArrayList<>();
        }
        return components;
    }

    public void setComponents(List<AgentConfig> components) {
        this.components = components;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public List<String> getAfterAgentCallbacks() {
        return afterAgentCallbacks;
    }

    public void setAfterAgentCallbacks(List<String> afterAgentCallbacks) {
        this.afterAgentCallbacks = afterAgentCallbacks;
    }

    public List<String> getTools() {
        return tools;
    }

    public void setTools(List<String> tools) {
        this.tools = tools;
    }

    public interface AgentConfigVisitor<R> {
        R visit(LlmAgentConfig config);
        R visit(SequentialAgentConfig config);
        R visit(LoopAgentConfig config);
    }

    public abstract <R> R accept(AgentConfigVisitor<R> visitor);
}
