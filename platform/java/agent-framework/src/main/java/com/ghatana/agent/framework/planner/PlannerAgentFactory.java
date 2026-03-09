/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.framework.planner;

import com.ghatana.agent.framework.runtime.BaseAgent;
import com.ghatana.agent.framework.tools.FunctionTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * Create an agent from a YAML configuration file.
     *
     * <p>Currently returns null as YAML agent loading requires additional
     * infrastructure (LLM gateway, output generators). Subclass or configure
     * to provide real implementations when the full agent runtime is available.
     *
     * @param yamlPath path to the agent YAML definition
     * @return the created agent, or null if creation fails
     */
    public BaseAgent<?, ?> createAgent(String yamlPath) {
        log.info("Creating agent from: {}", yamlPath);
        // TODO: Implement YAML-based agent creation when LLM gateway is integrated.
        // For now, return null - callers handle null gracefully.
        log.warn("Agent creation from YAML not yet implemented: {}", yamlPath);
        return null;
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
