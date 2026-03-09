package com.ghatana.agent.framework.llm;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.runtime.generators.LLMGenerator;
import com.ghatana.ai.llm.*;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Adapter that bridges agent-framework's LLMGenerator.LLMGateway interface
 * with libs:ai-integration's LLMGateway interface.
 * 
 * <p>Maps between:
 * <ul>
 *   <li>LLMGenerator.LLMGateway → com.ghatana.ai.llm.LLMGateway</li>
 *   <li>LLMGenerator.LLMConfig → CompletionRequest</li>
 *   <li>CompletionResult → LLMGenerator.LLMResponse</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose Adapter between agent framework and AI integration LLM interfaces
 * @doc.layer framework
 * @doc.pattern Adapter
 */
public class LLMGatewayAdapter implements LLMGenerator.LLMGateway {
    
    private static final Logger log = LoggerFactory.getLogger(LLMGatewayAdapter.class);
    
    private final com.ghatana.ai.llm.LLMGateway delegate;
    
    public LLMGatewayAdapter(@NotNull com.ghatana.ai.llm.LLMGateway delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
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
            // Convert LLMConfig → CompletionRequest
            CompletionRequest request = buildCompletionRequest(prompt, config);
            
            long startTime = System.currentTimeMillis();
            
            // Call underlying LLM gateway
            return delegate.complete(request)
                    .map(result -> {
                        long latency = System.currentTimeMillis() - startTime;
                        
                        // Convert CompletionResult → LLMResponse
                        LLMGenerator.LLMResponse response = new LLMResponseAdapter(result, latency);
                        return response;
                    })
                    .whenException(ex -> {
                        log.error("LLM completion failed for model: {}", config.getModel(), ex);
                        context.recordMetric("llm.adapter.failure", 1);
                    });
            
        } catch (Exception ex) {
            log.error("Failed to adapt LLM request", ex);
            return Promise.ofException(ex);
        }
    }
    
    /**
     * Builds CompletionRequest from LLMConfig and prompt.
     */
    private CompletionRequest buildCompletionRequest(String prompt, LLMGenerator.LLMConfig config) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // Add system prompt if present
        if (config.getSystemPrompt() != null && !config.getSystemPrompt().isEmpty()) {
            messages.add(ChatMessage.system(config.getSystemPrompt()));
        }
        
        // Add user prompt
        messages.add(ChatMessage.user(prompt));
        
        // Build request
        return CompletionRequest.builder()
                .model(config.getModel())
                .messages(messages)
                .temperature(config.getTemperature().floatValue())
                .maxTokens(config.getMaxTokens() != null ? config.getMaxTokens() : 2048)
                .build();
    }
    
    /**
     * Adapter that wraps CompletionResult as LLMResponse.
     */
    private static class LLMResponseAdapter implements LLMGenerator.LLMResponse {
        
        private final CompletionResult result;
        private final long latencyMillis;
        
        LLMResponseAdapter(CompletionResult result, long latencyMillis) {
            this.result = Objects.requireNonNull(result, "result cannot be null");
            this.latencyMillis = latencyMillis;
        }
        
        @Override
        @NotNull
        public String getContent() {
            return result.getText() != null ? result.getText() : "";
        }
        
        @Override
        @NotNull
        public LLMGenerator.TokenUsage getUsage() {
            return new TokenUsageAdapter(result);
        }
        
        @Override
        public long getLatencyMillis() {
            return latencyMillis;
        }
        
        @Override
        @NotNull
        public String getModel() {
            return result.getModelUsed() != null ? result.getModelUsed() : "unknown";
        }
    }
    
    /**
     * Adapter that wraps CompletionResult's token counts as TokenUsage.
     */
    private static class TokenUsageAdapter implements LLMGenerator.TokenUsage {
        
        private final CompletionResult result;
        
        TokenUsageAdapter(CompletionResult result) {
            this.result = Objects.requireNonNull(result, "result cannot be null");
        }
        
        @Override
        public int getPromptTokens() {
            return result.getPromptTokens();
        }
        
        @Override
        public int getCompletionTokens() {
            return result.getCompletionTokens();
        }
        
        @Override
        public int getTotalTokens() {
            return result.getTokensUsed();
        }
    }
    
    /**
     * Creates an adapter from the provided LLM gateway.
     * 
     * @param gateway The underlying LLM gateway
     * @return Adapted gateway for agent framework
     */
    @NotNull
    public static LLMGatewayAdapter adapt(@NotNull com.ghatana.ai.llm.LLMGateway gateway) {
        return new LLMGatewayAdapter(gateway);
    }
}
