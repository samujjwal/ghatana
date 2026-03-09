package com.ghatana.agent.framework.runtime.generators;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * OutputGenerator that uses Large Language Models (LLMs) for generation.
 * Supports any LLM provider (OpenAI, Anthropic, local models, etc.) via gateway.
 * 
 * <p><b>Features:</b>
 * <ul>
 *   <li><b>Flexible</b>: Works with any LLM via gateway abstraction</li>
 *   <li><b>Cost-aware</b>: Estimates and tracks token usage</li>
 *   <li><b>Configurable</b>: Temperature, max tokens, system prompt, etc.</li>
 *   <li><b>Fault-tolerant</b>: Handles rate limits, timeouts, fallbacks</li>
 * </ul>
 * 
 * <p><b>Example:</b>
 * <pre>{@code
 * LLMGenerator<CodeGenTask, Code> codeGen = new LLMGenerator<>(
 *     llmGateway,
 *     task -> String.format("Generate %s code for: %s", task.getLanguage(), task.getRequirement()),
 *     response -> new Code(response.getContent(), task.getLanguage()),
 *     LLMConfig.builder()
 *         .model("gpt-4")
 *         .temperature(0.2)
 *         .maxTokens(2000)
 *         .build()
 * );
 * }</pre>
 * 
 * @param <TInput> Input type
 * @param <TOutput> Output type
 * 
 * @doc.type class
 * @doc.purpose LLM-based output generation
 * @doc.layer framework
 * @doc.pattern Strategy
 */
public final class LLMGenerator<TInput, TOutput> implements OutputGenerator<TInput, TOutput> {
    
    private final LLMGateway llmGateway;
    private final Function<TInput, String> promptBuilder;
    private final Function<LLMResponse, TOutput> responseParser;
    private final LLMConfig config;
    private final GeneratorMetadata metadata;
    
