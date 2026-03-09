package com.ghatana.yappc.ai.agent;

import com.ghatana.ai.service.LLMService;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Base class for YAPPC AI agents.
 * 
 * <p>Provides common functionality for all AI agents using shared
 * libs/ai-integration and libs/agent-api.
 * 
 * @doc.type class
 * @doc.purpose Base AI agent implementation
 * @doc.layer product
 * @doc.pattern Abstract Base Class
 */
public abstract class BaseAgent {
    
    protected static final Logger LOG = LoggerFactory.getLogger(BaseAgent.class);
    
    protected final LLMService llmService;
    protected final String agentName;
    protected final String systemPrompt;
    
    public BaseAgent(
        @NotNull LLMService llmService,
        @NotNull String agentName,
        @NotNull String systemPrompt
    ) {
        this.llmService = llmService;
        this.agentName = agentName;
        this.systemPrompt = systemPrompt;
        LOG.info("Initialized agent: {}", agentName);
    }
    
    /**
     * Process a task with context.
     */
    @NotNull
    public Promise<String> process(@NotNull String task, @NotNull Map<String, Object> context) {
        LOG.debug("Processing task with agent {}: {}", agentName, task);
        return llmService.chat(systemPrompt, task);
    }
    
    /**
     * Executes the agent with given input.
     */
    @NotNull
    public Promise<String> execute(@NotNull String input) {
        LOG.debug("Executing agent {} with input: {}", agentName, input);
        return llmService.chat(systemPrompt, input);
    }
    
    /**
     * Executes the agent with context.
     */
    @NotNull
    public Promise<String> executeWithContext(
        @NotNull String input,
        @NotNull Map<String, Object> context
    ) {
        LOG.debug("Executing agent {} with context", agentName);
        return llmService.chat(systemPrompt, input);
    }
    
    /**
     * Gets the agent name.
     */
    @NotNull
    public String getName() {
        return agentName;
    }
    
    /**
     * Gets the system prompt.
     */
    @NotNull
    public String getSystemPrompt() {
        return systemPrompt;
    }
}
