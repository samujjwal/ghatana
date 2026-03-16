package com.ghatana.agent.framework.llm;

import com.ghatana.agent.framework.runtime.generators.LLMGenerator;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.ai.llm.ToolAwareCompletionService;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.eventloop.Eventloop;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Factory for creating configured LLM gateways for agent-framework.
 * 
 * @doc.type class
 * @doc.purpose Factory for wiring agent-framework LLMGenerator with libs:ai-integration LLMGateway
 * @doc.layer core
 * @doc.pattern Factory
 * 
 * <p>Supports multiple providers:</p>
 * <ul>
 *   <li><b>OpenAI</b>: GPT-4, GPT-3.5-Turbo via OPENAI_API_KEY</li>
 *   <li><b>Anthropic</b>: Claude via ANTHROPIC_API_KEY</li>
 *   <li><b>Fallback</b>: Auto-fallback to secondary providers on failure</li>
 * </ul>
 * 
 * <h3>Configuration</h3>
 * <p>Set environment variables:</p>
 * <pre>{@code
 * export OPENAI_API_KEY=sk-...
 * export ANTHROPIC_API_KEY=sk-ant-...
 * export LLM_PRIMARY_PROVIDER=openai      # or anthropic
 * export LLM_FALLBACK_PROVIDERS=anthropic # comma-separated
 * export LLM_DEFAULT_MODEL=gpt-4          # model name
 * }</pre>
 * 
 * <h3>Usage</h3>
 * <pre>{@code
 * Eventloop eventloop = Eventloop.getCurrentEventloop();
 * LLMGenerator.LLMGateway gateway = LLMGatewayFactory.createDefault(eventloop);
 * }</pre>
 * 
 * <p><b>NOTE</b>: This factory creates mock implementations for testing purposes.
 * Real provider implementations require instantiating provider-specific services.</p>
 */
public final class LLMGatewayFactory {
    
    private static final Logger log = LoggerFactory.getLogger(LLMGatewayFactory.class);
    
    // Environment variable names
    private static final String ENV_OPENAI_API_KEY = "OPENAI_API_KEY";
    private static final String ENV_ANTHROPIC_API_KEY = "ANTHROPIC_API_KEY";
    private static final String ENV_PRIMARY_PROVIDER = "LLM_PRIMARY_PROVIDER";
    private static final String ENV_FALLBACK_PROVIDERS = "LLM_FALLBACK_PROVIDERS";
    private static final String ENV_DEFAULT_MODEL = "LLM_DEFAULT_MODEL";
    
    // Default values
    private static final String DEFAULT_PRIMARY_PROVIDER = "openai";
    private static final String DEFAULT_MODEL = "gpt-4";
    
    private LLMGatewayFactory() {
        // Static factory only
    }
    