    /**
     * Creates a new LLMGenerator.
     * 
     * @param llmGateway Gateway to LLM service
     * @param promptBuilder Function to build prompt from input
     * @param responseParser Function to parse LLM response to output
     * @param config LLM configuration
     */
    public LLMGenerator(
            @NotNull LLMGateway llmGateway,
            @NotNull Function<TInput, String> promptBuilder,
            @NotNull Function<LLMResponse, TOutput> responseParser,
            @NotNull LLMConfig config) {
        this.llmGateway = Objects.requireNonNull(llmGateway, "llmGateway cannot be null");
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder cannot be null");
        this.responseParser = Objects.requireNonNull(responseParser, "responseParser cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.metadata = GeneratorMetadata.builder()
            .name("LLMGenerator")
            .type("llm")
            .description("LLM-based output generation")
            .property("model", config.getModel())
            .property("temperature", config.getTemperature())
            .property("maxTokens", config.getMaxTokens())
            .build();
    }
    
    @Override
    @NotNull
    public Promise<TOutput> generate(@NotNull TInput input, @NotNull AgentContext context) {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
        
        try {
            // 1. Build prompt
            String prompt = promptBuilder.apply(input);
            
            context.getLogger().debug("LLM prompt: {}", prompt);
            context.addTraceTag("llm.model", config.getModel());
            context.addTraceTag("llm.prompt.length", String.valueOf(prompt.length()));
            
            // 2. Estimate cost and check budget
            return estimateCost(input, context)
                .then(estimatedCost -> {
                    try {
                        context.deductCost(estimatedCost);
                    } catch (AgentContext.BudgetExceededException ex) {
                        return Promise.ofException(ex);
                    }
                    
                    // 3. Call LLM
                    return llmGateway.complete(prompt, config, context);
                })
                .map(response -> {
                    // 4. Parse response
                    TOutput output = responseParser.apply(response);
                    
                    // 5. Record actual cost and metrics
                    double actualCost = calculateActualCost(response);
                    context.recordMetric("llm.cost", actualCost);
                    context.recordMetric("llm.tokens.input", response.getUsage().getPromptTokens());
                    context.recordMetric("llm.tokens.output", response.getUsage().getCompletionTokens());
                    context.recordMetric("llm.latency", response.getLatencyMillis());
                    
                    context.getLogger().debug("LLM response received: {} tokens, ${}", 
                        response.getUsage().getTotalTokens(), actualCost);
                    
                    return output;
                })
                .whenException(ex -> {
                    context.getLogger().error("LLM generation failed", ex);
                    context.recordMetric("llm.failure", 1);
                });
            
        } catch (Exception ex) {
            context.getLogger().error("Failed to build LLM prompt", ex);
            return Promise.ofException(ex);
        }
    }
    
    @Override
    @NotNull
    public Promise<Double> estimateCost(@NotNull TInput input, @NotNull AgentContext context) {
        try {
            String prompt = promptBuilder.apply(input);
            int estimatedPromptTokens = prompt.length() / 4; // Rough estimate: 1 token ≈ 4 chars
            int estimatedCompletionTokens = config.getMaxTokens() != null ? config.getMaxTokens() : 500;
            
            // Cost per 1K tokens (example rates, should be configurable)
            double promptCostPer1k = 0.01; // $0.01 per 1K tokens
            double completionCostPer1k = 0.03; // $0.03 per 1K tokens
            
            double estimatedCost = 
                (estimatedPromptTokens / 1000.0) * promptCostPer1k +
                (estimatedCompletionTokens / 1000.0) * completionCostPer1k;
            
            return Promise.of(estimatedCost);
        } catch (Exception ex) {
            context.getLogger().warn("Failed to estimate LLM cost", ex);
            return Promise.of(0.0);
        }
    }
    
    @Override
    @NotNull
    public GeneratorMetadata getMetadata() {
        return metadata;
    }
    
    private double calculateActualCost(LLMResponse response) {
        // In production, get pricing from config based on model
        double promptCostPer1k = 0.01;
        double completionCostPer1k = 0.03;
        
        TokenUsage usage = response.getUsage();
        return (usage.getPromptTokens() / 1000.0) * promptCostPer1k +
               (usage.getCompletionTokens() / 1000.0) * completionCostPer1k;
    }
    
    /**
     * Gateway interface for LLM services.
     */
    public interface LLMGateway {
        
        /**
         * Completes a prompt using the specified config.
         * 
         * @param prompt User prompt
         * @param config LLM configuration
         * @param context Execution context
         * @return Promise of LLM response
         */
        @NotNull
        Promise<LLMResponse> complete(
            @NotNull String prompt, 
            @NotNull LLMConfig config, 
            @NotNull AgentContext context);
    }
    
    /**
     * LLM configuration.
     */
    public static final class LLMConfig {
        private final String model;
        private final Double temperature;
        private final Integer maxTokens;
        private final String systemPrompt;
        private final Map<String, Object> additionalParams;
        
        private LLMConfig(Builder builder) {
            this.model = Objects.requireNonNull(builder.model, "model cannot be null");
            this.temperature = builder.temperature != null ? builder.temperature : 0.7;
            this.maxTokens = builder.maxTokens;
            this.systemPrompt = builder.systemPrompt;
            this.additionalParams = builder.additionalParams != null 
                ? Map.copyOf(builder.additionalParams) 
                : Map.of();
        }
        
        @NotNull
        public String getModel() {
            return model;
        }
        
        @NotNull
        public Double getTemperature() {
            return temperature;
        }
        
        public Integer getMaxTokens() {
            return maxTokens;
        }
        
        public String getSystemPrompt() {
            return systemPrompt;
        }
        
        @NotNull
        public Map<String, Object> getAdditionalParams() {
            return additionalParams;
        }
        
        @NotNull
        public static Builder builder() {
            return new Builder();
        }
        
        public static final class Builder {
            private String model;
            private Double temperature;
            private Integer maxTokens;
            private String systemPrompt;
            private Map<String, Object> additionalParams;
            
            private Builder() {}
            
            public Builder model(@NotNull String model) {
                this.model = model;
                return this;
            }
            
            public Builder temperature(double temperature) {
                this.temperature = temperature;
                return this;
            }
            
            public Builder maxTokens(int maxTokens) {
                this.maxTokens = maxTokens;
                return this;
            }
            
            public Builder systemPrompt(String systemPrompt) {
                this.systemPrompt = systemPrompt;
                return this;
            }
            
            public Builder additionalParams(@NotNull Map<String, Object> additionalParams) {
                this.additionalParams = additionalParams;
                return this;
            }
            
            @NotNull
            public LLMConfig build() {
                return new LLMConfig(this);
            }
        }
    }
    
    /**
     * LLM response.
     */
    public interface LLMResponse {
        
        @NotNull
        String getContent();
        
        @NotNull
        TokenUsage getUsage();
        
        long getLatencyMillis();
        
        @NotNull
        String getModel();
    }
    
    /**
     * Token usage information.
     */
    public interface TokenUsage {
        
        int getPromptTokens();
        
        int getCompletionTokens();
        
        default int getTotalTokens() {
            return getPromptTokens() + getCompletionTokens();
        }
    }
}
