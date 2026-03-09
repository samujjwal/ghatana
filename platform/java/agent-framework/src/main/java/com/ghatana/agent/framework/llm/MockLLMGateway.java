package com.ghatana.agent.framework.llm;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.runtime.generators.LLMGenerator;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Mock LLM gateway for testing agent-framework without real API calls.
 * 
 * @doc.type class
 * @doc.purpose Mock LLM implementation for testing
 * @doc.layer test
 * @doc.pattern Mock
 * 
 * <p>Returns predefined responses based on prompt keywords.</p>
 * 
 * <h3>Usage</h3>
 * <pre>{@code
 * LLMGenerator.LLMGateway mockGateway = MockLLMGateway.withDefaultResponses();
 * LLMGenerator<Input, Output> generator = new LLMGenerator<>(mockGateway, ...);
 * }</pre>
 */
public class MockLLMGateway implements LLMGenerator.LLMGateway {
    
    private final ResponseStrategy responseStrategy;
    
    /**
     * Strategy for generating mock responses.
     */
    @FunctionalInterface
    public interface ResponseStrategy {
        String generateResponse(String prompt, LLMGenerator.LLMConfig config);
    }
    
    public MockLLMGateway(ResponseStrategy responseStrategy) {
        this.responseStrategy = Objects.requireNonNull(responseStrategy, "responseStrategy cannot be null");
    }
    
    /**
     * Create mock gateway with default response strategy.
     * 
     * <p>Default strategy returns simple acknowledgments based on prompt keywords.</p>
     * 
     * @return Configured mock gateway
     */
    @NotNull
    public static MockLLMGateway withDefaultResponses() {
        return new MockLLMGateway((prompt, config) -> {
            // Simple keyword-based responses
            String lowerPrompt = prompt.toLowerCase();
            
            if (lowerPrompt.contains("architecture")) {
                return "Based on the requirements, I recommend a microservices architecture with event-driven communication.";
            } else if (lowerPrompt.contains("implementation") || lowerPrompt.contains("code")) {
                return "Here's the implementation approach: 1) Define interfaces, 2) Implement core logic, 3) Add error handling.";
            } else if (lowerPrompt.contains("test")) {
                return "Test cases should cover: happy path, edge cases, error conditions, and integration scenarios.";
            } else if (lowerPrompt.contains("deploy") || lowerPrompt.contains("ops")) {
                return "Deployment strategy: blue-green deployment with health checks and automated rollback.";
            } else {
                return "Acknowledged. Processing request...";
            }
        });
    }
    
    /**
     * Create mock gateway with custom response strategy.
     * 
     * @param strategy Custom response generation strategy
     * @return Configured mock gateway
     */
    @NotNull
    public static MockLLMGateway withStrategy(@NotNull ResponseStrategy strategy) {
        return new MockLLMGateway(strategy);
    }
    
    /**
     * Create mock gateway that always returns the same response.
     * 
     * @param fixedResponse Response to return for all prompts
     * @return Configured mock gateway
     */
    @NotNull
    public static MockLLMGateway withFixedResponse(@NotNull String fixedResponse) {
        return new MockLLMGateway((prompt, config) -> fixedResponse);
    }
    
    @Override
    @NotNull
    public Promise<LLMGenerator.LLMResponse> complete(
            @NotNull String prompt,
            @NotNull LLMGenerator.LLMConfig config,
            @NotNull AgentContext context) {
        
        Objects.requireNonNull(prompt, "prompt cannot be null");
        Objects.requireNonNull(config, "config cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Generate mock response
            String responseContent = responseStrategy.generateResponse(prompt, config);
            
            // Calculate mock token usage (rough estimate)
            int promptTokens = estimateTokens(prompt);
            int completionTokens = estimateTokens(responseContent);
            long latency = System.currentTimeMillis() - startTime;
            
            MockLLMResponse response = new MockLLMResponse(
                responseContent,
                new MockTokenUsage(promptTokens, completionTokens),
                latency,
                config.getModel()
            );
            
            return Promise.of(response);
            
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
    
    /**
     * Estimate token count (very rough approximation: ~4 chars per token).
     */
    private int estimateTokens(String text) {
        return text.length() / 4;
    }
    
    /**
     * Mock LLM response implementation.
     */
    private static class MockLLMResponse implements LLMGenerator.LLMResponse {
        
        private final String content;
        private final LLMGenerator.TokenUsage usage;
        private final long latencyMillis;
        private final String model;
        
        MockLLMResponse(String content, LLMGenerator.TokenUsage usage, long latencyMillis, String model) {
            this.content = content;
            this.usage = usage;
            this.latencyMillis = latencyMillis;
            this.model = model;
        }
        
        @Override
        @NotNull
        public String getContent() {
            return content;
        }
        
        @Override
        @NotNull
        public LLMGenerator.TokenUsage getUsage() {
            return usage;
        }
        
        @Override
        public long getLatencyMillis() {
            return latencyMillis;
        }
        
        @Override
        @NotNull
        public String getModel() {
            return model;
        }
    }
    
    /**
     * Mock token usage implementation.
     */
    private static class MockTokenUsage implements LLMGenerator.TokenUsage {
        
        private final int promptTokens;
        private final int completionTokens;
        
        MockTokenUsage(int promptTokens, int completionTokens) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
        }
        
        @Override
        public int getPromptTokens() {
            return promptTokens;
        }
        
        @Override
        public int getCompletionTokens() {
            return completionTokens;
        }
        
        @Override
        public int getTotalTokens() {
            return promptTokens + completionTokens;
        }
    }
}
