package com.ghatana.agent.framework.llm;

import com.ghatana.agent.framework.runtime.generators.LLMGenerator;
import com.ghatana.ai.llm.DefaultLLMGateway;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.ai.llm.ToolAwareAnthropicCompletionService;
import com.ghatana.ai.llm.ToolAwareOpenAICompletionService;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpClient;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Production-ready LLM gateway builder for agent-framework.
 * 
 * @doc.type class
 * @doc.purpose Helper for building production LLM gateways with real providers
 * @doc.layer core
 * @doc.pattern Builder
 * 
 * <p>Simplifies creation of LLM gateways with OpenAI and other providers.</p>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * // Simple OpenAI gateway
 * LLMGenerator.LLMGateway gateway = ProductionLLMGatewayBuilder
 *     .withOpenAI(eventloop, httpClient, metrics)
 *     .apiKey(System.getenv("OPENAI_API_KEY"))
 *     .model("gpt-4")
 *     .build();
 * 
 * // Multi-provider with fallback
 * LLMGenerator.LLMGateway gateway = ProductionLLMGatewayBuilder
 *     .create(eventloop, metrics)
 *     .addOpenAI(httpClient, System.getenv("OPENAI_API_KEY"), "gpt-4")
 *     .primaryProvider("openai")
 *     .build();
 * }</pre>
 */
public class ProductionLLMGatewayBuilder {
    
    private static final Logger log = LoggerFactory.getLogger(ProductionLLMGatewayBuilder.class);
    
    private final Eventloop eventloop;
    private final MetricsCollector metrics;
    private final DefaultLLMGateway.Builder gatewayBuilder;
    
    private ProductionLLMGatewayBuilder(Eventloop eventloop, MetricsCollector metrics) {
        this.eventloop = Objects.requireNonNull(eventloop);
        this.metrics = Objects.requireNonNull(metrics);
        this.gatewayBuilder = DefaultLLMGateway.builder().metrics(metrics);
    }
    
    /**
     * Creates a builder for multi-provider gateway.
     * 
     * @param eventloop ActiveJ eventloop
     * @param metrics Metrics collector
     * @return Builder instance
     */
    @NotNull
    public static ProductionLLMGatewayBuilder create(
            @NotNull Eventloop eventloop,
            @NotNull MetricsCollector metrics) {
        return new ProductionLLMGatewayBuilder(eventloop, metrics);
    }
    
    /**
     * Quick builder for OpenAI-only gateway.
     * 
     * @param eventloop ActiveJ eventloop
     * @param httpClient HTTP client for API calls
     * @param metrics Metrics collector
     * @return OpenAI-specific builder
     */
    @NotNull
    public static OpenAIBuilder withOpenAI(
            @NotNull Eventloop eventloop,
            @NotNull HttpClient httpClient,
            @NotNull MetricsCollector metrics) {
        return new OpenAIBuilder(eventloop, httpClient, metrics);
    }
    
    /**
     * Adds OpenAI provider.
     * 
     * @param httpClient HTTP client
     * @param apiKey OpenAI API key
     * @param model Model name (e.g., "gpt-4")
     * @return This builder
     */
    @NotNull
    public ProductionLLMGatewayBuilder addOpenAI(
            @NotNull HttpClient httpClient,
            @NotNull String apiKey,
            @NotNull String model) {
        
        LLMConfiguration config = LLMConfiguration.builder()
                .apiKey(apiKey)
                .modelName(model)
                .temperature(0.7)
                .maxTokens(2000)
                .timeoutSeconds(30)
                .maxRetries(3)
                .build();
        
        ToolAwareOpenAICompletionService service = new ToolAwareOpenAICompletionService(
                config, httpClient, metrics);
        
        gatewayBuilder.addProvider("openai", service);
        log.info("Added OpenAI provider with model: {}", model);
        
        return this;
    }
    
    /**
     * Adds Anthropic provider.
     * 
     * @param httpClient HTTP client
     * @param apiKey Anthropic API key
     * @param model Model name (e.g., "claude-3-opus-20240229")
     * @return This builder
     */
    @NotNull
    public ProductionLLMGatewayBuilder addAnthropic(
            @NotNull HttpClient httpClient,
            @NotNull String apiKey,
            @NotNull String model) {
        
        LLMConfiguration config = LLMConfiguration.builder()
                .apiKey(apiKey)
                .modelName(model)
                .temperature(0.7)
                .maxTokens(4096)
                .timeoutSeconds(30)
                .maxRetries(3)
                .build();
        
        ToolAwareAnthropicCompletionService service = new ToolAwareAnthropicCompletionService(
                config, httpClient, metrics);
        
        gatewayBuilder.addProvider("anthropic", service);
        log.info("Added Anthropic provider with model: {}", model);
        
        return this;
    }
    
    /**
     * Sets the primary provider.
     * 
     * @param providerName Provider name
     * @return This builder
     */
    @NotNull
    public ProductionLLMGatewayBuilder primaryProvider(@NotNull String providerName) {
        gatewayBuilder.defaultProvider(providerName);
        return this;
    }
    
    /**
     * Sets fallback order.
     * 
     * @param providers Provider names in fallback order
     * @return This builder
     */
    @NotNull
    public ProductionLLMGatewayBuilder fallbackOrder(@NotNull List<String> providers) {
        gatewayBuilder.fallbackOrder(providers);
        return this;
    }
    
    /**
     * Builds the LLM gateway.
     * 
     * @return Configured gateway adapted for agent-framework
     */
    @NotNull
    public LLMGenerator.LLMGateway build() {
        LLMGateway underlyingGateway = gatewayBuilder.build();
        return LLMGatewayFactory.create(underlyingGateway);
    }
    
    /**
     * Simplified builder for OpenAI-only setup.
     */
    public static class OpenAIBuilder {
        private final Eventloop eventloop;
        private final HttpClient httpClient;
        private final MetricsCollector metrics;
        private String apiKey;
        private String model = "gpt-4";
        private double temperature = 0.7;
        private int maxTokens = 2000;
        
        OpenAIBuilder(Eventloop eventloop, HttpClient httpClient, MetricsCollector metrics) {
            this.eventloop = eventloop;
            this.httpClient = httpClient;
            this.metrics = metrics;
        }
        
        @NotNull
        public OpenAIBuilder apiKey(@NotNull String apiKey) {
            this.apiKey = apiKey;
            return this;
        }
        
        @NotNull
        public OpenAIBuilder model(@NotNull String model) {
            this.model = model;
            return this;
        }
        
        @NotNull
        public OpenAIBuilder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }
        
        @NotNull
        public OpenAIBuilder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }
        
        @NotNull
        public LLMGenerator.LLMGateway build() {
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalStateException("OpenAI API key is required");
            }
            
            LLMConfiguration config = LLMConfiguration.builder()
                    .apiKey(apiKey)
                    .modelName(model)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .timeoutSeconds(30)
                    .maxRetries(3)
                    .build();
            
            ToolAwareOpenAICompletionService service = new ToolAwareOpenAICompletionService(
                    config, httpClient, metrics);
            
            LLMGateway gateway = DefaultLLMGateway.builder()
                    .addProvider("openai", service)
                    .defaultProvider("openai")
                    .metrics(metrics)
                    .build();
            
            log.info("Created OpenAI gateway with model: {}", model);
            return LLMGatewayFactory.create(gateway);
        }
    }
}
