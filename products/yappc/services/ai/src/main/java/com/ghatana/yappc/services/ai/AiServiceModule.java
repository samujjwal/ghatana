/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC AI Service Module
 */
package com.ghatana.yappc.services.ai;

import com.ghatana.ai.llm.DefaultLLMGateway;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.ai.llm.OpenAICompletionService;
import com.ghatana.ai.llm.ToolAwareOpenAICompletionService;
import com.ghatana.ai.prompts.PromptTemplateManager;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.yappc.ai.canvas.CanvasService;
import com.ghatana.yappc.ai.router.AIModelRouter;
import com.ghatana.yappc.ai.router.AIRouterConfig;
import com.ghatana.yappc.ai.router.ModelSelector.SelectionStrategy;
import com.ghatana.yappc.ai.router.SemanticCache.CacheConfig;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import com.ghatana.yappc.ai.canvas.CanvasGenerationService;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ActiveJ DI module for YAPPC AI services.
 *
 * <p>Provides bindings for:
 * <ul>
 *   <li>{@link AIRouterConfig} — Multi-model routing configuration</li>
 *   <li>{@link AIModelRouter} — Intelligent model selection and routing</li>
 *   <li>{@link YAPPCAIService} — High-level AI facade (code gen, analysis, review)</li>
 *   <li>{@link CanvasService} — Canvas generation and management</li>
 *   <li>{@link CanvasGenerationService} — AI-powered canvas code generation</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose DI module for AI service layer
 * @doc.layer product
 * @doc.pattern Module
 */
public class AiServiceModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(AiServiceModule.class);

    @Override
    protected void configure() {
        logger.info("Configuring YAPPC AI Service DI bindings");
    }

    // ========== Platform Dependencies ==========

    /**
     * Provides MetricsCollector for AI service instrumentation.
     *
     * <p>Uses no-op implementation by default; override for production telemetry.</p>
     */
    @Provides
    MetricsCollector metricsCollector() {
        logger.info("Creating NoopMetricsCollector for AI service");
        return new NoopMetricsCollector();
    }

    /**
     * Provides PromptTemplateManager for prompt rendering and validation.
     */
    @Provides
    PromptTemplateManager promptTemplateManager() {
        logger.info("Creating PromptTemplateManager");
        return new PromptTemplateManager();
    }

    /**
     * Provides LLMGateway backed by environment-configured providers.
     *
     * <p>Reads {@code OPENAI_API_KEY} to configure an OpenAI provider.
     * Falls back to a no-op gateway when no key is set (development mode).</p>
     */
    @Provides
    LLMGateway llmGateway(MetricsCollector metrics) {
        String openAiKey = System.getenv("OPENAI_API_KEY");
        DefaultLLMGateway.Builder builder = DefaultLLMGateway.builder().metrics(metrics);

        if (openAiKey != null && !openAiKey.isBlank()) {
            LLMConfiguration config = LLMConfiguration.builder()
                    .apiKey(openAiKey)
                    .modelName(System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini"))
                    .temperature(0.7)
                    .maxTokens(2000)
                    .timeoutSeconds(30)
                    .maxRetries(3)
                    .build();
            builder.addProvider("openai", new ToolAwareOpenAICompletionService(config, null, metrics));
            builder.defaultProvider("openai");
            logger.info("AI service LLMGateway configured with OpenAI provider");
        } else {
            logger.warn("No OPENAI_API_KEY set — AI service running in dev mode without LLM provider");
            // Provide a stub that returns error promises so the injector still resolves
            builder.addProvider("stub", new ToolAwareOpenAICompletionService(
                    LLMConfiguration.builder().apiKey("stub").modelName("stub").build(), null, metrics));
            builder.defaultProvider("stub");
        }

        return builder.build();
    }

    /**
     * Provides default AI router configuration.
     *
     * <p>Uses task-based model selection with semantic caching enabled.
     * Override via system properties:
     * <ul>
     *   <li>{@code yappc.ai.model.default} — default model name</li>
     *   <li>{@code yappc.ai.cache.enabled} — enable/disable semantic cache</li>
     * </ul>
     */
    @Provides
    AIRouterConfig aiRouterConfig() {
        String defaultModel = System.getProperty("yappc.ai.model.default", "llama3.2");
        boolean cacheEnabled = Boolean.parseBoolean(
                System.getProperty("yappc.ai.cache.enabled", "true"));

        logger.info("AI Router config: model={}, cache={}", defaultModel, cacheEnabled);

        return AIRouterConfig.builder()
                .selectionStrategy(SelectionStrategy.TASK_BASED)
                .cacheConfig(CacheConfig.builder()
                        .enabled(cacheEnabled)
                        .build())
                .defaultModel(defaultModel)
                .build();
    }

    /**
     * Provides the multi-model AI router.
     *
     * <p>Supports Ollama (llama3.2, codellama, mistral, phi-3),
     * OpenAI, and Anthropic adapters with automatic fallback chains.</p>
     */
    @Provides
    AIModelRouter aiModelRouter(AIRouterConfig config) {
        logger.info("Creating AIModelRouter with strategy: {}", config.getSelectionStrategy());
        return new AIModelRouter(config);
    }

    /**
     * Provides the high-level YAPPC AI service.
     *
     * <p>Facade over AIModelRouter with simplified API for:
     * code generation, code analysis, code review, refactoring suggestions.</p>
     */
    @Provides
    YAPPCAIService yappcAiService() {
        logger.info("Creating YAPPCAIService with task-based routing");
        return YAPPCAIService.builder()
                .selectionStrategy(SelectionStrategy.TASK_BASED)
                .cacheEnabled(true)
                .build();
    }

    /**
     * Provides canvas generation and management service.
     */
    @Provides
    CanvasService canvasService() {
        logger.info("Creating CanvasService");
        return new CanvasService();
    }

    /**
     * Provides the AI-powered canvas code generation service.
     *
     * <p>Wires {@link LLMGateway}, {@link PromptTemplateManager}, and
     * {@link MetricsCollector} to produce production-ready code artifacts
     * from canvas designs.</p>
     */
    @Provides
    CanvasGenerationService canvasGenerationService(
            LLMGateway llmGateway,
            PromptTemplateManager promptTemplates,
            MetricsCollector metrics) {
        logger.info("Creating CanvasGenerationService backed by LLMGateway");
        return new CanvasGenerationService(llmGateway, promptTemplates, metrics);
    }
}