    /**
     * Create default LLM gateway from environment configuration.
     * 
     * <p>Automatically configures providers based on available API keys.</p>
     * 
     * <p><b>IMPORTANT</b>: For production use, create provider-specific services
     * (OpenAICompletionService, AnthropicCompletionService) and use the
     * {@link #create(LLMGateway)} method to wrap them.</p>
     * 
     * @param eventloop ActiveJ eventloop for async operations
     * @param metrics Metrics collector for observability
     * @return Configured LLMGateway adapted for agent-framework
     * @throws IllegalStateException if no API keys are configured
     */
    @NotNull
    public static LLMGenerator.LLMGateway createDefault(
            @NotNull Eventloop eventloop,
            @NotNull MetricsCollector metrics) {
        
        Objects.requireNonNull(eventloop, "eventloop cannot be null");
        Objects.requireNonNull(metrics, "metrics cannot be null");
        
        log.info("Creating default LLM gateway from environment configuration");
        
        // Detect available providers
        Map<String, String> availableProviders = detectAvailableProviders();
        if (availableProviders.isEmpty()) {
            throw new IllegalStateException(
                "No LLM providers configured. Please set OPENAI_API_KEY or ANTHROPIC_API_KEY"
            );
        }
        
        log.info("Available LLM providers: {}", availableProviders.keySet());

        // Create an ActiveJ HTTP client bound to the eventloop
        io.activej.http.HttpClient httpClient = io.activej.http.HttpClient.create(eventloop);

        String primaryProvider = System.getenv(ENV_PRIMARY_PROVIDER);
        if (primaryProvider == null || primaryProvider.isEmpty()) {
            primaryProvider = availableProviders.containsKey("openai")
                    ? "openai" : availableProviders.keySet().iterator().next();
        }

        String defaultModel = System.getenv(ENV_DEFAULT_MODEL);
        if (defaultModel == null || defaultModel.isEmpty()) {
            defaultModel = DEFAULT_MODEL;
        }

        ProductionLLMGatewayBuilder builder = ProductionLLMGatewayBuilder.create(eventloop, metrics);

        if (availableProviders.containsKey("openai")) {
            String model = primaryProvider.equals("openai") ? defaultModel : DEFAULT_MODEL;
            builder.addOpenAI(httpClient, availableProviders.get("openai"), model);
            log.info("Wired OpenAI provider with model: {}", model);
        }

        if (availableProviders.containsKey("anthropic")) {
            String claudeModel = "claude-3-haiku-20240307"; // default Claude model
            builder.addAnthropic(httpClient, availableProviders.get("anthropic"), claudeModel);
            log.info("Wired Anthropic provider with model: {}", claudeModel);
        }

        String fallbackEnv = System.getenv(ENV_FALLBACK_PROVIDERS);
        if (fallbackEnv != null && !fallbackEnv.isEmpty()) {
            builder.fallbackOrder(List.of(fallbackEnv.split(",")));
        }

        return builder.primaryProvider(primaryProvider).build();
    }
    
    /**
     * Create LLM gateway wrapper around an existing libs:ai-integration gateway.
     * 
     * <p><b>Recommended for production</b>: Build a DefaultLLMGateway with
     * provider-specific services, then adapt it:</p>
     * 
     * <pre>{@code
     * // 1. Create provider-specific services
     * OpenAICompletionService openai = new OpenAICompletionService(...);
     * AnthropicCompletionService anthropic = new AnthropicCompletionService(...);
     * 
     * // 2. Build gateway with providers
     * LLMGateway gateway = DefaultLLMGateway.builder()
     *     .addProvider("openai", openai)
     *     .addProvider("anthropic", anthropic)
     *     .defaultProvider("openai")
     *     .fallbackOrder(List.of("openai", "anthropic"))
     *     .metrics(metricsCollector)
     *     .build();
     * 
     * // 3. Adapt for agent-framework
     * LLMGenerator.LLMGateway agentGateway = LLMGatewayFactory.create(gateway);
     * }</pre>
     * 
     * @param underlyingGateway Configured LLMGateway from libs:ai-integration
     * @return Adapted gateway for agent-framework
     */
    @NotNull
    public static LLMGenerator.LLMGateway create(@NotNull LLMGateway underlyingGateway) {
        Objects.requireNonNull(underlyingGateway, "underlyingGateway cannot be null");
        log.info("Creating agent-framework LLM gateway wrapper");
        return LLMGatewayAdapter.adapt(underlyingGateway);
    }
    
    /**
     * Detect available LLM providers from environment variables.
     * 
     * @return Map of provider name → API key for available providers
     */
    @NotNull
    private static Map<String, String> detectAvailableProviders() {
        Map<String, String> providers = new HashMap<>();
        
        String openaiKey = System.getenv(ENV_OPENAI_API_KEY);
        if (openaiKey != null && !openaiKey.isEmpty()) {
            providers.put("openai", openaiKey);
        }
        
        String anthropicKey = System.getenv(ENV_ANTHROPIC_API_KEY);
        if (anthropicKey != null && !anthropicKey.isEmpty()) {
            providers.put("anthropic", anthropicKey);
        }
        
        return providers;
    }
}

