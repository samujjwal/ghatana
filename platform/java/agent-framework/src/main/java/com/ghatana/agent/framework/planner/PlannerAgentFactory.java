/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.framework.planner;

import com.ghatana.agent.AgentType;
import com.ghatana.agent.framework.config.AgentDefinition;
import com.ghatana.agent.framework.loader.AgentDefinitionLoader;
import com.ghatana.agent.framework.runtime.BaseAgent;
import com.ghatana.agent.framework.tools.FunctionTool;
import com.ghatana.agent.llm.LLMAgent;
import com.ghatana.agent.llm.LLMAgentConfig;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Factory for creating and configuring planner agents.
 *
 * <p>Provides tool registration, global context management, and agent creation
 * from YAML configuration files. This replaces the external AEP planner dependency
 * with a framework-level abstraction.
 *
 * @doc.type class
 * @doc.purpose Factory for planner agents with tool registry
 * @doc.layer framework
 * @doc.pattern Factory, Registry
 */
public class PlannerAgentFactory {

    private static final Logger log = LoggerFactory.getLogger(PlannerAgentFactory.class);

    private final Map<String, FunctionTool> registeredTools = new HashMap<>();
    private final Map<String, String> globalContext = new HashMap<>();
    private ChatLanguageModel llmModel;

    /**
     * Register a tool with the factory.
     *
     * @param toolName the unique tool identifier
     * @param tool     the FunctionTool instance
     */
    public void registerTool(String toolName, FunctionTool tool) {
        Objects.requireNonNull(toolName, "toolName must not be null");
        Objects.requireNonNull(tool, "tool must not be null");
        registeredTools.put(toolName, tool);
        log.debug("Registered tool: {}", toolName);
    }

    /**
     * Set a global context variable available to all agents.
     *
     * @param key   context variable name
     * @param value context variable value
     */
    public void registerGlobalContext(String key, String value) {
        Objects.requireNonNull(key, "key must not be null");
        globalContext.put(key, value);
        log.debug("Registered global context: {}={}", key, value);
    }

    /**
     * Register the LLM model to use for LLM-type agents created by this factory.
     *
     * <p>Required for creating agents of type {@link AgentType#LLM} or
     * {@link AgentType#PROBABILISTIC} via {@link #createAgent(String)}.
     *
     * @param llmModel LangChain4j ChatLanguageModel (e.g. OpenAiChatModel)
     */
    public void setLLMModel(ChatLanguageModel llmModel) {
        this.llmModel = Objects.requireNonNull(llmModel, "llmModel must not be null");
        log.info("Registered LLM model: {}", llmModel.getClass().getSimpleName());
    }

    /**
     * Create an agent from a YAML configuration file.
     *
     * <p>Loads an {@link AgentDefinition} from the given YAML path using
     * {@link AgentDefinitionLoader}, then instantiates the appropriate agent
     * type. For {@link AgentType#LLM} agents, an LLM model must be configured
     * via {@link #setLLMModel(ChatLanguageModel)} first.
     *
     * @param yamlPath path to the agent YAML definition file
     * @return a configured agent ready to process inputs
     * @throws IllegalArgumentException if the YAML file cannot be loaded
     * @throws IllegalStateException    if the agent type requires an LLM model
     *                                  that has not been configured
     */
    public BaseAgent<?, ?> createAgent(String yamlPath) {
        Objects.requireNonNull(yamlPath, "yamlPath must not be null");
        log.info("Creating agent from YAML: {}", yamlPath);

        AgentDefinitionLoader loader = new AgentDefinitionLoader();
        AgentDefinition definition;
        try {
            definition = loader.load(Path.of(yamlPath));
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Cannot load agent definition from: " + yamlPath, e);
        }

        log.info("Loaded agent definition: id={}, type={}, name={}",
                definition.getId(), definition.getType(), definition.getName());

        return buildAgent(definition);
    }

    /**
     * Instantiate a concrete agent from a loaded {@link AgentDefinition}.
     */
    private BaseAgent<?, ?> buildAgent(AgentDefinition definition) {
        AgentType type = definition.getType();

        if (type == AgentType.LLM || type == AgentType.PROBABILISTIC) {
            if (llmModel == null) {
                throw new IllegalStateException(
                        "LLM model not configured for agent type " + type + " (id=" +
                        definition.getId() + "). Call setLLMModel() before createAgent().");
            }

            LLMAgentConfig agentConfig = LLMAgentConfig.builder()
                    .systemPrompt(definition.getSystemPrompt() != null
                            ? definition.getSystemPrompt()
                            : "You are a helpful AI assistant.")
                    .build();

            // Inject any global context into the system prompt
            if (!globalContext.isEmpty()) {
                StringBuilder enrichedPrompt = new StringBuilder(agentConfig.getSystemPrompt());
                enrichedPrompt.append("\n\nContext:\n");
                globalContext.forEach((k, v) ->
                        enrichedPrompt.append(k).append(": ").append(v).append("\n"));
                agentConfig = LLMAgentConfig.builder()
                        .systemPrompt(enrichedPrompt.toString())
                        .build();
            }

            LLMAgent agent = new LLMAgent(definition.getId(), llmModel, agentConfig);
            log.info("Created LLMAgent: {}", definition.getId());
            return agent;
        }

        // For non-LLM agent types, delegate to the appropriate factory or throw
        // a descriptive error directing users to the right factory
        throw new IllegalArgumentException(
                "PlannerAgentFactory only creates LLM/PROBABILISTIC agents. " +
                "Agent '" + definition.getId() + "' has type=" + type + ". " +
                "Use the appropriate factory for this type.");
    }

    /**
     * Get all registered tools.
     *
     * @return unmodifiable copy of registered tools
     */
    public Map<String, FunctionTool> getRegisteredTools() {
        return Map.copyOf(registeredTools);
    }

    /**
     * Get all global context variables.
     *
     * @return unmodifiable copy of global context
     */
    public Map<String, String> getGlobalContext() {
        return Map.copyOf(globalContext);
    }

    /**
     * Get the number of registered tools.
     *
     * @return tool count
     */
    public int getToolCount() {
        return registeredTools.size();
    }
}
